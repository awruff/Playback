package org.polyfrost.polyreplay.camera;

import com.moulberry.flashback.action.PositionAndAngle;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CameraTrackTest {

    @Test
    void roundTripsAndInterpolatesBetweenFrames() {
        CameraTrack track = new CameraTrack(7, true, new Vec3(0, 64, 0), new Vec3(1, 64, 2),
            new float[]{-0.2f, 0.1f, 0.4f, 0.7f, 1.05f},
            new float[]{170f, 179f, 535f, 175f, 175f},
            new float[]{10f, 10f, 10f, 10f, -20f});

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        track.encode(buf);
        CameraTrack decoded = CameraTrack.decode(buf);

        assertEquals(0, buf.readableBytes());
        assertEquals(7, decoded.entityId());
        assertEquals(true, decoded.flying());
        assertEquals(5, decoded.partials().length);

        PositionAndAngle middle = decoded.at(0.5f);
        assertEquals(0.5, middle.x(), 1e-6);
        assertEquals(1.0, middle.z(), 1e-6);
        assertEquals(10f, middle.pitch(), 0.01f);

        PositionAndAngle wrapped = decoded.at(0.25f);
        assertEquals(177f, wrapped.yaw(), 0.1f);

        assertEquals(65, decoded.resample(64).size());
        assertEquals(-20f, decoded.at(2.0f).pitch(), 0.01f);
    }

    @Test
    void dropsFramesInsideStillRuns() {
        CameraTrack still = new CameraTrack(1, false, Vec3.ZERO, Vec3.ZERO,
            new float[]{0.0f, 0.2f, 0.4f, 0.6f, 0.8f},
            new float[]{5f, 5f, 5f, 5f, 6f},
            new float[]{0f, 0f, 0f, 0f, 0f});

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        still.encode(buf);
        CameraTrack decoded = CameraTrack.decode(buf);

        assertEquals(3, decoded.partials().length);
        assertEquals(5f, decoded.at(0.55f).yaw(), 0.01f);
    }

}
