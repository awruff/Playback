package org.polyfrost.playback.player;

import org.polyfrost.playback.Playback;

import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.Minecraft;
//? if >=26.3 {
import net.minecraft.client.player.FirstPersonHandsAndItems;
//?} else
//import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public class PlayerStateAction implements Action {

    public static final PlayerStateAction INSTANCE = new PlayerStateAction();
    private static final Identifier NAME = Identifier.fromNamespaceAndPath(Playback.MOD_ID, "action/player_state_optional");

    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<ItemStack>> OPTIONAL_ITEM = ByteBufCodecs.optional(ItemStack.OPTIONAL_STREAM_CODEC);

    private static @Nullable Payload lastWritten;
    private static @Nullable ItemStack lastMainHandItem;
    private static @Nullable ItemStack lastOffHandItem;
    private static int lastGuiTicks = Integer.MIN_VALUE;

    @Override
    public Identifier name() {
        return NAME;
    }

    @Override
    public void handle(ReplayServer replayServer, RegistryFriendlyByteBuf buf) {
        Playback.relay(replayServer, Payload.STREAM_CODEC.decode(buf));
    }

    public static void reset() {
        lastWritten = null;
        lastMainHandItem = null;
        lastOffHandItem = null;
        lastGuiTicks = Integer.MIN_VALUE;
    }

    public static void write(boolean chunkStart, AsyncReplaySaver... savers) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (chunkStart) {
            reset();
        }

        //? if >=26.3 {
        FirstPersonHandsAndItems hands = player.firstPersonHandsAndItems();
        //?} else
        //ItemInHandRenderer hands = minecraft.gameRenderer.itemInHandRenderer;
        ItemStack mainHandItem = changed(lastMainHandItem, hands.mainHandItem) ? hands.mainHandItem.copy() : null;
        ItemStack offHandItem = changed(lastOffHandItem, hands.offHandItem) ? hands.offHandItem.copy() : null;
        lastMainHandItem = hands.mainHandItem;
        lastOffHandItem = hands.offHandItem;

        float attackDelay = player.getCurrentItemAttackStrengthDelay();
        int attackTicker = Math.min(player.attackStrengthTicker, Mth.ceil(attackDelay));
        int guiTicks = minecraft.gui.hud.getGuiTicks();
        boolean guiTicksInStep = guiTicks == lastGuiTicks + 1;
        lastGuiTicks = guiTicks;

        Payload payload = new Payload(player.getId(), hands.oMainHandHeight, hands.mainHandHeight, hands.oOffHandHeight, hands.offHandHeight,
            mainHandItem, offHandItem, attackTicker, attackDelay, guiTicks);
        if (payload.equals(lastWritten) && guiTicksInStep && mainHandItem == null && offHandItem == null) {
            return;
        }
        lastWritten = payload;

        Playback.submit(INSTANCE, buf -> Payload.STREAM_CODEC.encode(buf, payload), savers);
    }

    private static boolean changed(@Nullable ItemStack last, ItemStack current) {
        return last == null || !ItemStack.matches(last, current);
    }

    public record Payload(int entityId, float oMainHandHeight, float mainHandHeight, float oOffHandHeight, float offHandHeight,
                          @Nullable ItemStack mainHandItem, @Nullable ItemStack offHandItem, int attackTicker, float attackDelay,
                          int guiTicks) implements CustomPacketPayload {

        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Playback.MOD_ID, "player_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of(Payload::encode, Payload::decode);

        @Override
        public Type<Payload> type() {
            return TYPE;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Payload other && other.entityId == this.entityId && other.oMainHandHeight == this.oMainHandHeight
                && other.mainHandHeight == this.mainHandHeight && other.oOffHandHeight == this.oOffHandHeight && other.offHandHeight == this.offHandHeight
                && other.attackTicker == this.attackTicker && other.attackDelay == this.attackDelay;
        }

        @Override
        public int hashCode() {
            return this.entityId;
        }

        private static void encode(RegistryFriendlyByteBuf buf, Payload payload) {
            buf.writeVarInt(payload.entityId);
            buf.writeByte(Math.round(payload.oMainHandHeight * 255.0f));
            buf.writeByte(Math.round(payload.mainHandHeight * 255.0f));
            buf.writeByte(Math.round(payload.oOffHandHeight * 255.0f));
            buf.writeByte(Math.round(payload.offHandHeight * 255.0f));
            OPTIONAL_ITEM.encode(buf, Optional.ofNullable(payload.mainHandItem));
            OPTIONAL_ITEM.encode(buf, Optional.ofNullable(payload.offHandItem));
            buf.writeVarInt(payload.attackTicker);
            buf.writeFloat(payload.attackDelay);
            buf.writeByte(0);
            buf.writeVarInt(payload.guiTicks);
        }

        private static Payload decode(RegistryFriendlyByteBuf buf) {
            int entityId = buf.readVarInt();
            float oMainHandHeight = buf.readUnsignedByte() / 255.0f;
            float mainHandHeight = buf.readUnsignedByte() / 255.0f;
            float oOffHandHeight = buf.readUnsignedByte() / 255.0f;
            float offHandHeight = buf.readUnsignedByte() / 255.0f;
            ItemStack mainHandItem = OPTIONAL_ITEM.decode(buf).orElse(null);
            ItemStack offHandItem = OPTIONAL_ITEM.decode(buf).orElse(null);
            int attackTicker = buf.readVarInt();
            float attackDelay = buf.readFloat();
            buf.readByte();
            return new Payload(entityId, oMainHandHeight, mainHandHeight, oOffHandHeight, offHandHeight, mainHandItem, offHandItem, attackTicker, attackDelay, buf.readVarInt());
        }
    }

}
