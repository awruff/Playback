package org.polyfrost.keyframe.mixin.flashback;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.ActionNextTick;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.io.ReplayWriter;
import com.moulberry.flashback.record.Recorder;
import com.moulberry.flashback.record.ReplayMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.Packet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import org.polyfrost.keyframe.Keyframe;
import org.polyfrost.keyframe.camera.CameraTrackAction;
import org.polyfrost.keyframe.ui.ContainerScreenAction;
import org.polyfrost.keyframe.player.PlayerStateAction;
import org.polyfrost.keyframe.ui.ScreenFramesAction;
import org.polyfrost.keyframe.shadow.ShadowRecorder;
import org.polyfrost.keyframe.shadow.ShadowReplay;
import org.polyfrost.keyframe.ui.UiSoundAction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Consumer;

@Mixin(Recorder.class)
public abstract class MixinRecorder implements ShadowRecorder {

    @Shadow
    @Final
    private AsyncReplaySaver asyncReplaySaver;

    @Shadow
    private int writtenTicksInChunk;

    @Shadow
    private int writtenTicks;

    @Shadow
    private boolean finishedConfiguration;

    @Shadow
    private boolean closeForWriting;

    @Shadow
    private boolean needsInitialSnapshot;

    @Override
    public boolean keyframe$wroteInitialSnapshot() {
        return !this.needsInitialSnapshot;
    }

    @Override
    public int keyframe$writtenTicks() {
        return this.writtenTicks;
    }

    @Override
    public int keyframe$writtenTicksInChunk() {
        return this.writtenTicksInChunk;
    }

    @Override
    public AsyncReplaySaver keyframe$saver() {
        return this.asyncReplaySaver;
    }

    @Override
    public void keyframe$flushChunk() {
        this.finishedConfiguration = true;
    }

    @Override
    public Path keyframe$folder() {
        return ((AsyncReplaySaverAccessor) this.asyncReplaySaver).keyframe$recordFolder();
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void keyframe$resetCameraTrack(RegistryAccess registryAccess, CallbackInfo ci) {
        Keyframe.registries = registryAccess;
        CameraTrackAction.reset();
        PlayerStateAction.reset();
        ScreenFramesAction.reset();
        UiSoundAction.reset();
        ContainerScreenAction.reset();
        //? if >=26.3
        org.polyfrost.keyframe.ui.GuiMeshCapture.reset();
    }

    @Inject(method = "endTick", at = @At("HEAD"))
    private void keyframe$writeMenuTick(boolean close, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        Recorder self = (Recorder) (Object) this;
        if (close || this.closeForWriting || self.isPaused() || self != Flashback.RECORDER || ShadowReplay.isShadow(self)
            || minecraft.level != null || minecraft.getConnection() != null) {
            return;
        }
        boolean chunkStart = this.writtenTicksInChunk == 0;
        //? if >=26.3
        org.polyfrost.keyframe.ui.GuiMeshCapture.write(chunkStart, this.asyncReplaySaver);
        ScreenFramesAction.write(chunkStart, this.asyncReplaySaver);
        UiSoundAction.write(chunkStart, this.asyncReplaySaver);
        this.asyncReplaySaver.submit(writer -> writer.startAndFinishAction(ActionNextTick.INSTANCE));
        this.writtenTicksInChunk++;
        this.writtenTicks++;
    }

    @Inject(method = "endTick", at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/record/Recorder;writeLocalData()V", shift = At.Shift.AFTER))
    private void keyframe$writeCameraTrack(boolean close, CallbackInfo ci) {
        if ((Object) this != Flashback.RECORDER || Minecraft.getInstance().level == null) {
            return;
        }
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null && !((ShadowRecorder) shadow).keyframe$wroteInitialSnapshot()) {
            shadow = null;
        }
        AsyncReplaySaver[] savers = shadow == null ? new AsyncReplaySaver[]{this.asyncReplaySaver}
            : new AsyncReplaySaver[]{this.asyncReplaySaver, ((ShadowRecorder) shadow).keyframe$saver()};
        boolean chunkStart = this.writtenTicksInChunk == 0 || shadow != null && ((ShadowRecorder) shadow).keyframe$writtenTicksInChunk() == 0;
        CameraTrackAction.write(savers);
        PlayerStateAction.write(chunkStart, savers);
        //? if >=26.3
        org.polyfrost.keyframe.ui.GuiMeshCapture.write(chunkStart, savers);
        ScreenFramesAction.write(chunkStart, savers);
        UiSoundAction.write(chunkStart, savers);
        ContainerScreenAction.write(chunkStart, savers);
    }

    @WrapWithCondition(method = "endTick", at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/record/Recorder;writeAccurateFirstPersonPosition(I)V"))
    private boolean keyframe$skipFlashbackFirstPersonTrack(Recorder recorder, int localPlayerUpdatesPerSecond) {
        return false;
    }

    @ModifyExpressionValue(method = {"writeLocalData", "writeSnapshot"}, at = @At(value = "FIELD", target = "Lcom/moulberry/flashback/configuration/FlashbackConfigV1$SubcategoryRecording;recordHotbar:Z"))
    private boolean keyframe$alwaysRecordHotbar(boolean original) {
        return true;
    }

    @Inject(method = "endTick", at = @At("TAIL"))
    private void keyframe$endCameraTrackTick(boolean close, CallbackInfo ci) {
        if ((Object) this == Flashback.RECORDER) {
            CameraTrackAction.endTick();
        }
    }

    //? if >=26.1 {
    @Inject(method = "startTick", at = @At("HEAD"))
    private void keyframe$teeStartTick(CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null) {
            shadow.startTick();
        }
    }

    @Inject(method = "endTickWithContext", at = @At("RETURN"))
    private void keyframe$teeEndTick(boolean close, CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null && !close) {
            shadow.endTickWithContext(false);
        }
    }
    //?} else {
    /*@Inject(method = "endTick", at = @At("RETURN"))
    private void keyframe$teeEndTick(boolean close, CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null && !close) {
            shadow.endTick(false);
        }
    }
    *///?}

