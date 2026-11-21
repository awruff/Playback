package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import org.objectweb.asm.Opcodes;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.PostChain;
import org.polyfrost.keyframe.standalone.playback.CursorSample;
import net.minecraft.client.DeltaTracker;
import org.polyfrost.keyframe.standalone.export.Export;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.polyfrost.keyframe.standalone.export.PreviewFrame;
import org.polyfrost.keyframe.standalone.playback.Puppet;
import org.polyfrost.keyframe.standalone.recording.Recorder;
import org.polyfrost.keyframe.standalone.playback.ScreenReplay;
import org.polyfrost.keyframe.standalone.editor.TitleOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class Mixin_GameRenderer_Core {
	@Inject(method = "renderWorld(FJ)V", at = @At("HEAD"))
	private void keyframe$camera(float tickDelta, long renderTimeLimit, CallbackInfo ci) {
		DeltaTracker deltaTracker = new DeltaTracker(tickDelta);
		Puppet.INSTANCE.applyCamera(deltaTracker);
		Puppet.INSTANCE.markView();
		Recorder.INSTANCE.writeCamera(deltaTracker);
	}

	@ModifyExpressionValue(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;yaw:F", opcode = Opcodes.GETFIELD))
	private float keyframe$cameraYaw(float yaw, @Local Entity entity) {
		return Puppet.cameraYaw(entity, yaw);
	}

	@ModifyExpressionValue(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;pitch:F", opcode = Opcodes.GETFIELD))
	private float keyframe$cameraPitch(float pitch, @Local Entity entity) {
		return Puppet.cameraPitch(entity, pitch);
	}

	@ModifyExpressionValue(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;lastYaw:F", opcode = Opcodes.GETFIELD))
	private float keyframe$cameraLastYaw(float yaw, @Local Entity entity) {
		return Puppet.cameraLastYaw(entity, yaw);
	}

	@ModifyExpressionValue(method = "transformCamera", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/Entity;lastPitch:F", opcode = Opcodes.GETFIELD))
	private float keyframe$cameraLastPitch(float pitch, @Local Entity entity) {
		return Puppet.cameraLastPitch(entity, pitch);
	}

	@WrapWithCondition(method = "render(FJ)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/PostChain;process(F)V"))
	private boolean keyframe$skipPreviewPostChain(PostChain chain, float tickDelta) {
		return !Keyframe.INSTANCE.getPreviewing();
	}

	@WrapOperation(
		method = "render(FJ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GameGui;render(F)V")
	)
	private void keyframe$fitHud(GameGui gui, float tickDelta, Operation<Void> original) {
		PreviewFrame.fitGui(() -> {
			if (Export.renderHud()) original.call(gui, tickDelta);
			TitleOverlay.draw();
		});
	}

	@WrapOperation(
		method = "render(FJ)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(IIF)V")
	)
	private void keyframe$replayCursor(Screen screen, int mouseX, int mouseY, float tickDelta, Operation<Void> original) {
		DeltaTracker deltaTracker = new DeltaTracker(tickDelta);
		Recorder.INSTANCE.writeCursor(deltaTracker);
		CursorSample cursor = ScreenReplay.cursor(deltaTracker);
		if (cursor == null) {
			ScreenReplay.drawPreview(deltaTracker);
			original.call(screen, mouseX, mouseY, tickDelta);
		} else {
			try {
				original.call(screen, cursor.getMouseX(), cursor.getMouseY(), tickDelta);
			} catch (RuntimeException e) {
				ScreenReplay.INSTANCE.drop(e);
			}
			ScreenReplay.drawCursor(cursor);
		}
	}
}
