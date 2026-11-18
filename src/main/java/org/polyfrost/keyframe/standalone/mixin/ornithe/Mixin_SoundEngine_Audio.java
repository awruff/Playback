package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.sound.system.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEngine.class)
public interface Mixin_SoundEngine_Audio {
	@Accessor("started")
	boolean keyframe$isStarted();
}
