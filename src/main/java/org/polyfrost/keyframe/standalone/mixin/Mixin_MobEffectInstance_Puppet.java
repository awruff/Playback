package org.polyfrost.keyframe.standalone.mixin;

//? if <1.21.10 {
/*import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MobEffectInstance.class)
public interface Mixin_MobEffectInstance_Puppet {
	@Invoker("tickDownDuration")
	int keyframe$tickDownDuration();
}
*///?}
