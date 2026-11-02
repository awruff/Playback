package org.polyfrost.polyreplay.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.polyfrost.polyreplay.ui.GuiCompat;
import org.polyfrost.polyreplay.ui.ScreenCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.6 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.FormattedCharSequence;
//?} else
//import net.minecraft.client.renderer.RenderType;
//? if 1.21.4
//import java.util.function.Function;
//? if >=26.1
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinGuiGraphicsExtractor {

    //? if >=1.21.6 {
    @Inject(method = "innerFill", at = @At("HEAD"))
    private void polyreplay$fill(RenderPipeline pipeline, TextureSetup textureSetup, int x0, int y0, int x1, int y1, int color1, @Nullable Integer color2, CallbackInfo ci) {
        if (ScreenCapture.active((GuiGraphicsExtractor) (Object) this) && textureSetup == TextureSetup.noTexture()) {
            int kind = pipeline == RenderPipelines.GUI_INVERT ? 1 : pipeline == RenderPipelines.GUI_TEXT_HIGHLIGHT ? 2 : 0;
            ScreenCapture.fill(GuiCompat.pose((GuiGraphicsExtractor) (Object) this), kind, x0, y0, x1, y1, color1, color2 != null ? color2 : color1);
        }
    }

    @Inject(method = "innerBlit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V", at = @At("HEAD"))
    private void polyreplay$blit(RenderPipeline pipeline, Identifier location, int x0, int x1, int y0, int y1, float u0, float u1, float v0, float v1, int color, CallbackInfo ci) {
        if (ScreenCapture.active((GuiGraphicsExtractor) (Object) this)) {
            ScreenCapture.blit(GuiCompat.pose((GuiGraphicsExtractor) (Object) this), 0, location, x0, x1, y0, y1, u0, u1, v0, v1, color);
        }
    }

    @Inject(method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V", at = @At("HEAD"))
    private void polyreplay$text(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow, CallbackInfo ci) {
        if (ScreenCapture.active((GuiGraphicsExtractor) (Object) this)) {
            ScreenCapture.text(GuiCompat.pose((GuiGraphicsExtractor) (Object) this), text, x, y, color, shadow);
        }
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
    private void polyreplay$item(@Nullable LivingEntity owner, @Nullable Level level, ItemStack stack, int x, int y, int seed, CallbackInfo ci) {
        if (ScreenCapture.active((GuiGraphicsExtractor) (Object) this) && !stack.isEmpty()) {
            ScreenCapture.item(GuiCompat.pose((GuiGraphicsExtractor) (Object) this), stack, x, y);
        }
    }

    @Inject(method = "nextStratum", at = @At("HEAD"))
    private void polyreplay$stratum(CallbackInfo ci) {
        if (ScreenCapture.active((GuiGraphicsExtractor) (Object) this)) {
            ScreenCapture.marker(ScreenCapture.STRATUM);
        }
    }

    @Inject(method = "blurBeforeThisStratum", at = @At("HEAD"))
    private void polyreplay$blur(CallbackInfo ci) {
        if (ScreenCapture.active((GuiGraphicsExtractor) (Object) this)) {
            ScreenCapture.marker(ScreenCapture.BLUR);
        }
    }

    @Inject(method = "enableScissor", at = @At("HEAD"))
    private void polyreplay$enableScissor(int x0, int y0, int x1, int y1, CallbackInfo ci) {
        if (ScreenCapture.active((GuiGraphicsExtractor) (Object) this)) {
            ScreenCapture.scissor(GuiCompat.pose((GuiGraphicsExtractor) (Object) this), true, x0, y0, x1, y1);
        }
    }

    @Inject(method = "disableScissor", at = @At("HEAD"))
    private void polyreplay$disableScissor(CallbackInfo ci) {
        if (ScreenCapture.active((GuiGraphicsExtractor) (Object) this)) {
            ScreenCapture.scissor(GuiCompat.pose((GuiGraphicsExtractor) (Object) this), false, 0, 0, 0, 0);
        }
    }
    //?} else {
    /*@Inject(method = "fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V", at = @At("HEAD"))
    private void polyreplay$fill(RenderType type, int x0, int y0, int x1, int y1, int z, int color, CallbackInfo ci) {
        if (ScreenCapture.active()) {
            float[] pose = GuiCompat.pose((GuiGraphicsExtractor) (Object) this);
            pose[6] += z;
            ScreenCapture.fill(pose, 0, x0, y0, x1, y1, color, color);
        }
    }

    @Inject(method = "fillGradient(Lnet/minecraft/client/renderer/RenderType;IIIIIII)V", at = @At("HEAD"))
    private void polyreplay$fillGradient(RenderType type, int x0, int y0, int x1, int y1, int color1, int color2, int z, CallbackInfo ci) {
        if (ScreenCapture.active()) {
            float[] pose = GuiCompat.pose((GuiGraphicsExtractor) (Object) this);
            pose[6] += z;
            ScreenCapture.fill(pose, 0, x0, y0, x1, y1, color1, color2);
        }
    }

    @Inject(method = "item(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V", at = @At("HEAD"))
    private void polyreplay$item(@Nullable LivingEntity owner, @Nullable Level level, ItemStack stack, int x, int y, int seed, int z, CallbackInfo ci) {
        if (ScreenCapture.active() && !stack.isEmpty()) {
            ScreenCapture.item(GuiCompat.pose((GuiGraphicsExtractor) (Object) this), stack, x, y);
        }
    }

    @Inject(method = "enableScissor", at = @At("HEAD"))
    private void polyreplay$enableScissor(int x0, int y0, int x1, int y1, CallbackInfo ci) {
        if (ScreenCapture.active()) {
            ScreenCapture.scissor(GuiCompat.pose((GuiGraphicsExtractor) (Object) this), true, x0, y0, x1, y1);
        }
    }

    @Inject(method = "disableScissor", at = @At("HEAD"))
    private void polyreplay$disableScissor(CallbackInfo ci) {
        if (ScreenCapture.active()) {
            ScreenCapture.scissor(GuiCompat.pose((GuiGraphicsExtractor) (Object) this), false, 0, 0, 0, 0);
        }
    }
    *///?}

    //? if 1.21.1 {
    /*@Inject(method = "innerBlit(Lnet/minecraft/resources/Identifier;IIIIIFFFF)V", at = @At("HEAD"))
    private void polyreplay$blit(Identifier texture, int x0, int x1, int y0, int y1, int z, float u0, float u1, float v0, float v1, CallbackInfo ci) {
        if (ScreenCapture.active()) {
            float[] pose = GuiCompat.pose((GuiGraphicsExtractor) (Object) this);
            pose[6] += z;
            ScreenCapture.blit(pose, 0, texture, x0, x1, y0, y1, u0, u1, v0, v1, -1);
        }
    }

    @Inject(method = "innerBlit(Lnet/minecraft/resources/Identifier;IIIIIFFFFFFFF)V", at = @At("HEAD"))
    private void polyreplay$blitColored(Identifier texture, int x0, int x1, int y0, int y1, int z, float u0, float u1, float v0, float v1, float r, float g, float b, float a, CallbackInfo ci) {
        if (ScreenCapture.active()) {
            float[] pose = GuiCompat.pose((GuiGraphicsExtractor) (Object) this);
            pose[6] += z;
            int color = (Math.round(a * 255.0f) << 24) | (Math.round(r * 255.0f) << 16) | (Math.round(g * 255.0f) << 8) | Math.round(b * 255.0f);
            ScreenCapture.blit(pose, 0, texture, x0, x1, y0, y1, u0, u1, v0, v1, color);
        }
    }
    *///?}

    //? if 1.21.4 {
    /*@Inject(method = "innerBlit(Ljava/util/function/Function;Lnet/minecraft/resources/Identifier;IIIIFFFFI)V", at = @At("HEAD"))
    private void polyreplay$blit(Function<Identifier, RenderType> type, Identifier texture, int x0, int x1, int y0, int y1, float u0, float u1, float v0, float v1, int color, CallbackInfo ci) {
        if (ScreenCapture.active()) {
            ScreenCapture.blit(GuiCompat.pose((GuiGraphicsExtractor) (Object) this), 0, texture, x0, x1, y0, y1, u0, u1, v0, v1, color);
        }
    }
    *///?}

    //? if >=26.1 {
    @Inject(method = "blitTiledSprite", at = @At("HEAD"))
    private void polyreplay$tiledBlit(RenderPipeline pipeline, TextureAtlasSprite sprite, int x, int y, int width, int height, int textureX, int textureY, int tileWidth, int tileHeight, int spriteWidth, int spriteHeight, int color, CallbackInfo ci) {
        if (!ScreenCapture.active((GuiGraphicsExtractor) (Object) this) || width <= 0 || height <= 0 || tileWidth <= 0 || tileHeight <= 0) {
            return;
        }
        float[] pose = GuiCompat.pose((GuiGraphicsExtractor) (Object) this);
        float u0 = sprite.getU((float) textureX / spriteWidth);
        float v0 = sprite.getV((float) textureY / spriteHeight);
        for (int tileX = 0; tileX < width; tileX += tileWidth) {
            int w = Math.min(tileWidth, width - tileX);
            float u1 = sprite.getU((float) (textureX + w) / spriteWidth);
            for (int tileY = 0; tileY < height; tileY += tileHeight) {
                int h = Math.min(tileHeight, height - tileY);
                float v1 = sprite.getV((float) (textureY + h) / spriteHeight);
                ScreenCapture.blit(pose, 0, sprite.atlasLocation(), x + tileX, x + tileX + w, y + tileY, y + tileY + h, u0, u1, v0, v1, color);
            }
        }
    }
    //?}

}
