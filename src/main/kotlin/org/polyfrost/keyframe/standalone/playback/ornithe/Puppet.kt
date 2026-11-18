package org.polyfrost.keyframe.standalone.playback

import io.netty.buffer.Unpooled
import net.minecraft.client.Minecraft
import net.minecraft.client.entity.living.player.Input
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity
import net.minecraft.client.sound.instance.SimpleSoundInstance
import net.minecraft.item.ItemStack
import net.minecraft.network.PacketByteBuf
import net.minecraft.resources.Identifier
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.mixin.ornithe.Mixin_ItemInHandRenderer_Puppet
import org.polyfrost.keyframe.standalone.mixin.ornithe.Mixin_PlayerEntity_Puppet
import org.polyfrost.keyframe.standalone.mixin.ornithe.Mixin_StatusEffectInstance_Puppet
import org.polyfrost.keyframe.standalone.recording.ReplayWriter
import java.io.IOException

object Puppet : PuppetCore() {
	private val sampledSlots = HashMap<Int, ItemStack?>()

	@JvmStatic
	val paused: Boolean
		get() = Keyframe.held || snapshot?.has(PAUSED) == true && Keyframe.loaded

	override fun playPortalTrigger() {
		val mc = Minecraft.getInstance()
		val player = mc.player ?: return
		mc.soundManager.play(SimpleSoundInstance.of(Identifier("portal.trigger"), player.random.nextFloat() * 0.4f + 0.8f))
	}

	fun ageWhileRolling() {
		val player = Minecraft.getInstance().player?.takeIf { it.health > 0f } ?: return
		for (effect in player.statusEffects) {
			if (effect.duration > 0) (effect as Mixin_StatusEffectInstance_Puppet).`keyframe$setDuration`(effect.duration - 1)
		}
	}

	@JvmStatic
	fun beforeTick(player: LocalClientPlayerEntity) {
		if (!Keyframe.active || Keyframe.rolling) return
		val snapshot = snapshot ?: return
		player.input = Input().apply { sneaking = snapshot.has(SHIFT) }
		applySlots(player)
		player.inventory.selectedSlot = Math.clamp(snapshot.selectedSlot.toLong(), 0, 8)
		applyBody(player, snapshot)
	}

	@JvmStatic
	fun afterTick(player: LocalClientPlayerEntity) {
		if (!Keyframe.active || Keyframe.rolling) return
		val snapshot = snapshot ?: return
		applyBody(player, snapshot)
		(player as Mixin_PlayerEntity_Puppet).`keyframe$setItemUseTimer`(snapshot.useItemRemaining)
		player.armSwinging = snapshot.has(SWINGING)
		player.armSwingingTicks = snapshot.swingTicks
		player.lastAttackAnimationProgress = snapshot.oldSwingAnimation
		player.attackAnimationProgress = snapshot.swingAnimation
		applyHands(player, snapshot)
		snapshot.effects?.let {
			player.portalTime = it.portalIntensity
			player.lastPortalTime = it.oPortalIntensity
		}
		player.easedPitch = snapshot.xBob
		player.easedYaw = snapshot.yBob
		player.lastEasedPitch = snapshot.xBobO
		player.lastEasedYaw = snapshot.yBobO
		player.bob = snapshot.bob
		player.walkDistance = snapshot.walkDist
		player.tilt = snapshot.offHandHeight
	}

	override fun applySample(player: LocalPlayer, sample: CameraSample) {
		player.yaw = sample.yaw
		player.lastYaw = sample.yaw
		player.pitch = sample.pitch
		player.lastPitch = sample.pitch
	}

	fun sampleCamera(deltaTracker: DeltaTracker): ByteArray? {
		val player = Minecraft.getInstance().player ?: return null
		val partial = deltaTracker.getGameTimeDeltaPartialTick(true)
		return encodeCamera(
			partial,
			player.lastYaw + (player.yaw - player.lastYaw) * partial,
			player.lastPitch + (player.pitch - player.lastPitch) * partial,
		)
	}

