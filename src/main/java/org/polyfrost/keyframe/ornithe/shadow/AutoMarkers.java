package org.polyfrost.keyframe.shadow;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import com.moulberry.flashback.record.ReplayMarker;
import kotlin.Unit;
import net.minecraft.block.BedBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.text.Formatting;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
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

    public static void onAttack(Entity entity) {
        if (entity instanceof PlayerEntity) {
            detector.attacked(entity.getNetworkId(), ticks);
        }
    }

    public static void onBreak(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.world != null && minecraft.world.getBlockState(pos).getBlock() instanceof BedBlock) {
            detector.bedBroken(pos.toLong(), ticks);
        }
    }

    public static void onDamage(int entityId) {
        PlayerEntity self = Minecraft.getInstance().player;
        if (self != null) {
            detector.damaged(entityId, self.getNetworkId(), ticks);
        }
    }

    public static void tick() {
        ticks++;
        ClientWorld world = Minecraft.getInstance().world;
        detector.tick(ticks,
            entity -> world != null && world.getEntity(entity) instanceof PlayerEntity player && player.getHealth() <= 0.0F,
            pos -> world == null || world.getBlockState(BlockPos.fromLong(pos)).getBlock() instanceof BedBlock);
    }

    public static void onSystemMessage(Text message) {
        PlayerEntity self = Minecraft.getInstance().player;
        if (self != null) {
            detector.message(Formatting.strip(message.getString()), self.getName(), ticks);
        }
    }

}
