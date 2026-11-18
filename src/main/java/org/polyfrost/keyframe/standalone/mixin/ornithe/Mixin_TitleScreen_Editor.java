package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.polyfrost.keyframe.standalone.editor.Editor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
abstract class Mixin_TitleScreen_Editor extends Screen {
	@Unique
	private static final int EDIT_BUTTON = 0x4B46;

	@Inject(method = "init", at = @At("TAIL"))
	private void keyframe$addEditButton(CallbackInfo ci) {
		this.buttons.add(new ButtonWidget(EDIT_BUTTON, 4, 4, 60, 20, "Edit"));
	}

	@Inject(method = "buttonClicked", at = @At("HEAD"))
	private void keyframe$openEditor(ButtonWidget button, CallbackInfo ci) {
		if (button.id == EDIT_BUTTON) Editor.INSTANCE.open();
	}
}
