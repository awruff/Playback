package org.polyfrost.keyframe.mixin.ornithe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.ActionNextTick;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.io.ReplayWriter;
import com.moulberry.flashback.record.Recorder;
import com.moulberry.flashback.record.ReplayMarker;
import net.minecraft.client.Minecraft;
import org.polyfrost.flashbacklegacy.shim.net.minecraft.core.RegistryAccess;
import org.polyfrost.flashbacklegacy.shim.net.minecraft.network.ConnectionProtocol;
import org.polyfrost.flashbacklegacy.shim.net.minecraft.network.protocol.Packet;
import org.polyfrost.flashbacklegacy.shim.net.minecraft.world.entity.Entity;
import org.polyfrost.keyframe.camera.CameraTrackAction;
import org.polyfrost.keyframe.player.PlayerStateAction;
import org.polyfrost.keyframe.shadow.ShadowRecorder;
import org.polyfrost.keyframe.shadow.ShadowReplay;
import org.polyfrost.keyframe.ui.ContainerScreenAction;
import org.polyfrost.keyframe.ui.ScreenFramesAction;
import org.polyfrost.keyframe.ui.UiSoundAction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.function.Consumer;

@Mixin(value = Recorder.class, remap = false)
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
    private volatile boolean closeForWriting;

    @Shadow
    private volatile boolean needsInitialSnapshot;

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
    private void keyframe$resetActions(RegistryAccess registryAccess, CallbackInfo ci) {
        CameraTrackAction.reset();
        PlayerStateAction.reset();
        ScreenFramesAction.reset();
        UiSoundAction.reset();
        ContainerScreenAction.reset();
    }

    @Inject(method = "endTick", at = @At("HEAD"))
    private void keyframe$writeMenuTick(boolean close, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        Recorder self = (Recorder) (Object) this;
        if (close || this.closeForWriting || self.isPaused() || self != Flashback.RECORDER || ShadowReplay.isShadow(self)
            || minecraft.world != null || minecraft.getNetworkHandler() != null) {
            return;
        }
        boolean chunkStart = this.writtenTicksInChunk == 0;
        ScreenFramesAction.write(chunkStart, this.asyncReplaySaver);
        UiSoundAction.write(chunkStart, this.asyncReplaySaver);
        this.asyncReplaySaver.submit(writer -> writer.startAndFinishAction(ActionNextTick.INSTANCE));
        this.writtenTicksInChunk++;
        this.writtenTicks++;
    }

    @Inject(method = "endTick", at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/record/Recorder;writeLocalData()V", shift = At.Shift.AFTER))
    private void keyframe$writeActions(boolean close, CallbackInfo ci) {
        if ((Object) this != Flashback.RECORDER || Minecraft.getInstance().world == null) {
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
        ScreenFramesAction.write(chunkStart, savers);
        UiSoundAction.write(chunkStart, savers);
        ContainerScreenAction.write(chunkStart, savers);
    }

    @WrapWithCondition(method = "endTick", at = @At(value = "INVOKE", target = "Lcom/moulberry/flashback/record/Recorder;writeAccurateFirstPersonPosition(I)V"))
    private boolean keyframe$skipFlashbackFirstPersonTrack(Recorder recorder, int localPlayerUpdatesPerSecond) {
        return false;
    }

    @ModifyExpressionValue(method = "writeLocalData", at = @At(value = "FIELD", target = "Lcom/moulberry/flashback/configuration/FlashbackConfigV1$SubcategoryRecording;recordHotbar:Z"))
    private boolean keyframe$alwaysRecordHotbar(boolean original) {
        return true;
    }

    @Inject(method = "endTick", at = @At("TAIL"))
    private void keyframe$endCameraTrackTick(boolean close, CallbackInfo ci) {
        if ((Object) this == Flashback.RECORDER) {
            CameraTrackAction.endTick();
        }
    }

    @Inject(method = "endTickWithContext", at = @At("RETURN"))
    private void keyframe$teeEndTick(boolean close, CallbackInfo ci) {
        Recorder shadow = ShadowReplay.teeTarget((Recorder) (Object) this);
        if (shadow != null && !close) {
            shadow.endTickWithContext(false);
        }
    }

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

    @Inject(method = "setRegistryAccess", at = @At("HEAD"))
    private void keyframe$teeRegistryAccess(RegistryAccess registryAccess, CallbackInfo ci) {
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

}
