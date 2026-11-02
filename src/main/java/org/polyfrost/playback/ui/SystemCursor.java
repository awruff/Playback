package org.polyfrost.playback.ui;

import org.polyfrost.playback.Playback;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SystemCursor {

    public record Loaded(Identifier texture, int width, int height, int hotX, int hotY) {
    }

    private record Raw(int width, int height, int hotX, int hotY, int[] argb) {
    }

    private static boolean tried;
    private static @Nullable Loaded loaded;

    public static @Nullable Loaded get() {
        if (!tried) {
            tried = true;
            try {
                Raw raw = read();
                if (raw != null) {
                    loaded = register(raw);
                }
            } catch (Exception | LinkageError e) {
                LogUtils.getLogger().warn("Could not load the system cursor", e);
            }
        }
        return loaded;
    }

    private static @Nullable Raw read() throws IOException {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return readWindows();
        }
        if (os.contains("mac")) {
            return readMac();
        }
        return readXcursor();
    }

    private static Loaded register(Raw raw) {
        NativeImage image = new NativeImage(raw.width(), raw.height(), false);
        for (int y = 0; y < raw.height(); y++) {
            for (int x = 0; x < raw.width(); x++) {
                int argb = raw.argb()[y * raw.width() + x];
                //? if >=1.21.4 {
                image.setPixel(x, y, argb);
                //?} else
                //image.setPixelRGBA(x, y, (argb & 0xFF00FF00) | ((argb & 0xFF) << 16) | ((argb >> 16) & 0xFF));
            }
        }
        //? if >=1.21.5 {
        DynamicTexture texture = new DynamicTexture(() -> "playback cursor", image);
        //?} else
        //DynamicTexture texture = new DynamicTexture(image);
        Identifier id = Identifier.fromNamespaceAndPath(Playback.MOD_ID, "system_cursor");
        Minecraft.getInstance().getTextureManager().register(id, texture);
        return new Loaded(id, raw.width(), raw.height(), raw.hotX(), raw.hotY());
    }

    private static @Nullable Raw readXcursor() throws IOException {
        Path home = Path.of(System.getProperty("user.home", "."));
        String theme = firstNonBlank(System.getenv("XCURSOR_THEME"), setting(home.resolve(".config/kcminputrc"), "cursorTheme"),
            setting(home.resolve(".config/gtk-3.0/settings.ini"), "gtk-cursor-theme-name"), "default");
        int size = parseInt(firstNonBlank(System.getenv("XCURSOR_SIZE"), setting(home.resolve(".config/kcminputrc"), "cursorSize"),
            setting(home.resolve(".config/gtk-3.0/settings.ini"), "gtk-cursor-theme-size")), 24);

        List<Path> roots = new ArrayList<>();
        String path = System.getenv("XCURSOR_PATH");
        if (path != null && !path.isBlank()) {
            for (String entry : path.split(":")) {
                roots.add(Path.of(entry.replaceFirst("^~", home.toString())));
            }
        } else {
            roots.add(home.resolve(".local/share/icons"));
            roots.add(home.resolve(".icons"));
            roots.add(Path.of("/usr/local/share/icons"));
            roots.add(Path.of("/usr/share/icons"));
            roots.add(Path.of("/usr/share/pixmaps"));
        }

        List<String> queue = new ArrayList<>(List.of(theme));
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < queue.size() && i < 16; i++) {
            String name = queue.get(i);
            if (!seen.add(name)) {
                continue;
            }
            for (Path root : roots) {
                for (String file : new String[]{"default", "left_ptr", "arrow"}) {
                    Path candidate = root.resolve(name).resolve("cursors").resolve(file);
                    if (Files.isRegularFile(candidate)) {
                        Raw raw = parseXcursor(Files.readAllBytes(candidate), size);
                        if (raw != null) {
                            return raw;
                        }
                    }
                }
                String inherits = setting(root.resolve(name).resolve("index.theme"), "Inherits");
                if (inherits != null) {
                    for (String parent : inherits.split("[,;]")) {
                        queue.add(parent.trim());
                    }
                }
            }
            if (i == queue.size() - 1) {
                queue.add("Adwaita");
                queue.add("default");
            }
        }
        return null;
    }

    private static @Nullable Raw parseXcursor(byte[] data, int wanted) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 16 || buf.getInt(0) != 0x72756358) {
            return null;
        }
        int count = buf.getInt(12);
        int best = -1;
        int bestSize = 0;
        for (int i = 0; i < count; i++) {
            int entry = 16 + i * 12;
            if (buf.getInt(entry) == 0xFFFD0002) {
                int nominal = buf.getInt(entry + 4);
                if (best < 0 || Math.abs(nominal - wanted) < Math.abs(bestSize - wanted)) {
                    best = buf.getInt(entry + 8);
                    bestSize = nominal;
                }
            }
        }
        if (best < 0) {
            return null;
        }
        int width = buf.getInt(best + 16);
        int height = buf.getInt(best + 20);
        int[] argb = new int[width * height];
        for (int i = 0; i < argb.length; i++) {
            int premultiplied = buf.getInt(best + 36 + i * 4);
            int alpha = premultiplied >>> 24;
            argb[i] = alpha == 0 || alpha == 255 ? premultiplied : (alpha << 24) | (unpremultiply(premultiplied >> 16, alpha) << 16)
                | (unpremultiply(premultiplied >> 8, alpha) << 8) | unpremultiply(premultiplied, alpha);
        }
        return new Raw(width, height, buf.getInt(best + 24), buf.getInt(best + 28), argb);
    }

    private static int unpremultiply(int channel, int alpha) {
        return Math.min(255, (channel & 0xFF) * 255 / alpha);
    }

    private static @Nullable Raw readWindows() throws IOException {
        List<Path> candidates = new ArrayList<>();
        try {
            Process process = new ProcessBuilder("reg", "query", "HKCU\\Control Panel\\Cursors", "/v", "Arrow").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = Pattern.compile("Arrow\\s+REG_\\w+\\s+(.+)").matcher(output);
            if (matcher.find()) {
                String value = matcher.group(1).trim();
                Matcher variable = Pattern.compile("%(\\w+)%").matcher(value);
                StringBuilder expanded = new StringBuilder();
                while (variable.find()) {
                    String replacement = System.getenv(variable.group(1));
                    variable.appendReplacement(expanded, Matcher.quoteReplacement(replacement == null ? variable.group() : replacement));
                }
                variable.appendTail(expanded);
                candidates.add(Path.of(expanded.toString()));
            }
        } catch (IOException ignored) {
        }
        String root = System.getenv("SystemRoot") == null ? "C:\\Windows" : System.getenv("SystemRoot");
        candidates.add(Path.of(root, "Cursors", "aero_arrow.cur"));
        for (Path candidate : candidates) {
            if (candidate.toString().toLowerCase(Locale.ROOT).endsWith(".cur") && Files.isRegularFile(candidate)) {
                Raw raw = parseCur(Files.readAllBytes(candidate), 32);
                if (raw != null) {
                    return raw;
                }
            }
        }
        return null;
    }

    private static @Nullable Raw parseCur(byte[] data, int wanted) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (data.length < 22 || buf.getShort(2) != 2) {
            return null;
        }
        int count = buf.getShort(4);
        int best = -1;
        int bestSize = 0;
        for (int i = 0; i < count; i++) {
            int entry = 6 + i * 16;
            int size = buf.get(entry) & 0xFF;
            size = size == 0 ? 256 : size;
            if (best < 0 || Math.abs(size - wanted) < Math.abs(bestSize - wanted)) {
                best = entry;
                bestSize = size;
            }
        }
        int hotX = buf.getShort(best + 4);
        int hotY = buf.getShort(best + 6);
        int length = buf.getInt(best + 8);
        int offset = buf.getInt(best + 12);
        if (buf.getInt(offset) == 0x474E5089) {
            try (NativeImage png = NativeImage.read(new ByteArrayInputStream(data, offset, length))) {
                int[] argb = new int[png.getWidth() * png.getHeight()];
                for (int y = 0; y < png.getHeight(); y++) {
                    for (int x = 0; x < png.getWidth(); x++) {
                        //? if >=1.21.4 {
                        argb[y * png.getWidth() + x] = png.getPixel(x, y);
                        //?} else {
                        /*int abgr = png.getPixelRGBA(x, y);
                        argb[y * png.getWidth() + x] = (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
                        *///?}
                    }
                }
                return new Raw(png.getWidth(), png.getHeight(), hotX, hotY, argb);
            }
        }
        int width = buf.getInt(offset + 4);
        int height = buf.getInt(offset + 8) / 2;
        if (buf.getShort(offset + 14) != 32) {
            return null;
        }
        int pixels = offset + buf.getInt(offset);
        int[] argb = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                argb[y * width + x] = buf.getInt(pixels + ((height - 1 - y) * width + x) * 4);
            }
        }
        return new Raw(width, height, hotX, hotY, argb);
    }

    private static @Nullable Raw readMac() throws IOException {
        Path dir = Path.of("/System/Library/Frameworks/ApplicationServices.framework/Versions/A/Frameworks/HIServices.framework/Versions/A/Resources/cursors/arrow");
        for (String name : new String[]{"cursor@2x.png", "cursor.png"}) {
            Path file = dir.resolve(name);
            if (Files.isRegularFile(file)) {
                int scale = name.contains("@2x") ? 2 : 1;
                int hotX = 0;
                int hotY = 0;
                Path info = dir.resolve("info.plist");
                if (Files.isRegularFile(info)) {
                    String plist = Files.readString(info);
                    hotX = (int) (plistNumber(plist, "hotx") * scale);
                    hotY = (int) (plistNumber(plist, "hoty") * scale);
                }
                try (NativeImage png = NativeImage.read(Files.newInputStream(file))) {
                    int[] argb = new int[png.getWidth() * png.getHeight()];
                    for (int y = 0; y < png.getHeight(); y++) {
                        for (int x = 0; x < png.getWidth(); x++) {
                            //? if >=1.21.4 {
                            argb[y * png.getWidth() + x] = png.getPixel(x, y);
                            //?} else {
                            /*int abgr = png.getPixelRGBA(x, y);
                            argb[y * png.getWidth() + x] = (abgr & 0xFF00FF00) | ((abgr & 0xFF) << 16) | ((abgr >> 16) & 0xFF);
                            *///?}
                        }
                    }
                    return new Raw(png.getWidth(), png.getHeight(), hotX, hotY, argb);
                }
            }
        }
        return null;
    }

    private static double plistNumber(String plist, String key) {
        Matcher matcher = Pattern.compile("<key>" + key + "</key>\\s*<(?:real|integer)>([-0-9.]+)<").matcher(plist);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0.0;
    }

    private static @Nullable String setting(Path file, String key) {
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.startsWith(key) && trimmed.substring(key.length()).trim().startsWith("=")) {
                    return trimmed.substring(trimmed.indexOf('=') + 1).trim();
                }
            }
        } catch (IOException | RuntimeException ignored) {
        }
        return null;
    }

    private static @Nullable String firstNonBlank(@Nullable String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static int parseInt(@Nullable String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

}
