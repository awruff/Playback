package org.polyfrost.keyframe.standalone.mixin;

//? if >=1.21.8 {
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface Mixin_LevelRenderer_Export {
	@Accessor("visibleSections")
	ObjectArrayList<SectionRenderDispatcher.RenderSection> keyframe$getVisibleSections();
}
//?}
