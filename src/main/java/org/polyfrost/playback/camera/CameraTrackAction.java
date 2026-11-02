package org.polyfrost.playback.camera;

import org.polyfrost.playback.Playback;

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
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class CameraTrackAction implements Action {

    public static final CameraTrackAction INSTANCE = new CameraTrackAction();
    private static final Identifier NAME = Identifier.fromNamespaceAndPath(Playback.MOD_ID, "action/camera_track_optional");
    private static final int PLAYBACK_STEPS = 64;

    private static final List<float[]> samples = new ArrayList<>();
    private static @Nullable Vec3 lastPosition;
    private static boolean wroteThisTick;

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
        Playback.relay(replayServer, flying);
        if (positions != null) {
            Playback.relay(replayServer, positions);
        }
    }

    public static void sample(LocalPlayer player, float partialTick) {
        float[] sample = {partialTick, player.getViewYRot(partialTick), player.getViewXRot(partialTick)};
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
        samples.add(new float[]{partialTick + 1.0f, player.getViewYRot(partialTick), player.getViewXRot(partialTick)});

        Vec3 end = player.oldPosition();
        Vec3 start = lastPosition != null ? lastPosition : end;
        lastPosition = end;

        int count = samples.size();
        float[] partials = new float[count];
        float[] yaws = new float[count];
        float[] pitches = new float[count];
        for (int i = 0; i < count; i++) {
            partials[i] = samples.get(i)[0];
            yaws[i] = samples.get(i)[1];
            pitches[i] = samples.get(i)[2];
        }

        Playback.submit(INSTANCE, new CameraTrack(player.getId(), player.getAbilities().flying, start, end, partials, yaws, pitches)::encode, savers);

        List<float[]> carried = new ArrayList<>(samples.subList(Math.max(0, count - 2), count));
        samples.clear();
        for (float[] sample : carried) {
            samples.add(new float[]{sample[0] - 1.0f, sample[1], sample[2]});
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

    public record Flying(int entityId, boolean flying) implements CustomPacketPayload {
        public static final Type<Flying> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Playback.MOD_ID, "flying"));
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
