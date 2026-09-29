package org.opensagetv.webplayer;

import java.io.IOException;
import java.util.Arrays;

/**
 * Stock MiniDVDPlayer wire/session state.  P07 owns protocol bytes and control
 * generations only; P08 attaches the actual MPEG-PS -> browser A/V consumer.
 */
final class DvdNativeSession {
    static final int DEFAULT_CAPACITY = 4 * 1024 * 1024;
    static final int DISABLE_SUBPICTURE = 62;
    private static final long PUSH_WAIT_MS = 1500L;
    private static final int MAX_METADATA_PAYLOAD = 64 * 1024;

    private final int capacity;
    private final DvdPtsClock clock = new DvdPtsClock();
    private final DvdPsInspector inspector = new DvdPsInspector();
    private final DvdSpuSession spu = new DvdSpuSession(this);
    private DvdPushBuffer input;
    private boolean pending;
    private boolean active;
    private boolean failed;
    private String failure = "";
    private int videoFormat;
    private long initCount;
    private long pushCommands;
    private long pushMediaCount;
    private long drainPolls;
    private long flushCount;
    private long transientEosCount;
    private long metadataCount;
    private long totalPushedBytes;
    private long epochPushedBytes;
    private boolean postDataFlushReady;
    private byte[] newCell = new byte[0];
    private byte[] clut = new byte[0];
    private byte[] spuControl = new byte[0];
    private long lastStc45k = -1L;
    private int streamType = -1;
    private int streamPosition = -1;
    private int dvdFormat = -1;

