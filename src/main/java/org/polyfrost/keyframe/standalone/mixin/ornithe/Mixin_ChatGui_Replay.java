package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.gui.chat.ChatGui;
import net.minecraft.text.Text;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatGui.class)
abstract class Mixin_ChatGui_Replay {
	@Inject(method = "addMessage(Lnet/minecraft/text/Text;IIZ)V", at = @At("HEAD"), cancellable = true)
	private void keyframe$dropLiveChat(Text text, int id, int ticks, boolean refresh, CallbackInfo ci) {
		if (!refresh && Keyframe.INSTANCE.getBlocksChat()) ci.cancel();
	}
}
