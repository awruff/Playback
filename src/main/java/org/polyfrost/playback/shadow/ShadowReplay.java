package org.polyfrost.playback.shadow;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ShadowReplay {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int START_DELAY_TICKS = 40;
    private static final int FLUSH_WAIT_TICKS = 20;
    private static final int CHUNK_TICKS = 200;

    private static final List<Clip> requests = new ArrayList<>();
    private static final List<Path> stoppedFolders = new ArrayList<>();

    private static volatile @Nullable Recorder shadow;
    private static int startDelay;
    private static int flushAt = -1;
    private static int lastChunk;
    private static volatile boolean savingZip;

    private static final class Clip {
        final int start;
        int end;
        final LocalDateTime time = LocalDateTime.now();

        Clip(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, minecraft) -> {
            if (!Flashback.isInReplay()) {
                startDelay = START_DELAY_TICKS;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(ShadowReplay::tick);
    }

    public static boolean isShadow(@Nullable Recorder recorder) {
        return recorder != null && recorder == shadow;
    }

    public static @Nullable Recorder teeTarget(Recorder recorder) {
        Recorder target = shadow;
        return target != null && target != recorder && recorder == Flashback.RECORDER ? target : null;
    }

    public static void restore() {
        if (Flashback.RECORDER == null && shadow != null) {
            Flashback.RECORDER = shadow;
        }
    }

    public static void saveLast() {
        Minecraft minecraft = Minecraft.getInstance();
        Recorder recorder = shadow;
        if (recorder == null || minecraft.player == null) {
            return;
        }
        int now = ((ShadowRecorder) recorder).playback$writtenTicks();
        requests.add(new Clip(Math.max(0, now - ShadowReplayConfig.length * 20), now));
        Component message = Component.literal("Saving the last " + ShadowReplayConfig.length + " seconds");
        //? if >=26.1 {
        minecraft.player.sendOverlayMessage(message);
        //?} else
        //minecraft.player.displayClientMessage(message, true);
    }

    public static void highlight(String description) {
        boolean wanted = switch (description) {
            case "Combo" -> ShadowReplayConfig.combos;
            case "Bed broken" -> ShadowReplayConfig.beds;
            default -> ShadowReplayConfig.kills;
        };
        Recorder recorder = shadow;
        if (recorder == null || !wanted) {
            return;
        }
        int now = ((ShadowRecorder) recorder).playback$writtenTicks();
        int after = Math.min(ShadowReplayConfig.after, ShadowReplayConfig.length) * 20;
        int start = Math.max(0, now + after - ShadowReplayConfig.length * 20);
        int end = now + after;
        Clip last = requests.isEmpty() ? null : requests.getLast();
        if (last != null && start <= last.end) {
            last.end = Math.max(last.end, end);
        } else {
            requests.add(new Clip(start, end));
        }
    }

    public static void stop() {
        Recorder recorder = shadow;
        if (recorder == null) {
            return;
        }
        shadow = null;
        flushAt = -1;
        if (Flashback.RECORDER == recorder) {
            Flashback.RECORDER = null;
        }
        //? if >=26.1 {
        recorder.endTickWithContext(true);
        //?} else
        //recorder.endTick(true);
        Path folder = recorder.finish();
        for (Clip clip : requests) {
            save(folder, clip);
        }
        requests.clear();
        stoppedFolders.add(folder);
    }

    private static void tick(Minecraft minecraft) {
        if (startDelay > 0 && minecraft.player != null && --startDelay == 0 && ShadowReplayConfig.enabled && shadow == null && !Flashback.isInReplay()) {
            shadow = new Recorder(minecraft.player.registryAccess());
            lastChunk = 0;
            restore();
        }
        if (shadow != null && !ShadowReplayConfig.enabled) {
            stop();
        }

        Recorder recorder = shadow;
        if (recorder != null) {
            ShadowRecorder ext = (ShadowRecorder) recorder;
            int now = ext.playback$writtenTicks();
            if (now - lastChunk >= CHUNK_TICKS) {
                ext.playback$flushChunk();
                lastChunk = now;
            }
            if (!requests.isEmpty() && !savingZip) {
                if (flushAt < 0) {
                    if (requests.stream().anyMatch(clip -> clip.end <= now)) {
                        ext.playback$flushChunk();
                        flushAt = now;
                        lastChunk = now;
                    }
                } else if (now - flushAt >= FLUSH_WAIT_TICKS) {
                    List<Clip> ready = new ArrayList<>(requests.stream().filter(clip -> clip.end <= flushAt).toList());
                    requests.removeAll(ready);
                    flushAt = -1;
                    saveAsync(ext.playback$folder(), ready);
                }
            }
        }

        if (!savingZip && !stoppedFolders.isEmpty()) {
            for (Path folder : stoppedFolders) {
                try {
                    FileUtils.deleteDirectory(folder.toFile());
                } catch (IOException e) {
                    Flashback.LOGGER.warn("Unable to delete shadow replay recording {}", folder, e);
                }
            }
            stoppedFolders.clear();
        }
    }

    private static void saveAsync(Path folder, List<Clip> clips) {
        if (clips.isEmpty()) {
            return;
        }
        savingZip = true;
        Thread thread = new Thread(() -> {
            try {
                for (Clip clip : clips) {
                    save(folder, clip);
                }
            } finally {
                Minecraft.getInstance().execute(() -> savingZip = false);
            }
        }, "Playback Shadow Replay saver");
        thread.setDaemon(true);
        thread.start();
    }

    private static synchronized void save(Path folder, Clip clip) {
        Path replays = Flashback.getReplayFolder();
        String base = "Clip_" + FILE_TIME.format(clip.time);
        String name = base + "_1";
        for (int i = 2; Files.exists(replays.resolve(name + ".zip")); i++) {
            name = base + "_" + i;
        }
        Path zip = replays.resolve(name + ".zip");
        try {
            if (ShadowReplayZip.write(folder, zip, clip.start, clip.end, name) != null) {
                Minecraft.getInstance().execute(() -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player != null) {
                        //? if >=26.1 {
                        minecraft.player.sendSystemMessage(savedMessage(zip));
                        //?} else
                        //minecraft.player.displayClientMessage(savedMessage(zip), false);
                    }
                });
            }
        } catch (IOException e) {
            Flashback.LOGGER.error("Unable to save Shadow Replay clip from {}", folder, e);
        }
    }

    private static Component savedMessage(Path zip) {
        String folder = zip.getParent().toString();
        //? if >=1.21.5 {
        ClickEvent open = new ClickEvent.OpenFile(folder);
        //?} else
        //ClickEvent open = new ClickEvent(ClickEvent.Action.OPEN_FILE, folder);
        String name = zip.getFileName().toString();
        return Component.literal("Saved a clip to " + name.substring(0, name.length() - 4) + " ")
            .append(Component.literal("[Open Folder]").withStyle(style -> style.withColor(ChatFormatting.AQUA).withClickEvent(open)));
    }

}
