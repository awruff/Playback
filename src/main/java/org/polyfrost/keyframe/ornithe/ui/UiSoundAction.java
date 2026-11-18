package org.polyfrost.keyframe.ui;

import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sound.SoundCategory;
import net.minecraft.client.sound.SoundPool;
import net.minecraft.client.sound.instance.AbstractSoundInstance;
import net.minecraft.client.sound.instance.AbstractTickableSoundInstance;
import net.minecraft.client.sound.instance.SoundInstance;
import net.minecraft.client.sound.instance.TickableSoundInstance;
import net.minecraft.client.sound.system.SoundManager;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.resource.Identifier;
import org.jspecify.annotations.Nullable;
import org.polyfrost.keyframe.Keyframe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class UiSoundAction {

    private static final byte ONE_SHOT = 0;
    private static final byte START = 1;
    private static final byte VOLUME = 2;
    private static final byte STOP = 3;

    private static @Nullable Action action;

    private static final List<Sound> pending = new ArrayList<>();
    private static final Map<SoundInstance, Integer> tracked = new IdentityHashMap<>();
    private static final Map<Integer, Float> trackedVolumes = new HashMap<>();
    private static int nextId;
    private static final Map<Integer, Loop> loops = new HashMap<>();

    public static void register() {
        action = Keyframe.action("action/ui_sound_optional", buf -> play(Payload.decode(buf)));
    }

    public static void capture(SoundInstance instance) {
        if (instance.getAttenuationType() != SoundInstance.Attenuation.NONE || !ScreenCapture.recording()) {
            return;
        }
        SoundPool pool = Minecraft.getInstance().getSoundManager().get(instance.getLocation());
        if (pool == null || pool.getCategory() != SoundCategory.MASTER) {
            return;
        }
        if (instance instanceof TickableSoundInstance || instance.isLooping()) {
            int id = nextId++;
            tracked.put(instance, id);
            trackedVolumes.put(id, instance.getVolume());
            pending.add(start(id, instance));
        } else {
            pending.add(new Sound(ONE_SHOT, -1, instance.getLocation(), instance.getVolume(), instance.getPitch(), false));
        }
    }

    private static Sound start(int id, SoundInstance instance) {
        return new Sound(START, id, instance.getLocation(), instance.getVolume(), instance.getPitch(), instance.isLooping());
    }

    public static void reset() {
        pending.clear();
        tracked.clear();
        trackedVolumes.clear();
        nextId = 0;
    }

    public static void write(boolean chunkStart, AsyncReplaySaver... savers) {
        if (action == null) {
            return;
        }
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        Iterator<Map.Entry<SoundInstance, Integer>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SoundInstance, Integer> entry = iterator.next();
            SoundInstance instance = entry.getKey();
            int id = entry.getValue();
            if (!soundManager.isPlaying(instance) || instance instanceof TickableSoundInstance tickable && tickable.isStopped()) {
                pending.add(new Sound(STOP, id, null, 0.0f, 0.0f, false));
                trackedVolumes.remove(id);
                iterator.remove();
                continue;
            }
            float volume = instance.getVolume();
            if (chunkStart) {
                pending.add(start(id, instance));
            } else if (trackedVolumes.get(id) != volume) {
                pending.add(new Sound(VOLUME, id, null, volume, 0.0f, false));
            }
            trackedVolumes.put(id, volume);
        }
        if (pending.isEmpty()) {
            return;
        }
        Payload payload = new Payload(List.copyOf(pending));
        pending.clear();
        Keyframe.submit(action, payload::encode, savers);
    }

    public static void play(Payload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        SoundManager soundManager = minecraft.getSoundManager();
        boolean hears = Keyframe.spectated() != null && minecraft.options.perspective == 0;
        for (Sound sound : payload.sounds()) {
            switch (sound.kind()) {
                case ONE_SHOT -> {
                    if (hears) {
                        soundManager.play(new Shot(sound));
                    }
                }
                case START -> {
                    Loop loop = loops.get(sound.id());
                    if (loop != null && soundManager.isPlaying(loop)) {
                        loop.setVolume(sound.volume());
                    } else if (hears) {
                        loop = new Loop(sound);
                        loops.put(sound.id(), loop);
                        soundManager.play(loop);
                    }
                }
                case VOLUME -> {
                    Loop loop = loops.get(sound.id());
                    if (loop != null) {
                        loop.setVolume(sound.volume());
                    }
                }
                default -> {
                    Loop loop = loops.remove(sound.id());
                    if (loop != null) {
                        loop.end();
                    }
                }
            }
        }
    }

    public static void clearReplay() {
        if (loops.isEmpty()) {
            return;
        }
        loops.values().forEach(Loop::end);
        loops.clear();
    }

    private static final class Shot extends AbstractSoundInstance {
        private Shot(Sound sound) {
            super(sound.location());
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = sound.volume();
            this.pitch = sound.pitch();
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private Loop(Sound sound) {
            super(sound.location());
            this.looping = sound.looping();
            this.period = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.volume = sound.volume();
            this.pitch = sound.pitch();
        }

        private void setVolume(float volume) {
            this.volume = volume;
        }

        private void end() {
            this.done = true;
        }

        @Override
        public void tick() {
        }
    }

    public record Sound(byte kind, int id, @Nullable Identifier location, float volume, float pitch, boolean looping) {
    }

    public record Payload(List<Sound> sounds) {

        private void encode(PacketByteBuf buf) {
            buf.writeVarInt(this.sounds.size());
            for (Sound sound : this.sounds) {
                buf.writeByte(sound.kind);
                buf.writeVarInt(sound.id + 1);
                if (sound.kind == ONE_SHOT || sound.kind == START) {
                    buf.writeString(sound.location.toString());
                    buf.writeFloat(sound.pitch);
                    buf.writeBoolean(sound.looping);
                }
                if (sound.kind != STOP) {
                    buf.writeFloat(sound.volume);
                }
            }
        }

        private static Payload decode(PacketByteBuf buf) {
            int count = buf.readVarInt();
            List<Sound> sounds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                byte kind = buf.readByte();
                int id = buf.readVarInt() - 1;
                Identifier location = null;
                float pitch = 0.0f;
                boolean looping = false;
                if (kind == ONE_SHOT || kind == START) {
                    location = new Identifier(buf.readString(32767));
                    pitch = buf.readFloat();
                    looping = buf.readBoolean();
                }
                float volume = kind != STOP ? buf.readFloat() : 0.0f;
                sounds.add(new Sound(kind, id, location, volume, pitch, looping));
            }
            return new Payload(sounds);
        }
    }

}
