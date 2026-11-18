package org.polyfrost.keyframe.standalone.recording

import org.polyfrost.keyframe.standalone.StandaloneConfig
import org.polyfrost.keyframe.standalone.editor.TICKS_PER_SECOND
import org.polyfrost.keyframe.standalone.playback.Keyframe


//? if >=1.21.1 {
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
//?}

private const val COMBO_HITS = 5
private const val HIT_CONFIRM_TICKS = 10
private const val COMBO_TIMEOUT_TICKS = 60
private const val KILL_CREDIT_TICKS = 100
private const val DUPLICATE_TICKS = 40
private const val BREAK_CONFIRM_TICKS = 10

internal enum class HighlightKind(val colour: Int, val label: String) {
	COMBO(0xFFAA00, "Combo"),
	KILL(0xFF5555, "Kill"),
	FINAL_KILL(0xFF55FF, "Final kill"),
	BED(0x55FFFF, "Bed broken"),
}

internal fun classifyMessage(text: String, name: String): HighlightKind? {
	if (':' in text || text.startsWith(name) || !Regex("\\bby ${Regex.escape(name)}\\b").containsMatchIn(text)) return null
	val lower = text.lowercase()
	return when {
		"bed" in lower -> HighlightKind.BED
		"final kill" in lower -> HighlightKind.FINAL_KILL
		else -> HighlightKind.KILL
	}
}

internal fun highlightClip(now: Int, afterSeconds: Int, clipLength: Int): Clip {
	val end = now + minOf(afterSeconds, clipLength) * TICKS_PER_SECOND
	return Clip(maxOf(0, end - clipLength * TICKS_PER_SECOND), end)
}

internal class HighlightDetector(private val onHighlight: (HighlightKind, Int) -> Unit) {
	private val lastMarked = HashMap<HighlightKind, Int>()
	private val hitPlayers = HashMap<Int, Int>()
	private val brokenBeds = HashMap<Long, Int>()
	private var target = -1
	private var attackTick = 0
	private var hitTick = Int.MIN_VALUE / 2
	private var combo = 0

	fun attacked(entity: Int, now: Int) {
		target = entity
		attackTick = now
	}

	fun damaged(entity: Int, self: Int, now: Int) {
		if (entity == self) {
			combo = 0
			return
		}
		if (entity != target || now - attackTick > HIT_CONFIRM_TICKS) return
		if (now - hitTick > COMBO_TIMEOUT_TICKS) combo = 0
		hitTick = now
		hitPlayers[entity] = now
		if (++combo == COMBO_HITS) detected(HighlightKind.COMBO, now)
	}

	fun bedBroken(pos: Long, now: Int) {
		brokenBeds[pos] = now
	}

	fun message(text: String, name: String, now: Int) {
		classifyMessage(text, name)?.let { detected(it, now) }
	}

	fun tick(now: Int, isDead: (Int) -> Boolean, isBed: (Long) -> Boolean) {
		hitPlayers.entries.removeIf { (entity, tick) ->
			val credited = now - tick <= KILL_CREDIT_TICKS
			val killed = credited && isDead(entity)
			if (killed) detected(HighlightKind.KILL, now)
			killed || !credited
		}
		brokenBeds.entries.removeIf { (pos, tick) ->
			val confirmed = now - tick >= BREAK_CONFIRM_TICKS
			if (confirmed && !isBed(pos)) detected(HighlightKind.BED, now)
			confirmed
		}
	}

	private fun detected(kind: HighlightKind, now: Int) {
		val dedupe = if (kind == HighlightKind.FINAL_KILL) HighlightKind.KILL else kind
		val last = lastMarked[dedupe]
		if (last != null && now - last < DUPLICATE_TICKS) return
		lastMarked[dedupe] = now
		onHighlight(kind, now)
	}
}

internal fun record(kind: HighlightKind, now: Int) {
	Recorder.writeHighlight(kind.colour, kind.label)
	val wanted = when (kind) {
		HighlightKind.COMBO -> StandaloneConfig.clipCombos
		HighlightKind.BED -> StandaloneConfig.clipBeds
		HighlightKind.KILL, HighlightKind.FINAL_KILL -> StandaloneConfig.clipKills
	}
	if (!StandaloneConfig.shadowReplay || !wanted) return
	val clip = highlightClip(now, StandaloneConfig.afterHighlight, StandaloneConfig.clipLength)
	Recorder.requestClip(clip.start, clip.end)
}

//? if >=1.21.1 {
object Highlights {
	private var detector = HighlightDetector(::record)

	fun register() {
		ClientTickEvents.END_CLIENT_TICK.register { minecraft ->
			val level = minecraft.level
			if (level != null && !Keyframe.active) {
				detector.tick(
					Recorder.currentTick,
					{ (level.getEntity(it) as? Player)?.isDeadOrDying == true },
					{ level.getBlockState(BlockPos.of(it)).`is`(BlockTags.BEDS) },
				)
			}
		}
		AttackEntityCallback.EVENT.register { _, level, _, entity, _ ->
			if (level.isClientSide && entity is Player && !Keyframe.active) detector.attacked(entity.id, Recorder.currentTick)
			InteractionResult.PASS
		}
		ClientPlayerBlockBreakEvents.AFTER.register { _, _, pos, state ->
			if (state.`is`(BlockTags.BEDS) && !Keyframe.active) detector.bedBroken(pos.asLong(), Recorder.currentTick)
		}
		ClientReceiveMessageEvents.GAME.register { message, overlay ->
			val name = Minecraft.getInstance().player?.name?.string
			if (!overlay && name != null && !Keyframe.active) detector.message(message.string, name, Recorder.currentTick)
		}
	}

	fun onDamage(entity: Int) {
		val self = Minecraft.getInstance().player ?: return
		if (!Keyframe.active) detector.damaged(entity, self.id, Recorder.currentTick)
	}

	fun reset() {
		detector = HighlightDetector(::record)
	}
}
//?}
