package org.polyfrost.keyframe.standalone.playback

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiElement
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screen.game.ChatScreen
import net.minecraft.client.gui.screen.game.inventory.CreativeInventoryScreen
import net.minecraft.client.gui.screen.game.inventory.SurvivalInventoryScreen
import net.minecraft.client.gui.screen.menu.GameMenuScreen
import net.minecraft.client.gui.widget.ListWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.client.render.Window
import net.minecraft.client.render.platform.GlStateManager
import net.minecraft.item.CreativeModeTab
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.export.PreviewFrame
import org.polyfrost.keyframe.standalone.mixin.ornithe.Mixin_CreativeInventoryScreen_ScreenState
import org.polyfrost.keyframe.standalone.mixin.ornithe.Mixin_CreativePlayerMenu_ScreenState
import org.polyfrost.keyframe.standalone.mixin.ornithe.Mixin_ListWidget_ScreenState
import org.lwjgl.input.Mouse

object ScreenReplay : ScreenReplayCore() {
	override fun applyToScreen(screen: Screen, state: ScreenState) {
		val creative = screen as? CreativeInventoryScreen
		val fields = creative as Mixin_CreativeInventoryScreen_ScreenState?
		state.creativeTab.toIntOrNull()?.let { CreativeModeTab.ALL.getOrNull(it) }?.let { fields?.`keyframe$setSelectedTab`(it) }
		widgets<TextFieldWidget>(screen).zip(state.editBoxes).forEach { (box, text) ->
			if (box.text != text) box.text = text
		}
		widgets<ListWidget>(screen).zip(state.scrollAreas).forEach { (list, amount) ->
			(list as Mixin_ListWidget_ScreenState).`keyframe$setScrollAmount`(amount.toFloat())
		}
		if (fields != null && fields.`keyframe$getScrollPosition`() != state.creativeScroll) {
			fields.`keyframe$setScrollPosition`(state.creativeScroll)
			(creative!!.menu as Mixin_CreativePlayerMenu_ScreenState).`keyframe$scrollItems`(state.creativeScroll)
		}
	}

	private inline fun <reified T> widgets(screen: Screen): List<T> =
		generateSequence<Class<*>>(screen.javaClass) { it.superclass.takeIf { parent -> parent != Screen::class.java } }
			.flatMap { it.declaredFields.asSequence() }
			.filter { T::class.java.isAssignableFrom(it.type) }
			.mapNotNull { runCatching { it.isAccessible = true; it.get(screen) as T? }.getOrNull() }
			.toList()

	fun sampleState(): ByteArray {
		val mc = Minecraft.getInstance()
		val screen = mc.screen
		val creative = screen as? CreativeInventoryScreen
		return encodeScreenState(
			ScreenState(
				mc.options.debugEnabled,
				tabListShown(mc),
				mc.options.hideGui,
				screen?.javaClass?.name.orEmpty(),
				if (creative != null) Mixin_CreativeInventoryScreen_ScreenState.`keyframe$getSelectedTab`().toString() else "",
				(creative as Mixin_CreativeInventoryScreen_ScreenState?)?.`keyframe$getScrollPosition`() ?: 0f,
				screen?.let { widgets<TextFieldWidget>(it) }.orEmpty().map { redactCommand(it.text) },
				screen?.let { widgets<ListWidget>(it) }.orEmpty().map { (it as Mixin_ListWidget_ScreenState).`keyframe$getScrollAmount`().toDouble() },
			),
		)
	}

	private fun tabListShown(mc: Minecraft): Boolean {
		val world = mc.world ?: return false
		val player = mc.player ?: return false
		return mc.options.playerListKey.isPressed &&
			(!mc.isIntegratedServerRunning || player.networkHandler.onlinePlayers.size > 1 || world.scoreboard.getDisplayObjective(0) != null)
	}

	override fun adopted(screen: Screen) {
		try {
			val mc = Minecraft.getInstance()
			val window = Window(mc)
			screen.init(mc, window.width, window.height)
		} catch (e: RuntimeException) {
			previewed = null
			drop(e)
		}
	}

	@JvmStatic
	fun drawPreview(deltaTracker: DeltaTracker) {
		val screen = previewed ?: return
		PreviewFrame.fitGui {
			val mc = Minecraft.getInstance()
			val window = Window(mc)
			val cursor = sample(deltaTracker, window)
			try {
				if (screen.width != window.width || screen.height != window.height) screen.init(mc, window.width, window.height)
				screen.render(cursor?.mouseX ?: -1, cursor?.mouseY ?: -1, deltaTracker.getGameTimeDeltaTicks())
				if (cursor != null) drawCursor(cursor)
			} catch (e: RuntimeException) {
				drop(e)
			}
		}
	}

	override fun pauseScreen() = GameMenuScreen()

	override fun create(name: String, player: LocalPlayer?, current: Screen?): Screen? = when (name) {
		SurvivalInventoryScreen::class.java.name -> player?.let { SurvivalInventoryScreen(it) }
		CreativeInventoryScreen::class.java.name -> player?.let { CreativeInventoryScreen(it) }
		GameMenuScreen::class.java.name -> GameMenuScreen()
		ChatScreen::class.java.name -> ChatScreen()
		"" -> null
		else -> reflect(name, player, current)
	}

	fun sampleCursor(deltaTracker: DeltaTracker): ByteArray {
		val mc = Minecraft.getInstance()
		val window = Window(mc)
		val x = Mouse.getX().toDouble() * window.scaledWidth / mc.width
		val y = window.scaledHeight - Mouse.getY().toDouble() * window.scaledHeight / mc.height - 1
		return encodeCursor(
			deltaTracker.getGameTimeDeltaPartialTick(true),
			(x - window.width / 2).toFloat(),
			(y - window.height / 2).toFloat(),
			window.scale,
		)
	}

	@JvmStatic
	fun cursor(deltaTracker: DeltaTracker): CursorSample? =
		if (Keyframe.previewing) null else sample(deltaTracker, Window(Minecraft.getInstance()))

	private fun sample(deltaTracker: DeltaTracker, window: Window): CursorSample? = sample(deltaTracker, window.width, window.height)

	@JvmStatic
	fun drawCursor(cursor: CursorSample) {
		val image = SystemCursor.get() ?: return
		val scale = image.scale / cursor.guiScale
		GlStateManager.pushMatrix()
		GlStateManager.translatef(cursor.x, cursor.y, 0f)
		GlStateManager.scalef(scale, scale, 1f)
		GlStateManager.enableBlend()
		GlStateManager.color4f(1f, 1f, 1f, 1f)
		Minecraft.getInstance().textureManager.bind(image.texture)
		GuiElement.drawTexture(-image.hotX, -image.hotY, 0f, 0f, image.width, image.height, image.width.toFloat(), image.height.toFloat())
		GlStateManager.popMatrix()
	}
}
