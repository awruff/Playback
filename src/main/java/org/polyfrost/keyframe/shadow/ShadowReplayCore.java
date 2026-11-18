package org.polyfrost.keyframe.shadow;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.Nullable;
import org.polyfrost.keyframe.standalone.recording.Clip;
import org.polyfrost.keyframe.standalone.recording.HighlightsKt;
import org.polyfrost.oneconfig.api.notifications.v1.NotificationType;
import org.polyfrost.oneconfig.api.notifications.v1.Notifications;
import org.polyfrost.oneconfig.utils.v1.NetworkUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

public abstract class ShadowReplayCore {

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final int START_DELAY_TICKS = 40;
    private static final int FLUSH_WAIT_TICKS = 20;
    private static final int CHUNK_TICKS = 200;

    private static final List<Request> requests = new ArrayList<>();
    private static final List<Path> stoppedFolders = new ArrayList<>();

    private static volatile @Nullable Recorder shadow;
    private static int startDelay;
    private static int flushAt = -1;
    private static int lastChunk;
    private static volatile boolean savingZip;

    private static final class Request {
        final int start;
        int end;
        final LocalDateTime time = LocalDateTime.now();

        Request(int start, int end) {
            this.start = start;
            this.end = end;
        }
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
        Clip clip = HighlightsKt.highlightClip(((ShadowRecorder) recorder).keyframe$writtenTicks(), ShadowReplayConfig.after, ShadowReplayConfig.length);
        Request last = requests.isEmpty() ? null : requests.getLast();
        if (last != null && clip.getStart() <= last.end) {
            last.end = Math.max(last.end, clip.getEnd());
        } else {
            requests.add(new Request(clip.getStart(), clip.getEnd()));
        }
    }

    protected static void joined() {
        if (!Flashback.isInReplay()) {
            startDelay = START_DELAY_TICKS;
        }
    }

    protected static boolean requestLast() {
        Recorder recorder = shadow;
        if (recorder == null) {
            return false;
        }
        int now = ((ShadowRecorder) recorder).keyframe$writtenTicks();
        requests.add(new Request(Math.max(0, now - ShadowReplayConfig.length * 20), now));
        return true;
    }

    protected static void stop(Executor mainThread) {
        Recorder recorder = shadow;
        if (recorder == null) {
            return;
        }
        shadow = null;
        flushAt = -1;
        if (Flashback.RECORDER == recorder) {
            Flashback.RECORDER = null;
        }
        //? if >=26.1 || <1.21.1 {
        recorder.endTickWithContext(true);
        //?} else
        //recorder.endTick(true);
        Path folder = recorder.finish();
        for (Request request : requests) {
            save(folder, request, mainThread);
        }
        requests.clear();
        stoppedFolders.add(folder);
    }

    protected static void tick(boolean hasPlayer, Supplier<Recorder> create, Executor mainThread) {
        if (startDelay > 0 && hasPlayer && --startDelay == 0 && ShadowReplayConfig.enabled && shadow == null && !Flashback.isInReplay()) {
            shadow = create.get();
            lastChunk = 0;
            restore();
        }
        if (shadow != null && !ShadowReplayConfig.enabled) {
            stop(mainThread);
        }

        Recorder recorder = shadow;
        if (recorder != null) {
            ShadowRecorder ext = (ShadowRecorder) recorder;
            int now = ext.keyframe$writtenTicks();
            if (now - lastChunk >= CHUNK_TICKS) {
                ext.keyframe$flushChunk();
                lastChunk = now;
            }
            if (!requests.isEmpty() && !savingZip) {
                if (flushAt < 0) {
                    if (requests.stream().anyMatch(request -> request.end <= now)) {
                        ext.keyframe$flushChunk();
                        flushAt = now;
                        lastChunk = now;
                    }
                } else if (now - flushAt >= FLUSH_WAIT_TICKS) {
                    List<Request> ready = new ArrayList<>(requests.stream().filter(request -> request.end <= flushAt).toList());
                    requests.removeAll(ready);
                    flushAt = -1;
                    saveAsync(ext.keyframe$folder(), ready, mainThread);
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

    private static void saveAsync(Path folder, List<Request> ready, Executor mainThread) {
        if (ready.isEmpty()) {
            return;
        }
        savingZip = true;
        Thread thread = new Thread(() -> {
            try {
                for (Request request : ready) {
                    save(folder, request, mainThread);
                }
            } finally {
                mainThread.execute(() -> savingZip = false);
            }
        }, "Keyframe Shadow Replay saver");
        thread.setDaemon(true);
        thread.start();
    }

    private static synchronized void save(Path folder, Request request, Executor mainThread) {
        Path replays = Flashback.getReplayFolder();
        String base = "Clip_" + FILE_TIME.format(request.time);
        String name = base + "_1";
        for (int i = 2; Files.exists(replays.resolve(name + ".zip")); i++) {
            name = base + "_" + i;
        }
        Path zip = replays.resolve(name + ".zip");
        try {
            if (ShadowReplayZip.write(folder, zip, request.start, request.end, name)) {
                mainThread.execute(() -> announceSaved(zip));
            }
        } catch (IOException e) {
            Flashback.LOGGER.error("Unable to save Shadow Replay clip from {}", folder, e);
        }
    }

    private static void announceSaved(Path zip) {
        if (!ShadowReplayConfig.notifications) return;
        String name = zip.getFileName().toString();
        Notifications.builder("Keyframe", "Saved " + name.substring(0, name.length() - 4))
            .type(NotificationType.SUCCESS)
            .action("Open folder", () -> NetworkUtils.browseLink(zip.getParent().toUri().toString()))
            .send();
    }

}
