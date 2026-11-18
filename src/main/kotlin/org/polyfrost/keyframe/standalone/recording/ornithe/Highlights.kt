package org.polyfrost.keyframe.standalone.recording

import net.minecraft.block.BedBlock
import net.minecraft.client.Minecraft
import net.minecraft.entity.Entity
import net.minecraft.entity.living.player.PlayerEntity
import net.minecraft.util.math.BlockPos
import org.polyfrost.keyframe.standalone.playback.Keyframe

object Highlights {
	private var detector = HighlightDetector(::record)

	@JvmStatic
	fun tick() {
		val world = Minecraft.getInstance().world
		if (world != null && !Keyframe.active) {
			detector.tick(
				Recorder.currentTick,
				{ (world.getEntity(it) as? PlayerEntity)?.let { player -> player.health <= 0f } == true },
				{ world.getBlockState(BlockPos.fromLong(it)).block is BedBlock },
			)
		}
	}

	@JvmStatic
	fun onAttack(entity: Entity) {
		if (entity is PlayerEntity && !Keyframe.active) detector.attacked(entity.networkId, Recorder.currentTick)
	}

	@JvmStatic
	fun onBreak(pos: BlockPos) {
		val world = Minecraft.getInstance().world ?: return
		if (world.getBlockState(pos).block is BedBlock && !Keyframe.active) detector.bedBroken(pos.toLong(), Recorder.currentTick)
	}

	@JvmStatic
	fun onMessage(message: String) {
		val name = Minecraft.getInstance().player?.name
		if (name != null && !Keyframe.active) detector.message(message, name, Recorder.currentTick)
	}

	@JvmStatic
	fun onDamage(entity: Int) {
		val self = Minecraft.getInstance().player ?: return
		if (!Keyframe.active) detector.damaged(entity, self.networkId, Recorder.currentTick)
	}

	@JvmStatic
	fun reset() {
		detector = HighlightDetector(::record)
	}
}
