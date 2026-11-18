package org.polyfrost.keyframe.ui;

import com.moulberry.flashback.Flashback;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiElement;
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen;
import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.client.render.TextRenderer;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.entity.ItemRenderer;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.platform.Lighting;
import net.minecraft.client.render.texture.DynamicTexture;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.DefaultVertexFormat;
import net.minecraft.client.render.vertex.Tesselator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.polyfrost.keyframe.Keyframe;
import org.polyfrost.keyframe.mixin.ornithe.AccessorScreenScreens;
import org.polyfrost.keyframe.mixin.ornithe.AccessorTitleScreenScreens;
import org.polyfrost.keyframe.standalone.playback.SystemCursor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

public class ScreenReplay {

    private interface Op {
        void run(float scale, UiLayout.Node node);
    }

    private record Draw(UiLayout.Node node, Op op) {
    }

    private record Decoded(UiLayout.Node root, List<Draw> draws) {
    }

    private record Resolved(ScreenCapture.Frame frame, Decoded decoded) {
    }

    private static final class Gradient extends GuiElement {
        private void fill(int x0, int y0, int x1, int y1, int color1, int color2) {
            this.fillGradient(x0, y0, x1, y1, color1, color2);
        }
    }

    private static final Gradient GRADIENT = new Gradient();
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);

    private static List<Resolved> frames = List.of();
    private static @Nullable Decoded last;
    private static boolean open;
    private static @Nullable TitleScreen title;

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
        if (Minecraft.getInstance().world == null) {
            return;
        }
        List<Resolved> resolved = new ArrayList<>(payload.frames().size());
        for (ScreenCapture.Frame frame : payload.frames()) {
            if (frame.ops() != null) {
                last = decode(new PacketByteBuf(Unpooled.wrappedBuffer(frame.ops())), frame.guiWidth(), frame.guiHeight());
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
        return open && Keyframe.spectated() != null && Minecraft.getInstance().options.perspective == 0;
    }

    public static boolean hidesHud() {
        return isShowing() && frames.getLast().frame().has(ScreenCapture.Frame.HUD);
    }

    public static boolean hidesChat() {
        return isShowing() && frames.getLast().frame().has(ScreenCapture.Frame.CHAT);
    }

    public static void render(float partial) {
        if (!isShowing()) {
            return;
        }
        Window window = new Window(Minecraft.getInstance());
        Resolved resolved = ScreenCapture.latest(frames, r -> r.frame().partial(), partial);
        ScreenCapture.Frame frame = resolved.frame();
        float scale = UiLayout.fitScale(frame.guiWidth(), frame.guiHeight(), window.getWidth(), window.getHeight());
        float[] mouse = UiLayout.layout(resolved.decoded().root(), window.getWidth() / scale, window.getHeight() / scale, frame.mouseX(), frame.mouseY());
        for (Draw draw : resolved.decoded().draws()) {
            draw.op().run(scale, draw.node());
        }
        if (frame.has(ScreenCapture.Frame.SCREEN)) {
            drawCursor(mouse[0] * scale, mouse[1] * scale, scale / frame.guiScale());
        }
    }

    private static Decoded decode(PacketByteBuf buf, int guiWidth, int guiHeight) {
        UiLayout.Node root = UiLayout.root(guiWidth, guiHeight);
        List<UiLayout.Node> groups = new ArrayList<>();
        groups.add(root);
        int count = buf.readVarInt();
        List<Draw> draws = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int type = buf.readByte();
            float[] pose = ScreenCapture.readPose(buf);
            UiLayout.Node parent = groups.getLast();
            switch (type) {
                case ScreenCapture.FILL -> {
                    int x0 = buf.readShort();
                    int y0 = buf.readShort();
                    int x1 = buf.readShort();
                    int y1 = buf.readShort();
                    int color1 = buf.readInt();
                    int color2 = buf.readInt();
                    add(draws, parent, UiLayout.rect(UiLayout.STRETCHABLE, pose, x0, y0, x1, y1), (s, n) -> {
                        push(s, n, pose);
                        if (color1 == color2) {
                            GuiElement.fill(x0, y0, x1, y1, color1);
                        } else {
                            GRADIENT.fill(x0, y0, x1, y1, color1, color2);
                        }
                        GlStateManager.popMatrix();
                    });
                }
                case ScreenCapture.BLIT -> {
                    int kind = buf.readByte();
                    Identifier texture = new Identifier(buf.readString(32767));
                    int x0 = buf.readShort();
                    int x1 = buf.readShort();
                    int y0 = buf.readShort();
                    int y1 = buf.readShort();
                    float u0 = buf.readFloat();
                    float u1 = buf.readFloat();
                    float v0 = buf.readFloat();
                    float v1 = buf.readFloat();
                    int color = buf.readInt();
                    add(draws, parent, UiLayout.rect(UiLayout.STRETCHABLE, pose, x0, y0, x1, y1), (s, n) -> {
                        float du = Math.abs(u1 - u0) > 1.0f ? (u1 - u0) * (UiLayout.stretchX(n, pose) - 1.0f) : 0.0f;
                        float dv = Math.abs(v1 - v0) > 1.0f ? (v1 - v0) * (UiLayout.stretchY(n, pose) - 1.0f) : 0.0f;
                        push(s, n, pose);
                        blit(kind, texture, x0, x1, y0, y1, u0, u1 + du, v0, v1 + dv, color);
                        GlStateManager.popMatrix();
                    });
                }
                case ScreenCapture.TEXT -> {
                    String text = buf.readString(32767);
                    float textX = buf.readFloat();
                    float textY = buf.readFloat();
                    int color = buf.readInt();
                    boolean shadow = buf.readBoolean();
                    TextRenderer font = Minecraft.getInstance().textRenderer;
                    add(draws, parent, UiLayout.rect(0, pose, textX, textY, textX + font.getWidth(text), textY + font.fontHeight), (s, n) -> {
                        push(s, n, pose);
                        font.draw(text, textX, textY, color, shadow);
                        GlStateManager.popMatrix();
                    });
                }
                case ScreenCapture.ITEM -> {
                    ItemStack stack = readItem(buf);
                    int itemX = buf.readShort();
                    int itemY = buf.readShort();
                    add(draws, parent, UiLayout.rect(0, pose, itemX, itemY, itemX + 16, itemY + 16), (s, n) -> {
                        if (stack != null) {
                            push(s, n, pose);
                            item(stack, itemX, itemY);
                            GlStateManager.popMatrix();
                        }
                    });
                }
                case ScreenCapture.ENTITY -> {
                    int entityId = buf.readVarInt();
                    int x = buf.readShort();
                    int y = buf.readShort();
                    int size = buf.readShort();
                    float mouseX = buf.readFloat();
                    float mouseY = buf.readFloat();
                    add(draws, parent, UiLayout.rect(0, pose, x - size, y - 2 * size, x + size, y), (s, n) -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        Entity entity = minecraft.world == null ? null : minecraft.world.getEntity(entityId);
                        if (entity instanceof LivingEntity living) {
                            push(s, n, pose);
                            SurvivalInventoryScreen.renderEntity(x, y, size, mouseX, mouseY, living);
                            GlStateManager.popMatrix();
                        }
                    });
                }
                case ScreenCapture.PANORAMA -> {
                    float spin = buf.readFloat();
                    add(draws, parent, new UiLayout.Node(0), (s, n) -> panorama(spin));
                }
                case ScreenCapture.MESH -> {
                    int mode = buf.readByte();
                    int kind = buf.readByte();
                    boolean smooth = buf.readBoolean();
                    Identifier texture = buf.readBoolean() ? new Identifier(buf.readString(32767)) : null;
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
                        UiLayout.include(node, pose, data[v * 4], data[v * 4 + 1]);
                    }
                    add(draws, parent, node, (s, n) -> {
                        push(s, n, pose);
                        mesh(mode, kind, smooth, texture, data, colors);
                        GlStateManager.popMatrix();
                    });
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

    static @Nullable ItemStack readItem(PacketByteBuf buf) {
        try {
            return buf.readItem();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void add(List<Draw> draws, UiLayout.Node parent, UiLayout.Node node, Op op) {
        draws.add(new Draw(parent.add(node), op));
    }

    private static void push(float scale, UiLayout.Node node, float[] pose) {
        push(scale * UiLayout.stretchX(node, pose), scale * UiLayout.stretchY(node, pose), scale * node.tx0, scale * node.ty0, UiLayout.shift(node, pose));
    }

    private static void push(float scaleX, float scaleY, float x, float y, float[] pose) {
        GlStateManager.pushMatrix();
        GlStateManager.translatef(x, y, 0.0f);
        GlStateManager.scalef(scaleX, scaleY, 1.0f);
        MATRIX.clear();
        MATRIX.put(new float[]{pose[0], pose[1], 0.0f, 0.0f, pose[2], pose[3], 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, pose[4], pose[5], pose[6], 1.0f});
        MATRIX.flip();
        GlStateManager.multMatrix(MATRIX);
    }

    private static void blend(int kind) {
        GlStateManager.enableBlend();
        switch (kind) {
            case ScreenCapture.BLEND_INVERT -> GlStateManager.blendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
            case ScreenCapture.BLEND_DARKEN -> GlStateManager.blendFuncSeparate(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
            default -> GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        }
    }

    private static void restore() {
        GlStateManager.blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);
        GlStateManager.color4f(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void blit(int kind, Identifier texture, int x0, int x1, int y0, int y1, float u0, float u1, float v0, float v1, int color) {
        Minecraft.getInstance().getTextureManager().bind(texture);
        blend(kind);
        GlStateManager.color4f((color >> 16 & 0xFF) / 255.0f, (color >> 8 & 0xFF) / 255.0f, (color & 0xFF) / 255.0f, (color >>> 24) / 255.0f);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuffer();
        builder.begin(GL11.GL_QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(x0, y1, 0.0).texture(u0, v1).nextVertex();
        builder.vertex(x1, y1, 0.0).texture(u1, v1).nextVertex();
        builder.vertex(x1, y0, 0.0).texture(u1, v0).nextVertex();
        builder.vertex(x0, y0, 0.0).texture(u0, v0).nextVertex();
        tesselator.end();
        restore();
    }

    private static void mesh(int mode, int kind, boolean smooth, @Nullable Identifier texture, float[] data, int[] colors) {
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().bind(texture);
        } else {
            GlStateManager.disableTexture();
        }
        blend(kind);
        GlStateManager.shadeModel(smooth ? GL11.GL_SMOOTH : GL11.GL_FLAT);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuffer();
        builder.begin(mode, texture != null ? DefaultVertexFormat.POSITION_TEX_COLOR : DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < colors.length; i++) {
            builder.vertex(data[i * 4], data[i * 4 + 1], 0.0);
            if (texture != null) {
                builder.texture(data[i * 4 + 2], data[i * 4 + 3]);
            }
            int color = colors[i];
            builder.color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, color >>> 24).nextVertex();
        }
        tesselator.end();
        GlStateManager.shadeModel(GL11.GL_FLAT);
        GlStateManager.enableTexture();
        restore();
    }

    private static void item(ItemStack stack, int x, int y) {
        ItemRenderer renderer = Minecraft.getInstance().getItemRenderer();
        float z = renderer.zOffset;
        renderer.zOffset = 0.0f;
        Lighting.turnOnGui();
        renderer.renderGuiItemModel(stack, x, y);
        Lighting.turnOff();
        renderer.zOffset = z;
    }

    private static void panorama(float spin) {
        Minecraft minecraft = Minecraft.getInstance();
        if (title == null) {
            title = new TitleScreen();
            ((AccessorScreenScreens) title).keyframe$setMinecraft(minecraft);
            ((AccessorTitleScreenScreens) title).keyframe$setBackgroundLocation(minecraft.getTextureManager().register("background", new DynamicTexture(256, 256)));
        }
        Window window = new Window(minecraft);
        title.width = window.getWidth();
        title.height = window.getHeight();
        ((AccessorTitleScreenScreens) title).keyframe$setTime((int) spin);
        GlStateManager.disableAlphaTest();
        ((AccessorTitleScreenScreens) title).keyframe$drawBackground(0, 0, spin - (int) spin);
        GlStateManager.enableAlphaTest();
    }

    private static float pixelGuiScale() {
        float scale = new Window(Minecraft.getInstance()).getScale();
        return Flashback.isExporting() && Flashback.EXPORT_JOB.getSettings().ssaa() ? scale / 2.0f : scale;
    }

    public static void drawCursor(float x, float y) {
        drawCursor(x, y, 1.0f / pixelGuiScale());
    }

    private static void drawCursor(float x, float y, float scale) {
        SystemCursor.Loaded system = SystemCursor.INSTANCE.get();
        if (system == null) {
            return;
        }
        int hotX = system.getHotX();
        int hotY = system.getHotY();
        push(scale, scale, x, y, ScreenCapture.IDENTITY);
        blit(ScreenCapture.BLEND_NORMAL, system.getTexture(), -hotX, system.getWidth() - hotX, -hotY, system.getHeight() - hotY, 0.0f, 1.0f, 0.0f, 1.0f, -1);
        GlStateManager.popMatrix();
    }

}
