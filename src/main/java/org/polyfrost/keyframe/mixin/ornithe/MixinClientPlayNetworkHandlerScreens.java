package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.network.handler.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryMenuDataS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenInventoryMenuS2CPacket;
import org.polyfrost.keyframe.ui.ContainerScreenAction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MixinClientPlayNetworkHandlerScreens {

    @Inject(method = "handleOpenInventoryMenu", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/PacketUtils;ensureOnSameThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/handler/PacketHandler;Lnet/minecraft/util/BlockableEventLoop;)V", shift = At.Shift.AFTER))
    private void keyframe$trackMenu(OpenInventoryMenuS2CPacket packet, CallbackInfo ci) {
        ContainerScreenAction.onOpenMenu(packet.getMenuId(), packet.getMenuType(), packet.getDisplayName(), packet.getSize());
    }

    @Inject(method = "handleInventoryMenuData", at = @At(value = "INVOKE", target = "Lnet/minecraft/inventory/menu/InventoryMenu;setData(II)V"))
    private void keyframe$trackMenuData(InventoryMenuDataS2CPacket packet, CallbackInfo ci) {
        ContainerScreenAction.onMenuData(packet.getMenuId(), packet.getDataId(), packet.getValue());
    }

}
