package org.polyfrost.keyframe.standalone.playback

//? if >=1.21.1 {
import io.netty.buffer.ByteBufUtil
import io.netty.buffer.Unpooled
import net.minecraft.client.KeyMapping
//? if >=1.21.4 {
import net.minecraft.client.player.ClientInput
//?} else
/*import net.minecraft.client.player.Input*/
import net.minecraft.client.player.LocalPlayer
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.RegistryAccess
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.sounds.SoundEvents
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Pose
//? if >=1.21.4
import net.minecraft.world.entity.player.Input
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack
//? if >=26.3 {
import net.minecraft.world.item.SwingAnimationType
import net.minecraft.world.item.component.SwingAnimation
//?}
//? if >=1.21.10
import org.polyfrost.keyframe.standalone.mixin.Mixin_ClientAvatarState_Puppet
import org.polyfrost.keyframe.standalone.mixin.Mixin_FirstPersonHandsAndItems_Puppet
import org.polyfrost.keyframe.standalone.mixin.Mixin_LivingEntity_Puppet
//? if >=1.21.10
import org.polyfrost.keyframe.standalone.mixin.Mixin_LocalPlayer_Spinning
//? if >=26.3
import org.polyfrost.keyframe.standalone.mixin.Mixin_SwingState_Puppet
import org.polyfrost.keyframe.camera.ViewCapture
//?}
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import org.polyfrost.keyframe.standalone.Standalone
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.recording.ReplayFile
import org.polyfrost.keyframe.standalone.recording.ReplayReader
import org.polyfrost.keyframe.standalone.recording.ReplayRecord
import org.polyfrost.keyframe.standalone.recording.ReplayWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer

internal const val ON_GROUND = 1
internal const val SHIFT = 1 shl 1
internal const val SPRINTING = 1 shl 2
internal const val SWIMMING = 1 shl 3
internal const val FALL_FLYING = 1 shl 4
internal const val FLYING = 1 shl 5
internal const val USING_ITEM = 1 shl 6
internal const val USING_OFF_HAND = 1 shl 7
internal const val LEFT_HANDED = 1 shl 8
internal const val PAUSED = 1 shl 9
internal const val SWINGING = 1 shl 10
internal const val SWINGING_OFF_HAND = 1 shl 11
internal const val SHOWS_MAIN_HAND_ITEM = 1 shl 12
internal const val SHOWS_OFF_HAND_ITEM = 1 shl 13

private const val LEGACY_CAMERA_PAYLOAD_LEN = 12
private const val CAMERA_PAYLOAD_LEN = 16
internal const val CARRIED_SLOT = -1

internal class SlotChange(val slot: Int, val stack: ByteArray)

internal data class ScreenEffects(
	val guiTicks: Int,
	val portalIntensity: Float,
	val oPortalIntensity: Float,
	val spinningTime: Float,
	val spinningSpeed: Float,
) {
	val entersPortal: Boolean
		get() = oPortalIntensity == 0f && portalIntensity > 0f
}

internal data class Snapshot(
	val x: Double,
	val y: Double,
	val z: Double,
	val yRot: Float,
	val xRot: Float,
	val deltaX: Double,
	val deltaY: Double,
	val deltaZ: Double,
	val pose: Int,
	val flags: Int,
	val selectedSlot: Int,
	val useItemRemaining: Int,
	val attackStrengthTicker: Int,
	val itemSwapTicker: Int,
	val xBob: Float,
	val yBob: Float,
	val xBobO: Float,
	val yBobO: Float,
	val bob: Float,
	val walkDist: Float,
	val swingType: Int,
	val swingDuration: Int,
	val swingTicks: Int,
	val oldSwingAnimation: Float,
	val swingAnimation: Float,
	val mainHandHeight: Float,
	val oMainHandHeight: Float,
	val offHandHeight: Float,
	val oOffHandHeight: Float,
	val slots: List<SlotChange>,
	val effects: ScreenEffects? = null,
) {
	fun has(flag: Int) = flags and flag != 0
}

interface Timed {
	val time: Double
}

internal class CameraSample(override val time: Double, val yaw: Float, val pitch: Float, val fov: Float = Float.NaN) : Timed

