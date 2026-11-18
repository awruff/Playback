package org.polyfrost.keyframe.camera;

import com.moulberry.flashback.action.PositionAndAngle;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public record CameraTrack(int entityId, boolean flying, Vec3d start, Vec3d end, float[] partials, float[] yaws, float[] pitches) {

    private static final float PARTIAL_SCALE = 85.0f;
    private static final float ANGLE_SCALE = 100.0f;

    public void encode(PacketByteBuf buf) {
        buf.writeVarInt(this.entityId);
        buf.writeBoolean(this.flying);
        buf.writeDouble(this.end.x);
        buf.writeDouble(this.end.y);
        buf.writeDouble(this.end.z);
        buf.writeFloat((float) (this.start.x - this.end.x));
        buf.writeFloat((float) (this.start.y - this.end.y));
        buf.writeFloat((float) (this.start.z - this.end.z));

        int count = this.partials.length;
        short[] yaws = new short[count];
        short[] pitches = new short[count];
        for (int i = 0; i < count; i++) {
            yaws[i] = (short) Math.round(CameraFrames.wrapDegrees(this.yaws[i]) * ANGLE_SCALE);
            pitches[i] = (short) Math.round(this.pitches[i] * ANGLE_SCALE);
        }

        int[] kept = CameraFrames.kept(yaws, pitches, null);

        buf.writeVarInt(kept.length);
        for (int i : kept) {
            buf.writeByte(Math.clamp(Math.round((this.partials[i] + 1.0f) * PARTIAL_SCALE), 0, 255));
            buf.writeShort(yaws[i]);
            buf.writeShort(pitches[i]);
        }
    }

    public static CameraTrack decode(PacketByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean flying = buf.readBoolean();
        Vec3d end = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3d start = end.add(buf.readFloat(), buf.readFloat(), buf.readFloat());

        int count = buf.readVarInt();
        float[] partials = new float[count];
        float[] yaws = new float[count];
        float[] pitches = new float[count];
        for (int i = 0; i < count; i++) {
            partials[i] = buf.readUnsignedByte() / PARTIAL_SCALE - 1.0f;
            yaws[i] = buf.readShort() / ANGLE_SCALE;
            pitches[i] = buf.readShort() / ANGLE_SCALE;
        }
        return new CameraTrack(entityId, flying, start, end, partials, yaws, pitches);
    }

    public PositionAndAngle at(float partial) {
        double x = this.start.x + (this.end.x - this.start.x) * partial;
        double y = this.start.y + (this.end.y - this.start.y) * partial;
        double z = this.start.z + (this.end.z - this.start.z) * partial;
        if (this.partials.length == 0) {
            return new PositionAndAngle(x, y, z, 0.0f, 0.0f);
        }
        return new PositionAndAngle(x, y, z, CameraFrames.yawAt(this.partials, this.yaws, partial), CameraFrames.lerpAt(this.partials, this.pitches, partial));
    }

    public List<PositionAndAngle> resample(int steps) {
        List<PositionAndAngle> points = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            points.add(this.at((float) i / steps));
        }
        return points;
    }

}
