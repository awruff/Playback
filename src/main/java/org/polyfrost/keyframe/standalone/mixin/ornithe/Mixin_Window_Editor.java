package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.render.Window;
import org.polyfrost.keyframe.standalone.export.PreviewFrame;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
abstract class Mixin_Window_Editor {
	@Shadow
	private int width;

	@Shadow
	private int height;

	@Shadow
	@Final
	@Mutable
	private double scaledWidth;

	@Shadow
	@Final
	@Mutable
	private double scaledHeight;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void keyframe$fitPreview(CallbackInfo ci) {
		if (PreviewFrame.getGuiWidth() == 0) return;
		this.width = PreviewFrame.getGuiWidth();
		this.height = PreviewFrame.getGuiHeight();
		this.scaledWidth = this.width;
		this.scaledHeight = this.height;
	}
}
