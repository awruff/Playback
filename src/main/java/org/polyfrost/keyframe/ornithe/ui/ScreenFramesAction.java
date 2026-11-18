package org.polyfrost.keyframe.ui;

import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import net.minecraft.network.PacketByteBuf;
import org.jspecify.annotations.Nullable;
import org.polyfrost.keyframe.Keyframe;
import org.polyfrost.keyframe.ui.ScreenWire.Frame;
import org.polyfrost.keyframe.ui.ScreenWire.Sample;

import java.util.ArrayList;
import java.util.List;

public class ScreenFramesAction {

    private static @Nullable Action action;

    private static final int OPS = 0x80;

    private static byte @Nullable [] lastOps;
    private static boolean wasOpen;

    public static void register() {
        action = Keyframe.action("action/screen_frames_v2_optional", buf -> ScreenReplay.accept(Payload.decode(buf)));
    }

    public static void reset() {
        lastOps = null;
        wasOpen = false;
        ScreenCapture.clear();
    }

    public static void write(boolean chunkStart, AsyncReplaySaver... savers) {
        if (action == null) {
            return;
        }
        if (chunkStart) {
            lastOps = null;
        }
        List<Frame> captured = ScreenCapture.take();
        if (captured.isEmpty() && !chunkStart && !wasOpen) {
            return;
        }

        List<Frame> frames = ScreenCapture.dedupe(lastOps, captured);
        wasOpen = !captured.isEmpty();
        if (wasOpen) {
            lastOps = captured.getLast().ops();
        }

        Payload payload = new Payload(wasOpen, frames);
        Keyframe.submit(action, payload::encode, savers);
    }

    public record Payload(boolean open, List<Frame> frames) {

        private void encode(PacketByteBuf buf) {
            buf.writeBoolean(this.open);
            if (!this.open) {
                return;
            }
            buf.writeVarInt(this.frames.size());
            for (Frame frame : this.frames) {
                ScreenCapture.writeSample(buf, frame.partial(), frame.mouseX(), frame.mouseY());
                buf.writeShort(frame.guiWidth());
                buf.writeShort(frame.guiHeight());
                buf.writeByte(frame.guiScale());
                buf.writeByte(frame.flags() | (frame.ops() != null ? OPS : 0));
                if (frame.ops() != null) {
                    buf.writeByteArray(frame.ops());
                }
            }
        }

        private static Payload decode(PacketByteBuf buf) {
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
                int flags = buf.readUnsignedByte();
                byte[] ops = (flags & OPS) != 0 ? buf.readByteArray() : null;
                frames.add(new Frame(mouse.partial(), mouse.x(), mouse.y(), guiWidth, guiHeight, guiScale, flags & ~OPS, ops));
            }
            return new Payload(true, frames);
        }
    }

}
