package org.polyfrost.keyframe.ui;

import io.netty.buffer.ByteBuf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

public abstract class ScreenWire {

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

    public static void writeSample(ByteBuf buf, float partial, float x, float y) {
        buf.writeByte(Math.clamp(Math.round(partial * 255.0f), 0, 255));
        buf.writeShort(Math.clamp(Math.round(x * COORDINATE_SCALE), Short.MIN_VALUE, Short.MAX_VALUE));
        buf.writeShort(Math.clamp(Math.round(y * COORDINATE_SCALE), Short.MIN_VALUE, Short.MAX_VALUE));
    }

    public static Sample readSample(ByteBuf buf) {
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

    public static void writePose(ByteBuf buf, float[] pose) {
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
    }

    public static float[] readPose(ByteBuf buf) {
        return switch (buf.readByte()) {
            case 0 -> IDENTITY;
            case 1 -> new float[]{1.0f, 0.0f, 0.0f, 1.0f, buf.readFloat(), buf.readFloat(), buf.readFloat()};
            default -> new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()};
        };
    }

    public static void writeBounds(ByteBuf buf, float[] pose, float dx, float dy, float x0, float y0, float x1, float y1) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (int corner = 0; corner < 4; corner++) {
            float x = (corner & 1) == 0 ? x0 : x1;
            float y = (corner & 2) == 0 ? y0 : y1;
            float px = pose[0] * x + pose[2] * y + pose[4] + dx;
            float py = pose[1] * x + pose[3] * y + pose[5] + dy;
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
        }
        buf.writeFloat(minX);
        buf.writeFloat(minY);
        buf.writeFloat(maxX);
        buf.writeFloat(maxY);
    }

    public static List<Frame> dedupe(byte @Nullable [] previous, List<Frame> captured) {
        List<Frame> frames = new ArrayList<>(captured.size());
        for (Frame frame : captured) {
            boolean same = previous != null && Arrays.equals(previous, frame.ops());
            previous = frame.ops();
            frames.add(same ? frame.withoutOps() : frame);
        }
        return frames;
    }

}
