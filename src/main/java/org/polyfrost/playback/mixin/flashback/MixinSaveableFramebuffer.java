package org.polyfrost.playback.mixin.flashback;

//? if >=26.1 {
//? if >=26.3 {
import com.mojang.renderpearl.api.buffers.GpuBuffer;
//?} else
/*import com.mojang.blaze3d.buffers.GpuBuffer;*/
import com.moulberry.flashback.exporting.SaveableFramebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(SaveableFramebuffer.class)
public abstract class MixinSaveableFramebuffer {

    @Shadow
    private GpuBuffer pbo;

    //? if >=26.3 {
    @ModifyArg(method = "startDownload", at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/commands/CommandEncoder;copyTextureToBuffer(Lcom/mojang/renderpearl/api/textures/GpuTexture;Lcom/mojang/renderpearl/api/buffers/GpuBuffer;JLjava/lang/Runnable;I)V"), index = 3)
    //?} else
    /*@ModifyArg(method = "startDownload", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/CommandEncoder;copyTextureToBuffer(Lcom/mojang/blaze3d/textures/GpuTexture;Lcom/mojang/blaze3d/buffers/GpuBuffer;JLjava/lang/Runnable;I)V"), index = 3)*/
    private Runnable playback$skipIfClosed(Runnable callback) {
        return () -> {
            if (this.pbo != null) callback.run();
        };
    }

}
//?}
