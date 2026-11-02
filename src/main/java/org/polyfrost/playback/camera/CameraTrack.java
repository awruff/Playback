package org.polyfrost.playback.camera;

import com.moulberry.flashback.action.PositionAndAngle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public record CameraTrack(int entityId, boolean flying, Vec3 start, Vec3 end, float[] partials, float[] yaws, float[] pitches) {

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
            yaws[i] = (short) Math.round(Mth.wrapDegrees(this.yaws[i]) * ANGLE_SCALE);
            pitches[i] = (short) Math.round(this.pitches[i] * ANGLE_SCALE);
        }

        List<Integer> kept = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            boolean sameAsPrevious = i > 0 && yaws[i] == yaws[i - 1] && pitches[i] == pitches[i - 1];
            boolean sameAsNext = i < count - 1 && yaws[i] == yaws[i + 1] && pitches[i] == pitches[i + 1];
            if (!sameAsPrevious || !sameAsNext) {
                kept.add(i);
            }
        }

        buf.writeVarInt(kept.size());
        for (int i : kept) {
            buf.writeByte(Math.clamp(Math.round((this.partials[i] + 1.0f) * PARTIAL_SCALE), 0, 255));
            buf.writeShort(yaws[i]);
            buf.writeShort(pitches[i]);
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
        return new CameraTrack(entityId, flying, start, end, partials, yaws, pitches);
    }

    public PositionAndAngle at(float partial) {
        Vec3 position = this.start.lerp(this.end, partial);
        int count = this.partials.length;
        if (count == 0) {
            return new PositionAndAngle(position.x, position.y, position.z, 0.0f, 0.0f);
        }

        int next = 0;
        while (next < count && this.partials[next] < partial) {
            next++;
        }
        if (next == 0 || next == count) {
            int i = next == 0 ? 0 : count - 1;
            return new PositionAndAngle(position.x, position.y, position.z, this.yaws[i], this.pitches[i]);
        }

        int previous = next - 1;
        float amount = (partial - this.partials[previous]) / (this.partials[next] - this.partials[previous]);
        float yaw = this.yaws[previous] + Mth.wrapDegrees(this.yaws[next] - this.yaws[previous]) * amount;
        float pitch = Mth.lerp(amount, this.pitches[previous], this.pitches[next]);
        return new PositionAndAngle(position.x, position.y, position.z, Mth.wrapDegrees(yaw), pitch);
    }

    public List<PositionAndAngle> resample(int steps) {
        List<PositionAndAngle> points = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            points.add(this.at((float) i / steps));
        }
        return points;
    }

}
