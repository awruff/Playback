package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;
import net.minecraft.client.gui.screens.Screen;
import org.polyfrost.keyframe.standalone.editor.Editor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

@Pseudo
@Mixin(targets = "org.polyfrost.polyplus.client.gui.ModIntegrationButtons", remap = false)
public abstract class Mixin_ModIntegrationButtons_Editor {
	@Unique
	private static Object keyframe$button;

	@ModifyReturnValue(method = "available", at = @At("RETURN"), require = 0)
	private List<Object> keyframe$addEditorButton(List<Object> buttons) {
		try {
			if (keyframe$button == null) {
				keyframe$button = Class.forName("org.polyfrost.polyplus.client.gui.ModIntegrationButton")
					.getConstructor(String.class, String.class, Function0.class, Function1.class, Function0.class)
					.newInstance(
						"assets/keyframe/ico/play.svg",
						"Keyframe",
						(Function0<Boolean>) () -> true,
						(Function1<Screen, Unit>) parent -> {
							Editor.INSTANCE.open();
							return Unit.INSTANCE;
						},
						(Function0<byte[]>) () -> null
					);
			}
		} catch (ReflectiveOperationException e) {
			return buttons;
		}
		List<Object> result = new ArrayList<>(buttons);
		result.add(keyframe$button);
		return result;
	}
}
