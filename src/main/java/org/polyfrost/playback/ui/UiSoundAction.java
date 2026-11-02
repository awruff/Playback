package org.polyfrost.playback.ui;

import org.polyfrost.playback.Playback;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class UiSoundAction implements Action {

    public static final UiSoundAction INSTANCE = new UiSoundAction();
    private static final Identifier NAME = Identifier.fromNamespaceAndPath(Playback.MOD_ID, "action/ui_sound_optional");

    private static final byte ONE_SHOT = 0;
    private static final byte START = 1;
    private static final byte VOLUME = 2;
    private static final byte STOP = 3;

    private static final List<Sound> pending = new ArrayList<>();
    private static final Map<SoundInstance, Integer> tracked = new IdentityHashMap<>();
    private static final Map<Integer, Float> trackedVolumes = new HashMap<>();
    private static int nextId;
    private static final Map<Integer, Loop> loops = new HashMap<>();

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        Payload payload = Payload.STREAM_CODEC.decode(buf);
        for (ReplayPlayer viewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(viewer, payload);
        }
    }

    public static void capture(SoundInstance instance) {
        //? if >=1.21.6 {
        boolean ui = instance.getSource() == SoundSource.UI;
        //?} else
        //boolean ui = instance.getSource() == SoundSource.MASTER && instance.isRelative() && instance.getAttenuation() == SoundInstance.Attenuation.NONE;
        if (!ui || instance.getSound() == null || !ScreenCapture.recording()) {
            return;
        }
        if (instance instanceof TickableSoundInstance || instance.isLooping()) {
            int id = nextId++;
            tracked.put(instance, id);
            trackedVolumes.put(id, instance.getVolume());
            pending.add(start(id, instance));
        } else {
            pending.add(new Sound(ONE_SHOT, -1, location(instance), instance.getVolume(), instance.getPitch(), false));
        }
    }

    private static Sound start(int id, SoundInstance instance) {
        return new Sound(START, id, location(instance), instance.getVolume(), instance.getPitch(), instance.isLooping());
    }

    private static Identifier location(SoundInstance instance) {
        //? if >=1.21.11 {
        return instance.getIdentifier();
        //?} else
        //return instance.getLocation();
    }

    public static void reset() {
        pending.clear();
        tracked.clear();
        trackedVolumes.clear();
        nextId = 0;
    }

    public static void write(boolean chunkStart, AsyncReplaySaver... savers) {
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        Iterator<Map.Entry<SoundInstance, Integer>> iterator = tracked.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<SoundInstance, Integer> entry = iterator.next();
            SoundInstance instance = entry.getKey();
            int id = entry.getValue();
            if (!soundManager.isActive(instance) || instance instanceof TickableSoundInstance tickable && tickable.isStopped()) {
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
        for (AsyncReplaySaver saver : savers) {
            saver.submit(writer -> {
                writer.startAction(INSTANCE);
                Payload.STREAM_CODEC.encode(writer.friendlyByteBuf(), payload);
                writer.finishAction(INSTANCE);
            });
        }
    }

    public static void play(Payload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean hears = Flashback.isInReplay() && Playback.spectated() != null && minecraft.options.getCameraType().isFirstPerson();
        for (Sound sound : payload.sounds()) {
            switch (sound.kind()) {
                case ONE_SHOT -> {
                    if (hears) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(sound.location()), sound.pitch(), sound.volume()));
                    }
                }
                case START -> {
                    Loop loop = loops.get(sound.id());
                    if (loop != null && minecraft.getSoundManager().isActive(loop)) {
                        loop.setVolume(sound.volume());
                    } else if (hears) {
                        loop = new Loop(sound);
                        loops.put(sound.id(), loop);
                        minecraft.getSoundManager().play(loop);
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

    private static final class Loop extends AbstractTickableSoundInstance {
        private Loop(Sound sound) {
            //? if >=1.21.6 {
            super(SoundEvent.createVariableRangeEvent(sound.location()), SoundSource.UI, SoundInstance.createUnseededRandom());
            //?} else
            //super(SoundEvent.createVariableRangeEvent(sound.location()), SoundSource.MASTER, SoundInstance.createUnseededRandom());
            this.looping = sound.looping();
            this.delay = 0;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
            this.volume = sound.volume();
            this.pitch = sound.pitch();
        }

        private void setVolume(float volume) {
            this.volume = volume;
        }

        private void end() {
            this.stop();
        }

        @Override
        public void tick() {
        }
    }

    public record Sound(byte kind, int id, @Nullable Identifier location, float volume, float pitch, boolean looping) {
    }

    public record Payload(List<Sound> sounds) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Playback.MOD_ID, "ui_sound"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of(Payload::encode, Payload::decode);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }

        private static void encode(RegistryFriendlyByteBuf buf, Payload payload) {
            buf.writeVarInt(payload.sounds.size());
            for (Sound sound : payload.sounds) {
                buf.writeByte(sound.kind);
                buf.writeVarInt(sound.id + 1);
                if (sound.kind == ONE_SHOT || sound.kind == START) {
                    buf.writeIdentifier(sound.location);
                    buf.writeFloat(sound.pitch);
                    buf.writeBoolean(sound.looping);
                }
                if (sound.kind != STOP) {
                    buf.writeFloat(sound.volume);
                }
            }
        }

        private static Payload decode(RegistryFriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<Sound> sounds = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                byte kind = buf.readByte();
                int id = buf.readVarInt() - 1;
                Identifier location = null;
                float pitch = 0.0f;
                boolean looping = false;
                if (kind == ONE_SHOT || kind == START) {
                    location = buf.readIdentifier();
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
