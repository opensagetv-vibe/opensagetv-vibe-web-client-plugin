package org.opensagetv.webplayer;

import java.io.IOException;
import java.io.OutputStream;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

/** Byte follower shared by the production FFmpeg feeder and file-growth tests. */
final class RecordingFollower {
    interface Sources {
        int segmentCount();
        boolean isRecording();
        SeekableMediaSource open(int index) throws IOException;
    }
    private static final int FINAL_SETTLE_POLLS=4;
    private static final long POLL_MS=250L;
    private RecordingFollower() {}

    static void copy(Sources sources, int firstSegment, long absoluteStart, OutputStream out,
                     BooleanSupplier active, LongConsumer bytesRead, Consumer<Boolean> recordingState)
            throws IOException, InterruptedException {
        long skip = Math.max(0L, absoluteStart);
        byte[] buffer = new byte[256 * 1024];
        int i = firstSegment;
        int completedSettle=0;
        while (active.getAsBoolean() && !Thread.currentThread().isInterrupted()) {
            int count = Math.max(0,sources.segmentCount());
            boolean recording = sources.isRecording(); recordingState.accept(recording);
            if (i >= count) {
                if (recording) { completedSettle=0;Thread.sleep(POLL_MS); continue; }
                if (++completedSettle < FINAL_SETTLE_POLLS) {Thread.sleep(POLL_MS);continue;}
                break;
            }
            completedSettle=0;
            try (SeekableMediaSource src = sources.open(i)) {
                if (src == null) { i++; continue; }
                long length = Math.max(0L, src.length());
                if (skip >= length && length > 0 && i < count - 1) {
                    skip -= length; i++; continue;
                }
                long position = Math.min(skip, length);
                if (position > 0) src.seek(position);
                skip = 0;
                int eofSettle=0;
                while (active.getAsBoolean() && !Thread.currentThread().isInterrupted()) {
                    long currentLength=Math.max(0L,src.length());
                    if(currentLength<position)throw new IOException("Media source shrank or was replaced during playback");
                    long available = currentLength - position;
                    if (available <= 0) {
                        count = Math.max(0,sources.segmentCount());
                        recording = sources.isRecording(); recordingState.accept(recording);
                        long after=Math.max(0L,src.length());
                        if(after<position)throw new IOException("Media source shrank or was replaced during playback");
                        // Recheck after metadata changed: final bytes may have
                        // been appended between the earlier length() and EOF.
                        if (after > position) {eofSettle=0;continue;}
                        if (i < count - 1) break;
                        if (recording) {eofSettle=0;Thread.sleep(POLL_MS);continue;}
                        // SageTV can clear the recording flag slightly before
                        // the final filesystem/MediaServer size becomes visible.
                        // Give completed EOF one bounded 1-second settle window.
                        if(++eofSettle<FINAL_SETTLE_POLLS){Thread.sleep(POLL_MS);continue;}
                        break;
                    }
                    eofSettle=0;
                    int n = src.read(buffer, 0, (int)Math.min(buffer.length, available));
                    if (n <= 0) {
                        if (sources.isRecording()) { Thread.sleep(POLL_MS); continue; }
                        if(++eofSettle<FINAL_SETTLE_POLLS){Thread.sleep(POLL_MS);continue;}
                        break;
                    }
                    out.write(buffer, 0, n); out.flush();
                    position += n; bytesRead.accept(n);
                }
            }
            i++;
        }
    }
}
