package org.polyfrost.playback.mixin;

//? if >=26.3 {
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.polyfrost.playback.ui.GuiMeshCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DynamicTexture.class)
public abstract class MixinDynamicTexture {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void playback$track(CallbackInfo ci) {
        GuiMeshCapture.track((DynamicTexture) (Object) this);
    }

}
//?}