internal fun encodeSnapshot(snapshot: Snapshot): ByteArray {
	val bytes = ByteArrayOutputStream()
	DataOutputStream(bytes).run {
		writeDouble(snapshot.x)
		writeDouble(snapshot.y)
		writeDouble(snapshot.z)
		writeFloat(snapshot.yRot)
		writeFloat(snapshot.xRot)
		writeDouble(snapshot.deltaX)
		writeDouble(snapshot.deltaY)
		writeDouble(snapshot.deltaZ)
		writeInt(snapshot.pose)
		writeInt(snapshot.flags)
		writeInt(snapshot.selectedSlot)
		writeInt(snapshot.useItemRemaining)
		writeInt(snapshot.attackStrengthTicker)
		writeInt(snapshot.itemSwapTicker)
		writeFloat(snapshot.xBob)
		writeFloat(snapshot.yBob)
		writeFloat(snapshot.xBobO)
		writeFloat(snapshot.yBobO)
		writeFloat(snapshot.bob)
		writeFloat(snapshot.walkDist)
		writeInt(snapshot.swingType)
		writeInt(snapshot.swingDuration)
		writeInt(snapshot.swingTicks)
		writeFloat(snapshot.oldSwingAnimation)
		writeFloat(snapshot.swingAnimation)
		writeFloat(snapshot.mainHandHeight)
		writeFloat(snapshot.oMainHandHeight)
		writeFloat(snapshot.offHandHeight)
		writeFloat(snapshot.oOffHandHeight)
		writeInt(snapshot.slots.size)
		for (change in snapshot.slots) {
			writeInt(change.slot)
			writeInt(change.stack.size)
			write(change.stack)
		}
		snapshot.effects?.let {
			writeInt(it.guiTicks)
			writeFloat(it.portalIntensity)
			writeFloat(it.oPortalIntensity)
			writeFloat(it.spinningTime)
			writeFloat(it.spinningSpeed)
		}
	}
	return bytes.toByteArray()
}

internal fun decodeSnapshot(payload: ByteArray): Snapshot = DataInputStream(ByteArrayInputStream(payload)).run {
	Snapshot(
		readDouble(),
		readDouble(),
		readDouble(),
		readFloat(),
		readFloat(),
		readDouble(),
		readDouble(),
		readDouble(),
		readInt(),
		readInt(),
		readInt(),
		readInt(),
		readInt(),
		readInt(),
		readFloat(),
		readFloat(),
		readFloat(),
		readFloat(),
		readFloat(),
		readFloat(),
		readInt(),
		readInt(),
		readInt(),
		readFloat(),
		readFloat(),
		readFloat(),
		readFloat(),
		readFloat(),
		readFloat(),
		List(readBounded()) { SlotChange(readInt(), readNBytes(readBounded())) },
		if (available() > 0) ScreenEffects(readInt(), readFloat(), readFloat(), readFloat(), readFloat()) else null,
	)
}

private fun DataInputStream.readBounded(): Int =
	readInt().also { if (it !in 0..available()) throw IOException("Malformed snapshot") }

internal fun encodeCamera(partial: Float, yaw: Float, pitch: Float, fov: Float = Float.NaN): ByteArray =
	ByteBuffer.allocate(CAMERA_PAYLOAD_LEN).putFloat(partial).putFloat(yaw).putFloat(pitch).putFloat(fov).array()

internal fun decodeCamera(record: ReplayRecord): CameraSample? {
	if (record.type != ReplayFile.CAMERA || record.payload.size != CAMERA_PAYLOAD_LEN && record.payload.size != LEGACY_CAMERA_PAYLOAD_LEN) return null
	val buffer = ByteBuffer.wrap(record.payload)
	return CameraSample(
		record.tick + buffer.getFloat().toDouble(),
		buffer.getFloat(),
		buffer.getFloat(),
		if (buffer.hasRemaining()) buffer.getFloat() else Float.NaN,
	)
}

internal fun progress(before: Timed, after: Timed, time: Double): Float {
	val span = after.time - before.time
	return if (span <= 0.0) 1f else Math.clamp((time - before.time) / span, 0.0, 1.0).toFloat()
}

