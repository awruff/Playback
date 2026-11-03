package org.polyfrost.playback.ui;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.polyfrost.playback.Playback;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.List;
import java.util.function.ToDoubleFunction;

public class ScreenCapture {

    public static final int FILL = 0;
    public static final int BLIT = 1;
    public static final int TEXT = 2;
    public static final int ITEM = 3;
    public static final int SCISSOR_ON = 4;
    public static final int SCISSOR_OFF = 5;
    public static final int ENTITY = 6;
    public static final int STRATUM = 7;
    public static final int BLUR = 8;
    public static final int MESH = 9;
    public static final int PANORAMA = 10;
    public static final int GROUP = 12;
    public static final int GROUP_END = 13;
    public static final int SPYGLASS = 14;

    public static final int GROUP_PLAIN = 0;
    public static final int GROUP_TOOLTIP = 1;

    public static final float[] IDENTITY = {1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f};
    private static final float COORDINATE_SCALE = 8.0f;

    public record Frame(float partial, float mouseX, float mouseY, int guiWidth, int guiHeight, int guiScale, int flags, byte @Nullable [] ops) {
        public static final int CHAT = 1;
        public static final int SCREEN = 2;
        public static final int HUD = 4;

        public boolean has(int flag) {
            return (this.flags & flag) != 0;
        }

        public Frame withoutOps() {
            return new Frame(this.partial, this.mouseX, this.mouseY, this.guiWidth, this.guiHeight, this.guiScale, this.flags, null);
        }
    }

    public record Sample(float partial, float x, float y) {
    }

    private static final List<Frame> frames = new ArrayList<>();
    private static @Nullable RegistryFriendlyByteBuf body;
    private static int count;
    private static boolean active;
    private static int flags;
    private static int blitKind = -1;
    private static final BitSet groups = new BitSet();
    private static int groupDepth;
    public static boolean disconnecting;
    private static boolean panorama;
    private static final Set<Object> shadowStates = Collections.newSetFromMap(new IdentityHashMap<>());
    private static float offsetX;
    private static float offsetY;
    private static float alpha = 1.0f;

    public static boolean recording() {
        Recorder recorder = Flashback.RECORDER;
        return recorder != null && !recorder.isPaused() && !Flashback.isInReplay();
    }

    public static boolean active() {
        return active && body != null;
    }

    public static boolean active(Object graphics) {
        //? if >=26.3 {
        return active() && capturesState(((GuiGraphicsExtractor) graphics).guiRenderState);
        //?} else
        //return active();
    }

    public static boolean capturesState(Object state) {
        //? if >=26.3 {
        return state == Minecraft.getInstance().gui.guiRenderState || shadowStates.contains(state);
        //?} else
        //return true;
    }

    public static boolean inShadow() {
        return !shadowStates.isEmpty();
    }

    public static float[] pushShadow(Object state, float x, float y, float layerAlpha) {
        float[] previous = {offsetX, offsetY, alpha};
        shadowStates.add(state);
        offsetX += x;
        offsetY += y;
        alpha *= layerAlpha;
        return previous;
    }

    public static void popShadow(Object state, float[] previous) {
        shadowStates.remove(state);
        offsetX = previous[0];
        offsetY = previous[1];
        alpha = previous[2];
    }

