package org.polyfrost.keyframe.player;

import com.moulberry.flashback.action.Action;
import com.moulberry.flashback.io.AsyncReplaySaver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.living.player.LocalClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import org.jspecify.annotations.Nullable;
import org.polyfrost.keyframe.Keyframe;
import org.polyfrost.keyframe.mixin.ornithe.ItemInHandRendererAccessor;

import java.io.IOException;
import java.io.UncheckedIOException;

public class PlayerStateAction {

    private static @Nullable Action action;

    private static @Nullable Payload lastWritten;
    private static @Nullable ItemStack lastItem;
    private static boolean itemRecorded;
    private static int lastGuiTicks = Integer.MIN_VALUE;

    public static void register() {
        action = Keyframe.action("action/player_state_optional", buf -> RecordedPlayerState.accept(Payload.decode(buf)));
    }

    public static void reset() {
        lastWritten = null;
        lastItem = null;
        itemRecorded = false;
        lastGuiTicks = Integer.MIN_VALUE;
    }

    public static void write(boolean chunkStart, AsyncReplaySaver... savers) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalClientPlayerEntity player = minecraft.player;
        if (player == null || action == null) {
            return;
        }
        if (chunkStart) {
            reset();
        }

        ItemInHandRendererAccessor hands = (ItemInHandRendererAccessor) minecraft.gameRenderer.itemInHandRenderer;
        ItemStack current = hands.keyframe$getItemInHand();
        boolean itemChanged = !itemRecorded || !ItemStack.matches(lastItem, current);
        ItemStack item = current == null ? null : current.copy();
        itemRecorded = true;
        lastItem = item;

        int guiTicks = minecraft.gui.getTicks();
        boolean guiTicksInStep = guiTicks == lastGuiTicks + 1;
        lastGuiTicks = guiTicks;

        Payload payload = new Payload(player.getNetworkId(), hands.keyframe$getLastHandHeight(), hands.keyframe$getHandHeight(), itemChanged, item, guiTicks);
        if (payload.sameHands(lastWritten) && guiTicksInStep && !itemChanged) {
            return;
        }
        lastWritten = payload;

        Keyframe.submit(action, payload::encode, savers);
    }

    public record Payload(int entityId, float oHandHeight, float handHeight, boolean itemChanged, @Nullable ItemStack item, int guiTicks) {

        boolean sameHands(@Nullable Payload other) {
            return other != null && other.entityId == this.entityId && other.oHandHeight == this.oHandHeight && other.handHeight == this.handHeight;
        }

        private void encode(PacketByteBuf buf) {
            buf.writeVarInt(this.entityId);
            buf.writeByte(Math.round(this.oHandHeight * 255.0f));
            buf.writeByte(Math.round(this.handHeight * 255.0f));
            buf.writeBoolean(this.itemChanged);
            if (this.itemChanged) {
                buf.writeItem(this.item);
            }
            buf.writeVarInt(this.guiTicks);
        }

        private static Payload decode(PacketByteBuf buf) {
            int entityId = buf.readVarInt();
            float oHandHeight = buf.readUnsignedByte() / 255.0f;
            float handHeight = buf.readUnsignedByte() / 255.0f;
            boolean itemChanged = buf.readBoolean();
            ItemStack item;
            try {
                item = itemChanged ? buf.readItem() : null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return new Payload(entityId, oHandHeight, handHeight, itemChanged, item, buf.readVarInt());
        }
    }

}
