package org.opensagetv.webplayer;

/** Cooperative CPU slice guard for future in-process subtitle decoders. */
final class SubtitleWorkBudget {
    private final long started=System.nanoTime();
    boolean exhausted(){return System.nanoTime()-started>=SubtitleLimits.MAX_DECODE_SLICE_NANOS;}
    long remainingNanos(){return Math.max(0L,SubtitleLimits.MAX_DECODE_SLICE_NANOS-(System.nanoTime()-started));}
}
