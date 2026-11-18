package org.polyfrost.keyframe.ui;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.game.inventory.AnvilScreen;
import net.minecraft.client.gui.screen.game.inventory.BeaconScreen;
import net.minecraft.client.gui.screen.game.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screen.game.inventory.ChestScreen;
import net.minecraft.client.gui.screen.game.inventory.CraftingTableScreen;
import net.minecraft.client.gui.screen.game.inventory.DispenserScreen;
import net.minecraft.client.gui.screen.game.inventory.EnchantingTableScreen;
import net.minecraft.client.gui.screen.game.inventory.FurnaceScreen;
import net.minecraft.client.gui.screen.game.inventory.HopperScreen;
import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.entity.living.player.PlayerInventory;
import net.minecraft.inventory.MenuInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.inventory.menu.EmptyMenuProvider;
import net.minecraft.inventory.menu.InventoryMenu;
import net.minecraft.inventory.menu.PlayerMenu;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import org.jspecify.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.polyfrost.keyframe.Keyframe;
import org.polyfrost.keyframe.mixin.ornithe.AccessorInventoryMenuScreenScreens;
import org.polyfrost.keyframe.ui.ScreenWire.Sample;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class ContainerScreenAction {

    private static final Set<String> REBUILDABLE = Set.of("minecraft:container", "minecraft:chest", "minecraft:hopper", "minecraft:furnace",
        "minecraft:brewing_stand", "minecraft:beacon", "minecraft:dispenser", "minecraft:dropper", "minecraft:crafting_table",
        "minecraft:enchanting_table", "minecraft:anvil");

    private static final byte CLOSED = 0;
    private static final byte FULL = 1;
    private static final byte DELTA = 2;

    private static @Nullable Action action;

    private static int openedMenu = -1;
    private static @Nullable String openedType;
    private static Text openedTitle = new LiteralText("");
    private static int openedSize;
    private static final Map<Integer, Integer> openedData = new LinkedHashMap<>();

    private static @Nullable Screen lastScreen;
    private static ItemStack[] lastSlots = new ItemStack[0];
    private static Map<Integer, Integer> lastData = Map.of();
    private static @Nullable ItemStack lastCarried;
    private static boolean wasOpen;
    private static final List<Sample> samples = new ArrayList<>();

    private static @Nullable Model model;
    private static List<Sample> replaySamples = List.of();
    private static @Nullable InventoryMenuScreen screen;
    private static @Nullable Model screenModel;
    private static @Nullable ClientPlayerEntity screenPlayer;

    public static void register() {
        action = Keyframe.action("action/container_screen_optional", buf -> accept(Payload.decode(buf)));
    }

    public static void onOpenMenu(int menuId, String type, Text title, int size) {
        openedMenu = menuId;
        openedType = type;
        openedTitle = title;
        openedSize = size;
        openedData.clear();
    }

    public static void onMenuData(int menuId, int id, int value) {
        if (menuId != openedMenu) {
            openedMenu = menuId;
            openedType = null;
            openedData.clear();
        }
        openedData.put(id, value);
    }

    public static boolean canRebuild(@Nullable Screen screen) {
        if (!(screen instanceof InventoryMenuScreen container)) {
            return false;
        }
        InventoryMenu menu = container.menu;
        if (menu instanceof PlayerMenu) {
            return screen instanceof SurvivalInventoryScreen;
        }
        return menu.networkId == openedMenu && openedType != null && REBUILDABLE.contains(openedType);
    }

    public static boolean recordMouse(Screen screen) {
        if (!ScreenCapture.recording() || !canRebuild(screen)) {
            return false;
        }
        AccessorInventoryMenuScreenScreens container = (AccessorInventoryMenuScreenScreens) screen;
        Minecraft minecraft = Minecraft.getInstance();
        Window window = new Window(minecraft);
        samples.add(new Sample(ScreenCapture.partial, (float) ScreenCapture.mouseX(minecraft, window) - container.keyframe$x(),
            (float) ScreenCapture.mouseY(minecraft, window) - container.keyframe$y()));
        return true;
    }

    public static void reset() {
        lastScreen = null;
        wasOpen = false;
        samples.clear();
    }

    public static void write(boolean chunkStart, AsyncReplaySaver... savers) {
        if (action == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.screen;
        Payload payload;
        if (!canRebuild(current)) {
            samples.clear();
            lastScreen = null;
            if (!wasOpen && !chunkStart) {
                return;
            }
            wasOpen = false;
            payload = new Payload(CLOSED, null, openedTitle, 0, List.of(), List.of(), null, List.of());
        } else {
            InventoryMenu menu = ((InventoryMenuScreen) current).menu;
            boolean player = menu instanceof PlayerMenu;
            Map<Integer, Integer> data = player ? Map.of() : Map.copyOf(openedData);
            boolean full = chunkStart || current != lastScreen;
            if (full) {
                lastSlots = new ItemStack[menu.slots.size()];
                lastData = Map.of();
                lastCarried = null;
            }
            List<SlotChange> slots = new ArrayList<>();
            for (int i = 0; i < lastSlots.length; i++) {
                ItemStack stack = menu.getSlot(i).getItem();
                if (full || !ItemStack.matches(lastSlots[i], stack)) {
                    slots.add(new SlotChange(i, ItemStack.copyOf(stack)));
                    lastSlots[i] = ItemStack.copyOf(stack);
                }
            }
            List<int[]> dataChanges = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : data.entrySet()) {
                if (full || !entry.getValue().equals(lastData.get(entry.getKey()))) {
                    dataChanges.add(new int[]{entry.getKey(), entry.getValue()});
                }
            }
            lastData = data;
            ItemStack cursor = minecraft.player.inventory.getCursorItem();
            boolean carriedChanged = full || !ItemStack.matches(lastCarried, cursor);
            if (carriedChanged) {
                lastCarried = ItemStack.copyOf(cursor);
            }
            payload = new Payload(full ? FULL : DELTA, player ? null : openedType, openedTitle, openedSize, slots, dataChanges,
                carriedChanged ? new Carried(lastCarried) : null, List.copyOf(samples));
            samples.clear();
            lastScreen = current;
            wasOpen = true;
        }

        Keyframe.submit(action, payload::encode, savers);
    }

    public static void accept(Payload payload) {
        if (payload.mode == CLOSED) {
            clearReplay();
            return;
        }
        if (payload.mode == FULL) {
            if (model == null || !Objects.equals(model.type, payload.menuType) || !model.title.equals(payload.title) || model.size != payload.size) {
                model = new Model(payload.menuType, payload.title, payload.size);
            }
            model.slots.clear();
            model.data.clear();
        }
        if (model == null) {
            return;
        }
        for (SlotChange change : payload.slots) {
            while (model.slots.size() <= change.index) {
                model.slots.add(null);
            }
            model.slots.set(change.index, change.stack);
        }
        for (int[] change : payload.data) {
            model.data.put(change[0], change[1]);
        }
        if (payload.carried != null) {
            model.carried = payload.carried.stack();
        }
        if (!payload.samples.isEmpty()) {
            replaySamples = payload.samples;
        }
    }

    public static void clearReplay() {
        model = null;
        replaySamples = List.of();
        screen = null;
        screenModel = null;
        screenPlayer = null;
    }

    public static boolean isShowing() {
        return model != null && Flashback.isInReplay() && Keyframe.spectated() != null && Minecraft.getInstance().options.perspective == 0;
    }

    public static boolean isReplayScreen(Object candidate) {
        return candidate != null && candidate == screen;
    }

    public static void render(float partial) {
        if (!isShowing()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientPlayerEntity player = Keyframe.spectated();
        if (screen == null || screenModel != model || screenPlayer != player) {
            screen = build(model, player);
            screenModel = model;
            screenPlayer = player;
        }
        if (screen == null || minecraft.player == null) {
            return;
        }
        Window window = new Window(minecraft);
        if (screen.width != window.getWidth() || screen.height != window.getHeight()) {
            InventoryMenu open = minecraft.player.menu;
            screen.init(minecraft, window.getWidth(), window.getHeight());
            minecraft.player.menu = open;
        }

        InventoryMenu menu = screen.menu;
        for (int i = 0; i < model.slots.size() && i < menu.slots.size(); i++) {
            menu.getSlot(i).setItem(model.slots.get(i));
        }
        for (Map.Entry<Integer, Integer> entry : model.data.entrySet()) {
            menu.setData(entry.getKey(), entry.getValue());
        }

        AccessorInventoryMenuScreenScreens bounds = (AccessorInventoryMenuScreenScreens) screen;
        Sample sample = replaySamples.isEmpty() ? new Sample(0.0f, -1000.0f, -1000.0f) : ScreenCapture.latest(replaySamples, Sample::partial, partial);
        float mouseX = bounds.keyframe$x() + sample.x();
        float mouseY = bounds.keyframe$y() + sample.y();
        PlayerInventory inventory = minecraft.player.inventory;
        ItemStack cursor = inventory.getCursorItem();
        inventory.setCursorItem(model.carried);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);
        try {
            screen.render(Math.round(mouseX), Math.round(mouseY), partial);
        } finally {
            inventory.setCursorItem(cursor);
        }
        ScreenReplay.drawCursor(mouseX, mouseY);
    }

    private static @Nullable InventoryMenuScreen build(Model model, ClientPlayerEntity player) {
        if (model.type == null) {
            return new SurvivalInventoryScreen(player);
        }
        PlayerInventory inventory = player.inventory;
        Minecraft minecraft = Minecraft.getInstance();
        return switch (model.type) {
            case "minecraft:container" -> new ChestScreen(inventory, new SimpleInventory(model.title, model.size));
            case "minecraft:crafting_table" -> new CraftingTableScreen(inventory, minecraft.world);
            case "minecraft:enchanting_table" -> new EnchantingTableScreen(inventory, minecraft.world, new EmptyMenuProvider(model.type, model.title));
            case "minecraft:anvil" -> new AnvilScreen(inventory, minecraft.world);
            default -> {
                MenuInventory container = new MenuInventory(model.type, model.title, model.size);
                yield switch (model.type) {
                    case "minecraft:hopper" -> new HopperScreen(inventory, container);
                    case "minecraft:furnace" -> new FurnaceScreen(inventory, container);
                    case "minecraft:brewing_stand" -> new BrewingStandScreen(inventory, container);
                    case "minecraft:beacon" -> new BeaconScreen(inventory, container);
                    case "minecraft:dispenser", "minecraft:dropper" -> new DispenserScreen(inventory, container);
                    default -> new ChestScreen(inventory, container);
                };
            }
        };
    }

    private static final class Model {
        private final @Nullable String type;
        private final Text title;
        private final int size;
        private final List<ItemStack> slots = new ArrayList<>();
        private final Map<Integer, Integer> data = new LinkedHashMap<>();
        private @Nullable ItemStack carried;

        private Model(@Nullable String type, Text title, int size) {
            this.type = type;
            this.title = title;
            this.size = size;
        }
    }

    public record SlotChange(int index, @Nullable ItemStack stack) {
    }

    public record Carried(@Nullable ItemStack stack) {
    }

    public record Payload(byte mode, @Nullable String menuType, Text title, int size, List<SlotChange> slots, List<int[]> data,
                          @Nullable Carried carried, List<Sample> samples) {

        private void encode(PacketByteBuf buf) {
            buf.writeByte(this.mode);
            if (this.mode == CLOSED) {
                return;
            }
            try {
                if (this.mode == FULL) {
                    buf.writeBoolean(this.menuType != null);
                    if (this.menuType != null) {
                        buf.writeString(this.menuType);
                    }
                    buf.writeText(this.title);
                    buf.writeVarInt(this.size);
                }
                buf.writeVarInt(this.slots.size());
                for (SlotChange change : this.slots) {
                    buf.writeVarInt(change.index);
                    buf.writeItem(change.stack);
                }
                buf.writeVarInt(this.data.size());
                for (int[] change : this.data) {
                    buf.writeVarInt(change[0]);
                    buf.writeVarInt(change[1]);
                }
                buf.writeBoolean(this.carried != null);
                if (this.carried != null) {
                    buf.writeItem(this.carried.stack());
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            buf.writeVarInt(this.samples.size());
            for (Sample sample : this.samples) {
                ScreenCapture.writeSample(buf, sample.partial(), sample.x(), sample.y());
            }
        }

        private static Payload decode(PacketByteBuf buf) {
            byte mode = buf.readByte();
            if (mode == CLOSED) {
                return new Payload(CLOSED, null, new LiteralText(""), 0, List.of(), List.of(), null, List.of());
            }
            String type = null;
            Text title = new LiteralText("");
            int size = 0;
            try {
                if (mode == FULL) {
                    type = buf.readBoolean() ? buf.readString(32767) : null;
                    title = buf.readText();
                    size = buf.readVarInt();
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            int slotCount = buf.readVarInt();
            List<SlotChange> slots = new ArrayList<>(slotCount);
            for (int i = 0; i < slotCount; i++) {
                slots.add(new SlotChange(buf.readVarInt(), ScreenReplay.readItem(buf)));
            }
            int dataCount = buf.readVarInt();
            List<int[]> data = new ArrayList<>(dataCount);
            for (int i = 0; i < dataCount; i++) {
                data.add(new int[]{buf.readVarInt(), buf.readVarInt()});
            }
            Carried carried = buf.readBoolean() ? new Carried(ScreenReplay.readItem(buf)) : null;
            int sampleCount = buf.readVarInt();
            List<Sample> samples = new ArrayList<>(sampleCount);
            for (int i = 0; i < sampleCount; i++) {
                samples.add(ScreenCapture.readSample(buf));
            }
            return new Payload(mode, type, title, size, slots, data, carried, samples);
        }
    }

}
