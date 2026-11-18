package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GameGui;
import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.client.gui.screen.game.ChatScreen;
import org.polyfrost.keyframe.ui.ContainerScreenAction;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.polyfrost.keyframe.ui.ScreenReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(GameGui.class)
public abstract class MixinGameGuiScreens {

    @WrapMethod(method = "render(F)V")
    private void keyframe$captureHud(float partial, Operation<Void> original) {
        if (!ScreenReplay.hidesHud()) {
            ScreenCapture.captureHud(() -> original.call(partial));
        }
        ScreenReplay.render(partial);
        ContainerScreenAction.render(partial);
    }

    @WrapOperation(method = "render(F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/chat/ChatGui;render(I)V"))
    private void keyframe$captureChat(ChatGui chat, int ticks, Operation<Void> original) {
        if (ScreenReplay.hidesChat()) {
            return;
        }
        boolean capture = ScreenCapture.recording() && Minecraft.getInstance().screen instanceof ChatScreen;
        boolean started = capture && !ScreenCapture.active();
        if (capture) {
            ScreenCapture.beginLayer(ScreenCapture.Frame.CHAT);
        }
        try {
            original.call(chat, ticks);
        } finally {
            if (started) {
                ScreenCapture.endLayer();
            }
        }
    }

}
