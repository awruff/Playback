package org.polyfrost.playback.mixin;

import org.polyfrost.playback.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=26.1 {
import net.minecraft.client.renderer.Panorama;
//?} else
//import net.minecraft.client.renderer.PanoramaRenderer;

//? if >=26.1 {
@Mixin(Panorama.class)
//?} else
//@Mixin(PanoramaRenderer.class)
public abstract class MixinPanorama {

    @Shadow
    private float spin;

    //? if >=26.1 {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    //?} else
    //@Inject(method = "render", at = @At("HEAD"))
    private void playback$capturePanorama(CallbackInfo ci) {
        if (ScreenCapture.active()) {
            ScreenCapture.panorama(-this.spin);
        }
    }

}
