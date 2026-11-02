package org.polyfrost.playback.player;

import org.polyfrost.playback.ui.ScreenCapture;
import org.polyfrost.playback.ui.ScreenFramesAction;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStatePayloadTest {

    @Test
    void roundTripsPlayerState() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();

        var payload = new PlayerStateAction.Payload(42, 0.2f, 0.6f, 1.0f, 1.0f, null, ItemStack.EMPTY, 7, 12.5f, 123456);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        PlayerStateAction.Payload.STREAM_CODEC.encode(buf, payload);
        PlayerStateAction.Payload decoded = PlayerStateAction.Payload.STREAM_CODEC.decode(buf);

        assertEquals(0, buf.readableBytes());
        assertEquals(payload, decoded);
        assertEquals(0.6f, decoded.mainHandHeight(), 1.0f / 255.0f);
        assertNull(decoded.mainHandItem());
        assertTrue(decoded.offHandItem().isEmpty());
        assertEquals(123456, decoded.guiTicks());
    }

    @Test
    void roundTripsScreenFrames() {
        byte[] ops = {1, 2, 3, 4};
        var frames = List.of(
            new ScreenCapture.Frame(0.25f, 120.5f, -4.125f, 427, 240, 2, true, ops),
            new ScreenCapture.Frame(0.75f, 121.0f, -4.0f, 427, 240, 2, false, null));
        var payload = new ScreenFramesAction.Payload(true, frames);

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ScreenFramesAction.Payload.STREAM_CODEC.encode(buf, payload);
        ScreenFramesAction.Payload decoded = ScreenFramesAction.Payload.STREAM_CODEC.decode(buf);

        assertEquals(0, buf.readableBytes());
        assertTrue(decoded.open());
        assertEquals(2, decoded.frames().size());
        ScreenCapture.Frame first = decoded.frames().get(0);
        assertEquals(0.25f, first.partial(), 1.0f / 255.0f);
        assertEquals(120.5f, first.mouseX(), 1.0f / 8.0f);
        assertEquals(-4.125f, first.mouseY(), 1.0f / 8.0f);
        assertEquals(427, first.guiWidth());
        assertEquals(2, first.guiScale());
        assertTrue(first.chat());
        assertArrayEquals(ops, first.ops());
        assertFalse(decoded.frames().get(1).chat());
        assertNull(decoded.frames().get(1).ops());
    }

    @Test
    void roundTripsClosedScreen() {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        ScreenFramesAction.Payload.STREAM_CODEC.encode(buf, new ScreenFramesAction.Payload(false, List.of()));
        ScreenFramesAction.Payload decoded = ScreenFramesAction.Payload.STREAM_CODEC.decode(buf);

        assertEquals(0, buf.readableBytes());
        assertFalse(decoded.open());
    }

}
