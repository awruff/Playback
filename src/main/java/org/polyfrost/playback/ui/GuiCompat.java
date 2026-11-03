package org.polyfrost.playback.ui;

import net.minecraft.client.Minecraft;
//? if 1.21.4 {
/*import net.minecraft.client.renderer.RenderType;
import java.util.function.Function;
*///?}
//? if 1.21.1 {
/*import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
*///?}
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
//? if >=1.21.6 {
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
*///?}

public class GuiCompat {

    //? if >=1.21.6 {
    private static final List<RenderPipeline> BLIT_PIPELINES = List.of(RenderPipelines.GUI_TEXTURED, RenderPipelines.CROSSHAIR, RenderPipelines.VIGNETTE,
        RenderPipelines.GUI_NAUSEA_OVERLAY, RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA);

    public static int blitKind(RenderPipeline pipeline) {
        return Math.max(BLIT_PIPELINES.indexOf(pipeline), 0);
    }
    //?}

    //? if 1.21.4 {
    /*private static final List<Function<Identifier, RenderType>> BLIT_TYPES = List.of(RenderType::guiTextured, RenderType::crosshair, RenderType::vignette,
        texture -> RenderType.guiNauseaOverlay(), RenderType::guiTexturedOverlay);

    public static int blitKind(Function<Identifier, RenderType> type, Identifier texture) {
        RenderType applied = type.apply(texture);
        for (int i = 0; i < BLIT_TYPES.size(); i++) {
            if (BLIT_TYPES.get(i).apply(texture) == applied) {
                return i;
            }
        }
        return 0;
    }
    *///?}

    public static float[] pose(GuiGraphicsExtractor graphics) {
        //? if >=1.21.6 {
        Matrix3x2fStack pose = graphics.pose();
        return new float[]{pose.m00(), pose.m01(), pose.m10(), pose.m11(), pose.m20(), pose.m21(), 0.0f};
        //?} else {
        /*Matrix4f pose = graphics.pose().last().pose();
        return new float[]{pose.m00(), pose.m01(), pose.m10(), pose.m11(), pose.m30(), pose.m31(), pose.m32()};
        *///?}
    }

    public static void push(GuiGraphicsExtractor graphics, float scale, float x, float y, float[] pose) {
        push(graphics, scale, scale, x, y, pose);
    }

    public static void push(GuiGraphicsExtractor graphics, float scaleX, float scaleY, float x, float y, float[] pose) {
        //? if >=1.21.6 {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scaleX, scaleY);
        graphics.pose().mul(new Matrix3x2f(pose[0], pose[1], pose[2], pose[3], pose[4], pose[5]));
        //?} else {
        /*graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scaleX, scaleY, 1.0f);
        graphics.pose().mulPose(new Matrix4f(pose[0], pose[1], 0.0f, 0.0f, pose[2], pose[3], 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, pose[4], pose[5], pose[6], 1.0f));
        *///?}
    }

    public static void pop(GuiGraphicsExtractor graphics) {
        //? if >=1.21.6 {
        graphics.pose().popMatrix();
        //?} else
        //graphics.pose().popPose();
    }

    public static void scissorOn(GuiGraphicsExtractor graphics, float scaleX, float scaleY, float x, float y, float[] pose, int x0, int y0, int x1, int y1) {
        //? if >=1.21.6 {
        push(graphics, scaleX, scaleY, x, y, pose);
        graphics.enableScissor(x0, y0, x1, y1);
        pop(graphics);
        //?} else
        //graphics.enableScissor(Math.round(x + (pose[0] * x0 + pose[4]) * scaleX), Math.round(y + (pose[3] * y0 + pose[5]) * scaleY), Math.round(x + (pose[0] * x1 + pose[4]) * scaleX), Math.round(y + (pose[3] * y1 + pose[5]) * scaleY));
    }

    public static void stratum(GuiGraphicsExtractor graphics, boolean blur) {
        //? if >=1.21.6 {
        Minecraft minecraft = Minecraft.getInstance();
        if (blur && minecraft.gui.screen() == null) {
            graphics.blurBeforeThisStratum();
        } else {
            graphics.nextStratum();
        }
        //?}
    }

    public static void fill(GuiGraphicsExtractor graphics, int kind, int x0, int y0, int x1, int y1, int color1, int color2) {
        //? if >=1.21.6 {
        if (color1 != color2) {
            graphics.fillGradient(x0, y0, x1, y1, color1, color2);
        } else {
            RenderPipeline pipeline = kind == 1 ? RenderPipelines.GUI_INVERT : kind == 2 ? RenderPipelines.GUI_TEXT_HIGHLIGHT : RenderPipelines.GUI;
            graphics.fill(pipeline, x0, y0, x1, y1, color1);
        }
        //?} else {
        /*if (color1 != color2) {
            graphics.fillGradient(x0, y0, x1, y1, color1, color2);
        } else {
            graphics.fill(x0, y0, x1, y1, color1);
        }
        *///?}
    }

    public static void blit(GuiGraphicsExtractor graphics, Identifier texture, int x0, int x1, int y0, int y1, float u0, float u1, float v0, float v1, int color) {
        blit(graphics, 0, texture, x0, x1, y0, y1, u0, u1, v0, v1, color);
    }

    public static void blit(GuiGraphicsExtractor graphics, int kind, Identifier texture, int x0, int x1, int y0, int y1, float u0, float u1, float v0, float v1, int color) {
        //? if >=1.21.6 {
        graphics.innerBlit(BLIT_PIPELINES.get(kind < BLIT_PIPELINES.size() ? kind : 0), texture, x0, x1, y0, y1, u0, u1, v0, v1, color);
        //?} elif 1.21.4 {
        /*graphics.innerBlit(BLIT_TYPES.get(kind < BLIT_TYPES.size() ? kind : 0), texture, x0, x1, y0, y1, u0, u1, v0, v1, color);
        *///?} else {
        /*if (kind != 0) {
            RenderSystem.enableBlend();
            if (kind == 1) {
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            } else if (kind == 2) {
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ZERO, GlStateManager.DestFactor.ONE_MINUS_SRC_COLOR, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
            } else {
                RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            }
        }
        graphics.innerBlit(texture, x0, x1, y0, y1, 0, u0, u1, v0, v1, (color >> 16 & 0xFF) / 255.0f, (color >> 8 & 0xFF) / 255.0f, (color & 0xFF) / 255.0f, (color >>> 24) / 255.0f);
        if (kind != 0) {
            RenderSystem.defaultBlendFunc();
        }
        *///?}
    }

    public static void text(GuiGraphicsExtractor graphics, Component text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        graphics.text(Minecraft.getInstance().font, text, x, y, color, shadow);
        //?} else
        //graphics.drawString(Minecraft.getInstance().font, text, x, y, color, shadow);
    }

    public static void item(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        //? if >=26.1 {
        graphics.item(stack, x, y);
        //?} else
        //graphics.renderItem(stack, x, y);
    }

}
