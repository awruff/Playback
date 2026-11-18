package org.polyfrost.keyframe.standalone.mixin;

//? if >=26.3 {
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.SDLEventHandler;
import net.minecraft.client.Minecraft;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.lwjgl.sdl.SDLEvents;
import org.lwjgl.sdl.SDL_Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SDLEventHandler.class)
abstract class Mixin_SDLEventHandler_Core {
	@WrapOperation(
		method = "handleKeyEvent",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V")
	)
	private void keyframe$gateKey(Minecraft minecraft, Runnable task, Operation<Void> original, @Local(argsOnly = true) SDL_Event event) {
		if (!Keyframe.INSTANCE.getBlocksInput()) {
			original.call(minecraft, task);
		} else if (event.type() == SDLEvents.SDL_EVENT_KEY_DOWN && event.key().scancode() == InputConstants.KEY_ESCAPE) {
			original.call(minecraft, (Runnable) Keyframe.INSTANCE::stop);
		} else {
			original.call(minecraft, (Runnable) () -> {});
		}
	}

	@WrapOperation(
		method = {
			"handleTextInputEvent",
			"handleTextEditingEvent",
			"handleMouseMotionEvent",
			"handleMouseButtonEvent",
			"handleMouseWheelEvent",
			"handleDropCompleteEvent"
		},
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;execute(Ljava/lang/Runnable;)V")
	)
	private void keyframe$gateInput(Minecraft minecraft, Runnable task, Operation<Void> original) {
		original.call(minecraft, Keyframe.INSTANCE.getBlocksInput() ? (Runnable) () -> {} : task);
	}
}
//?}
