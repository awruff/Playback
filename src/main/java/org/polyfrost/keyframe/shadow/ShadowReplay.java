package org.polyfrost.keyframe.shadow;

import com.moulberry.flashback.record.Recorder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.concurrent.Executor;

public class ShadowReplay extends ShadowReplayCore {

    private static final Executor MAIN_THREAD = task -> Minecraft.getInstance().execute(task);

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> joined());
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick(minecraft.player != null, () -> new Recorder(minecraft.player.registryAccess()), MAIN_THREAD));
    }

    public static void saveLast() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !requestLast()) {
            return;
        }
        Component message = Component.literal("Saving the last " + ShadowReplayConfig.length + " seconds");
        //? if >=26.1 {
        minecraft.player.sendOverlayMessage(message);
        //?} else
        //minecraft.player.displayClientMessage(message, true);
    }

    public static void stop() {
        stop(MAIN_THREAD);
    }

}
