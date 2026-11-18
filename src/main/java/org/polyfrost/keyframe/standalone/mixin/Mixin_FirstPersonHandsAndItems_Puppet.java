package org.polyfrost.keyframe.standalone.mixin;

//? if >=26.3 {
import net.minecraft.client.player.FirstPersonHandsAndItems;
//?} else
/*import net.minecraft.client.renderer.ItemInHandRenderer;*/
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if >=26.3 {
@Mixin(FirstPersonHandsAndItems.class)
//?} else
/*@Mixin(ItemInHandRenderer.class)*/
public interface Mixin_FirstPersonHandsAndItems_Puppet {
	@Accessor("mainHandItem")
	ItemStack keyframe$getMainHandItem();

	@Accessor("mainHandItem")
	void keyframe$setMainHandItem(ItemStack value);

	@Accessor("offHandItem")
	ItemStack keyframe$getOffHandItem();

	@Accessor("offHandItem")
	void keyframe$setOffHandItem(ItemStack value);

	@Accessor("mainHandHeight")
	float keyframe$getMainHandHeight();

	@Accessor("mainHandHeight")
	void keyframe$setMainHandHeight(float value);

	@Accessor("oMainHandHeight")
	float keyframe$getOMainHandHeight();

	@Accessor("oMainHandHeight")
	void keyframe$setOMainHandHeight(float value);

	@Accessor("offHandHeight")
	float keyframe$getOffHandHeight();

	@Accessor("offHandHeight")
	void keyframe$setOffHandHeight(float value);

	@Accessor("oOffHandHeight")
	float keyframe$getOOffHandHeight();

	@Accessor("oOffHandHeight")
	void keyframe$setOOffHandHeight(float value);
}
