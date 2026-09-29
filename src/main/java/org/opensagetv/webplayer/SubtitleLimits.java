package org.opensagetv.webplayer;

/** Central P02 resource limits. Later decoders must fail closed inside these bounds. */
final class SubtitleLimits {
    static final int MAX_CUES=2048;
    static final long MAX_QUEUED_BYTES=16L*1024L*1024L;
    static final int MAX_CUE_BYTES=2*1024*1024;
    static final int MAX_TEXT_CHARS=16384;
    static final int MAX_BITMAP_WIDTH=4096;
    static final int MAX_BITMAP_HEIGHT=2160;
    static final long MAX_BITMAP_PIXELS=4096L*2160L;
    static final int MAX_DISCOVERED_SERVICES=64;
    static final long MAX_TS_DISCOVERY_BYTES=4L*1024L*1024L;
    static final long MAX_SUBTITLE_TAP_BYTES=64L*1024L*1024L;
    static final int MAX_ACTIVE_TAPS=Math.max(1,Integer.getInteger("sagetv.webplayer.maxSubtitleTaps",8));
    static final long TAP_IDLE_MS=Math.max(5000L,Long.getLong("sagetv.webplayer.subtitleTapIdleMs",30000L));
    static final long MAX_TAP_WALL_MS=Math.max(60000L,Long.getLong("sagetv.webplayer.subtitleTapWallMs",30L*60L*1000L));
    static final long MAX_DECODE_SLICE_NANOS=Math.max(1000000L,Long.getLong("sagetv.webplayer.subtitleDecodeSliceNanos",25L*1000L*1000L));
    private SubtitleLimits(){}
    static String json(){return "{\"maxCues\":"+MAX_CUES+",\"maxQueuedBytes\":"+MAX_QUEUED_BYTES+",\"maxCueBytes\":"+MAX_CUE_BYTES+
            ",\"maxTextChars\":"+MAX_TEXT_CHARS+",\"maxBitmapWidth\":"+MAX_BITMAP_WIDTH+",\"maxBitmapHeight\":"+MAX_BITMAP_HEIGHT+
            ",\"maxBitmapPixels\":"+MAX_BITMAP_PIXELS+",\"maxDiscoveredServices\":"+MAX_DISCOVERED_SERVICES+
            ",\"maxTsDiscoveryBytes\":"+MAX_TS_DISCOVERY_BYTES+",\"maxSubtitleTapBytes\":"+MAX_SUBTITLE_TAP_BYTES+",\"maxActiveTaps\":"+MAX_ACTIVE_TAPS+"}";}
}