internal fun interpolateCamera(before: CameraSample, after: CameraSample, time: Double): CameraSample {
	val progress = progress(before, after, time)
	return CameraSample(
		time,
		before.yaw + (after.yaw - before.yaw) * progress,
		before.pitch + (after.pitch - before.pitch) * progress,
		before.fov + (after.fov - before.fov) * progress,
	)
}

internal class SampleTrack<T : Timed>(private val interpolate: (T, T, Double) -> T, private val next: () -> T?) {
	private var before: T? = null
	private var after: T? = null

	fun at(time: Double): T? {
		while (after.let { it == null || it.time <= time }) {
			val sample = next() ?: break
			before = after
			after = sample
		}
		val after = after ?: return null
		val before = before ?: return after
		return interpolate(before, after, time)
	}
}

abstract class PuppetCore {
	protected val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)

	internal var snapshot: Snapshot? = null
		private set
	internal val pendingSlots = HashMap<Int, ByteArray>()
	private var cameraReader: ReplayReader? = null
	private var camera: SampleTrack<CameraSample>? = null

	internal var sampledWriter: ReplayWriter? = null

	protected abstract fun playPortalTrigger()

	internal abstract fun applySample(player: LocalPlayer, sample: CameraSample)

	protected open fun ended() {}

	fun begin(reader: ReplayReader) {
		cameraReader = reader
		camera = SampleTrack(::interpolateCamera) { generateSequence(reader::next).firstNotNullOfOrNull(::decodeCamera) }
	}

	fun end() {
		cameraReader?.close()
		cameraReader = null
		camera = null
		ended()
		clearSnapshot()
	}

	fun clearSnapshot() {
		snapshot = null
		pendingSlots.clear()
	}

	fun onSnapshot(record: ReplayRecord) {
		try {
			val snapshot = decodeSnapshot(record.payload)
			this.snapshot = snapshot
			for (change in snapshot.slots) pendingSlots[change.slot] = change.stack
			if (snapshot.effects?.entersPortal == true && !Keyframe.rolling) playPortalTrigger()
		} catch (e: IOException) {
			LOGGER.error("Could not read snapshot at tick {}", record.tick, e)
		}
	}

	fun guiTicks(live: Int): Int = snapshot?.effects?.guiTicks ?: live

	fun applyCamera(deltaTracker: DeltaTracker) {
		if (Keyframe.rolling) return
		val camera = camera ?: return
		val player = Minecraft.getInstance().player ?: return
		val sample = camera.at(Keyframe.tick + deltaTracker.getGameTimeDeltaPartialTick(true).toDouble()) ?: return
		applySample(player, sample)
	}

	fun resetSampling() {
		sampledWriter = null
	}
}

//? if >=1.21.1 {
object Puppet : PuppetCore() {
	private val sampledSlots = HashMap<Int, ItemStack>()

	val paused: Boolean
		get() = Keyframe.held || snapshot?.has(PAUSED) == true && Minecraft.getInstance().connection?.hasClientLoaded() == true

	override fun ended() {
		ViewCapture.playFov(Float.NaN)
	}

	override fun playPortalTrigger() {
		val mc = Minecraft.getInstance()
		val player = mc.player ?: return
		mc.soundManager.play(
			SimpleSoundInstance.forLocalAmbience(SoundEvents.PORTAL_TRIGGER, player.getRandom().nextFloat() * 0.4f + 0.8f, 0.25f),
		)
	}

	fun isDown(key: KeyMapping, down: Boolean): Boolean {
		val mc = Minecraft.getInstance()
		if (!Keyframe.active || key !== mc.options.keyUse) return down
		return snapshot?.has(USING_ITEM) == true && mc.player?.isUsingItem == true
	}

	fun ageWhileRolling() {
		val player = Minecraft.getInstance().player ?: return
		player.activeEffectsMap.values.forEach { it.tickClient() }
		//? if >=26.3
		player.itemActivation().tick()
	}

	fun beforeTick(player: LocalPlayer) {
		if (!Keyframe.active || Keyframe.rolling) return
		val snapshot = snapshot ?: return
		//? if >=1.21.4 {
		player.input = ClientInput().apply {
			keyPresses = Input(false, false, false, false, false, snapshot.has(SHIFT), false)
		}
		//?} else {
		/*player.input = Input().apply {
			shiftKeyDown = snapshot.has(SHIFT)
		}
		*///?}
		applySlots(player)
		player.inventory.selectedSlot = Math.clamp(snapshot.selectedSlot.toLong(), 0, Inventory.getSelectionSize() - 1)
		applyBody(player, snapshot)
	}

