package org.polyfrost.keyframe.standalone.mixin.ornithe;

import java.util.List;
import net.minecraft.client.gui.ChatMessage;
import net.minecraft.client.gui.chat.ChatGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatGui.class)
public interface Mixin_ChatGui_Chat {
	@Accessor("messages")
	List<ChatMessage> keyframe$getMessages();
}
