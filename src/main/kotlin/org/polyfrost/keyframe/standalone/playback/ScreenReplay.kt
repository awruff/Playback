package org.polyfrost.keyframe.standalone.playback

//? if >=1.21.1 {
//? if >=1.21.10
import com.mojang.blaze3d.platform.cursor.CursorType
import net.minecraft.client.gui.GuiGraphicsExtractor
//? if >=1.21.4 {
import net.minecraft.client.gui.components.AbstractScrollArea
//?} else
/*import net.minecraft.client.gui.components.AbstractSelectionList*/
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.components.events.GuiEventListener
import net.minecraft.client.gui.screens.ChatScreen
import net.minecraft.client.gui.screens.LevelLoadingScreen
import net.minecraft.client.gui.screens.PauseScreen
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.client.player.LocalPlayer
//? if >=1.21.8
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.scores.DisplaySlot
import org.polyfrost.keyframe.standalone.mixin.Mixin_CreativeModeInventoryScreen_ScreenState
import org.polyfrost.keyframe.standalone.mixin.Mixin_EditBox_ScreenState
//?}
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import org.polyfrost.keyframe.standalone.Standalone
import org.polyfrost.keyframe.standalone.compat.*
import org.polyfrost.keyframe.standalone.export.Export
import org.polyfrost.keyframe.standalone.export.PreviewFrame
import org.polyfrost.keyframe.standalone.recording.ReplayFile
import org.polyfrost.keyframe.standalone.recording.ReplayReader
import org.polyfrost.keyframe.standalone.recording.ReplayRecord
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.lang.reflect.Modifier
import java.nio.ByteBuffer

private const val CURSOR_PAYLOAD_LEN = 16
private const val DEBUG_OVERLAY = 1
private const val TAB_LIST = 2
private const val HIDE_GUI = 4

class CursorSample(override val time: Double, val x: Float, val y: Float, val guiScale: Int) : Timed {
	val mouseX: Int get() = Math.round(x)
	val mouseY: Int get() = Math.round(y)
}

internal fun encodeCursor(partial: Float, x: Float, y: Float, guiScale: Int): ByteArray =
	ByteBuffer.allocate(CURSOR_PAYLOAD_LEN).putFloat(partial).putFloat(x).putFloat(y).putInt(guiScale).array()

internal fun decodeCursor(record: ReplayRecord): CursorSample? {
	if (record.type != ReplayFile.INPUT || record.payload.size != CURSOR_PAYLOAD_LEN) return null
	val buffer = ByteBuffer.wrap(record.payload)
	return CursorSample(record.tick + buffer.getFloat().toDouble(), buffer.getFloat(), buffer.getFloat(), buffer.getInt())
}

internal fun interpolateCursor(before: CursorSample, after: CursorSample, time: Double): CursorSample {
	val progress = progress(before, after, time)
	return CursorSample(time, before.x + (after.x - before.x) * progress, before.y + (after.y - before.y) * progress, after.guiScale)
}

data class ScreenState(
	val debugOverlay: Boolean,
	val tabList: Boolean,
	val hideGui: Boolean,
	val screen: String,
	val creativeTab: String,
	val creativeScroll: Float,
	val editBoxes: List<String>,
	val scrollAreas: List<Double>,
)

internal fun encodeScreenState(state: ScreenState): ByteArray {
	val bytes = ByteArrayOutputStream()
	DataOutputStream(bytes).run {
		writeByte(
			(if (state.debugOverlay) DEBUG_OVERLAY else 0) or
				(if (state.tabList) TAB_LIST else 0) or
				(if (state.hideGui) HIDE_GUI else 0),
		)
		writeText(state.screen)
		writeText(state.creativeTab)
		writeFloat(state.creativeScroll)
		writeInt(state.editBoxes.size)
		state.editBoxes.forEach { writeText(it) }
		writeInt(state.scrollAreas.size)
		state.scrollAreas.forEach { writeDouble(it) }
	}
	return bytes.toByteArray()
}

internal fun decodeScreenState(payload: ByteArray): ScreenState? = try {
	DataInputStream(ByteArrayInputStream(payload)).run {
		val flags = readUnsignedByte()
		ScreenState(
			flags and DEBUG_OVERLAY != 0,
			flags and TAB_LIST != 0,
			flags and HIDE_GUI != 0,
			readText(),
			readText(),
			readFloat(),
			readList { readText() },
			readList { readDouble() },
		)
	}
} catch (_: IOException) {
	null
}

private fun DataOutputStream.writeText(text: String) {
	val bytes = text.toByteArray(Charsets.UTF_8)
	writeInt(bytes.size)
	write(bytes)
}

