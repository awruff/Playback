package org.polyfrost.keyframe.standalone.mixin;

//? if <1.21.10 {
/*import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.polyfrost.keyframe.standalone.export.Export;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(DebugScreenOverlay.class)
abstract class Mixin_DebugScreenOverlay_Export {
	@WrapMethod(method = "extractRenderState")
	private void keyframe$gateOverlay(GuiGraphicsExtractor graphics, Operation<Void> original) {
		if (Export.renderHud() && !Keyframe.INSTANCE.getPreviewing()) {
			original.call(graphics);
		}
	}
}
*///?}
