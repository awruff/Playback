package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.polyfrost.keyframe.shadow.AutoMarkers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class MixinClientPlayerInteractionManagerAutoMarkers {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void keyframe$autoMarkAttack(PlayerEntity player, Entity target, CallbackInfo ci) {
        AutoMarkers.onAttack(target);
    }

    @Inject(method = "finishMiningBlock", at = @At("HEAD"))
    private void keyframe$autoMarkBreak(BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
        AutoMarkers.onBreak(pos);
    }

}
