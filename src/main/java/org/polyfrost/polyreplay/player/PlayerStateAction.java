package org.polyfrost.polyreplay.player;

import org.polyfrost.polyreplay.FirstPersonEffects;

import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.Minecraft;
//? if >=26.3 {
import net.minecraft.client.player.FirstPersonHandsAndItems;
//?} else
//import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class PlayerStateAction implements Action {

    public static final PlayerStateAction INSTANCE = new PlayerStateAction();
    private static final Identifier NAME = Identifier.fromNamespaceAndPath(FirstPersonEffects.MOD_ID, "action/player_state_optional");

    private static final byte SCREEN_UNCHANGED = 0;
    private static final byte SCREEN_OPENED = 2;
    private static final byte SCREEN_UPDATED = 3;

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
        Payload payload = Payload.STREAM_CODEC.decode(buf);
        for (ReplayPlayer viewer : replayServer.getReplayViewers()) {
            ServerPlayNetworking.send(viewer, payload);
        }
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
        if (player == null || minecraft.level == null) {
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

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), minecraft.level.registryAccess());
        Payload.STREAM_CODEC.encode(buf, payload);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        for (AsyncReplaySaver saver : savers) {
            saver.submit(writer -> {
                writer.startAction(INSTANCE);
                writer.friendlyByteBuf().writeBytes(bytes);
                writer.finishAction(INSTANCE);
            });
        }
    }

    private static boolean changed(@Nullable ItemStack last, ItemStack current) {
        return last == null || !ItemStack.matches(last, current);
    }

    public record Payload(int entityId, float oMainHandHeight, float mainHandHeight, float oOffHandHeight, float offHandHeight,
                          @Nullable ItemStack mainHandItem, @Nullable ItemStack offHandItem, int attackTicker, float attackDelay,
                          int guiTicks) implements CustomPacketPayload {

        public static final Type<Payload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(FirstPersonEffects.MOD_ID, "player_state"));
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
            writeOptionalItem(buf, payload.mainHandItem);
            writeOptionalItem(buf, payload.offHandItem);
            buf.writeVarInt(payload.attackTicker);
            buf.writeFloat(payload.attackDelay);
            buf.writeByte(SCREEN_UNCHANGED);
            buf.writeVarInt(payload.guiTicks);
        }

        private static Payload decode(RegistryFriendlyByteBuf buf) {
            int entityId = buf.readVarInt();
            float oMainHandHeight = buf.readUnsignedByte() / 255.0f;
            float mainHandHeight = buf.readUnsignedByte() / 255.0f;
            float oOffHandHeight = buf.readUnsignedByte() / 255.0f;
            float offHandHeight = buf.readUnsignedByte() / 255.0f;
            ItemStack mainHandItem = readOptionalItem(buf);
            ItemStack offHandItem = readOptionalItem(buf);
            int attackTicker = buf.readVarInt();
            float attackDelay = buf.readFloat();

            byte screenMode = buf.readByte();
            if (screenMode == SCREEN_OPENED) {
                buf.readByte();
                buf.readNullable(b -> b.readIdentifier());
                ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf);
            }
            if (screenMode == SCREEN_OPENED || screenMode == SCREEN_UPDATED) {
                skipLegacyScreen(buf);
            }
            int guiTicks = buf.isReadable() ? buf.readVarInt() : -1;
            return new Payload(entityId, oMainHandHeight, mainHandHeight, oOffHandHeight, offHandHeight, mainHandItem, offHandItem, attackTicker, attackDelay, guiTicks);
        }

        private static void skipLegacyScreen(RegistryFriendlyByteBuf buf) {
            buf.readNullable(b -> {
                b.readNullable(b2 -> b2.readIdentifier());
                b.readFloat();
                b.readUtf();
                b.readVarInt();
                return b.readVarInt();
            });
            int slotCount = buf.readVarInt();
            for (int i = 0; i < slotCount; i++) {
                buf.readVarInt();
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
            }
            int dataCount = buf.readVarInt();
            for (int i = 0; i < dataCount; i++) {
                buf.readVarInt();
                buf.readVarInt();
            }
            readOptionalItem(buf);
            int cursorCount = buf.readVarInt();
            for (int i = 0; i < cursorCount; i++) {
                buf.readUnsignedByte();
                buf.readShort();
                buf.readShort();
            }
        }

        private static void writeOptionalItem(RegistryFriendlyByteBuf buf, @Nullable ItemStack item) {
            buf.writeBoolean(item != null);
            if (item != null) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, item);
            }
        }

        private static @Nullable ItemStack readOptionalItem(RegistryFriendlyByteBuf buf) {
            return buf.readBoolean() ? ItemStack.OPTIONAL_STREAM_CODEC.decode(buf) : null;
        }
    }

}
