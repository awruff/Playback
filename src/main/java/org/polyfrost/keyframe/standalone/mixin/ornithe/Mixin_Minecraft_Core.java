package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleManager;
import net.minecraft.client.gui.ToastGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.client.render.pipeline.RenderTarget;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.Connection;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.polyfrost.keyframe.standalone.editor.Editor;
import org.polyfrost.keyframe.standalone.editor.EditorExport;
import org.polyfrost.keyframe.standalone.export.Export;
import org.polyfrost.keyframe.standalone.compat.GameClock;
import org.polyfrost.keyframe.standalone.recording.Highlights;
import org.polyfrost.keyframe.standalone.playback.InputGate;
import org.polyfrost.keyframe.standalone.playback.LocalEvents;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.polyfrost.keyframe.standalone.export.PreviewFrame;
import org.polyfrost.keyframe.standalone.playback.Puppet;
import org.polyfrost.keyframe.standalone.recording.Recorder;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class Mixin_Minecraft_Core {
	@Unique
	private Connection keyframe$leaving;

	@Shadow
	public abstract ClientPlayNetworkHandler getNetworkHandler();

	@Inject(method = "tick", at = @At("HEAD"))
	private void keyframe$tick(CallbackInfo ci) {
		InputGate.flush();
		Recorder.INSTANCE.tick();
		Keyframe.INSTANCE.pump();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void keyframe$endTick(CallbackInfo ci) {
		Recorder.INSTANCE.writeSnapshot();
		Editor.INSTANCE.tick();
		EditorExport.INSTANCE.tick();
		Highlights.tick();
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z"))
	private boolean keyframe$gateMouse(Operation<Boolean> original) {
		return InputGate.mouse(original);
	}

	@WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z"))
	private boolean keyframe$gateKeyboard(Operation<Boolean> original) {
		return InputGate.keyboard(original);
	}

	@Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("HEAD"))
	private void keyframe$rememberConnection(ClientWorld world, String message, CallbackInfo ci) {
		ClientPlayNetworkHandler handler = getNetworkHandler();
		this.keyframe$leaving = world == null && handler != null ? handler.getConnection() : null;
	}

	@Inject(method = "setWorld(Lnet/minecraft/client/world/ClientWorld;Ljava/lang/String;)V", at = @At("TAIL"))
	private void keyframe$closeRecording(ClientWorld world, String message, CallbackInfo ci) {
		if (this.keyframe$leaving != null) Recorder.INSTANCE.close(this.keyframe$leaving);
		this.keyframe$leaving = null;
	}

	@Inject(method = "shutdown", at = @At("HEAD"))
	private void keyframe$finish(CallbackInfo ci) {
		Recorder.INSTANCE.finish();
		Export.INSTANCE.cancel();
	}

	@WrapMethod(method = "pauseGame")
	private void keyframe$gatePause(Operation<Void> original) {
		if (!Keyframe.INSTANCE.getActive()) {
			original.call();
		}
	}

	@WrapMethod(method = "lockMouse")
	private void keyframe$gateGrab(Operation<Void> original) {
		if (!Keyframe.INSTANCE.getActive()) {
			original.call();
		}
	}

	@WrapMethod(method = "openScreen")
	private void keyframe$gateScreen(Screen screen, Operation<Void> original) {
		if (Keyframe.INSTANCE.allowsScreen(screen)) {
			original.call(screen);
		}
	}

	@Inject(method = "openScreen", at = @At("RETURN"))
	private void keyframe$writeScreen(CallbackInfo ci) {
		Recorder.INSTANCE.writeScreen();
	}

	@WrapOperation(
		method = "handleMouseDown",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/ParticleManager;addBlockMiningParticles(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Direction;)V")
	)
	private void keyframe$recordBreaking(ParticleManager particles, BlockPos pos, Direction face, Operation<Void> original) {
		LocalEvents.breaking(pos, face);
		original.call(particles, pos, face);
	}

	@WrapOperation(
		method = "runGame",
		at = @At(value = "FIELD", target = "Lnet/minecraft/client/Minecraft;paused:Z", opcode = Opcodes.PUTFIELD)
	)
	private void keyframe$holdPause(Minecraft minecraft, boolean paused, Operation<Void> original) {
		original.call(minecraft, paused || Puppet.getPaused());
	}

	@Inject(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/pipeline/RenderTarget;draw(II)V"))
	private void keyframe$readback(CallbackInfo ci) {
		Export.endFrame();
	}

	@WrapWithCondition(method = "runGame", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;sync(I)V"))
	private boolean keyframe$limitDisplayFps(int framerateLimit) {
		return !Export.getActive();
	}

	@WrapWithCondition(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/pipeline/RenderTarget;draw(II)V"))
	private boolean keyframe$blitPreview(RenderTarget target, int width, int height) {
		return Export.presentPreview();
	}

	@ModifyExpressionValue(method = "updateWindow", at = @At(value = "INVOKE", target = "Lorg/lwjgl/opengl/Display;wasResized()Z"))
	private boolean keyframe$holdExportSize(boolean resized) {
		return resized && !Export.getActive();
	}

	@ModifyReturnValue(method = "getTime", at = @At("RETURN"))
	private static long keyframe$clock(long time) {
		return GameClock.millis(time);
	}

	@WrapOperation(method = "runGame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ToastGui;render()V"))
	private void keyframe$fitToasts(ToastGui toasts, Operation<Void> original) {
		PreviewFrame.fitGui(() -> {
			if (Export.renderHud()) original.call(toasts);
		});
	}
}
