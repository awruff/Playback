package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.entity.living.effect.StatusEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StatusEffectInstance.class)
public interface Mixin_StatusEffectInstance_Puppet {
	@Accessor("duration")
	void keyframe$setDuration(int duration);
}
