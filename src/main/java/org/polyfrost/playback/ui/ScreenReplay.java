package org.polyfrost.playback.ui;

import org.polyfrost.playback.Playback;

import com.moulberry.flashback.Flashback;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
//? if >=26.3 {
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
//?}
//? if >=26.1 {
import net.minecraft.client.renderer.state.gui.PanoramaRenderState;
//?} else
//import net.minecraft.client.renderer.CubeMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ScreenReplay {

    private interface Op {
        void run(GuiGraphicsExtractor graphics, float scale, UiLayout.Node node);
    }

    private record Draw(UiLayout.Node node, Op op) {
    }

    private record Decoded(UiLayout.Node root, List<Draw> draws) {
    }

    private record Resolved(ScreenCapture.Frame frame, Decoded decoded) {
    }

    private static List<Resolved> frames = List.of();
    private static @Nullable Decoded last;
    private static boolean open;

    public static void reset() {
        frames = List.of();
        last = null;
        open = false;
    }

    public static void accept(ScreenFramesAction.Payload payload) {
        if (!payload.open()) {
            reset();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        List<Resolved> resolved = new ArrayList<>(payload.frames().size());
        for (ScreenCapture.Frame frame : payload.frames()) {
            if (frame.ops() != null) {
                last = decode(new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(frame.ops()), minecraft.level.registryAccess()), frame.guiWidth(), frame.guiHeight());
            } else if (last == null) {
                last = new Decoded(UiLayout.root(frame.guiWidth(), frame.guiHeight()), List.of());
            }
            resolved.add(new Resolved(frame, last));
        }
        if (!resolved.isEmpty()) {
            frames = resolved;
            open = true;
        }
    }

    public static boolean isShowing() {
        return open && Playback.spectated() != null && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    public static boolean hidesHud() {
        return isShowing() && frames.getLast().frame().has(ScreenCapture.Frame.HUD);
    }

    public static boolean hidesChat() {
        return isShowing() && frames.getLast().frame().has(ScreenCapture.Frame.CHAT);
    }

    public static void render(GuiGraphicsExtractor graphics, float partial) {
        if (!isShowing()) {
            return;
        }
        Resolved resolved = ScreenCapture.latest(frames, r -> r.frame().partial(), partial);
        ScreenCapture.Frame frame = resolved.frame();
        float scale = UiLayout.fitScale(frame.guiWidth(), frame.guiHeight(), graphics.guiWidth(), graphics.guiHeight());
        float[] mouse = UiLayout.layout(resolved.decoded().root(), graphics.guiWidth() / scale, graphics.guiHeight() / scale, frame.mouseX(), frame.mouseY());
        for (Draw draw : resolved.decoded().draws()) {
            draw.op().run(graphics, scale, draw.node());
        }
        if (frame.has(ScreenCapture.Frame.SCREEN)) {
            drawCursor(graphics, mouse[0] * scale, mouse[1] * scale, scale / frame.guiScale());
        }
    }

    private static Decoded decode(RegistryFriendlyByteBuf buf, int guiWidth, int guiHeight) {
        UiLayout.Node root = UiLayout.root(guiWidth, guiHeight);
        List<UiLayout.Node> groups = new ArrayList<>();
        groups.add(root);
        int count = buf.readVarInt();
        List<Draw> draws = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int type = buf.readByte();
            float[] pose = readPose(buf);
            UiLayout.Node parent = groups.getLast();
            switch (type) {
                case ScreenCapture.FILL -> {
                    int kind = buf.readByte();
                    int x0 = buf.readShort();
                    int y0 = buf.readShort();
                    int x1 = buf.readShort();
                    int y1 = buf.readShort();
                    int color1 = buf.readInt();
                    int color2 = buf.readInt();
                    add(draws, parent, rect(UiLayout.STRETCHABLE, pose, x0, y0, x1, y1), (g, s, n) -> {
                        push(g, s, n, pose);
                        GuiCompat.fill(g, kind, x0, y0, x1, y1, color1, color2);
                        GuiCompat.pop(g);
                    });
                }
                case ScreenCapture.BLIT -> {
                    int kind = buf.readByte();
                    Identifier texture = buf.readIdentifier();
                    int x0 = buf.readShort();
                    int x1 = buf.readShort();
                    int y0 = buf.readShort();
                    int y1 = buf.readShort();
                    float u0 = buf.readFloat();
                    float u1 = buf.readFloat();
                    float v0 = buf.readFloat();
                    float v1 = buf.readFloat();
                    int color = buf.readInt();
                    add(draws, parent, rect(UiLayout.STRETCHABLE, pose, x0, y0, x1, y1), (g, s, n) -> {
                        float du = Math.abs(u1 - u0) > 1.0f ? (u1 - u0) * (stretchX(n, pose) - 1.0f) : 0.0f;
                        float dv = Math.abs(v1 - v0) > 1.0f ? (v1 - v0) * (stretchY(n, pose) - 1.0f) : 0.0f;
                        push(g, s, n, pose);
                        GuiCompat.blit(g, kind, texture, x0, x1, y0, y1, u0, u1 + du, v0, v1 + dv, color);
                        GuiCompat.pop(g);
                    });
                }
                case ScreenCapture.TEXT -> {
                    Component text = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    float textX = buf.readFloat();
                    float textY = buf.readFloat();
                    int color = buf.readInt();
                    boolean shadow = buf.readBoolean();
                    Font font = Minecraft.getInstance().font;
                    add(draws, parent, rect(0, pose, textX, textY, textX + font.width(text), textY + font.lineHeight), (g, s, n) -> {
                        push(g, s, n, pose);
                        GuiCompat.text(g, text, Math.round(textX), Math.round(textY), color, shadow);
                        GuiCompat.pop(g);
                    });
                }
                case ScreenCapture.ITEM -> {
                    ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    int itemX = buf.readShort();
                    int itemY = buf.readShort();
                    add(draws, parent, rect(0, pose, itemX, itemY, itemX + 16, itemY + 16), (g, s, n) -> {
                        push(g, s, n, pose);
                        GuiCompat.item(g, stack, itemX, itemY);
                        GuiCompat.pop(g);
                    });
                }
                case ScreenCapture.SCISSOR_ON -> {
                    int x0 = buf.readShort();
                    int y0 = buf.readShort();
                    int x1 = buf.readShort();
                    int y1 = buf.readShort();
                    //? if >=1.21.6 {
                    float[] clip = pose;
                    //?} else
                    //float[] clip = ScreenCapture.IDENTITY;
                    add(draws, parent, rect(UiLayout.STRETCHABLE, clip, x0, y0, x1, y1), (g, s, n) ->
                        GuiCompat.scissorOn(g, s * stretchX(n, clip), s * stretchY(n, clip), s * n.tx0, s * n.ty0, shift(n, clip), x0, y0, x1, y1));
                }
                case ScreenCapture.STRATUM -> add(draws, parent, new UiLayout.Node(0), (g, s, n) -> GuiCompat.stratum(g, false));
                case ScreenCapture.BLUR -> add(draws, parent, new UiLayout.Node(0), (g, s, n) -> GuiCompat.stratum(g, true));
                case ScreenCapture.PANORAMA -> {
                    float spin = buf.readFloat();
                    //? if >=26.1 {
                    PanoramaRenderState panorama = new PanoramaRenderState(spin);
                    add(draws, parent, new UiLayout.Node(0), (g, s, n) -> g.guiRenderState.panoramaRenderState = panorama);
                    //?} else
                    //add(draws, parent, new UiLayout.Node(0), (g, s, n) -> panorama(g, spin));
                }
                case ScreenCapture.SPYGLASS -> {
                    float scope = buf.readFloat();
                    //? if >=26.2 {
                    add(draws, parent, new UiLayout.Node(0), (g, s, n) -> Minecraft.getInstance().gui.hud.extractSpyglassOverlay(g, scope));
                    //?} elif >=26.1 {
                    /*add(draws, parent, new UiLayout.Node(0), (g, s, n) -> Minecraft.getInstance().gui.extractSpyglassOverlay(g, scope));
                    *///?} else
                    //add(draws, parent, new UiLayout.Node(0), (g, s, n) -> Minecraft.getInstance().gui.renderSpyglassOverlay(g, scope));
                }
                case ScreenCapture.SCISSOR_OFF -> add(draws, parent, new UiLayout.Node(0), (g, s, n) -> g.disableScissor());
                case ScreenCapture.ENTITY -> {
                    int entityId = buf.readVarInt();
                    int x0 = buf.readShort();
                    int y0 = buf.readShort();
                    int x1 = buf.readShort();
                    int y1 = buf.readShort();
                    int size = buf.readShort();
                    float offsetY = buf.readFloat();
                    float mouseX = buf.readFloat();
                    float mouseY = buf.readFloat();
                    add(draws, parent, rect(0, pose, x0, y0, x1, y1), (g, s, n) -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        if (minecraft.level != null && minecraft.level.getEntity(entityId) instanceof LivingEntity entity) {
                            push(g, s, n, pose);
                            InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, size, offsetY, mouseX, mouseY, entity);
                            GuiCompat.pop(g);
                        }
                    });
                }
                case ScreenCapture.MESH -> {
                    int pipeline = buf.readByte();
                    int texture = buf.readVarInt() - 1;
                    boolean nearest = buf.readBoolean();
                    ScreenRectangle scissor = buf.readBoolean() ? new ScreenRectangle(buf.readShort(), buf.readShort(), buf.readShort(), buf.readShort()) : null;
                    int vertices = buf.readVarInt();
                    float[] data = new float[vertices * 4];
                    int[] colors = new int[vertices];
                    UiLayout.Node node = new UiLayout.Node(0);
                    for (int v = 0; v < vertices; v++) {
                        data[v * 4] = buf.readFloat();
                        data[v * 4 + 1] = buf.readFloat();
                        data[v * 4 + 2] = buf.readFloat();
                        data[v * 4 + 3] = buf.readFloat();
                        colors[v] = buf.readInt();
                        include(node, pose, data[v * 4], data[v * 4 + 1]);
                    }
                    //? if >=26.3 {
                    add(draws, parent, node, (g, s, n) -> {
                        push(g, s, n, pose);
                        Matrix3x2f matrix = new Matrix3x2f(g.pose());
                        GuiCompat.pop(g);
                        GuiElementRenderState mesh = GuiMeshCapture.replay(pipeline, texture, nearest, matrix, scissor, data, colors);
                        if (mesh != null) {
                            g.guiRenderState.addGuiElement(mesh);
                        }
                    });
                    //?}
                }
                case ScreenCapture.GROUP -> {
                    int kind = buf.readByte();
                    UiLayout.Node group = new UiLayout.Node(kind == ScreenCapture.GROUP_TOOLTIP ? UiLayout.FOLLOW_MOUSE : UiLayout.STRETCHABLE);
                    if (buf.readBoolean()) {
                        group.include(buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
                    }
                    groups.add(parent.add(group));
                }
                case ScreenCapture.GROUP_END -> {
                    if (groups.size() > 1) {
                        groups.removeLast().close();
                    }
                }
                default -> throw new IllegalStateException("Unknown screen op " + type);
            }
        }
        while (groups.size() > 1) {
            groups.removeLast().close();
        }
        return new Decoded(root, draws);
    }

    private static void add(List<Draw> draws, UiLayout.Node parent, UiLayout.Node node, Op op) {
        draws.add(new Draw(parent.add(node), op));
    }

    private static UiLayout.Node rect(int flags, float[] pose, float x0, float y0, float x1, float y1) {
        UiLayout.Node node = new UiLayout.Node(flags);
        include(node, pose, x0, y0);
        include(node, pose, x1, y0);
        include(node, pose, x0, y1);
        include(node, pose, x1, y1);
        return node;
    }

    private static void include(UiLayout.Node node, float[] pose, float x, float y) {
        float px = pose[0] * x + pose[2] * y + pose[4];
        float py = pose[1] * x + pose[3] * y + pose[5];
        node.include(px, py, px, py);
    }

    private static void push(GuiGraphicsExtractor graphics, float scale, UiLayout.Node node, float[] pose) {
        GuiCompat.push(graphics, scale * stretchX(node, pose), scale * stretchY(node, pose), scale * node.tx0, scale * node.ty0, shift(node, pose));
    }

    private static float[] shift(UiLayout.Node node, float[] pose) {
        return new float[]{pose[0], pose[1], pose[2], pose[3], pose[4] - node.x0, pose[5] - node.y0, pose[6]};
    }

    private static float stretchX(UiLayout.Node node, float[] pose) {
        return node.stretchedX() && node.x1 > node.x0 && pose[1] == 0.0f && pose[2] == 0.0f ? (node.tx1 - node.tx0) / (node.x1 - node.x0) : 1.0f;
    }

    private static float stretchY(UiLayout.Node node, float[] pose) {
        return node.stretchedY() && node.y1 > node.y0 && pose[1] == 0.0f && pose[2] == 0.0f ? (node.ty1 - node.ty0) / (node.y1 - node.y0) : 1.0f;
    }

    //? if <26.1 {
    /*private static @Nullable CubeMap cubeMap;

    private static void panorama(GuiGraphicsExtractor graphics, float spin) {
        if (cubeMap == null) {
            cubeMap = new CubeMap(Identifier.withDefaultNamespace("textures/gui/title/background/panorama"));
        }
        //? if >=1.21.6 {
        cubeMap.render(Minecraft.getInstance(), 10.0f, spin);
        //?} else {
        /^graphics.flush();
        cubeMap.render(Minecraft.getInstance(), 10.0f, spin, 1.0f);
        ^///?}
    }
    *///?}

    private static float[] readPose(RegistryFriendlyByteBuf buf) {
        return switch (buf.readByte()) {
            case 0 -> ScreenCapture.IDENTITY;
            case 1 -> new float[]{1.0f, 0.0f, 0.0f, 1.0f, buf.readFloat(), buf.readFloat(), buf.readFloat()};
            default -> new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()};
        };
    }

    private static float pixelGuiScale() {
        float scale = (float) Minecraft.getInstance().getWindow().getGuiScale();
        return Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().ssaa() ? scale / 2.0f : scale;
    }

    public static void drawCursor(GuiGraphicsExtractor graphics, float x, float y) {
        drawCursor(graphics, x, y, 1.0f / pixelGuiScale());
    }

    private static void drawCursor(GuiGraphicsExtractor graphics, float x, float y, float scale) {
        SystemCursor.Loaded system = SystemCursor.get();
        if (system == null) {
            return;
        }
        int hotX = system.hotX();
        int hotY = system.hotY();
        GuiCompat.push(graphics, scale, x, y, ScreenCapture.IDENTITY);
        GuiCompat.blit(graphics, system.texture(), -hotX, system.width() - hotX, -hotY, system.height() - hotY, 0.0f, 1.0f, 0.0f, 1.0f, -1);
        GuiCompat.pop(graphics);
    }

}
