package org.polyfrost.keyframe.standalone.mixin;

import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Consumer;

@Mixin(EditBox.class)
public interface Mixin_EditBox_ScreenState {
	@Accessor("responder")
	Consumer<String> keyframe$getResponder();
}
