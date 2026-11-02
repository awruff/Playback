package org.polyfrost.playback.shadow;

import org.polyfrost.playback.Playback;

import com.mojang.blaze3d.platform.InputConstants;
import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind;
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider;
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch;
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper;
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind;

public class ShadowReplayConfig extends Config {

    @Switch(title = "Shadow Replay", description = "Keeps a replay running in the background so you can save the last few seconds at any time, even when Flashback isn't recording", subcategory = "Shadow Replay")
    public static boolean enabled = true;

    @Keybind(title = "Save Shadow Replay", description = "Saves the last few seconds of gameplay as a clip", subcategory = "Shadow Replay")
    public static OneConfigKeybind saveKey = KeybindHelper.builder().key(InputConstants.KEY_F8).name("Save Shadow Replay").category("Playback")
        .action(ShadowReplay::saveLast).register();

    @Slider(title = "Clip length", description = "Seconds of gameplay in each clip", subcategory = "Shadow Replay", min = 5, max = 120, step = 5)
    public static int length = 30;

    @Slider(title = "Seconds after highlight", description = "How much of a highlight clip comes after the highlight", subcategory = "Highlights", min = 0, max = 15, step = 1)
    public static int after = 3;

    @Switch(title = "Clip combos", subcategory = "Highlights")
    public static boolean combos = true;

    @Switch(title = "Clip kills", subcategory = "Highlights")
    public static boolean kills = true;

    @Switch(title = "Clip bed breaks", subcategory = "Highlights")
    public static boolean beds = true;

    @Switch(title = "Record menus in singleplayer", description = "Menus like the pause screen don't pause the game while Flashback is recording, so they show up in the replay", subcategory = "Recording")
    public static boolean recordMenus = true;

    public static final ShadowReplayConfig INSTANCE = new ShadowReplayConfig();

    public ShadowReplayConfig() {
        super(Playback.MOD_ID + ".json", "Playback", Category.OTHER);
    }

}
