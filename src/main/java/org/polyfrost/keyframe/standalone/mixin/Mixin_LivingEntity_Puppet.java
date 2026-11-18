package org.polyfrost.keyframe.standalone.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LivingEntity.class)
public interface Mixin_LivingEntity_Puppet {
	//? if >=26.3 {
	@Accessor("swingState")
	LivingEntity.SwingState keyframe$getSwingState();
	//?}

	@Accessor("useItemRemaining")
	void keyframe$setUseItemRemaining(int ticks);

	@Accessor("attackStrengthTicker")
	int keyframe$getAttackStrengthTicker();

	@Accessor("attackStrengthTicker")
	void keyframe$setAttackStrengthTicker(int ticks);

	//? if >=1.21.11 {
	@Accessor("itemSwapTicker")
	int keyframe$getItemSwapTicker();

	@Accessor("itemSwapTicker")
	void keyframe$setItemSwapTicker(int ticks);
	//?}
}
