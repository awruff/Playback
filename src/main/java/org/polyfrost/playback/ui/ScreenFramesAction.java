package org.polyfrost.playback.ui;

import org.polyfrost.playback.Playback;
import org.polyfrost.playback.ui.ScreenCapture.Frame;
import org.polyfrost.playback.ui.ScreenCapture.Sample;

import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.playback.ReplayServer;
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

    private static byte @Nullable [] lastOps;
    private static boolean wasOpen;

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        Playback.relay(replayServer, Payload.STREAM_CODEC.decode(buf));
    }

    public static void reset() {
        lastOps = null;
        wasOpen = false;
        ScreenCapture.clear();
    }

    public static void write(boolean chunkStart, AsyncReplaySaver... savers) {
        if (chunkStart) {
            lastOps = null;
        }
        List<Frame> captured = ScreenCapture.take();
        if (captured.isEmpty() && !chunkStart && !wasOpen) {
            return;
        }

        List<Frame> frames = new ArrayList<>(captured.size());
        for (Frame frame : captured) {
            boolean same = lastOps != null && Arrays.equals(lastOps, frame.ops());
            lastOps = frame.ops();
            frames.add(same ? new Frame(frame.partial(), frame.mouseX(), frame.mouseY(), frame.guiWidth(), frame.guiHeight(), frame.guiScale(), frame.chat(), null) : frame);
        }
        wasOpen = !captured.isEmpty();

        Playback.submit(INSTANCE, buf -> Payload.STREAM_CODEC.encode(buf, new Payload(wasOpen, frames)), savers);
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
                ScreenCapture.writeSample(buf, frame.partial(), frame.mouseX(), frame.mouseY());
                buf.writeShort(frame.guiWidth());
                buf.writeShort(frame.guiHeight());
                buf.writeByte(frame.guiScale());
                buf.writeByte((frame.chat() ? 1 : 0) | (frame.ops() != null ? 2 : 0));
                if (frame.ops() != null) {
                    buf.writeByteArray(frame.ops());
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
                Sample mouse = ScreenCapture.readSample(buf);
                int guiWidth = buf.readShort();
                int guiHeight = buf.readShort();
                int guiScale = buf.readUnsignedByte();
                int flags = buf.readByte();
                byte[] ops = (flags & 2) != 0 ? buf.readByteArray() : null;
                frames.add(new Frame(mouse.partial(), mouse.x(), mouse.y(), guiWidth, guiHeight, guiScale, (flags & 1) != 0, ops));
            }
            return new Payload(true, frames);
        }
    }

}
