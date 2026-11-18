package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.render.vertex.Tesselator;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Tesselator.class)
public abstract class MixinTesselatorScreens {

    @Inject(method = "end", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/vertex/BufferUploader;end(Lnet/minecraft/client/render/vertex/BufferBuilder;)V"))
    private void keyframe$captureMesh(CallbackInfo ci) {
        Tesselator self = (Tesselator) (Object) this;
        if (ScreenCapture.active() && self == Tesselator.getInstance()) {
            ScreenCapture.mesh(self.getBuffer());
        }
    }

}
