package org.polyfrost.polyreplay.ui;

import org.polyfrost.polyreplay.FirstPersonEffects;

//? if >=26.3 {
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.zip.CRC32;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class GuiMeshCapture implements Action {

    public static final GuiMeshCapture INSTANCE = new GuiMeshCapture();
    private static final Identifier NAME = Identifier.fromNamespaceAndPath(FirstPersonEffects.MOD_ID, "action/gui_textures_optional");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final RenderPipeline[] PIPELINES = {RenderPipelines.GUI, RenderPipelines.GUI_TEXTURED, RenderPipelines.GUI_TEXTURED_PREMULTIPLIED_ALPHA,
        RenderPipelines.GUI_TEXT, RenderPipelines.GUI_TEXT_GRAYSCALE};

    private static final Set<DynamicTexture> dynamicTextures = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<DynamicTexture, Integer> ids = new WeakHashMap<>();
    private static final Set<DynamicTexture> used = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Integer, Long> sent = new HashMap<>();
    private static final Map<Class<?>, @Nullable Field> keptFields = new HashMap<>();
    private static final Map<GpuTextureView, @Nullable DynamicTexture> views = new IdentityHashMap<>();
    private static int nextId;

    private static final Map<Integer, DynamicTexture> replayTextures = new HashMap<>();

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        Payload payload = Payload.STREAM_CODEC.decode(buf);
        for (ReplayPlayer viewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    public static void track(DynamicTexture texture) {
        dynamicTextures.add(texture);
    }

    public static void reset() {
        ids.clear();
        used.clear();
        sent.clear();
        nextId = 0;
    }

    public static void capture(GuiElementRenderState element) {
        Field kept = keptFields.computeIfAbsent(element.getClass(), GuiMeshCapture::keptField);
        Object gpu = null;
        try {
            if (kept != null) {
                gpu = kept.get(element);
                kept.set(element, null);
            }
            int pipeline = indexOf(element.pipeline());
            if (pipeline < 0) {
                return;
            }
            TextureSetup setup = element.textureSetup();
            int texture = -1;
            if (setup.texure0() != null) {
                DynamicTexture dynamic = find(setup.texure0());
                if (dynamic == null || dynamic.getPixels() == null) {
                    return;
                }
                texture = ids.computeIfAbsent(dynamic, key -> nextId++);
                used.add(dynamic);
            } else if (pipeline != 0) {
                return;
            }
            Recorder recorder = new Recorder();
            element.buildVertices(recorder);
            if (recorder.count == 0) {
                return;
            }
            ScreenCapture.mesh(pipeline, texture, nearest(setup.sampler0()), element.scissorArea(), recorder.count, recorder.data, recorder.colors);
        } catch (IllegalAccessException ignored) {
        } finally {
            if (kept != null) {
                try {
                    kept.set(element, gpu);
                } catch (IllegalAccessException ignored) {
                }
            }
        }
    }

    private static @Nullable Field keptField(Class<?> type) {
        try {
            Field field = type.getDeclaredField("gpu");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static int indexOf(RenderPipeline pipeline) {
        for (int i = 0; i < PIPELINES.length; i++) {
            if (PIPELINES[i] == pipeline) {
                return i;
            }
        }
        return -1;
    }

    private static boolean nearest(@Nullable GpuSampler sampler) {
        return sampler == RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST) || sampler == RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
    }

    private static @Nullable DynamicTexture find(GpuTextureView view) {
        if (views.containsKey(view)) {
            return views.get(view);
        }
        DynamicTexture found = null;
        for (DynamicTexture texture : dynamicTextures) {
            if (texture.getTextureView() == view) {
                found = texture;
                break;
            }
        }
        views.put(view, found);
        return found;
    }

    public static void write(boolean chunkStart, AsyncReplaySaver... savers) {
        if (chunkStart) {
            sent.clear();
        }
        for (DynamicTexture texture : used) {
            NativeImage pixels = texture.getPixels();
            Integer id = ids.get(texture);
            if (pixels == null || id == null) {
                continue;
            }
            ByteBuffer bytes = MemoryUtil.memByteBuffer(pixels.getPointer(), pixels.getWidth() * pixels.getHeight() * 4);
            CRC32 crc = new CRC32();
            crc.update(bytes.duplicate());
            Long last = sent.put(id, crc.getValue());
            if (last != null && last == crc.getValue()) {
                continue;
            }
            Payload payload = new Payload(id, pixels.getWidth(), pixels.getHeight(), deflate(bytes));
            for (AsyncReplaySaver saver : savers) {
                saver.submit(writer -> {
                    writer.startAction(INSTANCE);
                    Payload.write(writer.friendlyByteBuf(), payload);
                    writer.finishAction(INSTANCE);
                });
            }
        }
        used.clear();
        views.clear();
    }

    private static byte[] deflate(ByteBuffer bytes) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        deflater.setInput(bytes.duplicate());
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] chunk = new byte[65536];
        while (!deflater.finished()) {
            out.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        return out.toByteArray();
    }

    public static void accept(Payload payload) {
        NativeImage image = new NativeImage(payload.width, payload.height, false);
        ByteBuffer target = MemoryUtil.memByteBuffer(image.getPointer(), payload.width * payload.height * 4);
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(payload.pixels);
            while (!inflater.finished() && target.hasRemaining()) {
                if (inflater.inflate(target) == 0 && inflater.needsInput()) {
                    break;
                }
            }
        } catch (DataFormatException e) {
            image.close();
            return;
        } finally {
            inflater.end();
        }
        DynamicTexture old = replayTextures.put(payload.id, new DynamicTexture(() -> "polyreplay_gui_" + payload.id, image));
        if (old != null) {
            old.close();
        }
    }

    public static void clearReplay() {
        if (replayTextures.isEmpty()) {
            return;
        }
        replayTextures.values().forEach(DynamicTexture::close);
        replayTextures.clear();
    }

    public static @Nullable GuiElementRenderState replay(int pipeline, int texture, boolean nearest, Matrix3x2f pose, @Nullable ScreenRectangle scissor,
                                                        float[] data, int[] colors) {
        RenderPipeline renderPipeline = PIPELINES[pipeline];
        TextureSetup setup = TextureSetup.noTexture();
        if (texture >= 0) {
            DynamicTexture dynamic = replayTextures.get(texture);
            if (dynamic == null) {
                return null;
            }
            GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(nearest ? FilterMode.NEAREST : FilterMode.LINEAR);
            setup = pipeline >= 3 ? TextureSetup.singleTextureWithLightmap(dynamic.getTextureView(), sampler) : TextureSetup.singleTexture(dynamic.getTextureView(), sampler);
        }
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        Vector2f point = new Vector2f();
        for (int i = 0; i < colors.length; i++) {
            pose.transformPosition(data[i * 4], data[i * 4 + 1], point);
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }
        ScreenRectangle bounds = new ScreenRectangle((int) Math.floor(minX), (int) Math.floor(minY),
            (int) Math.ceil(maxX) - (int) Math.floor(minX), (int) Math.ceil(maxY) - (int) Math.floor(minY));
        ScreenRectangle clip = scissor == null ? null : scissor.transformMaxBounds(pose);
        return new ReplayMesh(renderPipeline, setup, pose, data, colors, pipeline >= 3, clip, clip == null ? bounds : clip.intersection(bounds));
    }

    private record ReplayMesh(RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2f pose, float[] data, int[] colors, boolean light,
                              @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements GuiElementRenderState {
        @Override
        public void buildVertices(VertexConsumer consumer) {
            for (int i = 0; i < this.colors.length; i++) {
                VertexConsumer vertex = consumer.addVertexWith2DPose(this.pose, this.data[i * 4], this.data[i * 4 + 1])
                    .setUv(this.data[i * 4 + 2], this.data[i * 4 + 3]).setColor(this.colors[i]);
                if (this.light) {
                    vertex.setLight(FULL_BRIGHT);
                }
            }
        }
    }

    private static final class Recorder implements VertexConsumer {
        private float[] data = new float[64];
        private int[] colors = new int[16];
        private int count;

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (this.count == this.colors.length) {
                this.colors = java.util.Arrays.copyOf(this.colors, this.count * 2);
                this.data = java.util.Arrays.copyOf(this.data, this.count * 8);
            }
            this.data[this.count * 4] = x;
            this.data[this.count * 4 + 1] = y;
            this.colors[this.count] = -1;
            this.count++;
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this.setColor(a << 24 | r << 16 | g << 8 | b);
        }

        @Override
        public VertexConsumer setColor(int color) {
            if (this.count > 0) {
                this.colors[this.count - 1] = color;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            if (this.count > 0) {
                this.data[(this.count - 1) * 4 + 2] = u;
                this.data[(this.count - 1) * 4 + 3] = v;
            }
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv3(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }

    public record Payload(int id, int width, int height, byte[] pixels) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FirstPersonEffects.MOD_ID, "gui_texture"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of((buf, payload) -> write(buf, payload),
            buf -> new Payload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray()));

        private static void write(net.minecraft.network.FriendlyByteBuf buf, Payload payload) {
            buf.writeVarInt(payload.id);
            buf.writeVarInt(payload.width);
            buf.writeVarInt(payload.height);
            buf.writeByteArray(payload.pixels);
        }

        @Override
        public Type<Payload> type() {
            return TYPE;
        }
    }

}
//?}
