package org.polyfrost.keyframe.ui;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ScreenWireTest {

    @Test
    void posesRoundTripInEveryForm() {
        float[] translated = {1.0f, 0.0f, 0.0f, 1.0f, 3.0f, -4.5f, 2.0f};
        float[] rotated = {0.0f, 1.0f, -1.0f, 0.0f, 3.0f, 4.0f, 5.0f};
        ByteBuf buf = Unpooled.buffer();
        ScreenWire.writePose(buf, ScreenWire.IDENTITY);
        ScreenWire.writePose(buf, translated);
        ScreenWire.writePose(buf, rotated);

        assertEquals(1 + 13 + 29, buf.readableBytes());
        assertSame(ScreenWire.IDENTITY, ScreenWire.readPose(buf));
        assertArrayEquals(translated, ScreenWire.readPose(buf));
        assertArrayEquals(rotated, ScreenWire.readPose(buf));
    }

    @Test
    void dedupeDropsOpsRepeatedFromThePreviousFrame() {
        byte[] a = {1, 2};
        byte[] b = {3};
        List<ScreenWire.Frame> frames = ScreenWire.dedupe(a.clone(), List.of(frame(a), frame(a.clone()), frame(b), frame(b)));

        assertNull(frames.get(0).ops());
        assertNull(frames.get(1).ops());
        assertArrayEquals(b, frames.get(2).ops());
        assertNull(frames.get(3).ops());
    }

    private static ScreenWire.Frame frame(byte[] ops) {
        return new ScreenWire.Frame(0.0f, 0.0f, 0.0f, 1, 1, 1, 0, ops);
    }

}
