package org.polyfrost.keyframe.player;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.client.sound.instance.SimpleSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.effect.StatusEffect;
import net.minecraft.entity.living.effect.StatusEffectInstance;
import net.minecraft.resource.Identifier;
import org.jspecify.annotations.Nullable;
import org.polyfrost.keyframe.Keyframe;

public class SpectatedPlayer {

    private static @Nullable ClientPlayerEntity tracked;
    private static float portalIntensity;
    private static float oPortalIntensity;
    private static int pendingCamera = -1;

    public static @Nullable ClientPlayerEntity get() {
        ClientPlayerEntity player = Keyframe.spectated();
        if (player != tracked) {
            tracked = player;
            portalIntensity = 0.0f;
            oPortalIntensity = 0.0f;
        }
        return player;
    }

    public static void tick() {
        if (!Flashback.isInReplay()) {
            RecordedPlayerState.reset();
        }
        attachPendingCamera();
        ClientPlayerEntity player = get();
        ReplayServer replayServer = Flashback.getReplayServer();
        if (player == null || replayServer != null && (replayServer.replayPaused || replayServer.isFrozen())) {
            return;
        }

        boolean inPortal = player.world.containsMaterial(player.getShape().contract(1.0E-5, 1.0E-5, 1.0E-5), Material.PORTAL);
        if (inPortal && portalIntensity == 0.0f) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.of(new Identifier("portal.trigger"), player.getRandom().nextFloat() * 0.4f + 0.8f));
        }
        StatusEffectInstance nausea = player.getEffectInstance(StatusEffect.NAUSEA);
        boolean confused = nausea != null && nausea.getDuration() > 60;
        float step = inPortal ? 0.0125f : confused ? 0.006666667f : portalIntensity > 0.0f ? -0.05f : 0.0f;
        oPortalIntensity = portalIntensity;
        portalIntensity = Math.clamp(portalIntensity + step, 0.0f, 1.0f);

        RecordedPlayerState.tickGuiTicks();
    }

    public static void setPendingCamera(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        pendingCamera = Flashback.isInReplay() && minecraft.world != null && minecraft.world.getEntity(entityId) == null ? entityId : -1;
    }

    private static void attachPendingCamera() {
        Minecraft minecraft = Minecraft.getInstance();
        if (pendingCamera == -1 || !Flashback.isInReplay() || minecraft.world == null) {
            pendingCamera = -1;
            return;
        }
        Entity entity = minecraft.world.getEntity(pendingCamera);
        if (entity != null) {
            minecraft.setCamera(entity);
            pendingCamera = -1;
        }
    }

    public static float oPortalIntensity() {
        return oPortalIntensity;
    }

    public static float portalIntensity() {
        return portalIntensity;
    }

}
