package org.polyfrost.keyframe.standalone.mixin;

//? if >=1.21.8 {
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if >=26.2 {
@Mixin(SectionRenderDispatcher.RenderSection.SectionTask.class)
//?} else
/*@Mixin(SectionRenderDispatcher.RenderSection.CompileTask.class)*/
public interface Mixin_SectionTask_Export {
	@Accessor("isCompleted")
	AtomicBoolean keyframe$getIsCompleted();

	@Accessor("isCancelled")
	AtomicBoolean keyframe$getIsCancelled();
}
//?}