    public static int fade(int color, boolean premultiplied) {
        if (alpha >= 1.0f) {
            return color;
        }
        int a = Math.round((color >>> 24) * alpha);
        if (!premultiplied) {
            return a << 24 | color & 0xFFFFFF;
        }
        int r = Math.round((color >> 16 & 0xFF) * alpha);
        int g = Math.round((color >> 8 & 0xFF) * alpha);
        int b = Math.round((color & 0xFF) * alpha);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static void captureScreen(Screen screen, GuiGraphicsExtractor graphics, Runnable render) {
        boolean capture = !ContainerScreenAction.recordMouse(screen) && recording();
        if (capture) {
            beginLayer(false);
        }
        render.run();
        if (capture) {
            endScreen();
        }
    }

    public static void beginLayer(boolean isChat) {
        beginLayer(isChat ? Frame.CHAT : 0);
    }

    public static void beginHudLayer() {
        beginLayer(Frame.HUD);
    }

    private static void beginLayer(int flag) {
        if (Playback.registries == null) {
            return;
        }
        if (body == null) {
            body = new RegistryFriendlyByteBuf(Unpooled.buffer(), Playback.registries);
            count = 0;
        }
        flags |= flag;
        active = true;
    }

    public static void endLayer() {
        active = false;
    }

    public static void endScreen() {
        flags |= Frame.SCREEN;
        //? if >=26.3 {
        Minecraft minecraft = Minecraft.getInstance();
        if (active() && minecraft.level == null && !panorama) {
            panorama(-minecraft.gameRenderer.panorama().spin);
        }
        //?}
        active = false;
    }

    public static void captureHud(Runnable render) {
        if (!recording()) {
            render.run();
            return;
        }
        boolean started = !active();
        if (started) {
            beginHudLayer();
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

    public static void withBlitKind(int kind, Runnable render) {
        int previous = blitKind;
        blitKind = kind;
        try {
            render.run();
        } finally {
            blitKind = previous;
        }
    }

    public static void spyglass(float scale, Runnable render) {
        op(SPYGLASS, IDENTITY).writeFloat(scale);
        active = false;
        try {
            render.run();
        } finally {
            active = true;
        }
    }

    public static void endFrame() {
        Minecraft minecraft = Minecraft.getInstance();
        float partial = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        panorama = false;
        active = false;
        groups.clear();
        groupDepth = 0;
        if (body == null) {
            flags = 0;
            return;
        }
        RegistryFriendlyByteBuf out = new RegistryFriendlyByteBuf(Unpooled.buffer(), body.registryAccess());
        out.writeVarInt(count);
        out.writeBytes(body);
        byte[] ops = new byte[out.readableBytes()];
        out.readBytes(ops);
        frames.add(new Frame(partial, (float) mouseX(minecraft), (float) mouseY(minecraft), minecraft.getWindow().getGuiScaledWidth(), minecraft.getWindow().getGuiScaledHeight(),
            (int) minecraft.getWindow().getGuiScale(), flags, ops));
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
        panorama = false;
        flags = 0;
        groups.clear();
        groupDepth = 0;
    }

    public static double mouseX(Minecraft minecraft) {
        //? if >=1.21.8
        return minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        //? if <1.21.8
        //return minecraft.mouseHandler.xpos() * minecraft.getWindow().getGuiScaledWidth() / minecraft.getWindow().getScreenWidth();
    }

    public static double mouseY(Minecraft minecraft) {
        //? if >=1.21.8
        return minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        //? if <1.21.8
        //return minecraft.mouseHandler.ypos() * minecraft.getWindow().getGuiScaledHeight() / minecraft.getWindow().getScreenHeight();
    }

    public static void writeSample(FriendlyByteBuf buf, float partial, float x, float y) {
        buf.writeByte(Math.clamp(Math.round(partial * 255.0f), 0, 255));
        buf.writeShort(Math.clamp(Math.round(x * COORDINATE_SCALE), Short.MIN_VALUE, Short.MAX_VALUE));
        buf.writeShort(Math.clamp(Math.round(y * COORDINATE_SCALE), Short.MIN_VALUE, Short.MAX_VALUE));
    }

    public static Sample readSample(FriendlyByteBuf buf) {
        return new Sample(buf.readUnsignedByte() / 255.0f, buf.readShort() / COORDINATE_SCALE, buf.readShort() / COORDINATE_SCALE);
    }

    public static <T> T latest(List<T> items, ToDoubleFunction<T> partialOf, float partial) {
        T found = items.getFirst();
        for (T item : items) {
            if (partialOf.applyAsDouble(item) <= partial) {
                found = item;
            }
        }
        return found;
    }

    private static RegistryFriendlyByteBuf op(int type, float[] pose) {
        RegistryFriendlyByteBuf buf = body;
        buf.writeByte(type);
        count++;
        if (offsetX != 0.0f || offsetY != 0.0f) {
            pose = new float[]{pose[0], pose[1], pose[2], pose[3], pose[4] + offsetX, pose[5] + offsetY, pose[6]};
        }
        if (pose[0] == 1.0f && pose[1] == 0.0f && pose[2] == 0.0f && pose[3] == 1.0f) {
            if (pose[4] == 0.0f && pose[5] == 0.0f && pose[6] == 0.0f) {
                buf.writeByte(0);
            } else {
                buf.writeByte(1);
                buf.writeFloat(pose[4]);
                buf.writeFloat(pose[5]);
                buf.writeFloat(pose[6]);
            }
        } else {
            buf.writeByte(2);
            for (float value : pose) {
                buf.writeFloat(value);
            }
        }
        return buf;
    }

    public static void beginGroup(int kind, float[] pose, float x0, float y0, float x1, float y1) {
        if (beginGroup(kind, true)) {
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
            for (int corner = 0; corner < 4; corner++) {
                float x = (corner & 1) == 0 ? x0 : x1;
                float y = (corner & 2) == 0 ? y0 : y1;
                float px = pose[0] * x + pose[2] * y + pose[4] + offsetX;
                float py = pose[1] * x + pose[3] * y + pose[5] + offsetY;
                minX = Math.min(minX, px);
                minY = Math.min(minY, py);
                maxX = Math.max(maxX, px);
                maxY = Math.max(maxY, py);
            }
            body.writeFloat(minX);
            body.writeFloat(minY);
            body.writeFloat(maxX);
            body.writeFloat(maxY);
        }
    }

    public static void beginGroup(int kind) {
        beginGroup(kind, false);
    }

    private static boolean beginGroup(int kind, boolean rect) {
        boolean written = active();
        groups.set(groupDepth++, written);
        if (written) {
            RegistryFriendlyByteBuf buf = op(GROUP, IDENTITY);
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

    public static void fill(float[] pose, int kind, int x0, int y0, int x1, int y1, int color1, int color2) {
        RegistryFriendlyByteBuf buf = op(FILL, pose);
        buf.writeByte(kind);
        buf.writeShort(x0);
        buf.writeShort(y0);
        buf.writeShort(x1);
        buf.writeShort(y1);
        buf.writeInt(fade(color1, false));
        buf.writeInt(fade(color2, false));
    }

    public static void blit(float[] pose, int kind, Identifier texture, int x0, int x1, int y0, int y1, float u0, float u1, float v0, float v1, int color) {
        RegistryFriendlyByteBuf buf = op(BLIT, pose);
        buf.writeByte(blitKind >= 0 ? blitKind : kind);
        buf.writeIdentifier(texture);
        buf.writeShort(x0);
        buf.writeShort(x1);
        buf.writeShort(y0);
        buf.writeShort(y1);
        buf.writeFloat(u0);
        buf.writeFloat(u1);
        buf.writeFloat(v0);
        buf.writeFloat(v1);
        buf.writeInt(fade(color, false));
    }

    public static void text(float[] pose, FormattedCharSequence text, float x, float y, int color, boolean shadow) {
        text(pose, toComponent(text), x, y, color, shadow);
    }

    public static void text(float[] pose, Component text, float x, float y, int color, boolean shadow) {
        RegistryFriendlyByteBuf buf = op(TEXT, pose);
        ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, text);
        buf.writeFloat(x);
        buf.writeFloat(y);
        buf.writeInt(fade(color, false));
        buf.writeBoolean(shadow);
    }

    public static void item(float[] pose, ItemStack stack, int x, int y) {
        RegistryFriendlyByteBuf buf = op(ITEM, pose);
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
        buf.writeShort(x);
        buf.writeShort(y);
    }

    public static void marker(int type) {
        op(type, IDENTITY);
    }

    public static void panorama(float spin) {
        op(PANORAMA, IDENTITY).writeFloat(spin);
        panorama = true;
    }

    public static void scissor(float[] pose, boolean on, int x0, int y0, int x1, int y1) {
        RegistryFriendlyByteBuf buf = op(on ? SCISSOR_ON : SCISSOR_OFF, pose);
        if (on) {
            buf.writeShort(x0);
            buf.writeShort(y0);
            buf.writeShort(x1);
            buf.writeShort(y1);
        }
    }

    public static void entity(float[] pose, int entityId, int x0, int y0, int x1, int y1, int size, float offsetY, float mouseX, float mouseY) {
        RegistryFriendlyByteBuf buf = op(ENTITY, pose);
        buf.writeVarInt(entityId);
        buf.writeShort(x0);
        buf.writeShort(y0);
        buf.writeShort(x1);
        buf.writeShort(y1);
        buf.writeShort(size);
        buf.writeFloat(offsetY);
        buf.writeFloat(mouseX);
        buf.writeFloat(mouseY);
    }

    public static void mesh(int pipeline, int texture, boolean nearest, @Nullable ScreenRectangle scissor, int count, float[] data, int[] colors) {
        RegistryFriendlyByteBuf buf = op(MESH, IDENTITY);
        buf.writeByte(pipeline);
        buf.writeVarInt(texture + 1);
        buf.writeBoolean(nearest);
        buf.writeBoolean(scissor != null);
        if (scissor != null) {
            buf.writeShort(scissor.left());
            buf.writeShort(scissor.top());
            buf.writeShort(scissor.width());
            buf.writeShort(scissor.height());
        }
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeFloat(data[i * 4]);
            buf.writeFloat(data[i * 4 + 1]);
            buf.writeFloat(data[i * 4 + 2]);
            buf.writeFloat(data[i * 4 + 3]);
            buf.writeInt(fade(colors[i], pipeline == 2));
        }
    }

    public static Component toComponent(FormattedCharSequence text) {
        MutableComponent result = Component.empty();
        StringBuilder run = new StringBuilder();
        Style[] runStyle = {null};
        text.accept((index, style, codePoint) -> {
            if (runStyle[0] != null && !runStyle[0].equals(style)) {
                result.append(Component.literal(run.toString()).withStyle(runStyle[0]));
                run.setLength(0);
            }
            runStyle[0] = style;
            run.appendCodePoint(codePoint);
            return true;
        });
        if (run.length() > 0) {
            result.append(Component.literal(run.toString()).withStyle(runStyle[0]));
        }
        return result;
    }

}
