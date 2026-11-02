package org.polyfrost.polyreplay.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import net.minecraft.client.Minecraft;
import org.polyfrost.polyreplay.shadow.ShadowReplayConfig;
import org.polyfrost.polyreplay.shadow.ShadowReplay;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "disconnect*", at = @At("HEAD"))
    private void polyreplay$stopShadowReplay(CallbackInfo ci) {
        ShadowReplay.stop();
    }

    @WrapOperation(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;pause:Z", opcode = Opcodes.PUTFIELD))
    private void polyreplay$keepRecordingInMenus(Minecraft minecraft, boolean pause, Operation<Void> original) {
        Recorder recorder = Flashback.RECORDER;
        boolean recording = recorder != null && !recorder.isPaused() && !Flashback.isInReplay() && !ShadowReplay.isShadow(recorder);
        original.call(minecraft, pause && !(recording && ShadowReplayConfig.recordMenus));
    }

}
