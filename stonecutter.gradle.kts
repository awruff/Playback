plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

stonecutter parameters {
    replacements {
        string(current.parsed >= "26.1") {
            replace("classTweaker v1 named", "classTweaker v1 official")
        }
        string(current.parsed < "1.21.11") {
            replace("Identifier", "ResourceLocation")
        }
        string(current.parsed < "1.21.2") {
            replace("getDeltaTracker()", "getTimer()")
            replace("EntityEvent.PROTECTED_FROM_DEATH", "EntityEvent.TALISMAN_ACTIVATE")
            replace("player.oldPosition()", "new Vec3(player.xo, player.yo, player.zo)")
        }
        string(current.parsed < "26.3") {
            replace("com.mojang.renderpearl.api.pipeline", "com.mojang.blaze3d.pipeline")
            replace("com/mojang/renderpearl/api/pipeline", "com/mojang/blaze3d/pipeline")
        }
        string(current.parsed < "1.21.10") {
            replace(".sendToTrackingPlayers(", ".broadcast(")
        }
        string(current.parsed < "26.2") {
            replace("net.minecraft.client.gui.Hud;", "net.minecraft.client.gui.Gui;")
            replace("@Mixin(Hud.class)", "@Mixin(Gui.class)")
            replace("client/gui/Hud;", "client/gui/Gui;")
            replace("minecraft.gui.hud.getGuiTicks()", "minecraft.gui.getGuiTicks()")
            replace("minecraft.gui.screen()", "minecraft.screen")
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
            replace("extractEntityInInventoryFollowsMouse", "renderEntityInInventoryFollowsMouse")
        }
    }
}
