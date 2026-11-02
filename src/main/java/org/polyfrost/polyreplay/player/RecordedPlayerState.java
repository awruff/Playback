package org.polyfrost.polyreplay.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
//? if >=26.3 {
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
//?}
//? if <26.3
//import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class RecordedPlayerState {

    private static PlayerStateAction.@Nullable Payload latest;
    private static ItemStack mainHandItem = ItemStack.EMPTY;
    private static ItemStack offHandItem = ItemStack.EMPTY;
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
        if (payload.mainHandItem() != null) {
            mainHandItem = payload.mainHandItem();
        }
        if (payload.offHandItem() != null) {
            offHandItem = payload.offHandItem();
        }
    }

    public static void reset() {
        latest = null;
        mainHandItem = ItemStack.EMPTY;
        offHandItem = ItemStack.EMPTY;
        guiTicks = -1;
    }

    public static void tickGuiTicks() {
        if (guiTicksJustReceived) {
            guiTicksJustReceived = false;
        } else if (guiTicks >= 0) {
            guiTicks++;
        }
    }

    public static int guiTicks(@Nullable AbstractClientPlayer player) {
        return of(player) != null ? guiTicks : -1;
    }

    public static PlayerStateAction.@Nullable Payload of(@Nullable AbstractClientPlayer player) {
        return player != null && latest != null && latest.entityId() == player.getId() ? latest : null;
    }

    //? if >=26.3 {
    public static void applyHands(AbstractClientPlayer player, FirstPersonHandsAndItemsRenderState hands) {
        PlayerStateAction.Payload state = of(player);
        if (state == null) {
            return;
        }
        hands.oldMainHandHeight = state.oMainHandHeight();
        hands.mainHandHeight = state.mainHandHeight();
        hands.oldOffHandHeight = state.oOffHandHeight();
        hands.offHandHeight = state.offHandHeight();

        Minecraft minecraft = Minecraft.getInstance();
        boolean isMainHandRight = player.getMainArm() == HumanoidArm.RIGHT;
        ItemDisplayContext mainHandContext = isMainHandRight ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        ItemDisplayContext offHandContext = isMainHandRight ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        hands.mainHandItem = mainHandItem;
        hands.mainHandRenderState.clear();
        minecraft.getItemModelResolver().updateForTopItem(hands.mainHandRenderState, mainHandItem, mainHandContext, player.level(), player, player.getId() + mainHandContext.ordinal());
        hands.mainHandUseDuration = mainHandItem.getUseDuration(player);
        hands.mainHandChargeDuration = CrossbowItem.getChargeDuration(mainHandItem, player);
        hands.mainHandSwapScale = minecraft.getItemModelResolver().swapAnimationScale(mainHandItem);
        hands.hasMainHandMapData = extractMap(minecraft, player, mainHandItem, hands.mainHandMapRenderState);

        hands.offHandItem = offHandItem;
        hands.offHandRenderState.clear();
        minecraft.getItemModelResolver().updateForTopItem(hands.offHandRenderState, offHandItem, offHandContext, player.level(), player, player.getId() + offHandContext.ordinal());
        hands.offHandUseDuration = offHandItem.getUseDuration(player);
        hands.offHandChargeDuration = CrossbowItem.getChargeDuration(offHandItem, player);
        hands.offHandSwapScale = minecraft.getItemModelResolver().swapAnimationScale(offHandItem);
        hands.hasOffHandMapData = extractMap(minecraft, player, offHandItem, hands.offHandMapRenderState);
    }

    private static boolean extractMap(Minecraft minecraft, AbstractClientPlayer player, ItemStack item, MapRenderState state) {
        MapId mapId = item.get(DataComponents.MAP_ID);
        MapItemSavedData mapData = mapId == null ? null : MapItem.getSavedData(mapId, player.level());
        if (mapData == null) {
            return false;
        }
        minecraft.getMapRenderer().extractRenderState(mapId, mapData, state);
        return true;
    }
    //?}

    //? if <26.3 {
    /*public static void applyHands(AbstractClientPlayer player, ItemInHandRenderer hands) {
        PlayerStateAction.Payload state = of(player);
        if (state == null) {
            return;
        }
        hands.oMainHandHeight = state.oMainHandHeight();
        hands.mainHandHeight = state.mainHandHeight();
        hands.oOffHandHeight = state.oOffHandHeight();
        hands.offHandHeight = state.offHandHeight();
        hands.mainHandItem = mainHandItem;
        hands.offHandItem = offHandItem;
    }
    *///?}

}
