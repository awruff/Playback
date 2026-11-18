package org.polyfrost.keyframe.mixin;

//? if >=26.1 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import org.polyfrost.keyframe.Keyframe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
//?}
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.polyfrost.keyframe.camera.CameraTrackAction;
import org.polyfrost.keyframe.camera.ViewCapture;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {

    //? if >=26.1 {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private boolean isPanoramicMode;

    @Shadow
    private float oldFovModifier;

    @Shadow
    private float fovModifier;

    @Shadow
    protected abstract float modifyFovBasedOnDeathOrFluid(float partialTicks, float fov);

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;calculateFov(F)F"))
    private float keyframe$spectatedFov(Camera camera, float partialTicks, Operation<Float> original) {
        float fov = original.call(camera, partialTicks);
        AbstractClientPlayer spectated = Keyframe.spectated();
        if (this.isPanoramicMode || !this.minecraft.options.getCameraType().isFirstPerson() || spectated == null) {
            return CameraTrackAction.fov(fov, null, partialTicks);
        }
        fov = this.modifyFovBasedOnDeathOrFluid(partialTicks, fov * Mth.lerp(partialTicks, this.oldFovModifier, this.fovModifier));
        return CameraTrackAction.fov(fov, spectated, partialTicks);
    }
    //?}

    //? if >=26.1 {
    @Inject(method = "update", at = @At("TAIL"))
    //?} else
    /*@Inject(method = "setup", at = @At("TAIL"))*/
    private void keyframe$sampleCamera(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        ViewCapture.capture((Camera) (Object) this, partialTick);
        if (ScreenCapture.recording() && minecraft.player != null) {
            CameraTrackAction.sample(minecraft.player, partialTick);
        }
    }

}
