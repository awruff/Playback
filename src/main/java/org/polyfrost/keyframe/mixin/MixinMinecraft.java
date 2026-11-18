package org.polyfrost.keyframe.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.keyframe.shadow.ShadowReplayConfig;
import org.polyfrost.keyframe.shadow.ShadowReplay;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MixinMinecraft {

    @Inject(method = "disconnect*", at = @At("HEAD"))
    private void keyframe$stopShadowReplay(CallbackInfo ci) {
        ShadowReplay.stop();
    }

    //? if >=1.21.11 {
    @WrapMethod(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V")
    private void keyframe$markDisconnecting(Screen screen, boolean keepResourcePacks, boolean stopSound, Operation<Void> original) {
        ScreenCapture.disconnecting = true;
        try {
            original.call(screen, keepResourcePacks, stopSound);
        } finally {
            ScreenCapture.disconnecting = false;
        }
    }
    //?} else {
    /*@WrapMethod(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;Z)V")
    private void keyframe$markDisconnecting(Screen screen, boolean keepResourcePacks, Operation<Void> original) {
        ScreenCapture.disconnecting = true;
        try {
            original.call(screen, keepResourcePacks);
        } finally {
            ScreenCapture.disconnecting = false;
        }
    }
    *///?}

    @WrapOperation(method = "runTick", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;pause:Z", opcode = Opcodes.PUTFIELD))
    private void keyframe$keepRecordingInMenus(Minecraft minecraft, boolean pause, Operation<Void> original) {
        boolean recording = ScreenCapture.recording() && !ShadowReplay.isShadow(Flashback.RECORDER);
        original.call(minecraft, pause && !(recording && ShadowReplayConfig.recordMenus));
    }

}
