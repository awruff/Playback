package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.gui.ToastGui;
import org.polyfrost.keyframe.standalone.export.PreviewFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ToastGui.class)
abstract class Mixin_ToastGui_Editor {
	@WrapWithCondition(method = "setupToastsState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/platform/GlStateManager;viewport(IIII)V"))
	private boolean keyframe$keepPreviewViewport(int x, int y, int width, int height) {
		return PreviewFrame.getGuiWidth() == 0;
	}
}
