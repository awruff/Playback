package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.gui.components.ChatComponent;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.polyfrost.keyframe.standalone.playback.ScreenReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
abstract class Mixin_ChatComponent_Replay {
	//? if >=26.1 {
	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)
	//?} else
	/*@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V", at = @At("HEAD"), cancellable = true)*/
	private void keyframe$dropLiveChat(CallbackInfo ci) {
		if (Keyframe.INSTANCE.getBlocksChat()) ci.cancel();
	}

	@ModifyReturnValue(method = "isChatFocused", at = @At("RETURN"))
	private boolean keyframe$focusPreviewedChat(boolean focused) {
		return focused || ScreenReplay.INSTANCE.previewsChat();
	}
}
