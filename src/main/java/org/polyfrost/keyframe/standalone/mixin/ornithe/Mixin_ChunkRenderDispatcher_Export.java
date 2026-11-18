package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.google.common.util.concurrent.ListenableFutureTask;
import net.minecraft.client.render.world.ChunkCompileTask;
import net.minecraft.client.render.world.ChunkRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Queue;
import java.util.concurrent.BlockingQueue;

@Mixin(ChunkRenderDispatcher.class)
public interface Mixin_ChunkRenderDispatcher_Export {
	@Accessor("pendingTasks")
	BlockingQueue<ChunkCompileTask> keyframe$getPendingTasks();

	@Accessor("pendingUploads")
	Queue<ListenableFutureTask<?>> keyframe$getPendingUploads();
}