	fun afterTick(player: LocalPlayer) {
		if (!Keyframe.active || Keyframe.rolling) return
		val snapshot = snapshot ?: return
		applyBody(player, snapshot)
		(player as Mixin_LivingEntity_Puppet).run {
			`keyframe$setUseItemRemaining`(snapshot.useItemRemaining)
			`keyframe$setAttackStrengthTicker`(snapshot.attackStrengthTicker)
			//? if >=1.21.11
			`keyframe$setItemSwapTicker`(snapshot.itemSwapTicker)
			applySwing(player, snapshot)
		}
		applyHands(player, snapshot)
		snapshot.effects?.let { applyEffects(player, it) }
		player.xBob = snapshot.xBob
		player.yBob = snapshot.yBob
		player.xBobO = snapshot.xBobO
		player.yBobO = snapshot.yBobO
		//? if >=1.21.10 {
		(player.avatarState() as Mixin_ClientAvatarState_Puppet).run {
			`keyframe$setBob`(snapshot.bob)
			`keyframe$setWalkDist`(snapshot.walkDist)
		}
		//?} else {
		/*player.bob = snapshot.bob
		player.walkDist = snapshot.walkDist
		*///?}
	}

	override fun applySample(player: LocalPlayer, sample: CameraSample) {
		player.yRot = sample.yaw
		player.yRotO = sample.yaw
		player.xRot = sample.pitch
		player.xRotO = sample.pitch
		ViewCapture.playFov(sample.fov)
	}

	fun sampleCamera(deltaTracker: DeltaTracker): ByteArray? {
		val player = Minecraft.getInstance().player ?: return null
		val partial = deltaTracker.getGameTimeDeltaPartialTick(true)
		return encodeCamera(partial, ViewCapture.yaw(player, partial), ViewCapture.pitch(player, partial), ViewCapture.fovScale())
	}

	fun sampleSnapshot(writer: ReplayWriter): ByteArray? {
		val mc = Minecraft.getInstance()
		val player = mc.player ?: return null
		if (writer !== sampledWriter) {
			sampledWriter = writer
			sampledSlots.clear()
		}
		val delta = player.deltaMovement
		//? if >=1.21.10
		val avatar = player.avatarState()
		val living = player as Mixin_LivingEntity_Puppet
		val swing = sampleSwing(player)
		val hands = hands(player)
		//? if >=1.21.10
		val spinning = spinning(player)
		val flags = flag(player.onGround(), ON_GROUND) or
			flag(player.isShiftKeyDown, SHIFT) or
			flag(player.isSprinting, SPRINTING) or
			flag(player.isSwimming, SWIMMING) or
			flag(player.isFallFlying, FALL_FLYING) or
			flag(player.abilities.flying, FLYING) or
			flag(player.isUsingItem, USING_ITEM) or
			flag(player.usedItemHand == InteractionHand.OFF_HAND, USING_OFF_HAND) or
			flag(player.mainArm == HumanoidArm.LEFT, LEFT_HANDED) or
			flag(mc.isPaused, PAUSED) or
			flag(swing.active, SWINGING) or
			flag(swing.offHand, SWINGING_OFF_HAND) or
			flag(hands.`keyframe$getMainHandItem`() === player.mainHandItem, SHOWS_MAIN_HAND_ITEM) or
			flag(hands.`keyframe$getOffHandItem`() === player.offhandItem, SHOWS_OFF_HAND_ITEM)
		return encodeSnapshot(
			Snapshot(
				player.x,
				player.y,
				player.z,
				player.yRot,
				player.xRot,
				delta.x,
				delta.y,
				delta.z,
				player.pose.id(),
				flags,
				player.inventory.selectedSlot,
				player.useItemRemainingTicks,
				living.`keyframe$getAttackStrengthTicker`(),
				//? if >=1.21.11 {
				living.`keyframe$getItemSwapTicker`(),
				//?} else
				/*0,*/
				player.xBob,
				player.yBob,
				player.xBobO,
				player.yBobO,
				//? if >=1.21.10 {
				avatar.getInterpolatedBob(1f),
				avatar.getInterpolatedWalkDistance(1f),
				//?} else {
				/*player.bob,
				player.walkDist,
				*///?}
				swing.type,
				swing.duration,
				swing.ticks,
				swing.oldAnimation,
				swing.animation,
				hands.`keyframe$getMainHandHeight`(),
				hands.`keyframe$getOMainHandHeight`(),
				hands.`keyframe$getOffHandHeight`(),
				hands.`keyframe$getOOffHandHeight`(),
				sampleSlots(player),
				ScreenEffects(
					mc.gui.hud.guiTicks,
					player.portalEffectIntensity,
					player.oPortalEffectIntensity,
					//? if >=1.21.10 {
					spinning.`keyframe$getSpinningTime`(),
					spinning.`keyframe$getSpinningSpeed`(),
					//?} else {
					/*0f,
					0f,
					*///?}
				),
			),
		)
	}

