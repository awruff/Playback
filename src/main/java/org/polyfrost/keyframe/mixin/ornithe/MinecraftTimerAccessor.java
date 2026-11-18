package org.polyfrost.keyframe.mixin.ornithe;

import net.minecraft.client.Minecraft;
import net.minecraft.client.TickTimer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftTimerAccessor {

    @Accessor("timer")
    TickTimer keyframe$getTimer();

}
