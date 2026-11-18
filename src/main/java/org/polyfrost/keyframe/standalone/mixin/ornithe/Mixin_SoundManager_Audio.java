package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.sound.system.SoundEngine;
import net.minecraft.client.sound.system.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundManager.class)
public interface Mixin_SoundManager_Audio {
	@Accessor("engine")
	SoundEngine keyframe$getEngine();
}