	private fun flag(set: Boolean, flag: Int) = if (set) flag else 0

	private fun sampleSlots(player: LocalPlayer): List<SlotChange> {
		val inventory = player.inventory
		val changes = ArrayList<SlotChange>()
		for (slot in 0 until inventory.containerSize) sampleSlot(slot, inventory.getItem(slot), player.registryAccess(), changes)
		sampleSlot(CARRIED_SLOT, player.containerMenu.carried, player.registryAccess(), changes)
		return changes
	}

	private fun sampleSlot(slot: Int, stack: ItemStack, registries: RegistryAccess, changes: MutableList<SlotChange>) {
		if (ItemStack.matches(stack, sampledSlots.getOrDefault(slot, ItemStack.EMPTY))) return
		sampledSlots[slot] = stack.copy()
		try {
			changes += SlotChange(slot, encodeStack(stack, registries))
		} catch (e: RuntimeException) {
			LOGGER.warn("Could not record inventory slot {}", slot, e)
		}
	}

	private fun applySlots(player: LocalPlayer) {
		val inventory = player.inventory
		val registries = player.registryAccess()
		for ((slot, stack) in pendingSlots) {
			try {
				when (slot) {
					CARRIED_SLOT -> player.containerMenu.carried = decodeStack(stack, registries)
					in 0 until inventory.containerSize -> inventory.setItem(slot, decodeStack(stack, registries))
				}
			} catch (e: RuntimeException) {
				LOGGER.warn("Could not restore inventory slot {}", slot, e)
			}
		}
		pendingSlots.clear()
	}

