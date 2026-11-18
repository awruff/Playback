package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ConnectScreen.class)
abstract class Mixin_ConnectScreen_Core {
	@WrapMethod(method = "startConnecting")
	private static void keyframe$gateConnect(Screen parent, Minecraft minecraft, ServerAddress address, ServerData data, boolean quickPlay, @Nullable TransferState transfer, Operation<Void> original) {
		if (!Keyframe.INSTANCE.getActive()) {
			original.call(parent, minecraft, address, data, quickPlay, transfer);
		}
	}
}
