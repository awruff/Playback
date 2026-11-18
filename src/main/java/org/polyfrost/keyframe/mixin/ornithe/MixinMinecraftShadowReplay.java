package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import org.objectweb.asm.Opcodes;
import org.polyfrost.keyframe.shadow.ShadowReplay;
import org.polyfrost.keyframe.shadow.ShadowReplayConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftShadowReplay {

    @WrapMethod(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V")
    private void keyframe$markDisconnecting(ClientWorld world, String message, Operation<Void> original) {
        if (world != null) {
            original.call(world, message);
            return;
        }
        ShadowReplay.stop();
        ShadowReplay.disconnecting = true;
        try {
            original.call(world, message);
        } finally {
            ShadowReplay.disconnecting = false;
        }
    }

    @WrapOperation(method = "runGame", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;paused:Z", opcode = Opcodes.PUTFIELD))
    private void keyframe$keepRecordingInMenus(Minecraft minecraft, boolean pause, Operation<Void> original) {
        Recorder recorder = Flashback.RECORDER;
        boolean recording = recorder != null && !recorder.isPaused() && !Flashback.isInReplay() && !ShadowReplay.isShadow(recorder);
        original.call(minecraft, pause && !(recording && ShadowReplayConfig.recordMenus));
    }

}
