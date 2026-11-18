package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
//? if >=26.2 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuSurface;
import com.mojang.renderpearl.api.textures.GpuTextureView;
//?} else
/*import com.mojang.renderpearl.api.pipeline.RenderTarget;*/
import net.minecraft.client.Minecraft;
import org.polyfrost.keyframe.standalone.export.Export;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class Mixin_Minecraft_Export {
	@Inject(
		method = "renderFrame(Z)V",
		//? if >=26.2 {
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/LevelRenderer;endFrame()V", shift = At.Shift.AFTER)
		//?} elif >=1.21.4 {
		/*at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;flipFrame(Lcom/mojang/blaze3d/TracyFrameCapture;)V", shift = At.Shift.AFTER)
		*///?} else
		/*at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/systems/RenderSystem;flipFrame()V", shift = At.Shift.AFTER)*/
	)
	private void keyframe$readback(CallbackInfo ci) {
		Export.endFrame();
	}

	@WrapWithCondition(
		method = "renderFrame(Z)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/FramerateLimiter;limitDisplayFPS(I)V")
	)
	private boolean keyframe$limitDisplayFps(int framerateLimit) {
		return !Export.getActive();
	}

	//? if >=26.2 {
	@WrapWithCondition(
		method = "renderFrame(Z)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuSurface;acquireNextTexture()V")
	)
	private boolean keyframe$acquireSurface(GpuSurface surface) {
		return Export.acquireSurface() || surface.isAcquired();
	}

	@WrapOperation(
		method = "renderFrame(Z)V",
		at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/device/GpuSurface;blitFromTexture(Lcom/mojang/renderpearl/api/commands/CommandEncoder;Lcom/mojang/renderpearl/api/textures/GpuTextureView;)V")
	)
	private void keyframe$blitPreview(GpuSurface surface, CommandEncoder encoder, GpuTextureView view, Operation<Void> original) {
		original.call(surface, encoder, Export.surfaceView(view));
	}
	//?} else {
	/*@WrapWithCondition(
		method = "renderFrame(Z)V",
		//? if >=1.21.8 {
		at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/pipeline/RenderTarget;blitToScreen()V")
	)
	private boolean keyframe$blitPreview(RenderTarget target) {
		//?} else {
		/^at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/api/pipeline/RenderTarget;blitToScreen(II)V")
	)
	private boolean keyframe$blitPreview(RenderTarget target, int width, int height) {
		^///?}
		return Export.presentPreview();
	}
	*///?}

	//? if >=26.1 {
	@WrapWithCondition(
		method = "pauseIfInactive()V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;pauseGame(Z)V")
	)
	private boolean keyframe$pauseGame(Minecraft minecraft, boolean suppressPauseMenu) {
		return !Export.getActive();
	}
	//?}
}
