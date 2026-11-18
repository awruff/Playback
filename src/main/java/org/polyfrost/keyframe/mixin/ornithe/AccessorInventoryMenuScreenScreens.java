package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.gui.screen.game.inventory.InventoryMenuScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(InventoryMenuScreen.class)
public interface AccessorInventoryMenuScreenScreens {

    @Accessor("x")
    int keyframe$x();

    @Accessor("y")
    int keyframe$y();

}
