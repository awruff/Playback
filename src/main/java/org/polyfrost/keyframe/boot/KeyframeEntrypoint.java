package org.polyfrost.keyframe.boot;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class KeyframeEntrypoint implements ClientModInitializer {

    private static Boolean flashback;

    public static boolean flashback() {
        if (flashback == null) {
            flashback = FabricLoader.getInstance().isModLoaded("flashback") || legacyFlashbackLoaded();
        }
        return flashback;
    }

    private static boolean legacyFlashbackLoaded() {
        if (!FabricLoader.getInstance().isModLoaded("flashback_legacy")) {
            return false;
        }
        try {
            return Class.forName("org.polyfrost.flashbacklegacy.FlashbackLegacy").getField("flashbackLoaded").getBoolean(null);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Override
    public void onInitializeClient() {
        FabricLoader.getInstance().invokeEntrypoints(flashback() ? "keyframe-addon" : "keyframe-standalone", ClientModInitializer.class, ClientModInitializer::onInitializeClient);
    }

}
