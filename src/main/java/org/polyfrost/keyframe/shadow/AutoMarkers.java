package org.polyfrost.keyframe.shadow;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import com.moulberry.flashback.record.ReplayMarker;
import kotlin.Unit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.polyfrost.keyframe.standalone.recording.HighlightDetector;

public class AutoMarkers {

    private static final HighlightDetector detector = new HighlightDetector((kind, now) -> {
        Recorder recorder = Flashback.RECORDER;
        if (recorder != null && !Flashback.isInReplay()) {
            recorder.addMarker(new ReplayMarker(kind.getColour(), null, kind.getLabel()));
            ShadowReplay.highlight(kind.getLabel());
        }
        return Unit.INSTANCE;
    });
    private static int ticks;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> {
            ticks++;
            ClientLevel level = minecraft.level;
            detector.tick(ticks,
                entity -> level != null && level.getEntity(entity) instanceof Player player && player.isDeadOrDying(),
                pos -> level == null || level.getBlockState(BlockPos.of(pos)).is(BlockTags.BEDS));
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && entity instanceof Player) {
                detector.attacked(entity.getId(), ticks);
            }
            return InteractionResult.PASS;
        });
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
            if (state.is(BlockTags.BEDS)) {
                detector.bedBroken(pos.asLong(), ticks);
            }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            Player self = Minecraft.getInstance().player;
            if (!overlay && self != null) {
                detector.message(message.getString(), self.getName().getString(), ticks);
            }
        });
    }

    public static void onDamage(int entityId) {
        Player self = Minecraft.getInstance().player;
        if (self != null) {
            detector.damaged(entityId, self.getId(), ticks);
        }
    }

}
