package org.polyfrost.keyframe.standalone

import net.fabricmc.api.ClientModInitializer
import org.polyfrost.keyframe.standalone.editor.Editor
import org.polyfrost.keyframe.standalone.editor.EditorExport
import org.polyfrost.keyframe.standalone.recording.Highlights
import org.polyfrost.keyframe.standalone.recording.Recorder
import org.polyfrost.keyframe.standalone.recording.RecordingHud
//? if >=1.21.1 {
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.ClientCommands
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.Screens
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component
//?} else {
/*import com.mojang.brigadier.builder.LiteralArgumentBuilder
import org.polyfrost.oneconfig.internal.legacy.command.ClientCommandRegistrationCallback
import org.polyfrost.oneconfig.internal.legacy.command.FabricClientCommandSource
*///?}

object Standalone : ClientModInitializer {
	const val MOD_ID: String = "keyframe"

	override fun onInitializeClient() {
		StandaloneConfig.preload()
		RecordingHud.register(StandaloneConfig.id) { Recorder.recording }
		Channels.install()
		//? if >=1.21.1 {
		Highlights.register()
		ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(ClientCommands.literal("keyframe-record").executes {
				Recorder.toggle()
				1
			})
		}
		ClientLifecycleEvents.CLIENT_STOPPING.register { Recorder.finish() }
		ClientTickEvents.END_CLIENT_TICK.register {
			Editor.tick()
			EditorExport.tick()
		}
		ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
			if (screen is TitleScreen) {
				Screens.getWidgets(screen).add(Button.builder(Component.literal("Edit")) { Editor.open() }.bounds(4, 4, 60, 20).build())
			}
		}
		//?} else {
		/*ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
			dispatcher.register(LiteralArgumentBuilder.literal<FabricClientCommandSource>("keyframe-record").executes {
				Recorder.toggle()
				1
			})
		}
		*///?}
	}
}
