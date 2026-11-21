package org.polyfrost.keyframe.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class ViewCapture {

    private static float yawOffset;
    private static float pitchOffset;
    private static float fovScale = Float.NaN;
    private static float playbackFovScale = Float.NaN;
    private static float playbackYawOffset;
    private static float playbackPitchOffset;

    private ViewCapture() {
    }

    public static void capture(Camera camera, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        //? if >=1.21.11 {
        boolean following = player != null && camera.entity() == player;
        //?} else
        /*boolean following = player != null && camera.getEntity() == player;*/
        if (!following) {
            yawOffset = 0.0f;
            pitchOffset = 0.0f;
            return;
        }
        //? if >=1.21.11 {
        float yaw = camera.yRot();
        float pitch = camera.xRot();
        //?} else {
        /*float yaw = camera.getYRot();
        float pitch = camera.getXRot();
        *///?}
        if (minecraft.options.getCameraType().isMirrored()) {
            yaw -= 180.0f;
            pitch = -pitch;
        }
        yawOffset = Mth.wrapDegrees(yaw - player.getViewYRot(partialTick));
        pitchOffset = pitch - player.getViewXRot(partialTick);
    }

    public static float yaw(LocalPlayer player, float partialTick) {
        return player.getViewYRot(partialTick) + yawOffset;
    }

    public static float pitch(LocalPlayer player, float partialTick) {
        return player.getViewXRot(partialTick) + pitchOffset;
    }

    public static float yawOffset() {
        return yawOffset;
    }

    public static float pitchOffset() {
        return pitchOffset;
    }

    public static void playOffset(float yaw, float pitch) {
        playbackYawOffset = yaw;
        playbackPitchOffset = pitch;
    }

    public static float playYaw(float yaw) {
        return yaw + playbackYawOffset;
    }

    public static float playPitch(float pitch) {
        return pitch + playbackPitchOffset;
    }

    public static float fov(float fov) {
        float base = Minecraft.getInstance().options.fov().get();
        fovScale = fov / base;
        return Float.isNaN(playbackFovScale) ? fov : playbackFovScale * base;
    }

    public static float fovScale() {
        return fovScale;
    }

    public static void playFov(float scale) {
        playbackFovScale = scale;
    }

}
