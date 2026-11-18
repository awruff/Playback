package org.polyfrost.keyframe.standalone.mixin;

//? if >=26.3 {
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.SwingState.class)
public interface Mixin_SwingState_Puppet {
	@Accessor("currentSwing")
	void keyframe$setCurrentSwing(LivingEntity.SwingDescription swing);

	@Accessor("ticks")
	int keyframe$getTicks();

	@Accessor("ticks")
	void keyframe$setTicks(int value);

	@Accessor("oldAnimation")
	float keyframe$getOldAnimation();

	@Accessor("oldAnimation")
	void keyframe$setOldAnimation(float value);

	@Accessor("animation")
	float keyframe$getAnimation();

	@Accessor("animation")
	void keyframe$setAnimation(float value);
}
//?}
