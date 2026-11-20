package org.polyfrost.keyframe.standalone.mixin;

import java.util.List;
//? if >=26.1 {
import net.minecraft.client.multiplayer.chat.GuiMessage;
//?} else
/*import net.minecraft.client.GuiMessage;*/
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatComponent.class)
public interface Mixin_ChatComponent_Chat {
	@Accessor("allMessages")
	List<GuiMessage> keyframe$getAllMessages();
}
