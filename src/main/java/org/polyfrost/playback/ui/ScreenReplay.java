package org.polyfrost.playback.ui;

import org.polyfrost.playback.Playback;

import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
//? if >=26.3 {
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
//?}
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ScreenReplay {

    private interface Op {
        void run(GuiGraphicsExtractor graphics, float scale, float x, float y);
    }

    private record Resolved(float partial, float mouseX, float mouseY, int guiWidth, int guiHeight, int guiScale, boolean chat, List<Op> ops) {
    }

    private static List<Resolved> frames = List.of();
    private static List<Op> lastOps = List.of();
    private static boolean open;

    public static void reset() {
        frames = List.of();
        lastOps = List.of();
        open = false;
    }

    public static void accept(ScreenFramesAction.Payload payload) {
        if (!payload.open()) {
            reset();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        List<Resolved> resolved = new ArrayList<>(payload.frames().size());
        for (ScreenFramesAction.Frame frame : payload.frames()) {
            if (frame.ops() != null) {
                lastOps = decode(new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(frame.ops()), minecraft.level.registryAccess()), frame.guiWidth(), frame.guiHeight());
            }
            resolved.add(new Resolved(frame.partial(), frame.mouseX(), frame.mouseY(), frame.guiWidth(), frame.guiHeight(), frame.guiScale(), frame.chat(), lastOps));
        }
        if (!resolved.isEmpty()) {
            frames = resolved;
            open = true;
        }
    }

    public static boolean isShowing() {
        return open && Playback.spectated() != null && Minecraft.getInstance().options.getCameraType().isFirstPerson();
    }

    public static boolean hidesChat() {
        return isShowing() && frames.getLast().chat();
    }

    public static void render(GuiGraphicsExtractor graphics, float partial) {
        if (!isShowing()) {
            return;
        }
        Resolved frame = frames.getFirst();
        for (Resolved candidate : frames) {
            if (candidate.partial() <= partial) {
                frame = candidate;
            }
        }
        Minecraft minecraft = Minecraft.getInstance();
        float scale = (float) frame.guiScale() / (float) minecraft.getWindow().getGuiScale();
        float x = graphics.guiWidth() / 2.0f - frame.guiWidth() / 2.0f * scale;
        float y = graphics.guiHeight() / 2.0f - frame.guiHeight() / 2.0f * scale;
        for (Op op : frame.ops()) {
            op.run(graphics, scale, x, y);
        }
        drawCursor(graphics, x + frame.mouseX() * scale, y + frame.mouseY() * scale);
    }

    private static List<Op> decode(RegistryFriendlyByteBuf buf, int guiWidth, int guiHeight) {
        int count = buf.readVarInt();
        List<Op> ops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int type = buf.readByte();
            float[] pose = readPose(buf);
            switch (type) {
                case ScreenCapture.FILL -> {
                    int kind = buf.readByte();
                    int x0 = buf.readShort();
                    int y0 = buf.readShort();
                    int x1 = buf.readShort();
                    int y1 = buf.readShort();
                    int color1 = buf.readInt();
                    int color2 = buf.readInt();
                    boolean identity = pose[0] == 1.0f && pose[1] == 0.0f && pose[2] == 0.0f && pose[3] == 1.0f && pose[4] == 0.0f && pose[5] == 0.0f;
                    if (identity && x0 <= 0 && y0 <= 0 && x1 >= guiWidth && y1 >= guiHeight) {
                        ops.add((g, s, x, y) -> GuiCompat.fill(g, kind, 0, 0, g.guiWidth(), g.guiHeight(), color1, color2));
                        continue;
                    }
                    ops.add((g, s, x, y) -> {
                        GuiCompat.push(g, s, x, y, pose);
                        GuiCompat.fill(g, kind, x0, y0, x1, y1, color1, color2);
                        GuiCompat.pop(g);
                    });
                }
                case ScreenCapture.BLIT -> {
                    buf.readByte();
                    Identifier texture = buf.readIdentifier();
                    int x0 = buf.readShort();
                    int x1 = buf.readShort();
                    int y0 = buf.readShort();
                    int y1 = buf.readShort();
                    float u0 = buf.readFloat();
                    float u1 = buf.readFloat();
                    float v0 = buf.readFloat();
                    float v1 = buf.readFloat();
                    int color = buf.readInt();
                    ops.add((g, s, x, y) -> {
                        GuiCompat.push(g, s, x, y, pose);
                        GuiCompat.blit(g, texture, x0, x1, y0, y1, u0, u1, v0, v1, color);
                        GuiCompat.pop(g);
                    });
                }
                case ScreenCapture.TEXT -> {
                    Component text = ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
                    float textX = buf.readFloat();
                    float textY = buf.readFloat();
                    int color = buf.readInt();
                    boolean shadow = buf.readBoolean();
                    ops.add((g, s, x, y) -> {
                        GuiCompat.push(g, s, x, y, pose);
                        GuiCompat.text(g, text, Math.round(textX), Math.round(textY), color, shadow);
                        GuiCompat.pop(g);
                    });
                }
                case ScreenCapture.ITEM -> {
                    ItemStack stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                    int itemX = buf.readShort();
                    int itemY = buf.readShort();
                    ops.add((g, s, x, y) -> {
                        GuiCompat.push(g, s, x, y, pose);
                        GuiCompat.item(g, stack, itemX, itemY);
                        GuiCompat.pop(g);
                    });
                }
                case ScreenCapture.SCISSOR_ON -> {
                    int x0 = buf.readShort();
                    int y0 = buf.readShort();
                    int x1 = buf.readShort();
                    int y1 = buf.readShort();
                    ops.add((g, s, x, y) -> GuiCompat.scissorOn(g, s, x, y, pose, x0, y0, x1, y1));
                }
                case ScreenCapture.STRATUM -> ops.add((g, s, x, y) -> GuiCompat.stratum(g, false));
                case ScreenCapture.BLUR -> ops.add((g, s, x, y) -> GuiCompat.stratum(g, true));
                case ScreenCapture.SCISSOR_OFF -> ops.add((g, s, x, y) -> g.disableScissor());
                case ScreenCapture.ENTITY -> {
                    int entityId = buf.readVarInt();
                    int x0 = buf.readShort();
                    int y0 = buf.readShort();
                    int x1 = buf.readShort();
                    int y1 = buf.readShort();
                    int size = buf.readShort();
                    float offsetY = buf.readFloat();
                    float mouseX = buf.readFloat();
                    float mouseY = buf.readFloat();
                    ops.add((g, s, x, y) -> {
                        Minecraft minecraft = Minecraft.getInstance();
                        if (minecraft.level != null && minecraft.level.getEntity(entityId) instanceof LivingEntity entity) {
                            GuiCompat.push(g, s, x, y, pose);
                            GuiCompat.entity(g, entity, x0, y0, x1, y1, size, offsetY, mouseX, mouseY);
                            GuiCompat.pop(g);
                        }
                    });
                }
                case ScreenCapture.MESH -> {
                    int pipeline = buf.readByte();
                    int texture = buf.readVarInt() - 1;
                    boolean nearest = buf.readBoolean();
                    ScreenRectangle scissor = buf.readBoolean() ? new ScreenRectangle(buf.readShort(), buf.readShort(), buf.readShort(), buf.readShort()) : null;
                    int vertices = buf.readVarInt();
                    float[] data = new float[vertices * 4];
                    int[] colors = new int[vertices];
                    for (int v = 0; v < vertices; v++) {
                        data[v * 4] = buf.readFloat();
                        data[v * 4 + 1] = buf.readFloat();
                        data[v * 4 + 2] = buf.readFloat();
                        data[v * 4 + 3] = buf.readFloat();
                        colors[v] = buf.readInt();
                    }
                    //? if >=26.3 {
                    ops.add((g, s, x, y) -> {
                        GuiCompat.push(g, s, x, y, pose);
                        Matrix3x2f matrix = new Matrix3x2f(g.pose());
                        GuiCompat.pop(g);
                        GuiElementRenderState mesh = GuiMeshCapture.replay(pipeline, texture, nearest, matrix, scissor, data, colors);
                        if (mesh != null) {
                            g.guiRenderState.addGuiElement(mesh);
                        }
                    });
                    //?}
                }
                default -> throw new IllegalStateException("Unknown screen op " + type);
            }
        }
        return ops;
    }

    private static float[] readPose(RegistryFriendlyByteBuf buf) {
        return switch (buf.readByte()) {
            case 0 -> new float[]{1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f};
            case 1 -> new float[]{1.0f, 0.0f, 0.0f, 1.0f, buf.readFloat(), buf.readFloat(), buf.readFloat()};
            default -> new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()};
        };
    }

    public static void drawCursor(GuiGraphicsExtractor graphics, float x, float y) {
        SystemCursor.Loaded system = SystemCursor.get();
        if (system == null) {
            return;
        }
        int hotX = system.hotX();
        int hotY = system.hotY();
        float scale = 1.0f / (float) Minecraft.getInstance().getWindow().getGuiScale();
        GuiCompat.push(graphics, scale, x, y, new float[]{1.0f, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f});
        GuiCompat.blit(graphics, system.texture(), -hotX, system.width() - hotX, -hotY, system.height() - hotY, 0.0f, 1.0f, 0.0f, 1.0f, -1);
        GuiCompat.pop(graphics);
    }

}
