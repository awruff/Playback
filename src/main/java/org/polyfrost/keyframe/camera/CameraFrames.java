package org.polyfrost.keyframe.camera;

import org.jspecify.annotations.Nullable;

import java.util.Arrays;

public final class CameraFrames {

    private CameraFrames() {
    }

    public static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) {
            wrapped -= 360.0f;
        }
        if (wrapped < -180.0f) {
            wrapped += 360.0f;
        }
        return wrapped;
    }

    public static int[] kept(short[] yaws, short[] pitches, float @Nullable [] fovs) {
        int count = yaws.length;
        int[] kept = new int[count];
        int size = 0;
        for (int i = 0; i < count; i++) {
            boolean sameAsPrevious = i > 0 && same(yaws, pitches, fovs, i, i - 1);
            boolean sameAsNext = i < count - 1 && same(yaws, pitches, fovs, i, i + 1);
            if (!sameAsPrevious || !sameAsNext) {
                kept[size++] = i;
            }
        }
        return Arrays.copyOf(kept, size);
    }

    private static boolean same(short[] yaws, short[] pitches, float @Nullable [] fovs, int a, int b) {
        return yaws[a] == yaws[b] && pitches[a] == pitches[b] && (fovs == null || Float.compare(fovs[a], fovs[b]) == 0);
    }

    public static float lerpAt(float[] partials, float[] values, float partial) {
        return interpolate(partials, values, partial, false);
    }

    public static float yawAt(float[] partials, float[] yaws, float partial) {
        return interpolate(partials, yaws, partial, true);
    }

    private static float interpolate(float[] partials, float[] values, float partial, boolean degrees) {
        int count = partials.length;
        if (count == 0) {
            return Float.NaN;
        }
        int next = 0;
        while (next < count && partials[next] < partial) {
            next++;
        }
        if (next == 0 || next == count) {
            return values[next == 0 ? 0 : count - 1];
        }
        int previous = next - 1;
        float amount = (partial - partials[previous]) / (partials[next] - partials[previous]);
        if (degrees) {
            return wrapDegrees(values[previous] + wrapDegrees(values[next] - values[previous]) * amount);
        }
        return values[previous] + amount * (values[next] - values[previous]);
    }

}
