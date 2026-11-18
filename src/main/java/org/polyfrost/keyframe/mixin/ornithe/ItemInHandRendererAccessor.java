package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.render.ItemInHandRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererAccessor {

    @Accessor("itemInHand")
    ItemStack keyframe$getItemInHand();

    @Accessor("itemInHand")
    void keyframe$setItemInHand(ItemStack item);

    @Accessor("handHeight")
    float keyframe$getHandHeight();

    @Accessor("handHeight")
    void keyframe$setHandHeight(float height);

    @Accessor("lastHandHeight")
    float keyframe$getLastHandHeight();

    @Accessor("lastHandHeight")
    void keyframe$setLastHandHeight(float height);

}
