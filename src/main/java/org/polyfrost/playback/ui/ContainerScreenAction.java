package org.polyfrost.playback.ui;

import org.polyfrost.playback.Playback;
import org.polyfrost.playback.ui.ScreenCapture.Sample;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
//? if >=1.21.2 {
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
//?} else
//import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ContainerScreenAction implements Action {

    public static final ContainerScreenAction INSTANCE = new ContainerScreenAction();
    private static final Identifier NAME = Identifier.fromNamespaceAndPath(Playback.MOD_ID, "action/container_screen_optional");

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<ItemStack>> OPTIONAL_ITEM = ByteBufCodecs.optional(ItemStack.OPTIONAL_STREAM_CODEC);

    private static final byte CLOSED = 0;
    private static final byte FULL = 1;
    private static final byte DELTA = 2;

    private static @Nullable Screen lastScreen;
    private static ItemStack[] lastSlots = new ItemStack[0];
    private static int[] lastData = new int[0];
    private static ItemStack lastCarried = ItemStack.EMPTY;
    private static boolean wasOpen;
    private static final List<Sample> samples = new ArrayList<>();

    private static @Nullable Model model;
    private static List<Sample> replaySamples = List.of();
    private static @Nullable AbstractContainerScreen<?> screen;
    private static @Nullable Model screenModel;
    private static @Nullable AbstractClientPlayer screenPlayer;

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        Playback.relay(replayServer, Payload.STREAM_CODEC.decode(buf));
    }

    public static boolean canRebuild(@Nullable Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container) || screen instanceof CreativeModeInventoryScreen) {
            return false;
        }
        //? if >=1.21.2 {
        if (screen instanceof AbstractRecipeBookScreen<?> book && book.recipeBookComponent.isVisible()) {
        //?} else
        //if (screen instanceof RecipeUpdateListener book && book.getRecipeBookComponent().isVisible()) {
            return false;
        }
        AbstractContainerMenu menu = container.getMenu();
        if (menu instanceof InventoryMenu) {
            return screen instanceof InventoryScreen;
        }
        MenuType<?> type = menu.menuType;
        if (type == null || MenuScreens.getConstructor(type) == null) {
            return false;
        }
        Identifier id = BuiltInRegistries.MENU.getKey(type);
        return id != null && id.getNamespace().equals("minecraft");
    }

    public static boolean recordMouse(Screen screen) {
        if (!ScreenCapture.recording() || !canRebuild(screen)) {
            return false;
        }
        AbstractContainerScreen<?> container = (AbstractContainerScreen<?>) screen;
        Minecraft minecraft = Minecraft.getInstance();
        samples.add(new Sample(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false),
            (float) ScreenCapture.mouseX(minecraft) - container.leftPos, (float) ScreenCapture.mouseY(minecraft) - container.topPos));
        return true;
    }

    public static void reset() {
        lastScreen = null;
        wasOpen = false;
        samples.clear();
    }

    public static void write(boolean chunkStart, AsyncReplaySaver... savers) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen current = minecraft.gui.screen();
        Payload payload;
        if (!canRebuild(current)) {
            samples.clear();
            lastScreen = null;
            if (!wasOpen && !chunkStart) {
                return;
            }
            wasOpen = false;
            payload = new Payload(CLOSED, null, Component.empty(), List.of(), List.of(), null, List.of());
        } else {
            AbstractContainerMenu menu = ((AbstractContainerScreen<?>) current).getMenu();
            boolean full = chunkStart || current != lastScreen;
            int[] data = menu.dataSlots.stream().mapToInt(slot -> slot.get()).toArray();
            if (full) {
                lastSlots = new ItemStack[menu.slots.size()];
                lastData = new int[0];
                lastCarried = ItemStack.EMPTY;
            }
            List<SlotChange> slots = new ArrayList<>();
            for (int i = 0; i < lastSlots.length; i++) {
                ItemStack stack = menu.slots.get(i).getItem();
                if (full || !ItemStack.matches(lastSlots[i], stack)) {
                    slots.add(new SlotChange(i, stack.copy()));
                    lastSlots[i] = stack.copy();
                }
            }
            List<int[]> dataChanges = new ArrayList<>();
            for (int i = 0; i < data.length; i++) {
                if (full || i >= lastData.length || lastData[i] != data[i]) {
                    dataChanges.add(new int[]{i, data[i]});
                }
            }
            lastData = data;
            ItemStack carried = null;
            if (full || !ItemStack.matches(lastCarried, menu.getCarried())) {
                carried = menu.getCarried().copy();
                lastCarried = carried;
            }
            Identifier type = menu instanceof InventoryMenu ? null : BuiltInRegistries.MENU.getKey(menu.menuType);
            payload = new Payload(full ? FULL : DELTA, type, current.getTitle(), slots, dataChanges, carried, List.copyOf(samples));
            samples.clear();
            lastScreen = current;
            wasOpen = true;
        }

        Playback.submit(INSTANCE, buf -> Payload.STREAM_CODEC.encode(buf, payload), savers);
    }

    public static void accept(Payload payload) {
        if (payload.mode == CLOSED) {
            clearReplay();
            return;
        }
        if (payload.mode == FULL) {
            if (model == null || !Objects.equals(model.type, payload.menuType) || !model.title.equals(payload.title)) {
                model = new Model(payload.menuType, payload.title);
            }
            model.slots.clear();
            model.data.clear();
        }
        if (model == null) {
            return;
        }
        for (SlotChange change : payload.slots) {
            while (model.slots.size() <= change.index) {
                model.slots.add(ItemStack.EMPTY);
            }
            model.slots.set(change.index, change.stack);
        }
        for (int[] change : payload.data) {
            while (model.data.size() <= change[0]) {
                model.data.add(0);
            }
            model.data.set(change[0], change[1]);
        }
        if (payload.carried != null) {
            model.carried = payload.carried;
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
        return model != null && Flashback.isInReplay() && Playback.spectated() != null && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    public static boolean isReplayScreen(Object candidate) {
        return candidate != null && candidate == screen;
    }

    public static void render(GuiGraphicsExtractor graphics, float partial) {
        if (!isShowing()) {
            return;
        }
        AbstractClientPlayer player = Playback.spectated();
        if (screen == null || screenModel != model || screenPlayer != player) {
            screen = build(model, player);
            screenModel = model;
            screenPlayer = player;
        }
        if (screen == null) {
            return;
        }
        if (screen.width != graphics.guiWidth() || screen.height != graphics.guiHeight()) {
            //? if >=1.21.11 {
            screen.init(graphics.guiWidth(), graphics.guiHeight());
            //?} else
            //screen.init(Minecraft.getInstance(), graphics.guiWidth(), graphics.guiHeight());
        }

        AbstractContainerMenu menu = screen.getMenu();
        for (int i = 0; i < model.slots.size() && i < menu.slots.size(); i++) {
            menu.slots.get(i).set(model.slots.get(i));
        }
        for (int i = 0; i < model.data.size() && i < menu.dataSlots.size(); i++) {
            menu.setData(i, model.data.get(i));
        }
        menu.setCarried(model.carried);

        Sample sample = replaySamples.isEmpty() ? new Sample(0.0f, -1000.0f, -1000.0f) : ScreenCapture.latest(replaySamples, Sample::partial, partial);
        float mouseX = screen.leftPos + sample.x();
        float mouseY = screen.topPos + sample.y();
        //? if >=1.21.10 {
        screen.extractRenderStateWithTooltipAndSubtitles(graphics, Math.round(mouseX), Math.round(mouseY), partial);
        //?} else
        //screen.renderWithTooltip(graphics, Math.round(mouseX), Math.round(mouseY), partial);
        ScreenReplay.drawCursor(graphics, mouseX, mouseY);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static @Nullable AbstractContainerScreen<?> build(Model model, AbstractClientPlayer player) {
        if (model.type == null) {
            return new InventoryScreen(player);
        }
        //? if >=1.21.2 {
        MenuType type = BuiltInRegistries.MENU.getValue(model.type);
        //?} else
        //MenuType type = BuiltInRegistries.MENU.get(model.type);
        MenuScreens.ScreenConstructor constructor = type == null ? null : MenuScreens.getConstructor(type);
        if (constructor == null) {
            return null;
        }
        Screen built = constructor.create(type.create(0, player.getInventory()), player.getInventory(), model.title);
        return built instanceof AbstractContainerScreen<?> container ? container : null;
    }

    private static final class Model {
        private final @Nullable Identifier type;
        private final Component title;
        private final List<ItemStack> slots = new ArrayList<>();
        private final List<Integer> data = new ArrayList<>();
        private ItemStack carried = ItemStack.EMPTY;

        private Model(@Nullable Identifier type, Component title) {
            this.type = type;
            this.title = title;
        }
    }

    public record SlotChange(int index, ItemStack stack) {
    }

    public record Payload(byte mode, @Nullable Identifier menuType, Component title, List<SlotChange> slots, List<int[]> data,
                          @Nullable ItemStack carried, List<Sample> samples) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Playback.MOD_ID, "container_screen"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of(Payload::encode, Payload::decode);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }

        private static void encode(RegistryFriendlyByteBuf buf, Payload payload) {
            buf.writeByte(payload.mode);
            if (payload.mode == CLOSED) {
                return;
            }
            if (payload.mode == FULL) {
                buf.writeNullable(payload.menuType, FriendlyByteBuf::writeIdentifier);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.title);
            }
            buf.writeVarInt(payload.slots.size());
            for (SlotChange change : payload.slots) {
                buf.writeVarInt(change.index);
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, change.stack);
            }
            buf.writeVarInt(payload.data.size());
            for (int[] change : payload.data) {
                buf.writeVarInt(change[0]);
                buf.writeVarInt(change[1]);
            }
            OPTIONAL_ITEM.encode(buf, Optional.ofNullable(payload.carried));
            buf.writeVarInt(payload.samples.size());
            for (Sample sample : payload.samples) {
                ScreenCapture.writeSample(buf, sample.partial(), sample.x(), sample.y());
            }
        }

        private static Payload decode(RegistryFriendlyByteBuf buf) {
            byte mode = buf.readByte();
            if (mode == CLOSED) {
                return new Payload(CLOSED, null, Component.empty(), List.of(), List.of(), null, List.of());
            }
            Identifier type = null;
            Component title = Component.empty();
            if (mode == FULL) {
                type = buf.readNullable(FriendlyByteBuf::readIdentifier);
                title = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            }
            int slotCount = buf.readVarInt();
            List<SlotChange> slots = new ArrayList<>(slotCount);
            for (int i = 0; i < slotCount; i++) {
                slots.add(new SlotChange(buf.readVarInt(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf)));
            }
            int dataCount = buf.readVarInt();
            List<int[]> data = new ArrayList<>(dataCount);
            for (int i = 0; i < dataCount; i++) {
                data.add(new int[]{buf.readVarInt(), buf.readVarInt()});
            }
            ItemStack carried = OPTIONAL_ITEM.decode(buf).orElse(null);
            int sampleCount = buf.readVarInt();
            List<Sample> samples = new ArrayList<>(sampleCount);
            for (int i = 0; i < sampleCount; i++) {
                samples.add(ScreenCapture.readSample(buf));
            }
            return new Payload(mode, type, title, slots, data, carried, samples);
        }
    }

}
