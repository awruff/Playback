package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.render.world.ChunkRenderDispatcher;
import net.minecraft.client.render.world.RenderChunk;
import net.minecraft.client.render.world.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

@Mixin(WorldRenderer.class)
public interface Mixin_WorldRenderer_Export {
	@Accessor("dirtyChunks")
	Set<RenderChunk> keyframe$getDirtyChunks();

	@Accessor("chunkRenderDispatcher")
	ChunkRenderDispatcher keyframe$getChunkRenderDispatcher();
}
