package org.polyfrost.keyframe.standalone.mixin;

import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Window.class)
public interface Mixin_Window_Editor {
	@Accessor("guiScaledWidth")
	void keyframe$setGuiScaledWidth(int width);

	@Accessor("guiScaledHeight")
	void keyframe$setGuiScaledHeight(int height);
}
