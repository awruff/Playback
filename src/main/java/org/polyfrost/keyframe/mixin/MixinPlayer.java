package org.polyfrost.keyframe.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.polyfrost.keyframe.Keyframe;
import org.polyfrost.keyframe.player.PlayerStateAction;
import org.polyfrost.keyframe.player.RecordedPlayerState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public abstract class MixinPlayer {

    @ModifyReturnValue(method = "getAttackStrengthScale", at = @At("RETURN"))
    private float keyframe$recordedAttackStrength(float original, float partialTick) {
        PlayerStateAction.Payload state = this.keyframe$recordedState();
        return state != null ? Math.clamp((state.attackTicker() + partialTick) / state.attackDelay(), 0.0f, 1.0f) : original;
    }

    @ModifyReturnValue(method = "getCurrentItemAttackStrengthDelay", at = @At("RETURN"))
    private float keyframe$recordedAttackDelay(float original) {
        PlayerStateAction.Payload state = this.keyframe$recordedState();
        return state != null ? state.attackDelay() : original;
    }

    @Unique
    private PlayerStateAction.@Nullable Payload keyframe$recordedState() {
        if (!((Object) this instanceof AbstractClientPlayer player) || player != Keyframe.spectated()) {
            return null;
        }
        return RecordedPlayerState.of(player);
    }

}
