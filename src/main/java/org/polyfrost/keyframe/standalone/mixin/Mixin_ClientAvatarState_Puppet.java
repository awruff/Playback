package org.polyfrost.keyframe.standalone.mixin;

//? if >=1.21.10 {
import net.minecraft.client.entity.ClientAvatarState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientAvatarState.class)
public interface Mixin_ClientAvatarState_Puppet {
	@Accessor("bob")
	void keyframe$setBob(float bob);

	@Accessor("walkDist")
	void keyframe$setWalkDist(float walkDist);
}
//?}
