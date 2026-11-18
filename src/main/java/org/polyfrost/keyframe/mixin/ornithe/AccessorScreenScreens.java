package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Screen.class)
public interface AccessorScreenScreens {

    @Accessor("minecraft")
    void keyframe$setMinecraft(Minecraft minecraft);

}
