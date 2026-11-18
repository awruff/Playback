package org.polyfrost.keyframe.camera;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.packet.FlashbackAccurateEntityPosition;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.visuals.AccurateEntityPositionHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;
import org.jspecify.annotations.Nullable;
import org.polyfrost.keyframe.Keyframe;
import org.polyfrost.keyframe.mixin.ornithe.MinecraftTimerAccessor;

import java.util.ArrayList;
import java.util.List;

public class CameraTrackAction {

    private static final int PLAYBACK_STEPS = 64;
    private static @Nullable Action action;

    private static final List<float[]> samples = new ArrayList<>();
    private static @Nullable Vec3d lastPosition;
    private static boolean wroteThisTick;

    public static void register() {
        action = Keyframe.action("action/camera_track_optional", CameraTrackAction::handle);
    }

    private static void handle(PacketByteBuf buf) {
        CameraTrack track = CameraTrack.decode(buf);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.world != null && minecraft.world.getEntity(track.entityId()) instanceof PlayerEntity player) {
            player.abilities.flying = track.flying();
        }
        ReplayServer replayServer = Flashback.getReplayServer();
        if (Flashback.getConfig().advanced.disableIncreasedFirstPersonUpdates || replayServer == null || replayServer.fastForwarding) {
            return;
        }
        AccurateEntityPositionHandler.update(new FlashbackAccurateEntityPosition(track.entityId(), track.resample(PLAYBACK_STEPS)));
    }

    public static void sample(Entity player, float partialTick) {
        float[] sample = {partialTick, viewYaw(player, partialTick), viewPitch(player, partialTick)};
        if (!samples.isEmpty() && samples.getLast()[0] == partialTick) {
            samples.set(samples.size() - 1, sample);
        } else {
            samples.add(sample);
        }
    }

    public static void write(AsyncReplaySaver... savers) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalClientPlayerEntity player = minecraft.player;
        if (player == null || action == null) {
            return;
        }

        float partialTick = ((MinecraftTimerAccessor) minecraft).keyframe$getTimer().partialTick;
        samples.add(new float[]{partialTick + 1.0f, viewYaw(player, partialTick), viewPitch(player, partialTick)});

        Vec3d end = new Vec3d(player.lastX, player.lastY, player.lastZ);
        Vec3d start = lastPosition != null ? lastPosition : end;
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

        Keyframe.submit(action, new CameraTrack(player.getNetworkId(), player.abilities.flying, start, end, partials, yaws, pitches)::encode, savers);

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

    private static float viewYaw(Entity entity, float partialTick) {
        return entity.lastYaw + (entity.yaw - entity.lastYaw) * partialTick;
    }

    private static float viewPitch(Entity entity, float partialTick) {
        return entity.lastPitch + (entity.pitch - entity.lastPitch) * partialTick;
    }

}
