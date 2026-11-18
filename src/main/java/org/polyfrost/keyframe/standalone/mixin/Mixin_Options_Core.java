package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Options;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Options.class)
abstract class Mixin_Options_Core {
	@WrapMethod(method = "save")
	private void keyframe$gateSave(Operation<Void> original) {
		if (!Keyframe.INSTANCE.getActive()) {
			original.call();
		}
	}
}
