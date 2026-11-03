package org.polyfrost.playback.mixin;

//? if >=26.3 {
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.polyfrost.oneconfig.internal.ui.compose.LayerOp;
import org.polyfrost.playback.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(value = LayerOp.class, remap = false)
public abstract class MixinLayerOp {

    @Shadow
    @Final
    private List<Function1<GuiGraphicsExtractor, Unit>> ops;

    @Shadow
    @Final
    private ScreenRectangle bounds;

    @Shadow
    @Final
    private float alpha;

    @Inject(method = "invoke(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void playback$captureLayer(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!ScreenCapture.active(graphics)) {
            return;
        }
        boolean nested = ScreenCapture.inShadow();
        GuiRenderState state = new GuiRenderState();
        float[] previous = ScreenCapture.pushShadow(state, this.bounds.left(), this.bounds.top(), this.alpha);
        ScreenCapture.beginGroup(ScreenCapture.GROUP_PLAIN, ScreenCapture.IDENTITY, 0, 0, this.bounds.width(), this.bounds.height());
        try {
            GuiGraphicsExtractor shadow = new GuiGraphicsExtractor(Minecraft.getInstance(), state, 0, 0);
            for (Function1<GuiGraphicsExtractor, Unit> op : this.ops) {
                op.invoke(shadow);
            }
        } finally {
            ScreenCapture.endGroup();
            ScreenCapture.popShadow(state, previous);
        }
        if (nested) {
            ci.cancel();
        }
    }

}
//?}
