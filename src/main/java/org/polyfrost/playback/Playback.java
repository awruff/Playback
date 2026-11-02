package org.polyfrost.playback;

import org.polyfrost.playback.camera.CameraTrackAction;
import org.polyfrost.playback.player.PlayerStateAction;
import org.polyfrost.playback.player.RecordedPlayerState;
import org.polyfrost.playback.shadow.AutoMarkers;
import org.polyfrost.playback.shadow.ShadowReplay;
import org.polyfrost.playback.shadow.ShadowReplayConfig;
import org.polyfrost.playback.ui.ContainerScreenAction;
//? if >=26.3
import org.polyfrost.playback.ui.GuiMeshCapture;
import org.polyfrost.playback.ui.ScreenFramesAction;
import org.polyfrost.playback.ui.ScreenReplay;
import org.polyfrost.playback.ui.UiSoundAction;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.action.ActionRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.player.AbstractClientPlayer;
//? if >=26.3
import net.minecraft.client.player.ItemActivation;
//? if >=1.21.2
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jspecify.annotations.Nullable;

public class Playback implements ClientModInitializer {

    public static final String MOD_ID = "playback";
    //? if >=26.3
    public static final ItemActivation ITEM_ACTIVATION = new ItemActivation();

    private static @Nullable AbstractClientPlayer tracked;
    private static float portalIntensity;
    private static float oPortalIntensity;
    //? if >=26.3 {
    private static float spinningTime;
    private static float spinningSpeed;
    //?}

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> tick());
        AutoMarkers.register();
        ShadowReplay.register();
        ShadowReplayConfig.INSTANCE.preload();
        ActionRegistry.register(CameraTrackAction.INSTANCE);
        ActionRegistry.register(PlayerStateAction.INSTANCE);
        ActionRegistry.register(ScreenFramesAction.INSTANCE);
        PayloadTypeRegistry.clientboundPlay().register(ScreenFramesAction.Payload.TYPE, ScreenFramesAction.Payload.STREAM_CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ScreenFramesAction.Payload.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                ScreenReplay.accept(payload);
            }
        });
        ActionRegistry.register(ContainerScreenAction.INSTANCE);
        PayloadTypeRegistry.clientboundPlay().register(ContainerScreenAction.Payload.TYPE, ContainerScreenAction.Payload.STREAM_CODEC);
        ClientPlayNetworking.registerGlobalReceiver(ContainerScreenAction.Payload.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                ContainerScreenAction.accept(payload);
            }
        });
        //? if >=26.3 {
        ActionRegistry.register(GuiMeshCapture.INSTANCE);
        PayloadTypeRegistry.clientboundPlay().register(GuiMeshCapture.Payload.TYPE, GuiMeshCapture.Payload.STREAM_CODEC);
        ClientPlayNetworking.registerGlobalReceiver(GuiMeshCapture.Payload.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                GuiMeshCapture.accept(payload);
            }
        });
        //?}
        ActionRegistry.register(UiSoundAction.INSTANCE);
        PayloadTypeRegistry.clientboundPlay().register(UiSoundAction.Payload.TYPE, UiSoundAction.Payload.STREAM_CODEC);
        ClientPlayNetworking.registerGlobalReceiver(UiSoundAction.Payload.TYPE, (payload, context) -> UiSoundAction.play(payload));
        PayloadTypeRegistry.clientboundPlay().register(PlayerStateAction.Payload.TYPE, PlayerStateAction.Payload.STREAM_CODEC);
        ClientPlayNetworking.registerGlobalReceiver(PlayerStateAction.Payload.TYPE, (payload, context) -> {
            if (Flashback.isInReplay()) {
                RecordedPlayerState.accept(payload);
            }
        });
        PayloadTypeRegistry.clientboundPlay().register(CameraTrackAction.Flying.TYPE, CameraTrackAction.Flying.STREAM_CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CameraTrackAction.Flying.TYPE, (payload, context) -> {
            if (Flashback.isInReplay() && context.client().level != null && context.client().level.getEntity(payload.entityId()) instanceof Player player) {
                player.getAbilities().flying = payload.flying();
            }
        });
    }

    public static @Nullable AbstractClientPlayer spectated() {
        AbstractClientPlayer player = Flashback.getSpectatingPlayer();
        if (player != tracked) {
            tracked = player;
            portalIntensity = 0.0f;
            oPortalIntensity = 0.0f;
            //? if >=26.3 {
            spinningTime = 0.0f;
            spinningSpeed = 0.0f;
            ITEM_ACTIVATION.reset();
            //?}
        }
        return player;
    }

    private static void tick() {
        if (!Flashback.isInReplay()) {
            RecordedPlayerState.reset();
            ScreenReplay.reset();
            ContainerScreenAction.clearReplay();
            UiSoundAction.clearReplay();
            //? if >=26.3
            GuiMeshCapture.clearReplay();
        }
        AbstractClientPlayer player = spectated();
        if (player == null || player.level().tickRateManager().isEntityFrozen(player)) {
            return;
        }

        boolean inPortal = player.level().getBlockStatesIfLoaded(player.getBoundingBox().deflate(1.0E-5F)).anyMatch(state -> state.is(Blocks.NETHER_PORTAL));
        if (inPortal && portalIntensity == 0.0f) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forLocalAmbience(SoundEvents.PORTAL_TRIGGER, player.getRandom().nextFloat() * 0.4f + 0.8f, 0.25f));
        }
        //? if >=1.21.8 {
        float step = inPortal ? 0.0125f : portalIntensity > 0.0f ? -0.05f : 0.0f;
        //?} else {
        /*boolean confused = player.hasEffect(MobEffects.CONFUSION) && !player.getEffect(MobEffects.CONFUSION).endsWithin(60);
        float step = inPortal ? 0.0125f : confused ? 0.006666667f : portalIntensity > 0.0f ? -0.05f : 0.0f;
        *///?}
        oPortalIntensity = portalIntensity;
        portalIntensity = Math.clamp(portalIntensity + step, 0.0f, 1.0f);

        //? if >=26.3 {
        float nauseaIntensity = player.getEffectBlendFactor(MobEffects.NAUSEA, 1.0f);
        if (portalIntensity > 0.0f || nauseaIntensity > 0.0f) {
            spinningSpeed = (portalIntensity * 20.0f + nauseaIntensity * 7.0f) / (portalIntensity + nauseaIntensity);
            spinningTime += spinningSpeed;
        } else {
            spinningSpeed = 0.0f;
        }

        ITEM_ACTIVATION.tick();
        //?}
        RecordedPlayerState.tickGuiTicks();
    }

    public static float portalIntensity(float partialTick) {
        return Mth.lerp(partialTick, oPortalIntensity, portalIntensity);
    }

    public static float oPortalIntensity() {
        return oPortalIntensity;
    }

    public static float portalIntensity() {
        return portalIntensity;
    }

    //? if >=26.3 {
    public static float spinningAngle(float partialTick) {
        return spinningTime + partialTick * spinningSpeed;
    }
    //?}

    public static void activateTotem(AbstractClientPlayer player) {
        ItemStack totem = new ItemStack(Items.TOTEM_OF_UNDYING);
        for (InteractionHand hand : InteractionHand.values()) {
            //? if >=1.21.2 {
            if (player.getItemInHand(hand).has(DataComponents.DEATH_PROTECTION)) {
            //?} else {
            /*if (player.getItemInHand(hand).is(Items.TOTEM_OF_UNDYING)) {
            *///?}
                totem = player.getItemInHand(hand);
                break;
            }
        }
        //? if >=26.3 {
        ITEM_ACTIVATION.activate(totem, player.getRandom());
        //?} else
        //Minecraft.getInstance().gameRenderer.displayItemActivation(totem);
    }

}
