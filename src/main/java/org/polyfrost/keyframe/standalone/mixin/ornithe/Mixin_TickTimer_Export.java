package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.TickTimer;
import org.polyfrost.keyframe.standalone.compat.ArgentumCompat;
import org.polyfrost.keyframe.standalone.export.Export;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TickTimer.class)
abstract class Mixin_TickTimer_Export {
	@Shadow
	public int ticksThisFrame;

	@Shadow
	public float partialTick;

	@Inject(method = "advance", at = @At("TAIL"))
	private void keyframe$schedule(CallbackInfo ci) {
		if (!Export.getActive()) return;
		this.ticksThisFrame = Export.beginFrame();
		this.partialTick = Export.partialTick();
		ArgentumCompat.keepMainFramebuffer();
	}
}
