package org.polyfrost.keyframe;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.action.ActionRegistry;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.playback.ReplayServer;
import io.netty.buffer.Unpooled;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.ClientPlayerEntity;
import net.minecraft.network.PacketByteBuf;
import org.jspecify.annotations.Nullable;
import org.polyfrost.keyframe.camera.CameraTrackAction;
import org.polyfrost.keyframe.player.PlayerStateAction;
import org.polyfrost.keyframe.player.SpectatedPlayer;
import org.polyfrost.keyframe.shadow.AutoMarkers;
import org.polyfrost.keyframe.shadow.ShadowReplay;
import org.polyfrost.keyframe.shadow.ShadowReplayConfig;
import org.polyfrost.keyframe.ui.ContainerScreenAction;
import org.polyfrost.keyframe.ui.ScreenFramesAction;
import org.polyfrost.keyframe.standalone.recording.RecordingHud;
import org.polyfrost.keyframe.ui.ScreenCapture;
import org.polyfrost.keyframe.ui.ScreenReplay;
import org.polyfrost.keyframe.ui.UiSoundAction;
import org.polyfrost.flashbacklegacy.shim.net.minecraft.client.player.AbstractClientPlayer;
import org.polyfrost.flashbacklegacy.shim.net.minecraft.network.RegistryFriendlyByteBuf;
import org.polyfrost.flashbacklegacy.shim.net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Keyframe implements ClientModInitializer {

    public static final String MOD_ID = "keyframe";

    private static final Map<String, Consumer<PacketByteBuf>> HANDLERS = new HashMap<>();

    @Override
    public void onInitializeClient() {
        ShadowReplayConfig.INSTANCE.preload();
        RecordingHud.register(ShadowReplayConfig.INSTANCE.id, () -> ScreenCapture.recording() && !ShadowReplay.isShadow(Flashback.RECORDER));
        CameraTrackAction.register();
        PlayerStateAction.register();
        ScreenFramesAction.register();
        ContainerScreenAction.register();
        UiSoundAction.register();
    }

    public static Action action(String path, Consumer<PacketByteBuf> handler) {
        Action action = new Id(Identifier.fromNamespaceAndPath(MOD_ID, path));
        ActionRegistry.register(action);
        HANDLERS.put(action.name().toString(), handler);
        return action;
    }

    public static void dispatch(String action, PacketByteBuf body) {
        Consumer<PacketByteBuf> handler = HANDLERS.get(action);
        if (handler != null) {
            PacketByteBuf copy = new PacketByteBuf(Unpooled.copiedBuffer(body));
            Minecraft.getInstance().executeTask(() -> handler.accept(copy));
        }
    }

    public static void submit(Action action, Consumer<PacketByteBuf> encoder, AsyncReplaySaver... savers) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        encoder.accept(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        for (AsyncReplaySaver saver : savers) {
            saver.submit(writer -> {
                writer.startAction(action);
                writer.friendlyByteBuf().writeBytes(bytes);
                writer.finishAction(action);
            });
        }
    }

    public static @Nullable ClientPlayerEntity spectated() {
        AbstractClientPlayer player = Flashback.getSpectatingPlayer();
        return player == null ? null : (ClientPlayerEntity) player.handle;
    }

    public static void tick() {
        if (!Flashback.isInReplay()) {
            ScreenReplay.reset();
            ContainerScreenAction.clearReplay();
            UiSoundAction.clearReplay();
        }
        SpectatedPlayer.tick();
        AutoMarkers.tick();
        ShadowReplay.tick();
    }

    private record Id(Identifier name) implements Action {
        @Override
        public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        }
    }

}