private fun DataInputStream.readText(): String {
	val length = readInt()
	if (length !in 0..available()) throw EOFException()
	return String(readNBytes(length), Charsets.UTF_8)
}

private fun <T> DataInputStream.readList(item: DataInputStream.() -> T): List<T> {
	val count = readInt()
	if (count !in 0..available()) throw EOFException()
	return List(count) { item() }
}

internal class ChangedPayload {
	private var last: ByteArray? = null

	fun changed(payload: ByteArray): Boolean {
		if (payload.contentEquals(last)) return false
		last = payload
		return true
	}

	fun reset() {
		last = null
	}
}

internal fun <N> flattened(nodes: List<N>, children: (N) -> List<N>): List<N> =
	nodes.flatMap { listOf(it) + flattened(children(it), children) }

internal fun redactCommand(text: String): String = if (text.startsWith("/")) "/" else text

internal fun construct(type: Class<*>, context: List<Any>): Any? {
	if (Modifier.isAbstract(type.modifiers)) return null
	for (constructor in type.constructors.sortedBy { it.parameterCount }) {
		val args = constructor.parameterTypes.map { param -> context.firstOrNull(param.kotlin.javaObjectType::isInstance) }
		if (null !in args) return constructor.newInstance(*args.toTypedArray())
	}
	return null
}

abstract class ScreenReplayCore {
	protected val LOGGER = LoggerFactory.getLogger(Standalone.MOD_ID)

	private var reader: ReplayReader? = null
	private var track: SampleTrack<CursorSample>? = null
	private var pending: String? = null
	protected var previewed: Screen? = null
	private var state: ScreenState? = null
	private var tabList = false
	private var ownDebugOverlay = false
	private var ownHideGui = false

	protected abstract fun applyToScreen(screen: Screen, state: ScreenState)

	internal abstract fun create(name: String, player: LocalPlayer?, current: Screen?): Screen?

	protected abstract fun pauseScreen(): Screen

	protected abstract fun adopted(screen: Screen)

	fun begin(reader: ReplayReader) {
		val mc = Minecraft.getInstance()
		ownDebugOverlay = mc.debugEntries.isOverlayVisible
		ownHideGui = mc.gui.hud.isHidden
		this.reader = reader
		track = SampleTrack(::interpolateCursor) { generateSequence(reader::next).firstNotNullOfOrNull(::decodeCursor) }
	}

	fun end() {
		adopt(null)
		reader?.close()
		reader = null
		track = null
		pending = null
		state = null
		tabList = false
		showOverlays(ownDebugOverlay, ownHideGui)
	}

	fun onTransition(record: ReplayRecord) {
		pending = String(record.payload, Charsets.UTF_8)
		flush()
	}

	fun onState(record: ReplayRecord) {
		state = decodeScreenState(record.payload) ?: return
		applyState()
	}

	fun flush() {
		switchScreen()
		applyState()
	}

	private fun switchScreen() {
		val name = pending ?: return
		val mc = Minecraft.getInstance()
		val player = mc.player
		if ((player == null && !Keyframe.inMenu) || mc.gui.screen() is LevelLoadingScreen || Keyframe.rolling) return
		val current = if (Keyframe.previewing) previewed else mc.gui.screen()
		pending = null
		if (current?.javaClass?.name == name.ifEmpty { null }) return
		if (current != null) player?.clientSideCloseContainer()
		val screen = create(name, player, current)
		try {
			if (Keyframe.previewing) adopt(screen) else if (screen != null || (player == null && name.isEmpty())) mc.gui.setScreen(screen)
		} catch (e: RuntimeException) {
			drop(e)
		}
	}

	private fun applyState() {
		val state = state ?: return
		if (Keyframe.rolling) return
		tabList = state.tabList
		showOverlays(state.debugOverlay, state.hideGui)
		val screen = (if (Keyframe.previewing) previewed else Minecraft.getInstance().gui.screen()) ?: return
		if (screen.width == 0 || screen.javaClass.name != state.screen) return
		try {
			applyToScreen(screen, state)
		} catch (e: RuntimeException) {
			LOGGER.warn("Could not apply replayed state to {}", state.screen, e)
			this.state = state.copy(screen = "")
		}
	}

	private fun showOverlays(debugOverlay: Boolean, hideGui: Boolean) {
		val mc = Minecraft.getInstance()
		mc.debugEntries.isOverlayVisible = debugOverlay
		if (mc.gui.hud.isHidden != hideGui) mc.gui.hud.toggle()
	}

