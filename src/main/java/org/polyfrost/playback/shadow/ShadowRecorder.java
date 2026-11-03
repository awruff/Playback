package org.polyfrost.playback.shadow;

import com.moulberry.flashback.io.AsyncReplaySaver;

import java.nio.file.Path;

public interface ShadowRecorder {

    int playback$writtenTicks();

    boolean playback$wroteInitialSnapshot();

    int playback$writtenTicksInChunk();

    AsyncReplaySaver playback$saver();

    void playback$flushChunk();

    Path playback$folder();

}
