plugins {
    id("dev.kikugie.stonecutter")
    id("org.jetbrains.kotlin.jvm") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}

stonecutter active "26.3"

stonecutter parameters {
    replacements {
        string(current.parsed >= "26.1") {
            replace("classTweaker v1 named", "classTweaker v1 official")
        }
        string(current.parsed < "1.21.11" && current.parsed >= "1.21.1") {
            replace("Identifier", "ResourceLocation")
            replace("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation")
            replace("net.minecraft.util.Util", "net.minecraft.Util")
        }
        string(current.parsed < "1.21.2") {
            replace("getDeltaTracker()", "getTimer()")
            replace("EntityEvent.PROTECTED_FROM_DEATH", "EntityEvent.TALISMAN_ACTIVATE")
            replace("player.oldPosition()", "new Vec3(player.xo, player.yo, player.zo)")
            replace("sound.location().toString()", "sound.location.toString()")
        }
        string(current.parsed < "26.3") {
            replace("com.mojang.renderpearl.api.pipeline", "com.mojang.blaze3d.pipeline")
            replace("com/mojang/renderpearl/api/pipeline", "com/mojang/blaze3d/pipeline")
            replace("com.mojang.renderpearl.api.buffers", "com.mojang.blaze3d.buffers")
            replace("com/mojang/renderpearl/api/commands/CommandEncoder", "com/mojang/blaze3d/systems/CommandEncoder")
            replace("com.mojang.renderpearl.api.commands.CommandEncoder", "com.mojang.blaze3d.systems.CommandEncoder")
            replace("com.mojang.renderpearl.api.commands.GpuFence", "com.mojang.blaze3d.buffers.GpuFence")
            replace("com/mojang/renderpearl/api/device/GpuSurface", "com/mojang/blaze3d/systems/GpuSurface")
            replace("com.mojang.renderpearl.api.device.GpuSurface", "com.mojang.blaze3d.systems.GpuSurface")
            replace("com/mojang/renderpearl/api/textures", "com/mojang/blaze3d/textures")
            replace("com.mojang.renderpearl.api.textures", "com.mojang.blaze3d.textures")
            replace("com.mojang.renderpearl.api.GpuFormat", "com.mojang.blaze3d.GpuFormat")
        }
        string(current.parsed < "1.21.10") {
            replace(".sendToTrackingPlayers(", ".broadcast(")
            replace("renderWithTooltipAndSubtitles", "renderWithTooltip")
            replace("Lnet/minecraft/network/PacketProcessor;", "Lnet/minecraft/util/thread/BlockableEventLoop;")
            replace("\"onButton\"", "\"onPress\"")
        }
        string(current.parsed < "1.21.4") {
            replace("CREATIVE_MODE_TAB::getValue", "CREATIVE_MODE_TAB::get")
            replace("handleLoginFinished", "handleGameProfile")
            replace("ClientboundLoginFinishedPacket", "ClientboundGameProfilePacket")
        }
        string(current.parsed < "1.21.8") {
            replace("RenderSystem.getDevice()", "GlDevice")
            replace("getCurrentVersion().protocolVersion()", "getCurrentVersion().protocolVersion")
            replace("CLIENTBOUND_TEMPLATE.details()", "CLIENTBOUND_TEMPLATE")
            replace("details: ProtocolInfo.Details", "details: ProtocolInfo.Unbound<*, *>")
            replace("Lnet/minecraft/world/entity/Entity;DDDL", "Lnet/minecraft/world/entity/player/Player;DDDL")
            replace("playSeededSound(Lnet/minecraft/world/entity/Entity;Lnet", "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet")
            replace("@Nullable Entity except", "@Nullable Player except")
            replace("ClientLevel level, Entity except", "ClientLevel level, Player except")
            replace("@Nullable Entity source", "@Nullable Player source")
        }
        string(current.parsed < "1.21.6") {
            replace("SoundSource.UI", "SoundSource.MASTER")
        }
        string(current.parsed < "1.21.10" && current.parsed >= "1.21.1") {
            replace("InputConstants.MOD_SUPER", "org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER")
            replace("InputConstants.MOD_SHIFT", "org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT")
        }
        string(current.parsed < "26.2") {
            replace("minecraft.gui.hud.getGuiTicks()", "minecraft.gui.getGuiTicks()")
            replace(" minecraft.gui.screen()", " minecraft.screen")
        }
        string(current.parsed < "26.1") {
            replace("MixinGuiGraphicsExtractor", "MixinGuiGraphicsExtractor")
            replace("GuiGraphicsExtractor", "GuiGraphics")
            replace("clientboundPlay()", "playS2C()")
            replace("extractRenderStateWithTooltipAndSubtitles", "renderWithTooltipAndSubtitles")
            replace("\"extractRenderState\"", "\"render\"")
            replace("\"extractItem\"", "\"renderItem\"")
            replace("extractCameraOverlays", "renderCameraOverlays")
            replace("extractEffects", "renderEffects")
            replace("extractItemHotbar", "renderItemHotbar")
            replace("extractSelectedItemName", "renderSelectedItemName")
            replace("extractPlayerHealth", "renderPlayerHealth")
            replace("extractAirBubbles", "renderAirBubbles")
            replace("extractFood", "renderFood")
            replace("extractChat", "renderChat")
            replace("\"innerFill\"", "\"submitColoredRectangle\"")
            replace("\"text(Lnet/minecraft/client/gui/Font;", "\"drawString(Lnet/minecraft/client/gui/Font;")
            replace("\"item(Lnet/minecraft/world/entity/LivingEntity;", "\"renderItem(Lnet/minecraft/world/entity/LivingEntity;")
            replace("\"extractBackground\"", "\"renderBg\"")
            replace("extractBackground", "renderBackground")
            replace("extractEntityInInventoryFollowsMouse", "renderEntityInInventoryFollowsMouse")
            replace("extractDebugOverlay", "renderDebugOverlay")
            replace("extractTabList", "renderTabList")
            replace("extractGui(Lnet/minecraft/client/DeltaTracker;ZZ)V", "render(Lnet/minecraft/client/DeltaTracker;Z)V")
            replace("Lnet/minecraft/client/gui/Gui;extractRenderState(", "Lnet/minecraft/client/gui/Gui;render(")
            replace("renderFrame(Z)V", "runTick(Z)V")
            replace("Lnet/minecraft/client/FramerateLimiter;limitDisplayFPS", "Lcom/mojang/blaze3d/systems/RenderSystem;limitDisplayFPS")
            replace("Lcom/mojang/blaze3d/systems/RenderSystem;flipFrame(", "Lcom/mojang/blaze3d/platform/Window;updateDisplay(")
            replace("net/minecraft/client/renderer/state/gui/GuiRenderState", "net/minecraft/client/gui/render/state/GuiRenderState")
            replace("net.minecraft.client.renderer.state.gui.GuiRenderState", "net.minecraft.client.gui.render.state.GuiRenderState")
            replace("ClientCommands", "ClientCommandManager")
            replace("Screens.getWidgets", "Screens.getButtons")
            replace("creative::setSelectedTab", "creative::setSelectedItemGroup")
            replace("it.getSelectedTab()", "it.getSelectedItemGroup()")
            replace("\"charTyped\", \"preeditCallback\"", "\"charTyped\"")
        }
        string(current.parsed < "1.21.1") {
            replace("com.mojang.blaze3d.platform.InputConstants", "org.polyfrost.oneconfig.internal.legacy.InputConstants")
            replace("com.mojang.blaze3d.platform.NativeImage", "org.polyfrost.keyframe.standalone.compat.NativeImage")
            replace("import com.mojang.blaze3d.systems.RenderSystem", "import org.polyfrost.keyframe.standalone.compat.GlDevice")
            replace("net.minecraft.SharedConstants", "org.polyfrost.keyframe.standalone.compat.SharedConstants")
            replace("net.minecraft.client.DeltaTracker", "org.polyfrost.keyframe.standalone.compat.DeltaTracker")
            replace("net.minecraft.client.gui.screens.Screen", "net.minecraft.client.gui.screen.Screen")
            replace("net.minecraft.client.gui.screens.TitleScreen", "net.minecraft.client.gui.screen.menu.TitleScreen")
            replace("net.minecraft.client.renderer.texture.DynamicTexture", "net.minecraft.client.render.texture.DynamicTexture")
            replace("net.minecraft.resources.Identifier", "net.minecraft.resource.Identifier")
            replace("net.minecraft.util.TimeSource", "org.polyfrost.keyframe.standalone.compat.TimeSource")
            replace("net.minecraft.util.Util", "org.polyfrost.keyframe.standalone.compat.Util")
            replace("mc.window", "mc.gameWindow")
            replace("getInstance().window", "getInstance().gameWindow")
        }
    }
}
