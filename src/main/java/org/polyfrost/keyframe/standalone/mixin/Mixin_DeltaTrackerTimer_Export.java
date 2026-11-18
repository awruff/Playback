package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.DeltaTracker;
import org.polyfrost.keyframe.standalone.export.Export;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(DeltaTracker.Timer.class)
public abstract class Mixin_DeltaTrackerTimer_Export {
	@Shadow
	private boolean frozen;

	@Shadow
	private boolean paused;

	@Unique
	private float keyframe$heldPartialTick;

	@ModifyReturnValue(method = "advanceGameTime(J)I", at = @At("RETURN"))
	private int keyframe$scheduledTicks(int ticks) {
		return Export.getActive() ? Export.beginFrame() : ticks;
	}

	@ModifyReturnValue(method = "getGameTimeDeltaPartialTick(Z)F", at = @At("RETURN"))
	private float keyframe$scheduledPartialTick(float partialTick, boolean ignoreFrozenGame) {
		if (!Export.getActive() || (!ignoreFrozenGame && this.frozen)) return partialTick;
		if (!this.paused) this.keyframe$heldPartialTick = Export.partialTick();
		return this.keyframe$heldPartialTick;
	}
}