	fun tabListDown(down: Boolean): Boolean = if (Keyframe.active) tabList else down

	fun adopt(screen: Screen?) {
		if (screen === previewed) return
		previewed?.removed()
		previewed = screen
		if (screen != null) adopted(screen)
	}

	fun tickPreview() {
		try {
			previewed?.takeIf { it.width > 0 }?.tick()
		} catch (e: RuntimeException) {
			drop(e)
		}
	}

	fun drop(cause: RuntimeException) {
		LOGGER.warn("Dropping a replayed screen that failed", cause)
		pending = ""
	}

	internal fun reflect(name: String, player: LocalPlayer?, current: Screen?): Screen? {
		val mc = Minecraft.getInstance()
		return try {
			val type = Class.forName(name, false, javaClass.classLoader)
			if (!Screen::class.java.isAssignableFrom(type)) return null
			construct(type, listOfNotNull(current ?: pauseScreen(), mc.options, player, mc.level, mc, false)) as Screen?
		} catch (e: ReflectiveOperationException) {
			LOGGER.warn("Could not rebuild screen {}", name, e)
			null
		} catch (e: LinkageError) {
			LOGGER.warn("Could not rebuild screen {}", name, e)
			null
		}
	}

	protected fun sample(deltaTracker: DeltaTracker, width: Int, height: Int): CursorSample? {
		val sample = track?.at(Keyframe.tick + deltaTracker.getGameTimeDeltaPartialTick(true).toDouble()) ?: return null
		return CursorSample(sample.time, width / 2 + sample.x, height / 2 + sample.y, sample.guiScale)
	}
}

//? if >=1.21.1 {
object ScreenReplay : ScreenReplayCore() {
	var extractingPreview = false
		private set

	override fun applyToScreen(screen: Screen, state: ScreenState) {
		val creative = screen as? CreativeModeInventoryScreen
		if (creative != null) {
			Identifier.tryParse(state.creativeTab)?.let(BuiltInRegistries.CREATIVE_MODE_TAB::getValue)?.let(creative::setSelectedTab)
		}
		val widgets = widgets(screen)
		var edited = false
		widgets.filterIsInstance<EditBox>().zip(state.editBoxes).forEach { (box, text) ->
			if (box.value != text) {
				setText(box, text)
				edited = true
			}
		}
		//? if >=1.21.4 {
		widgets.filterIsInstance<AbstractScrollArea>().zip(state.scrollAreas).forEach { (area, amount) ->
			if (area.scrollAmount() != amount) area.setScrollAmount(amount)
		}
		//?} else {
		/*widgets.filterIsInstance<AbstractSelectionList<*>>().zip(state.scrollAreas).forEach { (area, amount) ->
			if (area.scrollAmount != amount) area.setScrollAmount(amount)
		}
		*///?}
		if (creative != null) {
			val fields = creative as Mixin_CreativeModeInventoryScreen_ScreenState
			if (edited) fields.`keyframe$refreshSearchResults`()
			if (fields.`keyframe$getScrollOffs`() != state.creativeScroll) {
				fields.`keyframe$setScrollOffs`(state.creativeScroll)
				creative.menu.scrollTo(state.creativeScroll)
			}
		}
	}

	private fun setText(box: EditBox, text: String) {
		val responder = (box as Mixin_EditBox_ScreenState).`keyframe$getResponder`()
		box.setResponder {}
		box.setValue(text)
		box.setResponder(responder)
	}

	private fun widgets(screen: Screen): List<GuiEventListener> =
		flattened(screen.children()) { (it as? ContainerEventHandler)?.children().orEmpty() }

	fun sampleState(): ByteArray {
		val mc = Minecraft.getInstance()
		val screen = mc.gui.screen()
		val creative = screen as? CreativeModeInventoryScreen
		val widgets = screen?.let(::widgets).orEmpty()
		return encodeScreenState(
			ScreenState(
				mc.debugEntries.isOverlayVisible,
				tabListShown(mc),
				mc.gui.hud.isHidden,
				screen?.javaClass?.name.orEmpty(),
				creative?.let { BuiltInRegistries.CREATIVE_MODE_TAB.getKey(it.getSelectedTab())?.toString() }.orEmpty(),
				(creative as Mixin_CreativeModeInventoryScreen_ScreenState?)?.`keyframe$getScrollOffs`() ?: 0f,
				widgets.filterIsInstance<EditBox>().map { redactCommand(it.value) },
				//? if >=1.21.4 {
				widgets.filterIsInstance<AbstractScrollArea>().map { it.scrollAmount() },
				//?} else
				/*widgets.filterIsInstance<AbstractSelectionList<*>>().map { it.scrollAmount },*/
			),
		)
	}

