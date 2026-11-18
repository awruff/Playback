package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.overlay.DebugOverlay;
import net.minecraft.client.render.Window;
import org.polyfrost.keyframe.standalone.export.Export;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.polyfrost.keyframe.standalone.playback.Puppet;
import org.polyfrost.keyframe.standalone.playback.ScreenReplay;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameGui.class)
abstract class Mixin_GameGui_Puppet {
	@Shadow
	private long lastHealthAnimationTime;

	@ModifyExpressionValue(
		method = "renderStatusBars",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/GameGui;ticks:I", opcode = Opcodes.GETFIELD)
	)
	private int keyframe$recordedGuiTicks(int ticks) {
		return Puppet.INSTANCE.guiTicks(ticks);
	}

	@Inject(method = "resetPlayerTabOverlay", at = @At("TAIL"))
	private void keyframe$forgetHealthBlink(CallbackInfo ci) {
		this.lastHealthAnimationTime = 0L;
	}

	@ModifyExpressionValue(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/options/KeyBinding;isPressed()Z")
	)
	private boolean keyframe$replayTabList(boolean down) {
		return ScreenReplay.INSTANCE.tabListDown(down);
	}

	@WrapWithCondition(
		method = "extractRenderState",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/overlay/DebugOverlay;render(Lnet/minecraft/client/render/Window;)V")
	)
	private boolean keyframe$extractDebugOverlay(DebugOverlay overlay, Window window) {
		return Export.renderHud() && !Keyframe.INSTANCE.getPreviewing();
	}
}