    @Inject(method = "trackPartialPosition", at = @At("HEAD"))
    private void keyframe$teePartialPosition(Entity entity, float partialTick, CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null) {
            shadow.trackPartialPosition(entity, partialTick);
        }
    }

    @Inject(method = "writePacketAsync", at = @At("HEAD"))
    private void keyframe$teePacket(Packet<?> packet, ConnectionProtocol protocol, CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null) {
            shadow.writePacketAsync(packet, protocol);
        }
    }

    @Inject(method = "writeLevelEvent", at = @At("HEAD"))
    private void keyframe$teeLevelEvent(int type, BlockPos pos, int data, boolean global, CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null) {
            shadow.writeLevelEvent(type, pos, data, global);
        }
    }

    @Inject(method = "writeSound", at = @At("HEAD"))
    private void keyframe$teeSound(Holder<SoundEvent> sound, SoundSource source, double x, double y, double z, float volume, float pitch, long seed, CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null) {
            shadow.writeSound(sound, source, x, y, z, volume, pitch, seed);
        }
    }

    @Inject(method = "writeEntitySound", at = @At("HEAD"))
    private void keyframe$teeEntitySound(Holder<SoundEvent> sound, SoundSource source, Entity entity, float volume, float pitch, long seed, CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null) {
            shadow.writeEntitySound(sound, source, entity, volume, pitch, seed);
        }
    }

    @Inject(method = "setRegistryAccess", at = @At("HEAD"))
    private void keyframe$teeRegistryAccess(RegistryAccess registryAccess, CallbackInfo ci) {
        Keyframe.registries = registryAccess;
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null) {
            shadow.setRegistryAccess(registryAccess);
        }
    }

    @Inject(method = "addMarker", at = @At("HEAD"))
    private void keyframe$teeMarker(ReplayMarker marker, CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null) {
            shadow.addMarker(marker);
        }
    }

    @Inject(method = "submitCustomTask", at = @At("HEAD"))
    private void keyframe$teeCustomTask(Consumer<ReplayWriter> task, CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null) {
            shadow.submitCustomTask(task);
        }
    }

    @Inject(method = "putDistantHorizonsPaths", at = @At("HEAD"))
    private void keyframe$teeDistantHorizons(Map<String, File> paths, CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null) {
            shadow.putDistantHorizonsPaths(paths);
        }
    }

}
