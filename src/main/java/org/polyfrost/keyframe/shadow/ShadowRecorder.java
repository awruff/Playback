package org.polyfrost.keyframe.shadow;

import com.moulberry.flashback.io.AsyncReplaySaver;

import java.nio.file.Path;

public interface ShadowRecorder {

    int keyframe$writtenTicks();

    boolean keyframe$wroteInitialSnapshot();

    int keyframe$writtenTicksInChunk();

    AsyncReplaySaver keyframe$saver();

    void keyframe$flushChunk();

    Path keyframe$folder();

}
