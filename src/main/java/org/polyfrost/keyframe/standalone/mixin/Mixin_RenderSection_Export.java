package org.polyfrost.keyframe.standalone.mixin;

//? if >=1.21.8 {
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.polyfrost.keyframe.standalone.export.Export;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SectionRenderDispatcher.RenderSection.class)
public abstract class Mixin_RenderSection_Export {
	@Inject(method = "compileSync", at = @At("TAIL"))
	private void keyframe$completeSyncTask(CallbackInfo ci) {
		Export.completeSyncTask((SectionRenderDispatcher.RenderSection) (Object) this);
	}
}
//?}
