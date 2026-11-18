package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.gui.screen.menu.TitleScreen;
import net.minecraft.resource.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(TitleScreen.class)
public interface AccessorTitleScreenScreens {

    @Accessor("time")
    void keyframe$setTime(int time);

    @Accessor("backgroundLocation")
    void keyframe$setBackgroundLocation(Identifier location);

    @Invoker("drawBackground")
    void keyframe$drawBackground(int mouseX, int mouseY, float delta);

}
