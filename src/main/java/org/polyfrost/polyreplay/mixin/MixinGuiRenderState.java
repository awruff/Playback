package org.polyfrost.polyreplay.mixin;

//? if >=26.3 {
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.polyfrost.polyreplay.ui.GuiMeshCapture;
import org.polyfrost.polyreplay.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class MixinGuiRenderState {

    @Inject(method = "addGuiElement", at = @At("HEAD"))
    private void polyreplay$captureModdedElement(GuiElementRenderState element, CallbackInfo ci) {
        if (ScreenCapture.active() && ScreenCapture.capturesState(this) && !element.getClass().getName().startsWith("net.minecraft.")) {
            GuiMeshCapture.capture(element);
        }
    }

}
//?}
