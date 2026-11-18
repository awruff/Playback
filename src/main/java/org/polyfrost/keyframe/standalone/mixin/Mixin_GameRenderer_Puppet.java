package org.polyfrost.keyframe.standalone.mixin;

//? if <26.1 {
/*import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.renderer.GameRenderer;
import org.polyfrost.keyframe.camera.ViewCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameRenderer.class)
abstract class Mixin_GameRenderer_Puppet {
	//? if >=1.21.2 {
	@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getFov(Lnet/minecraft/client/Camera;FZ)F", ordinal = 0))
	private float keyframe$fov(float fov) {
		return ViewCapture.fov(fov);
	}
	//?} else {
	/^@ModifyExpressionValue(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;getFov(Lnet/minecraft/client/Camera;FZ)D", ordinal = 0))
	private double keyframe$fov(double fov) {
		return ViewCapture.fov((float) fov);
	}
	^///?}
}
*///?}
