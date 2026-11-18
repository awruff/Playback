package org.polyfrost.keyframe.camera;

import com.moulberry.flashback.action.PositionAndAngle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public record CameraTrack(int entityId, boolean flying, Vec3 start, Vec3 end, float[] partials, float[] yaws, float[] pitches, float[] fovs) {

    private static final float PARTIAL_SCALE = 85.0f;
    private static final float ANGLE_SCALE = 100.0f;

    public void encode(FriendlyByteBuf buf) {
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

        int[] kept = CameraFrames.kept(yaws, pitches, this.fovs);

        buf.writeVarInt(kept.length);
        for (int i : kept) {
            buf.writeByte(Math.clamp(Math.round((this.partials[i] + 1.0f) * PARTIAL_SCALE), 0, 255));
            buf.writeShort(yaws[i]);
            buf.writeShort(pitches[i]);
        }
        for (int i : kept) {
            buf.writeFloat(this.fovs[i]);
        }
    }

    public static CameraTrack decode(FriendlyByteBuf buf) {
        int entityId = buf.readVarInt();
        boolean flying = buf.readBoolean();
        Vec3 end = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 start = end.add(buf.readFloat(), buf.readFloat(), buf.readFloat());

        int count = buf.readVarInt();
        float[] partials = new float[count];
        float[] yaws = new float[count];
        float[] pitches = new float[count];
        for (int i = 0; i < count; i++) {
            partials[i] = buf.readUnsignedByte() / PARTIAL_SCALE - 1.0f;
            yaws[i] = buf.readShort() / ANGLE_SCALE;
            pitches[i] = buf.readShort() / ANGLE_SCALE;
        }
        float[] fovs = new float[count];
        for (int i = 0; i < count; i++) {
            fovs[i] = buf.readableBytes() >= Float.BYTES ? buf.readFloat() : Float.NaN;
        }
        return new CameraTrack(entityId, flying, start, end, partials, yaws, pitches, fovs);
    }

    public PositionAndAngle at(float partial) {
        Vec3 position = this.start.lerp(this.end, partial);
        if (this.partials.length == 0) {
            return new PositionAndAngle(position.x, position.y, position.z, 0.0f, 0.0f);
        }
        return new PositionAndAngle(position.x, position.y, position.z, CameraFrames.yawAt(this.partials, this.yaws, partial), CameraFrames.lerpAt(this.partials, this.pitches, partial));
    }

    public List<PositionAndAngle> resample(int steps) {
        List<PositionAndAngle> points = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            points.add(this.at((float) i / steps));
        }
        return points;
    }

}
