package org.polyfrost.playback.mixin;

//? if >=26.1 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import org.polyfrost.playback.Playback;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Camera.class)
public abstract class MixinCamera {

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
    private float playback$spectatedFov(Camera camera, float partialTicks, Operation<Float> original) {
        float fov = original.call(camera, partialTicks);
        if (this.isPanoramicMode || !this.minecraft.options.getCameraType().isFirstPerson() || Playback.spectated() == null) {
            return fov;
        }
        return this.modifyFovBasedOnDeathOrFluid(partialTicks, fov * Mth.lerp(partialTicks, this.oldFovModifier, this.fovModifier));
    }

}
//?}
