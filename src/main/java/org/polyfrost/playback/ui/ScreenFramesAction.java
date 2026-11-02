package org.polyfrost.playback.ui;

import org.polyfrost.playback.Playback;

import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ScreenFramesAction implements Action {

    public static final ScreenFramesAction INSTANCE = new ScreenFramesAction();
    private static final Identifier NAME = Identifier.fromNamespaceAndPath(Playback.MOD_ID, "action/screen_frames_optional");
    private static final float COORDINATE_SCALE = 8.0f;

    private static byte @Nullable [] lastOps;
    private static boolean wasOpen;

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

    public static void reset() {
        lastOps = null;
        wasOpen = false;
        ScreenCapture.clear();
    }

    public static void write(boolean chunkStart, AsyncReplaySaver... savers) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (chunkStart) {
            lastOps = null;
        }
        List<ScreenCapture.Frame> captured = ScreenCapture.take();
        if (captured.isEmpty() && !chunkStart && !wasOpen) {
            return;
        }

        List<Frame> frames = new ArrayList<>(captured.size());
        for (ScreenCapture.Frame frame : captured) {
            boolean same = lastOps != null && Arrays.equals(lastOps, frame.ops());
            lastOps = frame.ops();
            frames.add(new Frame(frame.partial(), frame.mouseX(), frame.mouseY(), frame.guiWidth(), frame.guiHeight(), frame.guiScale(), frame.chat(),
                same ? null : frame.ops()));
        }
        wasOpen = !captured.isEmpty();

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), minecraft.level.registryAccess());
        Payload.STREAM_CODEC.encode(buf, new Payload(wasOpen, frames));
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        for (AsyncReplaySaver saver : savers) {
            saver.submit(writer -> {
                writer.startAction(INSTANCE);
                writer.friendlyByteBuf().writeBytes(bytes);
                writer.finishAction(INSTANCE);
            });
        }
    }

    public record Frame(float partial, float mouseX, float mouseY, int guiWidth, int guiHeight, int guiScale, boolean chat, byte @Nullable [] ops) {
    }

    public record Payload(boolean open, List<Frame> frames) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Playback.MOD_ID, "screen_frames"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of(Payload::encode, Payload::decode);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }

        private static void encode(RegistryFriendlyByteBuf buf, Payload payload) {
            buf.writeBoolean(payload.open);
            if (!payload.open) {
                return;
            }
            buf.writeVarInt(payload.frames.size());
            for (Frame frame : payload.frames) {
                buf.writeByte(Math.clamp(Math.round(frame.partial * 255.0f), 0, 255));
                buf.writeShort(Math.clamp(Math.round(frame.mouseX * COORDINATE_SCALE), Short.MIN_VALUE, Short.MAX_VALUE));
                buf.writeShort(Math.clamp(Math.round(frame.mouseY * COORDINATE_SCALE), Short.MIN_VALUE, Short.MAX_VALUE));
                buf.writeShort(frame.guiWidth);
                buf.writeShort(frame.guiHeight);
                buf.writeByte(frame.guiScale);
                buf.writeByte((frame.chat ? 1 : 0) | (frame.ops != null ? 2 : 0));
                if (frame.ops != null) {
                    buf.writeVarInt(frame.ops.length);
                    buf.writeBytes(frame.ops);
                }
            }
        }

        private static Payload decode(RegistryFriendlyByteBuf buf) {
            if (!buf.readBoolean()) {
                return new Payload(false, List.of());
            }
            int count = buf.readVarInt();
            List<Frame> frames = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                float partial = buf.readUnsignedByte() / 255.0f;
                float mouseX = buf.readShort() / COORDINATE_SCALE;
                float mouseY = buf.readShort() / COORDINATE_SCALE;
                int guiWidth = buf.readShort();
                int guiHeight = buf.readShort();
                int guiScale = buf.readUnsignedByte();
                int flags = buf.readByte();
                byte[] ops = null;
                if ((flags & 2) != 0) {
                    ops = new byte[buf.readVarInt()];
                    buf.readBytes(ops);
                }
                frames.add(new Frame(partial, mouseX, mouseY, guiWidth, guiHeight, guiScale, (flags & 1) != 0, ops));
            }
            return new Payload(true, frames);
        }
    }

}
