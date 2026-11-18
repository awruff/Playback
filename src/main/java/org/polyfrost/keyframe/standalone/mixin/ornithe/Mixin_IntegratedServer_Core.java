package org.polyfrost.keyframe.standalone.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.concurrent.Future;
import java.util.concurrent.locks.LockSupport;

@Mixin(IntegratedServer.class)
abstract class Mixin_IntegratedServer_Core {
	@WrapOperation(
		method = "stop",
		at = @At(value = "INVOKE", target = "Lcom/google/common/util/concurrent/Futures;getUnchecked(Ljava/util/concurrent/Future;)Ljava/lang/Object;")
	)
	private Object keyframe$waitWhileRunning(Future<?> task, Operation<Object> original) {
		while (!task.isDone()) {
			if (!((MinecraftServer) (Object) this).isRunning()) return null;
			LockSupport.parkNanos(1_000_000L);
		}
		return original.call(task);
	}
}
