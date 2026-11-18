package org.polyfrost.keyframe.ui;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.Window;
import net.minecraft.client.render.platform.GlStateManager;
import net.minecraft.client.render.texture.Texture;
import net.minecraft.client.render.vertex.BufferBuilder;
import net.minecraft.client.render.vertex.VertexFormat;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.polyfrost.keyframe.mixin.ornithe.AccessorTextureManagerScreens;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class ScreenCapture extends ScreenWire {

    public static final int BLEND_NORMAL = 0;
    public static final int BLEND_INVERT = 1;
    public static final int BLEND_DARKEN = 2;

    private static final float GUI_DEPTH = 2000.0f;

    private static final List<Frame> frames = new ArrayList<>();
    private static @Nullable PacketByteBuf body;
    private static int count;
    private static boolean active;
    private static int flags;
    private static final BitSet groups = new BitSet();
    private static int groupDepth;
    public static float partial;
    private static final FloatBuffer MATRIX = BufferUtils.createFloatBuffer(16);
    private static final FloatBuffer COLOR = BufferUtils.createFloatBuffer(16);
    private static final Map<Integer, Identifier> textureNames = new HashMap<>();

    public static boolean recording() {
        Recorder recorder = Flashback.RECORDER;
        return recorder != null && !recorder.isPaused() && !Flashback.isInReplay();
    }

    public static boolean active() {
        return active && body != null;
    }

    public static void captureScreen(Screen screen, Runnable render) {
        boolean capture = !ContainerScreenAction.recordMouse(screen) && recording();
        if (capture) {
            beginLayer(0);
        }
        render.run();
        if (capture) {
            flags |= Frame.SCREEN;
            active = false;
        }
    }

    public static void beginLayer(int flag) {
        if (body == null) {
            body = new PacketByteBuf(Unpooled.buffer());
            count = 0;
        }
        flags |= flag;
        active = true;
    }

    public static void endLayer() {
        active = false;
    }

    public static void captureHud(Runnable render) {
        if (!recording()) {
            render.run();
            return;
        }
        boolean started = !active();
        if (started) {
            beginLayer(Frame.HUD);
        }
        beginGroup(GROUP_PLAIN);
        try {
            render.run();
        } finally {
            endGroup();
            if (started) {
                endLayer();
            }
        }
    }

    public static void opaque(Runnable render) {
        opaqueValue(() -> {
            render.run();
            return null;
        });
    }

    public static <T> T opaqueValue(Supplier<T> render) {
        boolean previous = active;
        active = false;
        try {
            return render.get();
        } finally {
            active = previous;
        }
    }

    public static void endFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        active = false;
        groups.clear();
        groupDepth = 0;
        if (body == null) {
            flags = 0;
            return;
        }
        PacketByteBuf out = new PacketByteBuf(Unpooled.buffer());
        out.writeVarInt(count);
        out.writeBytes(body);
        byte[] ops = new byte[out.readableBytes()];
        out.readBytes(ops);
        Window window = new Window(minecraft);
        frames.add(new Frame(partial, (float) mouseX(minecraft, window), (float) mouseY(minecraft, window), window.getWidth(), window.getHeight(), window.getScale(), flags, ops));
        body.release();
        body = null;
        flags = 0;
    }

    public static List<Frame> take() {
        List<Frame> taken = new ArrayList<>(frames);
        frames.clear();
        return taken;
    }

    public static void clear() {
        frames.clear();
        body = null;
        active = false;
        flags = 0;
        groups.clear();
        groupDepth = 0;
    }

    public static double mouseX(Minecraft minecraft, Window window) {
        return Mouse.getX() * window.getScaledWidth() / minecraft.width;
    }

    public static double mouseY(Minecraft minecraft, Window window) {
        return window.getScaledHeight() - Mouse.getY() * window.getScaledHeight() / minecraft.height - 1.0;
    }

    private static float[] pose(float z) {
        MATRIX.clear();
        GlStateManager.getFloat(GL11.GL_MODELVIEW_MATRIX, MATRIX);
        return new float[]{MATRIX.get(0), MATRIX.get(1), MATRIX.get(4), MATRIX.get(5), MATRIX.get(12), MATRIX.get(13), MATRIX.get(14) + GUI_DEPTH + z};
    }

    private static int color() {
        COLOR.clear();
        GlStateManager.getFloat(GL11.GL_CURRENT_COLOR, COLOR);
        return channel(COLOR.get(3)) << 24 | channel(COLOR.get(0)) << 16 | channel(COLOR.get(1)) << 8 | channel(COLOR.get(2));
    }

    private static int channel(float value) {
        return Math.clamp(Math.round(value * 255.0f), 0, 255);
    }

    private static int blend() {
        if (!GL11.glIsEnabled(GL11.GL_BLEND)) {
            return BLEND_NORMAL;
        }
        int src = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int dst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        if (dst == GL11.GL_ONE_MINUS_SRC_COLOR) {
            return src == GL11.GL_ONE_MINUS_DST_COLOR ? BLEND_INVERT : src == GL11.GL_ZERO ? BLEND_DARKEN : BLEND_NORMAL;
        }
        return BLEND_NORMAL;
    }

    private static @Nullable Identifier boundTexture() {
        int id = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        Identifier name = textureNames.get(id);
        if (name == null) {
            textureNames.clear();
            Map<Identifier, Texture> textures = ((AccessorTextureManagerScreens) Minecraft.getInstance().getTextureManager()).keyframe$textures();
            for (Map.Entry<Identifier, Texture> entry : textures.entrySet()) {
                textureNames.put(entry.getValue().getGlId(), entry.getKey());
            }
            name = textureNames.get(id);
        }
        return name;
    }

    private static PacketByteBuf op(int type, float[] pose) {
        PacketByteBuf buf = body;
        buf.writeByte(type);
        count++;
        writePose(buf, pose);
        return buf;
    }

    public static void beginGroup(int kind, float x0, float y0, float x1, float y1) {
        if (beginGroup(kind, true)) {
            writeBounds(body, pose(0.0f), 0.0f, 0.0f, x0, y0, x1, y1);
        }
    }

    public static void group(boolean capture, int kind, Runnable render) {
        group(capture, () -> beginGroup(kind), render);
    }

    public static void group(boolean capture, float x0, float y0, float x1, float y1, Runnable render) {
        group(capture, () -> beginGroup(GROUP_PLAIN, x0, y0, x1, y1), render);
    }

    private static void group(boolean capture, Runnable begin, Runnable render) {
        if (!capture) {
            render.run();
            return;
        }
        begin.run();
        try {
            render.run();
        } finally {
            endGroup();
        }
    }

    public static void beginGroup(int kind) {
        beginGroup(kind, false);
    }

    private static boolean beginGroup(int kind, boolean rect) {
        boolean written = active();
        groups.set(groupDepth++, written);
        if (written) {
            PacketByteBuf buf = op(GROUP, IDENTITY);
            buf.writeByte(kind);
            buf.writeBoolean(rect);
        }
        return written;
    }

    public static void endGroup() {
        if (groupDepth == 0) {
            return;
        }
        if (groups.get(--groupDepth) && active()) {
            op(GROUP_END, IDENTITY);
        }
    }

    public static void fill(float z, int x0, int y0, int x1, int y1, int color1, int color2) {
        PacketByteBuf buf = op(FILL, pose(z));
        buf.writeShort(x0);
        buf.writeShort(y0);
        buf.writeShort(x1);
        buf.writeShort(y1);
        buf.writeInt(color1);
        buf.writeInt(color2);
    }

    public static void blit(float z, int x0, int x1, int y0, int y1, float u0, float u1, float v0, float v1) {
        Identifier texture = boundTexture();
        if (texture == null) {
            return;
        }
        PacketByteBuf buf = op(BLIT, pose(z));
        buf.writeByte(blend());
        buf.writeString(texture.toString());
        buf.writeShort(x0);
        buf.writeShort(x1);
        buf.writeShort(y0);
        buf.writeShort(y1);
        buf.writeFloat(u0);
        buf.writeFloat(u1);
        buf.writeFloat(v0);
        buf.writeFloat(v1);
        buf.writeInt(color());
    }

    public static void text(String text, float x, float y, int color, boolean shadow) {
        PacketByteBuf buf = op(TEXT, pose(0.0f));
        buf.writeString(text);
        buf.writeFloat(x);
        buf.writeFloat(y);
        buf.writeInt(color);
        buf.writeBoolean(shadow);
    }

    public static void item(float z, ItemStack stack, int x, int y) {
        PacketByteBuf buf = op(ITEM, pose(z));
        buf.writeItem(stack);
        buf.writeShort(x);
        buf.writeShort(y);
    }

    public static void entity(int entityId, int x, int y, int size, float mouseX, float mouseY) {
        PacketByteBuf buf = op(ENTITY, pose(0.0f));
        buf.writeVarInt(entityId);
        buf.writeShort(x);
        buf.writeShort(y);
        buf.writeShort(size);
        buf.writeFloat(mouseX);
        buf.writeFloat(mouseY);
    }

    public static void panorama(float spin) {
        op(PANORAMA, IDENTITY).writeFloat(spin);
    }

    public static void mesh(BufferBuilder builder) {
        int vertices = builder.getVertexCount();
        VertexFormat format = builder.getFormat();
        if (vertices == 0 || format == null) {
            return;
        }
        boolean textured = format.hasUv(0) && GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        Identifier texture = textured ? boundTexture() : null;
        if (textured && texture == null) {
            return;
        }
        ByteBuffer data = builder.getBuffer();
        int stride = format.getVertexSize();
        int uv = textured ? format.getUvOffset(0) : 0;
        int colorOffset = format.hasColor() ? format.getColorOffset() : -1;
        int flat = colorOffset < 0 ? color() : 0;

        PacketByteBuf buf = op(MESH, pose(data.getFloat(8)));
        buf.writeByte(builder.getDrawMode());
        buf.writeByte(blend());
        buf.writeBoolean(GL11.glGetInteger(GL11.GL_SHADE_MODEL) == GL11.GL_SMOOTH);
        buf.writeBoolean(texture != null);
        if (texture != null) {
            buf.writeString(texture.toString());
        }
        buf.writeVarInt(vertices);
        for (int i = 0; i < vertices; i++) {
            int base = i * stride;
            buf.writeFloat(data.getFloat(base));
            buf.writeFloat(data.getFloat(base + 4));
            buf.writeFloat(textured ? data.getFloat(base + uv) : 0.0f);
            buf.writeFloat(textured ? data.getFloat(base + uv + 4) : 0.0f);
            if (colorOffset < 0) {
                buf.writeInt(flat);
            } else {
                int at = base + colorOffset;
                buf.writeInt((data.get(at + 3) & 0xFF) << 24 | (data.get(at) & 0xFF) << 16 | (data.get(at + 1) & 0xFF) << 8 | data.get(at + 2) & 0xFF);
            }
        }
    }

}
