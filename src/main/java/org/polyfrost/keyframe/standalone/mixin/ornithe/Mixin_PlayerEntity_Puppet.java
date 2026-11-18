package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.entity.living.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerEntity.class)
public interface Mixin_PlayerEntity_Puppet {
	@Accessor("itemInUse")
	void keyframe$setItemInUse(ItemStack item);

	@Accessor("itemUseTimer")
	void keyframe$setItemUseTimer(int ticks);
}
