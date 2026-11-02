package org.polyfrost.playback.mixin.flashback;

import com.moulberry.flashback.io.AsyncReplaySaver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.file.Path;

@Mixin(AsyncReplaySaver.class)
public interface AsyncReplaySaverAccessor {

    @Accessor("recordFolder")
    Path playback$recordFolder();

}
