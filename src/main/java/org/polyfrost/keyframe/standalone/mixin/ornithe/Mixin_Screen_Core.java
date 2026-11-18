package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.keyframe.standalone.playback.InputGate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Screen.class)
abstract class Mixin_Screen_Core {
	@WrapOperation(method = "handleInputs", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Mouse;next()Z"))
	private boolean keyframe$gateMouse(Operation<Boolean> original) {
		return InputGate.mouse(original);
	}

	@WrapOperation(method = "handleInputs", at = @At(value = "INVOKE", target = "Lorg/lwjgl/input/Keyboard;next()Z"))
	private boolean keyframe$gateKeyboard(Operation<Boolean> original) {
		return InputGate.keyboard(original);
	}
}
