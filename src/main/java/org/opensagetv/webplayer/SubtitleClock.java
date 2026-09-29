package org.opensagetv.webplayer;

/** 33-bit MPEG PTS to recording/browser time mapping with explicit discontinuity reset. */
final class SubtitleClock {
    private static final long WRAP=1L<<33,HALF=WRAP>>1;
    private boolean anchored; private long baseUnwrapped,lastRaw,lastUnwrapped,recordingAnchorMs;
    synchronized void reset(){anchored=false;baseUnwrapped=lastRaw=lastUnwrapped=recordingAnchorMs=0;}
    synchronized void anchor(long pts90k,long recordingMs){long raw=pts90k&(WRAP-1);anchored=true;baseUnwrapped=raw;lastRaw=raw;lastUnwrapped=raw;recordingAnchorMs=Math.max(0,recordingMs);}
    synchronized long sourcePtsToRecordingMs(long pts90k){long raw=pts90k&(WRAP-1);if(!anchored)anchor(raw,0);long delta=raw-lastRaw;if(delta>HALF)delta-=WRAP;else if(delta<-HALF)delta+=WRAP;lastUnwrapped+=delta;lastRaw=raw;return Math.max(0,recordingAnchorMs+(lastUnwrapped-baseUnwrapped)*1000L/90000L);}
    static long presentationMs(long browserCurrentMs,int delayMs){long v=browserCurrentMs-(long)delayMs;return Math.max(0,v);}
}
