package org.polyfrost.keyframe.player;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.polyfrost.keyframe.mixin.ornithe.ItemInHandRendererAccessor;

public class RecordedPlayerState {

    private static PlayerStateAction.@Nullable Payload latest;
    private static @Nullable ItemStack item;
    private static int guiTicks = -1;
    private static boolean guiTicksJustReceived;

    public static void accept(PlayerStateAction.Payload payload) {
        if (latest == null || latest.entityId() != payload.entityId()) {
            reset();
        }
        latest = payload;
        if (payload.guiTicks() >= 0) {
            guiTicks = payload.guiTicks();
            guiTicksJustReceived = true;
        }
        if (payload.itemChanged()) {
            item = payload.item();
        }
    }

    public static void reset() {
        latest = null;
        item = null;
        guiTicks = -1;
    }

    public static void tickGuiTicks() {
        if (guiTicksJustReceived) {
            guiTicksJustReceived = false;
        } else if (guiTicks >= 0) {
            guiTicks++;
        }
    }

    public static int guiTicks(@Nullable Entity player) {
        return of(player) != null ? guiTicks : -1;
    }

    public static PlayerStateAction.@Nullable Payload of(@Nullable Entity player) {
        return player != null && latest != null && latest.entityId() == player.getNetworkId() ? latest : null;
    }

    public static void applyHands(Entity player, ItemInHandRendererAccessor hands) {
        PlayerStateAction.Payload state = of(player);
        if (state == null) {
            return;
        }
        hands.keyframe$setLastHandHeight(state.oHandHeight());
        hands.keyframe$setHandHeight(state.handHeight());
        hands.keyframe$setItemInHand(item);
    }

}
