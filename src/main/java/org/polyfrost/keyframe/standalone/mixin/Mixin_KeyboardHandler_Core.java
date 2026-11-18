package org.polyfrost.keyframe.standalone.mixin;

//? if <26.3 {
/*import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
//? if >=1.21.10
/^import net.minecraft.client.input.KeyEvent;^/
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class Mixin_KeyboardHandler_Core {
	@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
	//? if >=1.21.10 {
	/^private void keyframe$gateKey(long handle, int action, KeyEvent event, CallbackInfo ci) {
		if (!Keyframe.INSTANCE.getBlocksInput()) return;
		if (action == InputConstants.PRESS && event.key() == InputConstants.KEY_ESCAPE) Keyframe.INSTANCE.stop();
		ci.cancel();
	}
	^///?} else {
	/^private void keyframe$gateKey(long handle, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
		if (!Keyframe.INSTANCE.getBlocksInput()) return;
		if (action == InputConstants.PRESS && key == InputConstants.KEY_ESCAPE) Keyframe.INSTANCE.stop();
		ci.cancel();
	}
	^///?}

	@Inject(method = {"charTyped", "preeditCallback"}, at = @At("HEAD"), cancellable = true)
	private void keyframe$gateText(CallbackInfo ci) {
		if (Keyframe.INSTANCE.getBlocksInput()) ci.cancel();
	}
}
*///?}