	private fun applyBody(player: LocalPlayer, snapshot: Snapshot) {
		player.pose = Pose.BY_ID.apply(snapshot.pose)
		player.setPos(snapshot.x, snapshot.y, snapshot.z)
		player.yRot = snapshot.yRot
		player.xRot = snapshot.xRot
		player.setDeltaMovement(snapshot.deltaX, snapshot.deltaY, snapshot.deltaZ)
		player.setOnGround(snapshot.has(ON_GROUND))
		player.isSwimming = snapshot.has(SWIMMING)
		player.abilities.flying = snapshot.has(FLYING)
		player.mainArm = if (snapshot.has(LEFT_HANDED)) HumanoidArm.LEFT else HumanoidArm.RIGHT
		if (player.isSprinting != snapshot.has(SPRINTING)) player.isSprinting = snapshot.has(SPRINTING)
		if (player.isFallFlying != snapshot.has(FALL_FLYING)) {
			if (snapshot.has(FALL_FLYING)) player.startFallFlying() else player.stopFallFlying()
		}
		if (snapshot.has(USING_ITEM)) {
			player.startUsingItem(if (snapshot.has(USING_OFF_HAND)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND)
		} else if (player.isUsingItem) {
			player.stopUsingItem()
		}
	}

	private class SwingSample(
		val active: Boolean,
		val offHand: Boolean,
		val type: Int,
		val duration: Int,
		val ticks: Int,
		val oldAnimation: Float,
		val animation: Float,
	)

	//? if >=26.3 {
	private fun swingState(player: LocalPlayer) =
		(player as Mixin_LivingEntity_Puppet).`keyframe$getSwingState`() as Mixin_SwingState_Puppet

	private fun sampleSwing(player: LocalPlayer): SwingSample {
		val swing = swingState(player)
		val current = player.currentSwing
		return SwingSample(
			current != null,
			current?.hand() == InteractionHand.OFF_HAND,
			current?.animation()?.type()?.ordinal ?: 0,
			current?.durationTicks() ?: 0,
			swing.`keyframe$getTicks`(),
			swing.`keyframe$getOldAnimation`(),
			swing.`keyframe$getAnimation`(),
		)
	}

	private fun applySwing(player: LocalPlayer, snapshot: Snapshot) {
		val swing = swingState(player)
		val type = SwingAnimationType.entries.getOrElse(snapshot.swingType) { SwingAnimation.DEFAULT.type() }
		val hand = if (snapshot.has(SWINGING_OFF_HAND)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
		val duration = snapshot.swingDuration
		swing.`keyframe$setCurrentSwing`(
			if (snapshot.has(SWINGING)) LivingEntity.SwingDescription(hand, SwingAnimation(type, duration), duration) else null,
		)
		swing.`keyframe$setTicks`(snapshot.swingTicks)
		swing.`keyframe$setOldAnimation`(snapshot.oldSwingAnimation)
		swing.`keyframe$setAnimation`(snapshot.swingAnimation)
	}

	private fun hands(player: LocalPlayer) = player.firstPersonHandsAndItems() as Mixin_FirstPersonHandsAndItems_Puppet

	private fun spinning(player: LocalPlayer) = player as Mixin_LocalPlayer_Spinning
	//?} else {
	/*private fun sampleSwing(player: LocalPlayer) = SwingSample(
		player.swinging,
		player.swingingArm == InteractionHand.OFF_HAND,
		0,
		0,
		player.swingTime,
		player.oAttackAnim,
		player.attackAnim,
	)

	private fun applySwing(player: LocalPlayer, snapshot: Snapshot) {
		player.swinging = snapshot.has(SWINGING)
		player.swingingArm = if (snapshot.has(SWINGING_OFF_HAND)) InteractionHand.OFF_HAND else InteractionHand.MAIN_HAND
		player.swingTime = snapshot.swingTicks
		player.oAttackAnim = snapshot.oldSwingAnimation
		player.attackAnim = snapshot.swingAnimation
	}

	private fun hands(player: LocalPlayer) =
		Minecraft.getInstance().gameRenderer.itemInHandRenderer as Mixin_FirstPersonHandsAndItems_Puppet

	//? if >=1.21.10
	/^private fun spinning(player: LocalPlayer) = Minecraft.getInstance().gameRenderer as Mixin_LocalPlayer_Spinning^/
	*///?}

	private fun applyHands(player: LocalPlayer, snapshot: Snapshot) {
		hands(player).run {
			`keyframe$setMainHandHeight`(snapshot.mainHandHeight)
			`keyframe$setOMainHandHeight`(snapshot.oMainHandHeight)
			`keyframe$setOffHandHeight`(snapshot.offHandHeight)
			`keyframe$setOOffHandHeight`(snapshot.oOffHandHeight)
			if (snapshot.has(SHOWS_MAIN_HAND_ITEM)) `keyframe$setMainHandItem`(player.mainHandItem)
			if (snapshot.has(SHOWS_OFF_HAND_ITEM)) `keyframe$setOffHandItem`(player.offhandItem)
		}
	}

	private fun applyEffects(player: LocalPlayer, effects: ScreenEffects) {
		player.portalEffectIntensity = effects.portalIntensity
		player.oPortalEffectIntensity = effects.oPortalIntensity
		//? if >=1.21.10 {
		spinning(player).run {
			`keyframe$setSpinningTime`(effects.spinningTime)
			`keyframe$setSpinningSpeed`(effects.spinningSpeed)
		}
		//?}
	}

	private fun encodeStack(stack: ItemStack, registries: RegistryAccess): ByteArray {
		val buffer = RegistryFriendlyByteBuf(Unpooled.buffer(), registries)
		ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, stack)
		return ByteBufUtil.getBytes(buffer)
	}

	private fun decodeStack(stack: ByteArray, registries: RegistryAccess): ItemStack =
		ItemStack.OPTIONAL_STREAM_CODEC.decode(RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(stack), registries))
}
//?}
