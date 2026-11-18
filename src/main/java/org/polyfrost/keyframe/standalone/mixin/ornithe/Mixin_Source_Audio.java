package org.polyfrost.keyframe.standalone.mixin.ornithe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import paulscode.sound.ICodec;
import paulscode.sound.Source;

@Mixin(value = Source.class, remap = false)
public interface Mixin_Source_Audio {
	@Accessor("codec")
	ICodec keyframe$getCodec();
}
