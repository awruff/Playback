package org.polyfrost.keyframe.shadow;

import com.moulberry.flashback.record.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.Executor;

public class ShadowReplay extends ShadowReplayCore {

    private static final Executor MAIN_THREAD = task -> Minecraft.getInstance().executeTask(task);

    public static boolean disconnecting;

    private static @Nullable ClientPlayNetworkHandler lastConnection;

    public static void saveLast() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && requestLast()) {
            minecraft.gui.setOverlayMessage("Saving the last " + ShadowReplayConfig.length + " seconds", false);
        }
    }

    public static void stop() {
        stop(MAIN_THREAD);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPlayNetworkHandler connection = minecraft.getNetworkHandler();
        if (connection != lastConnection) {
            lastConnection = connection;
            if (connection != null) {
                joined();
            }
        }
        tick(minecraft.player != null, () -> new Recorder(org.polyfrost.flashbacklegacy.shim.net.minecraft.client.Minecraft.getInstance().get$player().registryAccess()), MAIN_THREAD);
    }

}