	fun sampleSnapshot(writer: ReplayWriter): ByteArray? {
		val mc = Minecraft.getInstance()
		val player = mc.player ?: return null
		if (writer !== sampledWriter) {
			sampledWriter = writer
			sampledSlots.clear()
		}
		val hands = hands()
		val flags = flag(player.onGround, ON_GROUND) or
			flag(player.isSneaking, SHIFT) or
			flag(player.isSprinting, SPRINTING) or
			flag(player.abilities.flying, FLYING) or
			flag(player.hasItemInUse(), USING_ITEM) or
			flag(mc.isPaused, PAUSED) or
			flag(player.armSwinging, SWINGING) or
			flag(hands.`keyframe$getItemInHand`() === player.inventory.selectedItem, SHOWS_MAIN_HAND_ITEM)
		return encodeSnapshot(
			Snapshot(
				player.x,
				player.y,
				player.z,
				player.yaw,
				player.pitch,
				player.velocityX,
				player.velocityY,
				player.velocityZ,
				0,
				flags,
				player.inventory.selectedSlot,
				player.itemUseTimer,
				0,
				0,
				player.easedPitch,
				player.easedYaw,
				player.lastEasedPitch,
				player.lastEasedYaw,
				player.bob,
				player.walkDistance,
				0,
				0,
				player.armSwingingTicks,
				player.lastAttackAnimationProgress,
				player.attackAnimationProgress,
				hands.`keyframe$getHandHeight`(),
				hands.`keyframe$getLastHandHeight`(),
				player.tilt,
				player.lastTilt,
				sampleSlots(player),
				ScreenEffects(mc.gui.ticks, player.portalTime, player.lastPortalTime, 0f, 0f),
			),
		)
	}

	private fun flag(set: Boolean, flag: Int) = if (set) flag else 0

	private fun sampleSlots(player: LocalClientPlayerEntity): List<SlotChange> {
		val inventory = player.inventory
		val changes = ArrayList<SlotChange>()
		for (slot in 0 until inventory.size) sampleSlot(slot, inventory.getItem(slot), changes)
		sampleSlot(CARRIED_SLOT, inventory.cursorItem, changes)
		return changes
	}

	private fun sampleSlot(slot: Int, stack: ItemStack?, changes: MutableList<SlotChange>) {
		if (ItemStack.matches(stack, sampledSlots[slot])) return
		sampledSlots[slot] = stack?.copy()
		try {
			changes += SlotChange(slot, encodeStack(stack))
		} catch (e: RuntimeException) {
			LOGGER.warn("Could not record inventory slot {}", slot, e)
		}
	}

	private fun applySlots(player: LocalClientPlayerEntity) {
		val inventory = player.inventory
		for ((slot, stack) in pendingSlots) {
			try {
				when (slot) {
					CARRIED_SLOT -> inventory.cursorItem = decodeStack(stack)
					in 0 until inventory.size -> inventory.setItem(slot, decodeStack(stack))
				}
			} catch (e: IOException) {
				LOGGER.warn("Could not restore inventory slot {}", slot, e)
			}
		}
		pendingSlots.clear()
	}

	private fun applyBody(player: LocalClientPlayerEntity, snapshot: Snapshot) {
		player.setPosition(snapshot.x, snapshot.y, snapshot.z)
		player.yaw = snapshot.yRot
		player.pitch = snapshot.xRot
		player.velocityX = snapshot.deltaX
		player.velocityY = snapshot.deltaY
		player.velocityZ = snapshot.deltaZ
		player.onGround = snapshot.has(ON_GROUND)
		player.abilities.flying = snapshot.has(FLYING)
		if (player.isSprinting != snapshot.has(SPRINTING)) player.isSprinting = snapshot.has(SPRINTING)
		(player as Mixin_PlayerEntity_Puppet).`keyframe$setItemInUse`(if (snapshot.has(USING_ITEM)) player.inventory.selectedItem else null)
	}

	private fun hands() = Minecraft.getInstance().itemInHandRenderer as Mixin_ItemInHandRenderer_Puppet

	private fun applyHands(player: LocalClientPlayerEntity, snapshot: Snapshot) {
		hands().run {
			`keyframe$setHandHeight`(snapshot.mainHandHeight)
			`keyframe$setLastHandHeight`(snapshot.oMainHandHeight)
			if (snapshot.has(SHOWS_MAIN_HAND_ITEM)) `keyframe$setItemInHand`(player.inventory.selectedItem)
		}
	}

	private fun encodeStack(stack: ItemStack?): ByteArray {
		val buffer = PacketByteBuf(Unpooled.buffer())
		buffer.writeItem(stack)
		return ByteArray(buffer.readableBytes()).also(buffer::readBytes)
	}

	private fun decodeStack(stack: ByteArray): ItemStack? = PacketByteBuf(Unpooled.wrappedBuffer(stack)).readItem()
}
