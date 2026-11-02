package org.polyfrost.playback.shadow;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moulberry.flashback.FlashbackGson;
import com.moulberry.flashback.record.ReplayMarker;
import org.jspecify.annotations.Nullable;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ShadowReplayZip {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public record Span(List<String> chunks, int offset, int totalTicks) {
    }

    public static @Nullable Span span(Map<String, Integer> durations, int start, int end) {
        List<String> chunks = new ArrayList<>();
        int offset = 0;
        int tick = 0;
        for (Map.Entry<String, Integer> chunk : durations.entrySet()) {
            int chunkEnd = tick + chunk.getValue();
            if (chunkEnd > start && tick < end) {
                if (chunks.isEmpty()) {
                    offset = tick;
                }
                chunks.add(chunk.getKey());
            }
            tick = chunkEnd;
        }
        return chunks.isEmpty() ? null : new Span(chunks, offset, sum(durations, chunks));
    }

    private static int sum(Map<String, Integer> durations, List<String> chunks) {
        int total = 0;
        for (String chunk : chunks) {
            total += durations.get(chunk);
        }
        return total;
    }

    public static @Nullable Span write(Path recording, Path zip, int start, int end, String name) throws IOException {
        JsonObject meta = readMeta(recording.resolve("metadata.json"));
        if (meta == null) {
            meta = readMeta(recording.resolve("metadata.json.old"));
        }
        if (meta == null || !meta.has("chunks")) {
            return null;
        }
        JsonObject chunksJson = meta.getAsJsonObject("chunks");
        Map<String, Integer> durations = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> chunk : chunksJson.entrySet()) {
            if (Files.exists(recording.resolve(chunk.getKey()))) {
                durations.put(chunk.getKey(), chunk.getValue().getAsJsonObject().get("duration").getAsInt());
            }
        }
        Span span = span(durations, start, end);
        if (span == null) {
            return null;
        }

        JsonObject keptChunks = new JsonObject();
        for (String chunk : span.chunks()) {
            keptChunks.add(chunk, chunksJson.get(chunk));
        }
        meta.add("chunks", keptChunks);
        meta.addProperty("total_ticks", span.totalTicks());
        meta.addProperty("uuid", UUID.randomUUID().toString());
        meta.addProperty("name", name);
        JsonObject markers = new JsonObject();
        if (meta.has("markers")) {
            for (Map.Entry<String, JsonElement> marker : meta.getAsJsonObject("markers").entrySet()) {
                int tick = Integer.parseInt(marker.getKey()) - span.offset();
                if (tick >= 0 && tick <= span.totalTicks()) {
                    markers.add(String.valueOf(tick), marker.getValue());
                }
            }
        }
        markers.add(String.valueOf(Math.max(0, start - span.offset())), FlashbackGson.COMPRESSED.toJsonTree(new ReplayMarker(0x55FF55, null, "Clip start")));
        markers.add(String.valueOf(Math.min(span.totalTicks(), end - span.offset())), FlashbackGson.COMPRESSED.toJsonTree(new ReplayMarker(0xFF5555, null, "Clip end")));
        meta.add("markers", markers);

        Files.createDirectories(zip.getParent());
        try (ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zip)))) {
            out.setLevel(Deflater.BEST_SPEED);
            out.putNextEntry(new ZipEntry("metadata.json"));
            out.write(GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
            Path caches = recording.resolve("level_chunk_caches");
            if (Files.isDirectory(caches)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(caches)) {
                    for (Path file : files) {
                        copy(out, file, "level_chunk_caches/" + file.getFileName());
                    }
                }
            }
            copy(out, recording.resolve("level_chunk_cache"), "level_chunk_cache");
            copy(out, recording.resolve("icon.png"), "icon.png");
            for (String chunk : span.chunks()) {
                copy(out, recording.resolve(chunk), chunk);
            }
        }
        return span;
    }

    private static void copy(ZipOutputStream out, Path file, String name) throws IOException {
        if (!Files.isRegularFile(file)) {
            return;
        }
        out.putNextEntry(new ZipEntry(name));
        Files.copy(file, (OutputStream) out);
        out.closeEntry();
    }

    private static @Nullable JsonObject readMeta(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

}
