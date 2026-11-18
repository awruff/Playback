package org.polyfrost.keyframe.standalone.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
//? if >=1.21.8 {
import net.minecraft.world.entity.player.Player;
//?} else
/*import net.minecraft.world.entity.Entity;*/
import org.polyfrost.keyframe.standalone.playback.Keyframe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

//? if >=1.21.8 {
@Mixin(Player.class)
//?} else
/*@Mixin(Entity.class)*/
abstract class Mixin_Player_Puppet {
	//? if >=1.21.8 {
	@ModifyReturnValue(method = "isLocalClientAuthoritative", at = @At("RETURN"))
	//?} else
	/*@ModifyReturnValue(method = "isControlledByLocalInstance", at = @At("RETURN"))*/
	private boolean keyframe$yieldToKeyframe(boolean authoritative) {
		return authoritative && !Keyframe.INSTANCE.getActive();
	}
}
