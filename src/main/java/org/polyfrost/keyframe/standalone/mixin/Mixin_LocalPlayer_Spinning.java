package org.polyfrost.keyframe.standalone.mixin;

//? if >=1.21.10 {
//? if >=26.3 {
import net.minecraft.client.player.LocalPlayer;
//?} else
/*import net.minecraft.client.renderer.GameRenderer;*/
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

//? if >=26.3 {
@Mixin(LocalPlayer.class)
//?} else
/*@Mixin(GameRenderer.class)*/
public interface Mixin_LocalPlayer_Spinning {
	@Accessor("spinningEffectTime")
	float keyframe$getSpinningTime();

	@Accessor("spinningEffectTime")
	void keyframe$setSpinningTime(float value);

	@Accessor("spinningEffectSpeed")
	float keyframe$getSpinningSpeed();

	@Accessor("spinningEffectSpeed")
	void keyframe$setSpinningSpeed(float value);
}
//?}
