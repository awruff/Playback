package org.polyfrost.keyframe.standalone.mixin;

//? if >=1.21.8 <26.1 {
/*import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
//? if >=1.21.11
/^import com.mojang.renderpearl.api.textures.GpuSampler;^/
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GuiGraphicsExtractor.class)
public interface Mixin_Graphics_Blit {
	//? if >=1.21.11 {
	/^@Invoker("submitBlit")
	void keyframe$submitBlit(RenderPipeline pipeline, GpuTextureView view, GpuSampler sampler, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, int color);
	^///?} else {
	@Invoker("submitBlit")
	void keyframe$submitBlit(RenderPipeline pipeline, GpuTextureView view, int x0, int y0, int x1, int y1, float u0, float u1, float v0, float v1, int color);
	//?}
}
*///?}
