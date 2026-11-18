package org.polyfrost.keyframe.camera;

import org.polyfrost.keyframe.Keyframe;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CameraTrackAction implements Action {

    public static final CameraTrackAction INSTANCE = new CameraTrackAction();
    private static final Identifier NAME = Identifier.fromNamespaceAndPath(Keyframe.MOD_ID, "action/camera_track_optional");
    private static final int PLAYBACK_STEPS = 64;

    private static final List<float[]> samples = new ArrayList<>();
    private static @Nullable Vec3 lastPosition;
    private static boolean wroteThisTick;
    private static volatile @Nullable Zoom playing;

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        CameraTrack track = CameraTrack.decode(buf);
        Flying flying = new Flying(track.entityId(), track.flying());
        FlashbackAccurateEntityPosition positions = Flashback.getConfig().advanced.disableIncreasedFirstPersonUpdates ? null
            : new FlashbackAccurateEntityPosition(track.entityId(), track.resample(PLAYBACK_STEPS));
        Keyframe.relay(replayServer, flying);
        if (positions != null) {
            Keyframe.relay(replayServer, positions);
        }
        Keyframe.relay(replayServer, new Zoom(track.entityId(), track.partials(), track.fovs()));
    }

    public static float fov(float fov, @Nullable Entity spectated, float partialTick) {
        ViewCapture.playFov(spectated == null ? Float.NaN : fovScale(spectated, partialTick));
        return ViewCapture.fov(fov);
    }

    private static float fovScale(Entity entity, float partialTick) {
        Zoom zoom = playing;
        if (zoom == null || zoom.entityId() != entity.getId()) {
            return Float.NaN;
        }
        return CameraFrames.lerpAt(zoom.partials(), zoom.fovs(), partialTick);
    }

    public static void play(@Nullable Zoom zoom) {
        playing = zoom;
    }

    public static void sample(LocalPlayer player, float partialTick) {
        float[] sample = {partialTick, ViewCapture.yaw(player, partialTick), ViewCapture.pitch(player, partialTick), ViewCapture.fovScale()};
        if (!samples.isEmpty() && samples.getLast()[0] == partialTick) {
            samples.set(samples.size() - 1, sample);
        } else {
            samples.add(sample);
        }
    }

    public static void write(AsyncReplaySaver... savers) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        minecraft.mouseHandler.handleAccumulatedMovement();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        samples.add(new float[]{partialTick + 1.0f, ViewCapture.yaw(player, partialTick), ViewCapture.pitch(player, partialTick), ViewCapture.fovScale()});

        Vec3 end = player.oldPosition();
        Vec3 start = lastPosition != null ? lastPosition : end;
        lastPosition = end;

        int count = samples.size();
        float[] partials = new float[count];
        float[] yaws = new float[count];
        float[] pitches = new float[count];
        float[] fovs = new float[count];
        for (int i = 0; i < count; i++) {
            partials[i] = samples.get(i)[0];
            yaws[i] = samples.get(i)[1];
            pitches[i] = samples.get(i)[2];
            fovs[i] = samples.get(i)[3];
        }

        Keyframe.submit(INSTANCE, new CameraTrack(player.getId(), player.getAbilities().flying, start, end, partials, yaws, pitches, fovs)::encode, savers);

        List<float[]> carried = new ArrayList<>(samples.subList(Math.max(0, count - 2), count));
        samples.clear();
        for (float[] sample : carried) {
            samples.add(new float[]{sample[0] - 1.0f, sample[1], sample[2], sample[3]});
        }
        wroteThisTick = true;
    }

    public static void endTick() {
        if (!wroteThisTick) {
            reset();
        }
        wroteThisTick = false;
    }

    public static void reset() {
        samples.clear();
        lastPosition = null;
        wroteThisTick = false;
    }

    public record Zoom(int entityId, float[] partials, float[] fovs) implements CustomPacketPayload {
        public static final Type<Zoom> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Keyframe.MOD_ID, "zoom"));
        public static final StreamCodec<FriendlyByteBuf, Zoom> STREAM_CODEC = StreamCodec.of(
            (buf, zoom) -> {
                buf.writeVarInt(zoom.entityId);
                buf.writeVarInt(zoom.partials.length);
                for (int i = 0; i < zoom.partials.length; i++) {
                    buf.writeFloat(zoom.partials[i]);
                    buf.writeFloat(zoom.fovs[i]);
                }
            },
            buf -> {
                int entityId = buf.readVarInt();
                int count = buf.readVarInt();
                float[] partials = new float[count];
                float[] fovs = new float[count];
                for (int i = 0; i < count; i++) {
                    partials[i] = buf.readFloat();
                    fovs[i] = buf.readFloat();
                }
                return new Zoom(entityId, partials, fovs);
            }
        );

        @Override
        public Type<Zoom> type() {
            return TYPE;
        }
    }

    public record Flying(int entityId, boolean flying) implements CustomPacketPayload {
        public static final Type<Flying> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Keyframe.MOD_ID, "flying"));
        public static final StreamCodec<FriendlyByteBuf, Flying> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Flying::entityId,
            ByteBufCodecs.BOOL, Flying::flying,
            Flying::new
        );

        @Override
        public Type<Flying> type() {
            return TYPE;
        }
    }

}
