package org.polyfrost.polyreplay.shadow;

import com.moulberry.flashback.io.AsyncReplaySaver;

import java.nio.file.Path;

public interface ShadowRecorder {

    int polyreplay$writtenTicks();

    int polyreplay$writtenTicksInChunk();

    AsyncReplaySaver polyreplay$saver();

    void polyreplay$flushChunk();

    Path polyreplay$folder();

}
