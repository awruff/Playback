package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//? if >=26.2 {
import net.minecraft.client.gui.Hud;
//?} else
/*import net.minecraft.client.gui.Gui;*/
import org.polyfrost.keyframe.standalone.playback.Puppet;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

//? if >=26.2 {
@Mixin(Hud.class)
//?} else
/*@Mixin(Gui.class)*/
abstract class Mixin_Hud_Puppet {
	@Shadow
	private long healthBlinkTime;

	@ModifyExpressionValue(
		//? if >=1.21.10 {
		method = {"extractPlayerHealth", "extractAirBubbles", "extractFood"},
		//?} else
		/*method = {"extractPlayerHealth", "extractFood"},*/
		//? if >=26.2 {
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Hud;tickCount:I", opcode = Opcodes.GETFIELD)
		//?} else
		/*at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/Gui;tickCount:I", opcode = Opcodes.GETFIELD)*/
	)
	private int keyframe$recordedGuiTicks(int tickCount) {
		return Puppet.INSTANCE.guiTicks(tickCount);
	}

	@Inject(method = "onDisconnected", at = @At("TAIL"))
	private void keyframe$forgetHealthBlink(CallbackInfo ci) {
		this.healthBlinkTime = 0L;
	}
}
