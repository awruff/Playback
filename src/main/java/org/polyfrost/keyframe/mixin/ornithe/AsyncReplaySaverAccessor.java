package org.polyfrost.keyframe.mixin.ornithe;

import com.moulberry.flashback.io.AsyncReplaySaver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

@Mixin(value = AsyncReplaySaver.class, remap = false)
public interface AsyncReplaySaverAccessor {

    @Accessor("recordFolder")
    Path keyframe$recordFolder();

}
