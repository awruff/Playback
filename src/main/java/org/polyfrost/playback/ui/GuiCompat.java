package org.polyfrost.playback.ui;

import net.minecraft.client.Minecraft;
//? if 1.21.4
/*import net.minecraft.client.renderer.RenderType;*/
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
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
        //? if >=1.21.6 {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.pose().mul(new Matrix3x2f(pose[0], pose[1], pose[2], pose[3], pose[4], pose[5]));
        //?} else {
        /*graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0f);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.pose().mulPose(new Matrix4f(pose[0], pose[1], 0.0f, 0.0f, pose[2], pose[3], 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, pose[4], pose[5], pose[6], 1.0f));
        *///?}
    }

    public static void pop(GuiGraphicsExtractor graphics) {
        //? if >=1.21.6 {
        graphics.pose().popMatrix();
        //?} else
        //graphics.pose().popPose();
    }

    public static void scissorOn(GuiGraphicsExtractor graphics, float scale, float x, float y, float[] pose, int x0, int y0, int x1, int y1) {
        //? if >=1.21.6 {
        push(graphics, scale, x, y, pose);
        graphics.enableScissor(x0, y0, x1, y1);
        pop(graphics);
        //?} else
        //graphics.enableScissor(Math.round(x + x0 * scale), Math.round(y + y0 * scale), Math.round(x + x1 * scale), Math.round(y + y1 * scale));
    }

    public static void stratum(GuiGraphicsExtractor graphics, boolean blur) {
        //? if >=1.21.6 {
        if (blur) {
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
        //? if >=1.21.6 {
        graphics.innerBlit(RenderPipelines.GUI_TEXTURED, texture, x0, x1, y0, y1, u0, u1, v0, v1, color);
        //?} elif 1.21.4 {
        /*graphics.innerBlit(RenderType::guiTextured, texture, x0, x1, y0, y1, u0, u1, v0, v1, color);
        *///?} else
        //graphics.innerBlit(texture, x0, x1, y0, y1, 0, u0, u1, v0, v1, (color >> 16 & 0xFF) / 255.0f, (color >> 8 & 0xFF) / 255.0f, (color & 0xFF) / 255.0f, (color >>> 24) / 255.0f);
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
