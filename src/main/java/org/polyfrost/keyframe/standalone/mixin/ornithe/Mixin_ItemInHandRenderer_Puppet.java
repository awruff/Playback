package org.polyfrost.keyframe.standalone.mixin.ornithe;

import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemInHandRenderer.class)
public interface Mixin_ItemInHandRenderer_Puppet {
	@Accessor("itemInHand")
	ItemStack keyframe$getItemInHand();

	@Accessor("itemInHand")
	void keyframe$setItemInHand(ItemStack value);

	@Accessor("handHeight")
	float keyframe$getHandHeight();

	@Accessor("handHeight")
	void keyframe$setHandHeight(float value);

	@Accessor("lastHandHeight")
	float keyframe$getLastHandHeight();

	@Accessor("lastHandHeight")
	void keyframe$setLastHandHeight(float value);
}
