package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.MouseHandler;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
//? if <26.3 {
/*import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}

@Mixin(MouseHandler.class)
abstract class Mixin_MouseHandler_Core {
	@WrapMethod(method = "grabMouse")
	private void keyframe$gateGrab(Operation<Void> original) {
		if (!Keyframe.INSTANCE.getActive()) {
			original.call();
		}
	}

	//? if <26.3 {
	/*@Inject(method = {"onMove", "onButton", "onScroll", "onDrop"}, at = @At("HEAD"), cancellable = true)
	private void keyframe$gateInput(CallbackInfo ci) {
		if (Keyframe.INSTANCE.getBlocksInput()) ci.cancel();
	}
	*///?}
}
