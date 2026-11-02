package org.polyfrost.playback.shadow;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShadowReplayZipTest {

    private static final Map<String, Integer> CHUNKS = new LinkedHashMap<>();

    static {
        CHUNKS.put("c0.flashback", 6000);
        CHUNKS.put("c1.flashback", 6000);
        CHUNKS.put("c2.flashback", 300);
    }

    @Test
    void keepsOnlyTheChunkHoldingTheWindow() {
        ShadowReplayZip.Span span = ShadowReplayZip.span(CHUNKS, 7000, 7600);
        assertEquals(List.of("c1.flashback"), span.chunks());
        assertEquals(6000, span.offset());
        assertEquals(6000, span.totalTicks());
    }

    @Test
    void keepsBothChunksWhenTheWindowCrossesABoundary() {
        ShadowReplayZip.Span span = ShadowReplayZip.span(CHUNKS, 11900, 12200);
        assertEquals(List.of("c1.flashback", "c2.flashback"), span.chunks());
        assertEquals(6000, span.offset());
        assertEquals(6300, span.totalTicks());
    }

    @Test
    void windowEndingOnABoundaryDoesNotPullInTheNextChunk() {
        assertEquals(List.of("c0.flashback"), ShadowReplayZip.span(CHUNKS, 5400, 6000).chunks());
    }

    @Test
    void windowPastTheRecordingIsEmpty() {
        assertNull(ShadowReplayZip.span(CHUNKS, 20000, 20600));
    }

}