	private fun tabListShown(mc: Minecraft): Boolean {
		val level = mc.level ?: return false
		val player = mc.player ?: return false
		return mc.options.keyPlayerList.isDown &&
			(!mc.isLocalServer || player.connection.listedOnlinePlayers.size > 1 || level.scoreboard.getDisplayObjective(DisplaySlot.LIST) != null)
	}

	override fun adopted(screen: Screen) {
		screen.added()
	}

	fun previewsChat(): Boolean = previewed is ChatScreen

	fun extractPreview(graphics: GuiGraphicsExtractor, deltaTracker: DeltaTracker) {
		val screen = previewed ?: return
		PreviewFrame.fitGui(graphics) {
			val cursor = sample(deltaTracker, graphics)
			extractingPreview = true
			try {
				if (screen.width != graphics.guiWidth() || screen.height != graphics.guiHeight()) screen.init(graphics.guiWidth(), graphics.guiHeight())
				screen.extractRenderStateWithTooltipAndSubtitles(graphics, cursor?.mouseX ?: -1, cursor?.mouseY ?: -1, deltaTracker.getGameTimeDeltaTicks())
				if (cursor != null) drawCursor(graphics, cursor)
			} catch (e: RuntimeException) {
				drop(e)
			} finally {
				extractingPreview = false
			}
			//? if >=1.21.10
			graphics.requestCursor(CursorType.DEFAULT)
		}
	}

	fun extractPreviewDebugOverlay(graphics: GuiGraphicsExtractor) {
		//? if >=1.21.10 {
		if (!Keyframe.previewing || !Export.renderHud()) return
		PreviewFrame.fitGui(graphics) { Minecraft.getInstance().gui.hud.extractDebugOverlay(graphics) }
		//?}
	}

	override fun pauseScreen() = PauseScreen(true)

	override fun create(name: String, player: LocalPlayer?, current: Screen?): Screen? = when (name) {
		InventoryScreen::class.java.name -> player?.let { InventoryScreen(it) }
		CreativeModeInventoryScreen::class.java.name -> player?.let {
			CreativeModeInventoryScreen(
				it,
				it.connection.enabledFeatures(),
				Minecraft.getInstance().options.operatorItemsTab().get(),
			)
		}
		PauseScreen::class.java.name -> PauseScreen(true)
		//? if >=1.21.10 {
		ChatScreen::class.java.name -> ChatScreen("", false)
		//?} else
		/*ChatScreen::class.java.name -> ChatScreen("")*/
		"" -> null
		else -> reflect(name, player, current)
	}

	fun sampleCursor(deltaTracker: DeltaTracker): ByteArray {
		val mc = Minecraft.getInstance()
		val window = mc.window
		return encodeCursor(
			deltaTracker.getGameTimeDeltaPartialTick(true),
			(mc.mouseHandler.getScaledXPos(window) - window.guiScaledWidth / 2).toFloat(),
			(mc.mouseHandler.getScaledYPos(window) - window.guiScaledHeight / 2).toFloat(),
			//? if >=1.21.10 {
			window.guiScale,
			//?} else
			/*window.guiScale.toInt(),*/
		)
	}

	fun cursor(deltaTracker: DeltaTracker, graphics: GuiGraphicsExtractor): CursorSample? =
		if (Keyframe.previewing) null else sample(deltaTracker, graphics)

	private fun sample(deltaTracker: DeltaTracker, graphics: GuiGraphicsExtractor): CursorSample? =
		sample(deltaTracker, graphics.guiWidth(), graphics.guiHeight())

	fun drawCursor(graphics: GuiGraphicsExtractor, cursor: CursorSample) {
		val image = SystemCursor.get() ?: return
		graphics.pose().pushMatrix().translate(cursor.x, cursor.y).scale(image.scale / cursor.guiScale)
		//? if >=1.21.8 {
		graphics.blit(RenderPipelines.GUI_TEXTURED, image.texture, -image.hotX, -image.hotY, 0f, 0f, image.width, image.height, image.width, image.height)
		//?} elif >=1.21.4 {
		/*graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, image.texture, -image.hotX, -image.hotY, 0f, 0f, image.width, image.height, image.width, image.height)
		*///?} else
		/*graphics.blit(image.texture, -image.hotX, -image.hotY, 0f, 0f, image.width, image.height, image.width, image.height)*/
		graphics.pose().popMatrix()
	}
}
//?}
