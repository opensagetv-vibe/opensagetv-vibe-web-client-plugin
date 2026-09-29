package org.opensagetv.webplayer;

import java.io.IOException;

/**
 * Bounded generation-aware byte pipe for SageTV MiniDVDPlayer MPEG-PS PUSH data.
 *
 * A writer never overwrites unread bytes.  A FLUSH cancels the current reader
 * generation and atomically starts a new empty epoch.  Segment EOS applies only
 * to the current generation; it is not the end of the reusable DVD session.
 */
final class DvdPushBuffer {
    static final int READ_EOS = -1;
    static final int READ_CANCELLED = -2;

    private final byte[] ring;
    private int readPos;
    private int writePos;
    private int size;
    private long generation = 1L;
    private boolean segmentEos;
    private boolean readerObservedEos;
    private long decoderDrainedGeneration = Long.MIN_VALUE;
    private boolean closed;
    private long totalWritten;
    private long totalRead;
    private long epochWritten;
    private long epochRead;

    DvdPushBuffer(int capacity) {
        if (capacity < 64 * 1024) throw new IllegalArgumentException("DVD push capacity must be at least 64 KiB");
        ring = new byte[capacity];
    }

    synchronized int capacity() { return ring.length; }
    synchronized int queuedBytes() { return size; }
    synchronized int freeBytes() { return ring.length - size; }
    synchronized long generation() { return generation; }
    synchronized long totalWritten() { return totalWritten; }
    synchronized long totalRead() { return totalRead; }
    synchronized long epochWritten() { return epochWritten; }
    synchronized long epochRead() { return epochRead; }
    synchronized boolean segmentEosSignaled() { return segmentEos; }
    synchronized boolean readerObservedEos() { return readerObservedEos; }
    synchronized boolean isClosed() { return closed; }

    /**
     * Writes one complete MiniClient PUSH payload or fails without partially
     * accepting it.  Stock SageTV honors the previously returned free-space
     * value, so waiting here is an exceptional back-pressure path.
     */
    synchronized void write(byte[] source, int offset, int length, long timeoutMs) throws IOException {
        if (source == null || offset < 0 || length < 0 || offset + length > source.length)
            throw new IOException("Invalid DVD PUSH payload bounds");
        if (length == 0) return;
        if (length > ring.length) throw new IOException("DVD PUSH payload exceeds bounded input capacity");
        long deadline = System.nanoTime() + Math.max(1L, timeoutMs) * 1_000_000L;
        while (!closed && ring.length - size < length) {
            long remainNs = deadline - System.nanoTime();
            if (remainNs <= 0L) throw new IOException("DVD PUSH input back-pressure timeout");
            long waitMs = Math.max(1L, Math.min(250L, remainNs / 1_000_000L));
            try { wait(waitMs); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted while waiting for DVD input capacity", e); }
        }
        if (closed) throw new IOException("DVD PUSH input is closed");
        int first = Math.min(length, ring.length - writePos);
        System.arraycopy(source, offset, ring, writePos, first);
        int rest = length - first;
        if (rest > 0) System.arraycopy(source, offset + first, ring, 0, rest);
        writePos = (writePos + length) % ring.length;
        size += length;
        totalWritten += length;
        epochWritten += length;
        // New bytes after a previously observed EOF belong to the same epoch
        // only if the server failed to FLUSH. Do not keep a stale drained mark.
        readerObservedEos = false;
        decoderDrainedGeneration = Long.MIN_VALUE;
        notifyAll();
    }

    /**
     * Blocking consumer read. Temporary input starvation never returns zero;
     * readers wake on bytes, segment EOS, FLUSH generation change or close.
     */
    synchronized int read(byte[] target, int offset, int length, long readerGeneration) throws IOException {
        if (target == null || offset < 0 || length < 0 || offset + length > target.length)
            throw new IOException("Invalid DVD reader buffer bounds");
        if (length == 0) return 0;
        while (true) {
            if (readerGeneration != generation) return READ_CANCELLED;
            if (size > 0) {
                int count = Math.min(length, size);
                int first = Math.min(count, ring.length - readPos);
                System.arraycopy(ring, readPos, target, offset, first);
                int rest = count - first;
                if (rest > 0) System.arraycopy(ring, 0, target, offset + first, rest);
                readPos = (readPos + count) % ring.length;
                size -= count;
                totalRead += count;
                epochRead += count;
                notifyAll();
                return count;
            }
            if (segmentEos) {
                readerObservedEos = true;
                notifyAll();
                return READ_EOS;
            }
            if (closed) return READ_EOS;
            try { wait(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException("Interrupted while reading DVD input", e); }
        }
    }

    synchronized void signalSegmentEnd() {
        if (closed) return;
        segmentEos = true;
        notifyAll();
    }

    synchronized void markDecoderDrained(long decoderGeneration) {
        if (decoderGeneration == generation) {
            decoderDrainedGeneration = decoderGeneration;
            notifyAll();
        }
    }

    synchronized boolean drainReady() {
        return segmentEos && size == 0 && readerObservedEos && decoderDrainedGeneration == generation;
    }

    /** Starts a distinct byte/decoder epoch and cancels all old readers. */
    synchronized long flush() {
        generation++;
        readPos = writePos = size = 0;
        segmentEos = false;
        readerObservedEos = false;
        decoderDrainedGeneration = Long.MIN_VALUE;
        epochWritten = 0L;
        epochRead = 0L;
        notifyAll();
        return generation;
    }

    synchronized void close() {
        if (closed) return;
        closed = true;
        generation++;
        readPos = writePos = size = 0;
        segmentEos = true;
        readerObservedEos = true;
        decoderDrainedGeneration = generation;
        notifyAll();
    }
}
