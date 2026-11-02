package org.polyfrost.playback.mixin;

//? if <1.21.6 {
/*import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.polyfrost.playback.ui.ScreenCapture;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Font.class)
public abstract class MixinFont {

    @Inject(method = "drawInternal(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I", at = @At("HEAD"))
    private void playback$string(String text, float x, float y, int color, boolean shadow, Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode, int background, int light, boolean bidirectional, CallbackInfoReturnable<Integer> cir) {
        if (ScreenCapture.active() && text != null) {
            ScreenCapture.text(pose(matrix), Component.literal(text), x, y, color, shadow);
        }
    }

    //? if 1.21.1 {
    /^@Inject(method = "drawInternal(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I", at = @At("HEAD"))
    private void playback$sequence(FormattedCharSequence text, float x, float y, int color, boolean shadow, Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode, int background, int light, CallbackInfoReturnable<Integer> cir) {
    ^///?} else {
    /^@Inject(method = "drawInternal(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I", at = @At("HEAD"))
    private void playback$sequence(FormattedCharSequence text, float x, float y, int color, boolean shadow, Matrix4f matrix, MultiBufferSource buffer, Font.DisplayMode mode, int background, int light, boolean bidirectional, CallbackInfoReturnable<Integer> cir) {
    ^///?}
        if (ScreenCapture.active()) {
            ScreenCapture.text(pose(matrix), text, x, y, color, shadow);
        }
    }

    private static float[] pose(Matrix4f matrix) {
        return new float[]{matrix.m00(), matrix.m01(), matrix.m10(), matrix.m11(), matrix.m30(), matrix.m31(), matrix.m32()};
    }

}
*///?}
