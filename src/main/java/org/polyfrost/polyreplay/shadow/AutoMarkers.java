package org.polyfrost.polyreplay.shadow;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import com.moulberry.flashback.record.ReplayMarker;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class AutoMarkers {

    private static final int COMBO_HITS = 5;
    private static final int HIT_CONFIRM_TICKS = 10;
    private static final int COMBO_TIMEOUT_TICKS = 60;
    private static final int KILL_CREDIT_TICKS = 100;
    private static final int DUPLICATE_TICKS = 40;
    private static final int BREAK_CONFIRM_TICKS = 10;

    private static final Map<String, Integer> lastMarked = new HashMap<>();
    private static final Map<Integer, Integer> hitPlayers = new HashMap<>();
    private static final Map<BlockPos, Integer> brokenBeds = new HashMap<>();
    private static int ticks;
    private static int target = -1;
    private static int attackTick;
    private static int hitTick = Integer.MIN_VALUE / 2;
    private static int combo;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(AutoMarkers::tick);
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() && entity instanceof Player) {
                target = entity.getId();
                attackTick = ticks;
            }
            return InteractionResult.PASS;
        });
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> {
            if (state.is(BlockTags.BEDS)) {
                brokenBeds.put(pos.immutable(), ticks);
            }
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                onSystemMessage(message);
            }
        });
    }

    public static void onDamage(int entityId) {
        Player self = Minecraft.getInstance().player;
        if (self != null && entityId == self.getId()) {
            combo = 0;
            return;
        }
        if (entityId != target || ticks - attackTick > HIT_CONFIRM_TICKS) {
            return;
        }
        if (ticks - hitTick > COMBO_TIMEOUT_TICKS) {
            combo = 0;
        }
        hitTick = ticks;
        hitPlayers.put(entityId, ticks);
        if (++combo == COMBO_HITS) {
            mark(0xFFAA00, "Combo");
        }
    }

    private static void tick(Minecraft minecraft) {
        ticks++;
        hitPlayers.entrySet().removeIf(hit -> {
            if (ticks - hit.getValue() > KILL_CREDIT_TICKS || minecraft.level == null) {
                return true;
            }
            if (minecraft.level.getEntity(hit.getKey()) instanceof Player player && player.isDeadOrDying()) {
                mark(0xFF5555, "Kill");
                return true;
            }
            return false;
        });
        brokenBeds.entrySet().removeIf(broken -> {
            if (minecraft.level == null) {
                return true;
            }
            if (ticks - broken.getValue() < BREAK_CONFIRM_TICKS) {
                return false;
            }
            if (!minecraft.level.getBlockState(broken.getKey()).is(BlockTags.BEDS)) {
                mark(0x55FFFF, "Bed broken");
            }
            return true;
        });
    }

    private static void onSystemMessage(Component message) {
        Player self = Minecraft.getInstance().player;
        if (self == null) {
            return;
        }
        String text = message.getString();
        String name = self.getName().getString();
        if (text.contains(":") || text.startsWith(name) || !Pattern.compile("\\bby " + Pattern.quote(name) + "\\b").matcher(text).find()) {
            return;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("bed")) {
            mark(0x55FFFF, "Bed broken");
        } else if (lower.contains("final kill")) {
            mark(0xFF55FF, "Final kill");
        } else {
            mark(0xFF5555, "Kill");
        }
    }

    private static void mark(int colour, String description) {
        Recorder recorder = Flashback.RECORDER;
        if (recorder == null || Flashback.isInReplay()) {
            return;
        }
        String kind = description.equals("Final kill") ? "Kill" : description;
        Integer last = lastMarked.get(kind);
        if (last != null && ticks - last < DUPLICATE_TICKS) {
            return;
        }
        lastMarked.put(kind, ticks);
        recorder.addMarker(new ReplayMarker(colour, null, description));
        ShadowReplay.highlight(description);
    }

}