    DvdNativeSession() { this(DEFAULT_CAPACITY); }
    DvdNativeSession(int capacity) {
        if (capacity < 64 * 1024) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    int init(int formatCode) {
        synchronized(this){
            closeInput();
            input = new DvdPushBuffer(capacity);
            pending = true;
            active = false;
            failed = false;
            failure = "";
            videoFormat = formatCode;
            initCount++;
            pushCommands = pushMediaCount = drainPolls = flushCount = transientEosCount = metadataCount = 0L;
            totalPushedBytes = epochPushedBytes = 0L;
            postDataFlushReady = false;
            newCell = clut = spuControl = new byte[0];
            lastStc45k = -1L;
            streamType = streamPosition = dvdFormat = -1;
            clock.resetAll();
            inspector.resetEpoch();
        }
        // SPU composition reads the DVD clock/format. Never acquire the SPU
        // monitor while holding the DVD-session monitor or subtitle polling
        // can form the inverse lock order.
        spu.reset();
        return 1;
    }

    synchronized void cancelPendingForOrdinaryOpenUrl() {
        pending = false;
        active = false;
        closeInput();
    }

    synchronized boolean pending() { return pending; }
    synchronized boolean active() { return active; }
    synchronized boolean failed() { return failed; }
    synchronized String failure() { return failure; }
    synchronized int capacity() { return capacity; }
    synchronized int videoFormat() { return videoFormat; }
    synchronized long generation() { return input == null ? 0L : input.generation(); }
    synchronized DvdPushBuffer input() { return input; }
    synchronized long mediaTimeMs() { return clock.currentMillis(); }
    synchronized DvdPtsClock clock() { return clock; }
    synchronized DvdSpuSession spu() { return spu; }

    private synchronized boolean ensureActive() {
        if (!pending || failed) return false;
        if (input == null) input = new DvdPushBuffer(capacity);
        active = true;
        return true;
    }

    /** Handles the raw body of MEDIACMD_PUSHBUFFER and returns native free space/-2. */
    int push(byte[] commandBody) throws IOException {
        int buffSize;
        int flags;
        int dataOffset;
        DvdPushBuffer pipe;
        synchronized (this) {
            pushCommands++;
            if (!pending || failed) return capacity;
            if (commandBody == null || commandBody.length < 8) return capacity;
            buffSize = MiniClientSession.readInt(commandBody, 0);
            flags = MiniClientSession.readInt(commandBody, 4);
            if (buffSize < 0) { failLocked("negative DVD PUSH size"); return 0; }
            if (commandBody.length >= buffSize + 18) dataOffset = 18; // detailed-stats form
            else if (commandBody.length >= buffSize + 8) dataOffset = 8;
            else { failLocked("truncated DVD PUSH payload"); return 0; }

            boolean looksLikeDvd = (flags & 0x100) != 0 || containsMpegPsPackHeader(commandBody, dataOffset, buffSize);
            if (!active && looksLikeDvd) ensureActive();
            // SageTV's four 16-KiB startup bandwidth probes are not MPEG-PS and
            // must never create/activate the DVD session.
            if (!active) return capacity;
            pipe = input;
        }

        if (buffSize > 0) {
            try { pipe.write(commandBody, dataOffset, buffSize, PUSH_WAIT_MS); }
            catch (IOException e) {
                synchronized (this) { failLocked(e.getMessage()); }
                throw e;
            }
            long observedGeneration;
            synchronized (this) {
                inspector.observe(commandBody,dataOffset,buffSize,clock);
                observedGeneration=pipe.generation();
                totalPushedBytes += buffSize;
                epochPushedBytes += buffSize;
                pushMediaCount++;
                postDataFlushReady = false;
            }
            spu.onGeneration(observedGeneration);
            spu.observe(commandBody,dataOffset,buffSize);
        }
        if ((flags & 0x80) != 0) {
            pipe.signalSegmentEnd();
            synchronized (this) { transientEosCount++; }
        }

        if ((flags & 0x100) != 0) {
            synchronized (this) { drainPolls++; }
            // Initial EMPTY/PAUSE poll before any bytes has no decoder/input
            // tail.  A post-data FLUSH is also immediately ready by contract.
            synchronized (this) {
                if (epochPushedBytes == 0L && (totalPushedBytes == 0L || postDataFlushReady)) return -2;
            }
            // Stock MiniDVDPlayer does not send flag 0x80 when the DVD VM
            // enters DVD_PROCESS_EMPTY.  Its first 0x100 poll is the only
            // end-of-cell signal available to a software decoder.  Close this
            // generation's producer side so FFmpeg can flush delayed frames,
            // write ENDLIST and let the matching browser report completion.
            // Repeated polls are intentionally idempotent; FLUSH creates the
            // next reusable generation.
            pipe.signalSegmentEnd();
            if (pipe.drainReady()) return -2;
        }
        return pipe.freeBytes();
    }

    int flush() {
        long nextGeneration;
        synchronized(this){
            if (!pending || failed) return 1;
            // Stock MiniDVDPlayer performs one harmless pre-data initialization
            // FLUSH.  Do not create a new decoder generation for it.
            if (totalPushedBytes == 0L) return 1;
            if (input != null) input.flush();
            epochPushedBytes = 0L;
            postDataFlushReady = true;
            flushCount++;
            clock.beginDiscontinuity();
            inspector.resetEpoch();
            nextGeneration=input == null ? 0L : input.generation();
        }
        spu.onGeneration(nextGeneration);
        return 1;
    }

    synchronized int setNewCell(byte[] body) {
        if (!ensureActive()) return -1;
        byte[] payload = payload(body);
        if (payload == null || payload.length != 4) return -1;
        newCell = payload;
        int wireOffset45k = MiniClientSession.readInt(payload, 0);
        clock.onNewCell(wireOffset45k);
        inspector.resetEpoch();
        metadataCount++;
        return 0;
    }

    int setClut(byte[] body) {
        byte[] payload = payload(body);
        if (payload == null) return -1;
        synchronized(this){if (!ensureActive()) return -1;clut = payload;metadataCount++;}
        spu.setClut(payload);
        return 0;
    }

    int setSpuControl(byte[] body) {
        byte[] payload = payload(body);
        if (payload == null) return -1;
        synchronized(this){if (!ensureActive()) return -1;spuControl = payload;metadataCount++;}
        spu.setHighlight(payload);
        return 0;
    }

    synchronized int setStc(byte[] body) {
        if (!ensureActive() || body == null || body.length < 4) return -1;
        long wire = MiniClientSession.readInt(body, 0) & 0xffffffffL;
        lastStc45k = wire;
        clock.setServerStc45k(wire);
        metadataCount++;
        return 0;
    }

    int setStream(byte[] body) {
        if (body == null || body.length < 8) return -1;
        int type = MiniClientSession.readInt(body, 0);
        int position = MiniClientSession.readInt(body, 4);
        if (type != 0 && type != 1) return -1;
        if(type==0 && position>=0 && !DvdAudioStreamCode.decode(position).supportedByFfmpegPolicy()) return -1;
        synchronized(this){if (!ensureActive()) return -1;streamType = type;streamPosition = position;metadataCount++;}
        if(type==1)spu.select(position);
        return 0;
    }

    synchronized int setFormat(byte[] body) {
        if (!ensureActive() || body == null || body.length < 4) return -1;
        dvdFormat = MiniClientSession.readInt(body, 0);
        metadataCount++;
        return 0;
    }

    synchronized void updatePlayerClock(long browserPositionMs) {
        clock.updatePlayerClockMillis(browserPositionMs);
    }

    synchronized byte[] newCellPayload() { return Arrays.copyOf(newCell,newCell.length); }
    synchronized byte[] clutPayload() { return Arrays.copyOf(clut,clut.length); }
    synchronized byte[] spuControlPayload() { return Arrays.copyOf(spuControl,spuControl.length); }
    synchronized int streamType() { return streamType; }
    synchronized int streamPosition() { return streamPosition; }
    synchronized int dvdFormat() { return dvdFormat; }
    synchronized long lastStc45k() { return lastStc45k; }
    synchronized long totalPushedBytes() { return totalPushedBytes; }
    synchronized long epochPushedBytes() { return epochPushedBytes; }
    synchronized boolean epochHasVideo(){ return inspector.hasVideo(); }
    synchronized boolean epochStillCandidate(){ return inspector.stillCandidate(); }
    synchronized long epochPictureCount(){ return inspector.pictureStarts(); }
    synchronized int requestedAudioWireCode(){ return streamType==0?streamPosition:-1; }
    synchronized String requestedAudioDescription(){ return requestedAudioWireCode()<0?"auto":DvdAudioStreamCode.decode(requestedAudioWireCode()).description(); }

    synchronized String json() {
        DvdPushBuffer p = input;
        return "{\"pending\":"+pending+",\"active\":"+active+",\"failed\":"+failed+
                ",\"failure\":\""+HttpUtil.json(failure)+"\",\"videoFormat\":"+videoFormat+
                ",\"capacity\":"+capacity+",\"queued\":"+(p==null?0:p.queuedBytes())+
                ",\"free\":"+(p==null?capacity:p.freeBytes())+",\"generation\":"+(p==null?0:p.generation())+
                ",\"totalPushedBytes\":"+totalPushedBytes+",\"epochPushedBytes\":"+epochPushedBytes+
                ",\"initCount\":"+initCount+",\"pushCommands\":"+pushCommands+",\"mediaPushes\":"+pushMediaCount+
                ",\"drainPolls\":"+drainPolls+",\"flushes\":"+flushCount+",\"segmentEos\":"+transientEosCount+
                ",\"metadataCommands\":"+metadataCount+",\"cellOffset45k\":"+(newCell.length==4?(MiniClientSession.readInt(newCell,0)&0xffffffffL):-1L)+",\"clock90k\":"+clock.currentClock90k()+
                ",\"ptsOffset90k\":"+clock.ptsOffset90k()+",\"streamType\":"+streamType+
                ",\"streamPosition\":"+streamPosition+",\"format\":"+dvdFormat+
                ",\"audioSelector\":\""+HttpUtil.json(requestedAudioDescription())+"\",\"ps\":"+inspector.json()+"}";
    }

    synchronized void close() {
        pending = active = false;
        closeInput();
        clock.resetAll();
    }

    private void closeInput() {
        if (input != null) input.close();
        input = null;
    }

    private void failLocked(String message) {
        failed = true;
        failure = message == null ? "DVD native session failed" : message;
        if (input != null) input.close();
    }

    private static byte[] payload(byte[] body) {
        if (body == null || body.length < 4) return null;
        int size = MiniClientSession.readInt(body, 0);
        if (size < 0 || size > MAX_METADATA_PAYLOAD || size > body.length - 4) return null;
        return Arrays.copyOfRange(body, 4, 4 + size);
    }

    static boolean containsMpegPsPackHeader(byte[] data, int offset, int length) {
        if (data == null || offset < 0 || length < 4 || offset > data.length) return false;
        int end = Math.min(data.length, offset + length) - 3;
        for (int i=offset;i<end;i++)
            if (data[i]==0 && data[i+1]==0 && data[i+2]==1 && (data[i+3]&0xff)==0xBA) return true;
        return false;
    }
}
