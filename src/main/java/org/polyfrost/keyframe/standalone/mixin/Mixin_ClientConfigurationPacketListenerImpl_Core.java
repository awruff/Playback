package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import java.util.List;
import net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl;
import net.minecraft.server.packs.repository.KnownPack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientConfigurationPacketListenerImpl.class)
abstract class Mixin_ClientConfigurationPacketListenerImpl_Core {
	@ModifyExpressionValue(
		method = "handleSelectKnownPacks",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/KnownPacksManager;trySelectingPacks(Ljava/util/List;)Ljava/util/List;")
	)
	private List<KnownPack> keyframe$declineKnownPacks(List<KnownPack> selected) {
		return List.of();
	}
}
