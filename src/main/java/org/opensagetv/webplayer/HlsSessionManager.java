package org.opensagetv.webplayer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * FFmpeg -> continuous TS / HLS bridge (legacy class name retained).
 *
 * This ports the stable streaming architecture from the user's Python HTML5
 * client into the Jetty plugin: FFmpeg writes an EVENT HLS playlist plus
 * atomic MPEG-TS segments, and Jetty serves snapshots of those files.  For
 * media that is not directly mounted in the Jetty/SageTV JVM, a feeder reads
 * the SageTV MediaServer protocol (port 7818) and writes MPEG-TS to FFmpeg's
 * stdin.  No Python/FastAPI server is involved.
 */
final class HlsSessionManager {
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<String, Session>();
    private static final long STALE_MS = 4L * 60L * 60L * 1000L;
    private static final int STARTUP_TIMEOUT_MS = 15000;
    private static final int DVD_STARTUP_TIMEOUT_MS = Math.max(3000, Integer.getInteger("sagetv.webplayer.dvdStartupTimeoutMs", 7000));
    private static final int DVD_HARDWARE_STARTUP_TIMEOUT_MS = Math.max(1500, Integer.getInteger("sagetv.webplayer.dvdHardwareStartupTimeoutMs", 3500));
    private static final int DVD_EOS_FALLBACK_GRACE_MS = Math.max(500, Integer.getInteger("sagetv.webplayer.dvdEosFallbackGraceMs", 1500));
    private static final int DVD_SOFTWARE_EOS_FLUSH_TIMEOUT_MS = Math.max(5000, Integer.getInteger("sagetv.webplayer.dvdSoftwareEosFlushTimeoutMs", 15000));
    private static final int DVD_REPLAY_MAX_BYTES = Math.max(1024 * 1024, Integer.getInteger("sagetv.webplayer.dvdStartupReplayBytes", 16 * 1024 * 1024));
    private static final java.util.concurrent.ScheduledExecutorService REAPER=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"webplayer-stream-reaper");t.setDaemon(true);return t;});
    static {REAPER.scheduleWithFixedDelay(()->{
        long now=System.currentTimeMillis(),idle=Math.max(30000L,Long.getLong("sagetv.webplayer.streamIdleMs",120000L));
        for(Session s:SESSIONS.values())if(!s.cleanupQueued&&now-s.lastAccessAt>idle)stop(s.id);
    },30,30,TimeUnit.SECONDS);}

    static final class Session {
        final String id;
        final int mediaId;
        final int firstSegment;
        final int audioIndex;
        final double startSeconds;
        volatile long durationMs;
        volatile boolean activeRecording;
        final File directory;
        final File playlist;
        final File continuous;
        final StreamOptions options;
        final PlaybackSessionContext playbackContext;
        volatile PlaybackSessionContext.Token playbackToken;
        volatile StreamPlan streamPlan;
        volatile MediaProbe.Info sourceInfo;
        volatile A53CaptionTap captionTap;
        volatile SubtitleSourceBranch subtitleBranch;
        volatile String subtitleBranchError="";
        volatile TeletextSubtitleSession teletext;
        volatile TeletextSourcePump teletextPump;
        volatile DvbSubtitleSession dvb;
        volatile DvbSourcePump dvbPump;
        volatile OrdinarySubtitleSession ordinarySubtitles;
        volatile int dvbCommandPid=-1;
        volatile SageApiBridge sourceSage;
        volatile Object sourceMediaFile;
        volatile long sourceAbsoluteStart;
        volatile String seekStrategy="recording-start";
        volatile long seekRequestedMs;
        volatile long seekAnchorMs;
        volatile long seekResidualMs;
        volatile long seekAnchorErrorMs;
        volatile int seekIndexSamples;
        volatile int seekPacketSize;
        volatile DvdNativeSession dvdSource;
        volatile long dvdGeneration;
        volatile int dvdAudioWireCode=-1;
        volatile boolean dvdInputEos;
        volatile boolean dvdConverterEnded;
        volatile boolean dvdReadyNotified;
        volatile boolean dvdNoPlayableMedia;
        volatile long dvdBrowserTimeMs;
        final ByteArrayOutputStream dvdStartupReplay = new ByteArrayOutputStream();
        volatile boolean dvdReplayTruncated;
        volatile long dvdReplayBytes;
        volatile String dvdFallbackReason = "";
        volatile long lastAccessAt = System.currentTimeMillis();
        volatile long deliveredBytes;
        final long maxBytes = Math.max(64L*1024L*1024L, Long.getLong("sagetv.webplayer.streamMaxBytes",8L*1024L*1024L*1024L));
        final long createdAt = System.currentTimeMillis();

        volatile Process process;
        volatile Thread feederThread;
        volatile Thread stderrThread;
        volatile Thread monitorThread;
        volatile boolean stopRequested;
        volatile boolean cleanupQueued;
        volatile boolean running;
        volatile boolean playlistReady;
        volatile boolean softwareFallback;
        volatile boolean hardwareDecodeFallback;
        volatile int fallbackStage; // 0=full hardware/native, 1=hardware encode only, 2=software
        volatile String encoder = "";
        volatile String accelerator = "";
        volatile String decodeAccelerator = "";
        volatile String encoderDetail = "";
        volatile boolean hardware;
        volatile boolean hardwareDecode;
        volatile boolean hardwareFilters;
        volatile String inputMode = "";
        volatile String inputDetail = "";
        volatile String error = "";
        volatile String stderrTail = "";
        volatile long bytesInput;
        volatile long bytesProduced;
        volatile int segments;
        volatile double hlsSeconds;
        volatile boolean endList;
        volatile int exitCode = Integer.MIN_VALUE;
        volatile long lastUpdatedAt = createdAt;
        volatile long launchedAt;
        volatile long firstSegmentAt;
        volatile double hlsFillRate;
        volatile long lastSegmentAt;
        volatile double recentFillRate;
        private long rateSampleAt;
        private double rateSampleSeconds;
        private long metricsAt;

        Session(String id, int mediaId, int firstSegment, int audioIndex, double startSeconds,
                long durationMs, boolean activeRecording, File directory) {
            this(id,mediaId,firstSegment,audioIndex,startSeconds,durationMs,activeRecording,directory,null);
        }

        Session(String id, int mediaId, int firstSegment, int audioIndex, double startSeconds,
                long durationMs, boolean activeRecording, File directory, StreamOptions options) {
            this(id,mediaId,firstSegment,audioIndex,startSeconds,durationMs,activeRecording,directory,options,null,null);
        }

        Session(String id, int mediaId, int firstSegment, int audioIndex, double startSeconds,
                long durationMs, boolean activeRecording, File directory, StreamOptions options,
                PlaybackSessionContext playbackContext, PlaybackSessionContext.Token playbackToken) {
            this.id = id;
            this.mediaId = mediaId;
            this.firstSegment = firstSegment;
            this.audioIndex = audioIndex;
            this.startSeconds = Math.max(0.0, startSeconds);
            this.durationMs = Math.max(0L, durationMs);
            this.activeRecording = activeRecording;
            this.directory = directory;
            this.playlist = new File(directory, "stream.m3u8");
            this.continuous = new File(directory, "stream.ts");
            this.options = options;
            this.playbackContext=playbackContext;
            this.playbackToken=playbackToken;
        }
        boolean isTs() { return options != null && options.ts(); }
        String transport() { return isTs() ? "mpegts" : "hls"; }
        String mediaUrl() { return isTs() ? "continuous/"+id+"/stream.ts" : playlistUrl(); }
        String settingsJson() {
            return "{\"requested\":"+(options==null?StreamOptions.defaults().json():options.json())+
                ",\"effective\":"+(streamPlan==null?"null":streamPlan.json(encoder,hardware,hardwareDecode,hardwareFilters,decodeAccelerator,accelerator,encoderDetail))+
                ",\"source\":"+(sourceInfo==null?"null":sourceInfo.json())+"}";
        }
        String playlistUrl() { return "hls/" + id + "/stream.m3u8"; }
    }

    private HlsSessionManager() {}

    static Session start(int mediaId, int firstSegment, int audioIndex, double startSeconds) throws IOException {
        return start(mediaId,firstSegment,audioIndex,startSeconds,null);
    }

    static Session start(int mediaId,int firstSegment,int audioIndex,double startSeconds,StreamOptions options) throws IOException {
        return start(mediaId,firstSegment,audioIndex,startSeconds,options,null,null);
    }

    static Session start(int mediaId,int firstSegment,int audioIndex,double startSeconds,StreamOptions options,PlaybackSessionContext playbackContext,PlaybackSessionContext.Token playbackToken) throws IOException {
        if(!Double.isFinite(startSeconds)||startSeconds<0)throw new IllegalArgumentException("Invalid start position");
        cleanupStale();
        if(SESSIONS.values().stream().filter(v -> v.running).count() >= Math.max(1,Integer.getInteger("sagetv.webplayer.maxStreams",8)))
            throw new IOException("Streaming session limit reached; stop another recording first");
        SageApiBridge sage = SageApiBridge.create();
        Object mediaFile = sage.getMediaFile(mediaId);
        if (mediaFile == null) throw new IOException("SageTV MediaFile not found: " + mediaId);
        int count = sage.getNumberOfSegments(mediaFile);
        if (count <= 0) throw new IOException("SageTV MediaFile has no segments");
        if (firstSegment < 0 || firstSegment >= count) firstSegment = 0;
        Object airing = sage.getMediaFileAiring(mediaFile);
        long durationMs = sage.getPlaybackDuration(mediaFile);
        boolean recording = sage.isFileCurrentlyRecording(mediaFile);

        String id = UUID.randomUUID().toString().replace("-", "");
        File dir = new File(root(), id);
        if (!dir.mkdirs()) throw new IOException("Could not create HLS session folder " + dir);
        Session session = new Session(id, mediaId, firstSegment, Math.max(0, audioIndex), startSeconds,
                durationMs, recording, dir, options,playbackContext,playbackToken);
        SESSIONS.put(id, session);
        try {
            launch(session, sage, mediaFile, 0);
            return session;
        } catch (IOException | RuntimeException e) {
            stopProcess(session); SESSIONS.remove(id, session); deleteTree(dir); throw e;
        }
    }


    /** Start one browser HLS decode epoch from the stock MiniDVDPlayer MPEG-PS ring. */
    static Session startDvd(DvdNativeSession source) throws IOException {
        return startDvd(source,StreamOptions.defaults());
    }

    static Session startDvd(DvdNativeSession source, StreamOptions requestedOptions) throws IOException {
        if(source==null||!source.pending()||source.input()==null)throw new IOException("DVD native input is unavailable");
        cleanupStale();
        if(SESSIONS.values().stream().filter(v -> v.running).count() >= Math.max(1,Integer.getInteger("sagetv.webplayer.maxStreams",8)))
            throw new IOException("Streaming session limit reached; stop another recording first");
        String id=UUID.randomUUID().toString().replace("-","");
        File dir=new File(root(),id);if(!dir.mkdirs())throw new IOException("Could not create DVD HLS folder "+dir);
        StreamOptions dvdOptions=(requestedOptions==null?StreamOptions.defaults():requestedOptions).forDvdTranscode();
        Session session=new Session(id,-1,0,0,0.0,0L,false,dir,dvdOptions);
        session.dvdSource=source;session.dvdGeneration=source.generation();session.dvdAudioWireCode=source.requestedAudioWireCode();
        SESSIONS.put(id,session);
        try { launchDvd(session,0); return session; }
        catch(IOException|RuntimeException e){stopProcess(session);SESSIONS.remove(id,session);deleteTree(dir);throw e;}
    }

    private static void launchDvd(final Session session, int fallbackStage) throws IOException {
        synchronized(session){
            if(session.stopRequested)throw new IOException("DVD HLS session was stopped");
            stopProcessOnly(session);clearHlsFiles(session.directory);
            session.error="";session.stderrTail="";session.playlistReady=false;session.segments=0;session.hlsSeconds=0;session.bytesProduced=0;session.endList=false;session.exitCode=Integer.MIN_VALUE;
            session.dvdInputEos=false;session.dvdConverterEnded=false;session.dvdNoPlayableMedia=false;session.dvdReadyNotified=false;
            DvdNativeSession source=session.dvdSource;if(source==null||source.input()==null)throw new IOException("DVD input closed before converter startup");
            session.dvdGeneration=source.generation();session.dvdAudioWireCode=source.requestedAudioWireCode();
            TranscoderManager.Probe detected=TranscoderManager.forDvd(session.options);if(!detected.available)throw new IOException(detected.detail);
            TranscoderManager.Probe probe=fallbackStage>=2?TranscoderManager.softwareFallback(detected,"DVD hardware startup failed"):
                    fallbackStage==1?TranscoderManager.encodeOnlyFallback(detected,"DVD hardware decode/VPP startup failed"):detected;
            session.fallbackStage=fallbackStage;session.softwareFallback=fallbackStage>=2;session.hardwareDecodeFallback=fallbackStage==1;
            session.encoder=probe.videoEncoder;session.accelerator=probe.accelerator;session.decodeAccelerator=probe.decodeAccelerator;session.encoderDetail=probe.detail;session.hardware=probe.hardware;session.hardwareDecode=probe.hardwareDecode;session.hardwareFilters=probe.hardwareFilters;
            session.inputMode="dvd-mpegps-push";session.inputDetail="MiniDVDPlayer generation "+session.dvdGeneration+" -> FFmpeg H.264/AAC HLS; audio="+source.requestedAudioDescription();
            List<String> command=TranscoderManager.buildDvdHlsCommand(probe,session.dvdAudioWireCode,session.directory);
            ProcessBuilder pb=new ProcessBuilder(command);pb.directory(session.directory);
            if(session.stopRequested)throw new IOException("DVD HLS session was stopped");
            session.process=pb.start();session.launchedAt=System.currentTimeMillis();session.firstSegmentAt=0;session.hlsFillRate=0;session.lastSegmentAt=0;session.recentFillRate=0;session.rateSampleAt=0;session.rateSampleSeconds=0;session.metricsAt=0;session.running=true;session.lastUpdatedAt=System.currentTimeMillis();
            startStderrReader(session);startDvdFeeder(session,fallbackStage);startDvdMonitor(session,fallbackStage,session.options==null||session.options.allowFallback);
        }
    }

    private static void appendDvdReplay(Session session, byte[] data, int length) {
        if(session==null||data==null||length<=0||session.playlistReady)return;
        synchronized(session.dvdStartupReplay){
            int remaining=DVD_REPLAY_MAX_BYTES-session.dvdStartupReplay.size();
            if(remaining<=0){session.dvdReplayTruncated=true;return;}
            int keep=Math.min(length,remaining);
            session.dvdStartupReplay.write(data,0,keep);
            session.dvdReplayBytes=session.dvdStartupReplay.size();
            if(keep<length)session.dvdReplayTruncated=true;
        }
    }

    private static byte[] dvdReplaySnapshot(Session session) {
        if(session==null)return new byte[0];
        synchronized(session.dvdStartupReplay){return session.dvdStartupReplay.toByteArray();}
    }

    private static void startDvdFeeder(final Session session, final int fallbackStage){
        final Process process=session.process;final DvdNativeSession source=session.dvdSource;final DvdPushBuffer input=source==null?null:source.input();final long generation=session.dvdGeneration;
        final byte[] replay=fallbackStage>0?dvdReplaySnapshot(session):new byte[0];
        Thread t=new Thread(new Runnable(){public void run(){
            if(process==null||input==null)return;
            byte[] b=new byte[32768];
            try(OutputStream out=process.getOutputStream()){
                if(replay.length>0&&session.process==process&&process.isAlive()){
                    out.write(replay);out.flush();session.bytesInput+=replay.length;
                }
                while(!session.stopRequested&&session.process==process&&process.isAlive()){
                    int n=input.read(b,0,b.length,generation);
                    if(n==DvdPushBuffer.READ_CANCELLED)break;
                    if(n==DvdPushBuffer.READ_EOS){session.dvdInputEos=true;break;}
                    if(n>0){appendDvdReplay(session,b,n);out.write(b,0,n);out.flush();session.bytesInput+=n;}
                }
            }catch(Exception e){if(!session.stopRequested&&session.process==process)session.error="DVD MPEG-PS feeder failed: "+compact(e.getMessage());}
        }},"webplayer-dvd-feed-"+shortId(session.id));t.setDaemon(true);session.feederThread=t;t.start();
    }

    private static void startDvdMonitor(final Session session, final int fallbackStage, final boolean allowFallback){
        final Process monitored=session.process;
        Thread t=new Thread(new Runnable(){public void run(){
            // Hardware stages are replaceable and must fail over promptly.
            // The final software stage is different: an open DVD generation
            // can be a still/looping menu cell whose first publishable HLS
            // segment is not complete until the DVD VM closes the cell.  Do
            // not report that healthy, blocked input as a startup failure.
            long deadline=fallbackStage<2
                    ?System.currentTimeMillis()+Math.min(DVD_STARTUP_TIMEOUT_MS,DVD_HARDWARE_STARTUP_TIMEOUT_MS)
                    :Long.MAX_VALUE;
            long eosDeadline=Long.MAX_VALUE;
            try{
                while(!session.stopRequested&&session.process==monitored){
                    refreshMetrics(session);
                    Process p=session.process;if(p==null)break;
                    if(session.playlistReady)break;
                    if(!p.isAlive()){session.exitCode=p.exitValue();break;}
                    long now=System.currentTimeMillis();
                    // Once the stock DVD VM closes a short cell, FFmpeg has
                    // all bytes it will ever receive for this generation. A
                    // hardware stage that still cannot publish a segment must
                    // fall back promptly; waiting the full timeout for every
                    // stage makes button navigation appear locked.
                    if(session.dvdInputEos&&eosDeadline==Long.MAX_VALUE){
                        // Replaceable hardware attempts get a short grace
                        // period.  The terminal software process must be
                        // allowed to flush its encoder and HLS muxer after
                        // stdin closes; otherwise benign FFmpeg warnings can
                        // be mistaken for a startup failure just before the
                        // first segment is atomically published.
                        eosDeadline=now+(fallbackStage<2?DVD_EOS_FALLBACK_GRACE_MS:DVD_SOFTWARE_EOS_FLUSH_TIMEOUT_MS);
                    }
                    if(now>Math.min(deadline,eosDeadline))break;
                    Thread.sleep(100L);
                }
                if(session.process!=monitored)return;
                // FFmpeg atomically renames the first segment/playlist as it
                // exits.  The process can therefore become non-alive between
                // the loop's metrics snapshot and its liveness check.  Take
                // one final filesystem snapshot before classifying startup;
                // otherwise a clean exit with playable HLS is mislabeled as
                // a decoder warning/failure.
                refreshMetrics(session,true);
                if(!session.stopRequested&&!session.playlistReady){
                    Process p=session.process;
                    boolean dead=p==null||!p.isAlive();
                    String reason=session.error.length()>0?session.error:
                        (session.stderrTail.length()>0?compact(session.stderrTail):
                            (dead?"DVD FFmpeg exited before producing an HLS segment":"DVD hardware pipeline produced no HLS segment during startup"));
                    boolean canFallback=allowFallback&&fallbackStage<2&&(fallbackStage>0||session.hardware);
                    if(canFallback){
                        int next=(fallbackStage==0&&session.hardwareDecode)?1:2;
                        session.dvdFallbackReason=reason;
                        session.error=reason+(next==1?"; retrying hardware encode with software decode/video processing":"; retrying with libx264");
                        launchDvd(session,next);
                        return;
                    }
                    // No later stage can replace the final software process.
                    // Stop it here so status flags cannot claim that a failed
                    // converter is still alive and accepting DVD input.
                    if(p!=null&&p.isAlive()){
                        p.destroy();
                        if(!p.waitFor(2L,TimeUnit.SECONDS)&&p.isAlive())p.destroyForcibly();
                    }
                    session.dvdNoPlayableMedia=true;session.dvdConverterEnded=true;session.running=false;
                    if(session.error.length()==0)session.error=reason;
                    refreshMetrics(session,true);return;
                }
                Process p=session.process;
                if(p!=null){
                    while(!session.stopRequested&&session.process==monitored&&!p.waitFor(250L,TimeUnit.MILLISECONDS))refreshMetrics(session);
                    if(session.process!=monitored||session.stopRequested)return;
                    int rc=p.exitValue();session.exitCode=rc;session.running=false;session.dvdConverterEnded=true;refreshMetrics(session,true);
                    if(!session.playlistReady){session.dvdNoPlayableMedia=true;if(rc!=0&&session.error.length()==0)session.error="DVD FFmpeg produced no browser media (exit "+rc+")";}
                    else if(rc!=0&&session.error.length()==0)session.error="DVD FFmpeg exited with code "+rc;
                }
            }catch(InterruptedException e){Thread.currentThread().interrupt();}
            catch(Exception e){if(!session.stopRequested&&session.process==monitored)session.error=compact(e.getMessage());}
            finally{if(session.process==monitored){if(monitored==null||!monitored.isAlive())session.running=false;session.lastUpdatedAt=System.currentTimeMillis();}}
        }},"webplayer-dvd-monitor-"+shortId(session.id));t.setDaemon(true);session.monitorThread=t;t.start();
    }

    /** Browser consumption, not FFmpeg stdin EOF alone, completes the native DVD drain contract. */
    static boolean updateDvdBrowserProgress(Session s,long relativeTimeMs){
        return updateDvdBrowserProgress(s,relativeTimeMs,false);
    }
    static boolean updateDvdBrowserProgress(Session s,long relativeTimeMs,boolean browserEnded){
        if(s==null||s.dvdSource==null)return false;s.dvdBrowserTimeMs=Math.max(0,relativeTimeMs);refreshMetrics(s);
        if(!s.dvdConverterEnded)return false;
        // HTML media timelines may end slightly before the rounded sum of HLS
        // EXTINF durations.  An ended report is authoritative only after the
        // matching HLS session's converter has ended; expectedHlsSession is
        // checked by MiniClientMediaBridge before this method is reached.
        boolean consumed=browserEnded||s.dvdNoPlayableMedia||!s.playlistReady||s.hlsSeconds*1000.0-s.dvdBrowserTimeMs<=550.0;
        if(consumed){DvdPushBuffer input=s.dvdSource.input();if(input!=null)input.markDecoderDrained(s.dvdGeneration);return true;}return false;
    }

    static Session get(String id) {
        if (id == null) return null;
        Session s=SESSIONS.get(id);
        if(s!=null)s.lastAccessAt=System.currentTimeMillis();
        return s;
    }

    static void stopAll() { REAPER.shutdownNow();for(String id:SESSIONS.keySet())stop(id); }

    static boolean stop(String id) {
        Session session = get(id);
        if (session == null) return false;
        synchronized(session){if(session.cleanupQueued)return true;session.cleanupQueued=true;}
        stopProcess(session);
        // Delay deletion so an HLS client with one in-flight segment request can finish cleanly.
        Thread cleanup = new Thread(new Runnable() {
            @Override public void run() {
                try { Thread.sleep(30000L); } catch (InterruptedException ignored) {}
                if (!session.running) {
                    deleteTree(session.directory);
                    SESSIONS.remove(session.id, session);
                }
            }
        }, "webplayer-hls-cleanup-" + id.substring(0, Math.min(8, id.length())));
        cleanup.setDaemon(true);
        cleanup.start();
        return true;
    }

    static File resolveFile(String id, String filename) throws IOException {
        Session s = get(id);
        if (s == null) throw new IOException("Unknown HLS session");
        if (filename == null || filename.length() == 0 || filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new IOException("Invalid HLS filename");
        }
        if (!"stream.m3u8".equals(filename) && !filename.matches("seg_[0-9]{5,}\\.ts")) {
            throw new IOException("HLS file is not allowed");
        }
        File f = new File(s.directory, filename).getCanonicalFile();
        File base = s.directory.getCanonicalFile();
        if (!f.getPath().startsWith(base.getPath() + File.separator)) throw new IOException("Invalid HLS path");
        return f;
    }

    static File waitForFile(String id, String filename, long timeoutMs) throws IOException {
        long end = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        File f = resolveFile(id, filename);
        while ((!f.isFile() || f.length() <= 0) && System.currentTimeMillis() < end) {
            Session s = get(id);
            if (s == null) break;
            if (!s.running && s.error.length() > 0) break;
            try { Thread.sleep(100L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        return f;
    }

    static String json(Session s) {
        if (s == null) return "{}";
        // The producer monitor refreshes metrics continuously. Status requests
        // share a throttled snapshot rather than sorting thousands of files on
        // every browser poll / Jetty worker.
        refreshMetrics(s);
        StringBuilder b = new StringBuilder(1024);
        b.append('{')
            .append("\"session\":\"").append(HttpUtil.json(s.id)).append("\",")
            .append("\"mediaId\":").append(s.mediaId).append(',')
            .append("\"playlistUrl\":\"").append(HttpUtil.json(s.playlistUrl())).append("\",")
            .append("\"transport\":\"").append(s.transport()).append("\",")
            .append("\"streamUrl\":\"").append(HttpUtil.json(s.mediaUrl())).append("\",")
            .append("\"streamSettings\":").append(s.settingsJson()).append(',')
            .append("\"playbackEpoch\":").append(s.playbackToken==null?"null":s.playbackToken.json()).append(',')
            .append("\"playbackSelection\":").append(s.playbackContext==null?"null":s.playbackContext.selectionJson()).append(',')
            .append("\"subtitleQueues\":{\"broadcast\":").append(s.playbackContext==null?"null":s.playbackContext.cues().json(Math.round(s.dvdSource==null?s.startSeconds*1000.0:s.dvdBrowserTimeMs))).append(",\"file\":").append(s.playbackContext==null?"null":s.playbackContext.fileCues().json(Math.round(s.dvdSource==null?s.startSeconds*1000.0:s.dvdBrowserTimeMs))).append("},")
            .append("\"captions\":").append(s.captionTap==null?"null":s.captionTap.json()).append(',')
            .append("\"teletext\":").append(s.teletext==null?"null":s.teletext.diagnosticsJson()).append(',')
            .append("\"teletextSource\":").append(s.teletextPump==null?"null":s.teletextPump.json()).append(',')
            .append("\"dvbBitmap\":").append(s.dvb==null?"null":s.dvb.diagnosticsJson()).append(',')
            .append("\"dvbSource\":").append(s.dvbPump==null?"null":s.dvbPump.json()).append(',')
            .append("\"deliveredBytes\":").append(s.deliveredBytes).append(',')
            .append("\"maxOutputBytes\":").append(s.maxBytes).append(',')
            .append("\"running\":").append(s.running).append(',')
            .append("\"playlistReady\":").append(s.playlistReady).append(',')
            .append("\"segments\":").append(s.segments).append(',')
            .append("\"hlsSeconds\":").append(String.format(Locale.US, "%.3f", s.hlsSeconds)).append(',')
            .append("\"bytesProduced\":").append(s.bytesProduced).append(',')
            .append("\"bytesInput\":").append(s.bytesInput).append(',')
            .append("\"encoder\":\"").append(HttpUtil.json(s.encoder)).append("\",")
            .append("\"accelerator\":\"").append(HttpUtil.json(s.accelerator)).append("\",")
            .append("\"hardware\":").append(s.hardware).append(',')
            .append("\"hardwareDecode\":").append(s.hardwareDecode).append(',')
            .append("\"hardwareFilters\":").append(s.hardwareFilters).append(',')
            .append("\"decodeAccelerator\":\"").append(HttpUtil.json(s.decodeAccelerator)).append("\",")
            .append("\"hardwareDecodeFallback\":").append(s.hardwareDecodeFallback).append(',')
            .append("\"fallbackStage\":").append(s.fallbackStage).append(',')
            .append("\"softwareFallback\":").append(s.softwareFallback).append(',')
            .append("\"inputMode\":\"").append(HttpUtil.json(s.inputMode)).append("\",")
            .append("\"inputDetail\":\"").append(HttpUtil.json(s.inputDetail)).append("\",")
            .append("\"startSeconds\":").append(String.format(Locale.US, "%.3f", s.startSeconds)).append(',')
            .append("\"seekStrategy\":\"").append(HttpUtil.json(s.seekStrategy)).append("\",")
            .append("\"seekRequestedMs\":").append(s.seekRequestedMs).append(',')
            .append("\"seekAnchorMs\":").append(s.seekAnchorMs).append(',')
            .append("\"seekResidualMs\":").append(s.seekResidualMs).append(',')
            .append("\"seekAnchorErrorMs\":").append(s.seekAnchorErrorMs).append(',')
            .append("\"seekIndexSamples\":").append(s.seekIndexSamples).append(',')
            .append("\"seekPacketSize\":").append(s.seekPacketSize).append(',')
            .append("\"durationMs\":").append(s.durationMs).append(',')
            .append("\"activeRecording\":").append(s.activeRecording).append(',')
            .append("\"dvd\":").append(s.dvdSource!=null).append(',')
            .append("\"dvdGeneration\":").append(s.dvdGeneration).append(',')
            .append("\"dvdAudioWireCode\":").append(s.dvdAudioWireCode).append(',')
            .append("\"dvdInputEos\":").append(s.dvdInputEos).append(',')
            .append("\"dvdConverterEnded\":").append(s.dvdConverterEnded).append(',')
            .append("\"dvdNoPlayableMedia\":").append(s.dvdNoPlayableMedia).append(',')
            .append("\"dvdReplayBytes\":").append(s.dvdReplayBytes).append(',')
            .append("\"dvdReplayTruncated\":").append(s.dvdReplayTruncated).append(',')
            .append("\"dvdFallbackReason\":\"").append(HttpUtil.json(s.dvdFallbackReason)).append("\",")
            .append("\"hlsFillRate\":").append(String.format(Locale.US, "%.3f", s.hlsFillRate)).append(',')
            .append("\"recentFillRate\":").append(String.format(Locale.US, "%.3f", s.recentFillRate)).append(',')
            .append("\"lastSegmentAgeMs\":").append(s.lastSegmentAt==0?"null":String.valueOf(Math.max(0,System.currentTimeMillis()-s.lastSegmentAt))).append(',')
            .append("\"threading\":{\"feeder\":").append(s.feederThread!=null&&s.feederThread.isAlive())
            .append(",\"stderrReader\":").append(s.stderrThread!=null&&s.stderrThread.isAlive())
            .append(",\"producerMonitor\":").append(s.monitorThread!=null&&s.monitorThread.isAlive())
            .append(",\"ffmpegProcess\":").append(s.process!=null&&s.process.isAlive()).append("},")
            .append("\"firstSegmentMs\":").append(s.firstSegmentAt == 0 ? "null" : String.valueOf(s.firstSegmentAt - s.createdAt)).append(',')
            .append("\"producerAgeMs\":").append(s.launchedAt == 0 ? 0 : Math.max(0L, System.currentTimeMillis() - s.launchedAt)).append(',')
            .append("\"endList\":").append(s.endList).append(',')
            .append("\"exitCode\":").append(s.exitCode == Integer.MIN_VALUE ? "null" : String.valueOf(s.exitCode)).append(',')
            .append("\"error\":\"").append(HttpUtil.json(s.error)).append("\",")
            .append("\"stderrTail\":\"").append(HttpUtil.json(s.stderrTail)).append("\"")
            .append('}');
        return b.toString();
    }

    private static void launch(final Session session, final SageApiBridge sage, final Object mediaFile, final int fallbackStage) throws IOException {
        synchronized (session) {
        if (session.stopRequested) throw new IOException("HLS session was stopped");
        stopProcessOnly(session);
        clearHlsFiles(session.directory);
        session.error = "";
        session.stderrTail = "";
        session.subtitleBranchError = "";
        OrdinarySubtitleSession priorOrdinary=session.ordinarySubtitles;session.ordinarySubtitles=null;if(priorOrdinary!=null)priorOrdinary.stop();
        TeletextSourcePump priorTeletextPump=session.teletextPump;session.teletextPump=null;if(priorTeletextPump!=null)priorTeletextPump.stop();
        session.teletext=null;
        DvbSourcePump priorDvbPump=session.dvbPump;session.dvbPump=null;if(priorDvbPump!=null)priorDvbPump.stop();
        session.dvb=null;
        session.playlistReady = false;
        session.segments = 0;
        session.hlsSeconds = 0;
        session.bytesProduced = 0;
        session.endList = false;
        session.exitCode = Integer.MIN_VALUE;

        InputPlan plan = createInputPlan(session, sage, mediaFile);
        session.sourceSage=sage;session.sourceMediaFile=mediaFile;session.sourceAbsoluteStart=plan.startAbsoluteByte;
        session.seekStrategy=plan.seekStrategy;session.seekRequestedMs=Math.round(session.startSeconds*1000.0);
        session.seekAnchorMs=plan.seekAnchorMs;session.seekResidualMs=plan.postInputSeekMs;
        session.seekAnchorErrorMs=Math.abs(session.seekRequestedMs-session.seekAnchorMs);session.seekIndexSamples=plan.seekSamples;session.seekPacketSize=plan.seekPacketSize;
        if(session.options!=null && session.streamPlan==null) {
            session.sourceInfo = probeSource(session,sage,mediaFile);
            session.streamPlan = new StreamPlan(session.options,session.sourceInfo);
        }
        if(session.options!=null && ("teletext".equals(session.options.captions)||"stv".equals(session.options.captionAuthority)) && session.playbackContext!=null && session.playbackToken!=null){
            session.teletext=new TeletextSubtitleSession(session.playbackContext,session.playbackToken,Math.round(session.startSeconds*1000.0));
            session.teletext.seed(session.sourceInfo);
            session.teletext.configure(true,session.options.captionPage,session.options.captionLanguage);
            session.teletextPump=TeletextSourcePump.start(session,sage,mediaFile,session.teletext,plan.startAbsoluteByte);
        }
        if(session.playbackContext!=null && session.playbackToken!=null && session.options!=null && (session.options.dvbEnabled()||session.dvbCommandPid>=0)){
            int requestedPid=session.dvbCommandPid>=0?session.dvbCommandPid:session.options.captionPid;
            int requestedPage=session.dvbCommandPid>=0?-1:session.options.captionCompositionPage;
            String requestedLanguage=session.dvbCommandPid>=0?"":session.options.captionLanguage;
            startDvbDecoder(session,sage,mediaFile,plan.startAbsoluteByte,requestedPid,requestedPage,requestedLanguage);
        }
        TranscoderManager.Probe detected = session.streamPlan==null ? TranscoderManager.probe() : TranscoderManager.forStream(session.streamPlan);
        if (!detected.available) throw new IOException(detected.detail);
        final TranscoderManager.Probe probe = fallbackStage >= 2 ? TranscoderManager.softwareFallback(detected, "HLS hardware startup failed") :
                fallbackStage == 1 ? TranscoderManager.encodeOnlyFallback(detected, "hardware decode/VPP startup failed") : detected;
        session.fallbackStage=fallbackStage;
        session.softwareFallback = fallbackStage>=2;
        session.hardwareDecodeFallback = fallbackStage==1;
        session.encoder = probe.videoEncoder;
        session.accelerator = probe.accelerator;
        session.decodeAccelerator = probe.decodeAccelerator;
        session.encoderDetail = probe.detail;
        session.hardware = probe.hardware;
        session.hardwareDecode = probe.hardwareDecode;
        session.hardwareFilters = probe.hardwareFilters;
        if(session.streamPlan!=null && session.streamPlan.subtitle!=null && session.playbackContext!=null && session.playbackToken!=null){
            try{
                if(plan.pipeInput||plan.concatInput)session.subtitleBranchError="ordinary subtitle extraction requires a directly authorized local source in this build";
                else session.ordinarySubtitles=OrdinarySubtitleSession.start(session.playbackContext,session.playbackToken,session.streamPlan.subtitle,plan.inputFile,probe.executable,Math.round(session.startSeconds*1000.0),session.options.subtitleOffsetMs);
            }catch(Exception e){session.subtitleBranchError=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
        }

        session.inputMode = plan.mode;
        session.inputDetail = plan.detail;
        List<String> command = session.streamPlan==null ? TranscoderManager.buildHlsCommand(probe, plan.inputFile, plan.concatInput,
                plan.pipeInput, plan.pipeInput ? 0.0 : session.startSeconds, session.audioIndex, session.directory) :
                StreamCommand.build(probe,plan.inputFile,plan.concatInput,plan.pipeInput,plan.pipeInput?0:session.startSeconds,session.directory,session.streamPlan);
        if(plan.pipeInput && plan.postInputSeekMs>0) addPostInputSeek(command,plan.postInputSeekMs/1000.0);
        if(session.streamPlan!=null && session.streamPlan.subtitle!=null && session.playbackToken!=null){
            try {
                if(plan.pipeInput||plan.concatInput)session.subtitleBranchError="selected subtitle source preservation is not yet available for pipe/concat input";
                else if(session.streamPlan.subtitle.sidecarFile==null)session.subtitleBranch=SubtitleSourceBranch.start(plan.inputFile,session.streamPlan.subtitle,session.directory,probe.executable,session.playbackToken,()->session.activeRecording&&!session.stopRequested);
            } catch(Exception e){session.subtitleBranchError=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
        }
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(session.directory);
        if(session.options==null || !session.options.captionsEnabled())
            pb.redirectOutput(ProcessBuilder.Redirect.to(new File(System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "NUL" : "/dev/null")));
        if (session.stopRequested) throw new IOException("HLS session was stopped");
        session.process = pb.start();
        session.launchedAt = System.currentTimeMillis();
        session.firstSegmentAt = 0L; session.hlsFillRate = 0.0;
        session.lastSegmentAt=0L;session.recentFillRate=0.0;session.rateSampleAt=0L;session.rateSampleSeconds=0.0;session.metricsAt=0L;
        session.running = true;
        session.lastUpdatedAt = System.currentTimeMillis();

        startStderrReader(session);
        if(session.options!=null && session.options.captionsEnabled()) {
            session.captionTap = new A53CaptionTap(new File(session.directory,"captions.bin"));
            session.captionTap.start(session.process.getInputStream());
        }
        if (plan.pipeInput) startFeeder(session, sage, mediaFile, plan.startAbsoluteByte);
        else {
            try { if (session.process.getOutputStream() != null) session.process.getOutputStream().close(); } catch (Exception ignored) {}
        }
        // Never overwrite an already exposed continuous byte stream during fallback.
        // The browser requests a fresh session instead, preserving byte-offset identity.
        startMonitor(session, sage, mediaFile, fallbackStage, probe.hardware && !session.isTs() &&
                (session.options==null||session.options.allowFallback));
        }
    }

    private static void startDvbDecoder(Session session,SageApiBridge sage,Object mediaFile,long absoluteStart,int pid,int compositionPage,String language){
        DvbSubtitleSession decoder=new DvbSubtitleSession(session.playbackContext,session.playbackToken,Math.round(session.startSeconds*1000.0));
        decoder.seed(session.sourceInfo);
        decoder.configure(true,pid,compositionPage,language);
        session.dvb=decoder;
        session.dvbPump=DvbSourcePump.start(session,sage,mediaFile,decoder,absoluteStart);
    }

    /** Apply ordinary-video media command 36/type 1 without requiring DVB to be pre-enabled in the profile. */
    static int selectDvbSourcePid(Session session,int sourcePid){
        if(session==null||session.playbackContext==null||session.playbackToken==null)return -1;
        synchronized(session){
            if(sourcePid==8192){session.dvbCommandPid=-1;if(session.dvb!=null)session.dvb.selectSourcePid(-1);return 0;}
            if(sourcePid<0||sourcePid>8191)return -1;
            session.dvbCommandPid=sourcePid;
            if(session.dvb!=null)return session.dvb.requestSourcePid(sourcePid)?0:-1;
            if(session.sourceSage==null||session.sourceMediaFile==null)return -1;
            DvbSubtitleSession decoder=new DvbSubtitleSession(session.playbackContext,session.playbackToken,Math.round(session.startSeconds*1000.0));
            decoder.seed(session.sourceInfo);
            boolean accepted=decoder.requestSourcePid(sourcePid);
            if(!accepted)return -1;
            session.dvb=decoder;
            session.dvbPump=DvbSourcePump.start(session,session.sourceSage,session.sourceMediaFile,decoder,session.sourceAbsoluteStart);
            return 0;
        }
    }

    private static MediaProbe.Info probeSource(Session s,SageApiBridge sage,Object mf) throws IOException {
        File ff=TranscoderManager.findExecutable();
        if(ff==null)throw new IOException("FFmpeg executable not found");
        String ffmpeg=ff.getAbsolutePath();
        File source=sage.getFileForSegment(mf,s.firstSegment);
        if(source!=null&&source.isFile()&&source.canRead())return MediaProbe.read(source,ffmpeg);
        // A bounded sample avoids asking ffprobe to connect to arbitrary browser URLs.
        File sample=new File(s.directory,"probe-input.ts");
        try {
            if(source==null){MediaProbe.Info unknown=new MediaProbe.Info();unknown.warning="Source metadata unavailable; using conservative transcoding";return unknown;}
            try(SeekableMediaSource in=MediaSourceFactory.open(sage,source);OutputStream out=new FileOutputStream(sample)) {
                byte[] b=new byte[32768];long remaining=Math.min(4L*1024L*1024L,Math.max(0,in.length()));
                while(remaining>0) {int n=in.read(b,0,(int)Math.min(b.length,remaining));if(n<=0)break;out.write(b,0,n);remaining-=n;}
            }
            return MediaProbe.read(sample,ffmpeg);
        } finally {sample.delete();}
    }

    private static final class InputPlan {
        final File inputFile;
        final boolean concatInput;
        final boolean pipeInput;
        final long startAbsoluteByte;
        final long seekAnchorMs;
        final long postInputSeekMs;
        final int seekSamples;
        final int seekPacketSize;
        final String seekStrategy;
        final String mode;
        final String detail;
        InputPlan(File inputFile, boolean concatInput, boolean pipeInput, long startAbsoluteByte,
                  long seekAnchorMs,long postInputSeekMs,int seekSamples,int seekPacketSize,String seekStrategy,
                  String mode, String detail) {
            this.inputFile = inputFile; this.concatInput = concatInput; this.pipeInput = pipeInput;
            this.startAbsoluteByte = Math.max(0,startAbsoluteByte);this.seekAnchorMs=Math.max(0,seekAnchorMs);
            this.postInputSeekMs=Math.max(0,postInputSeekMs);this.seekSamples=Math.max(0,seekSamples);
            this.seekPacketSize=Math.max(0,seekPacketSize);this.seekStrategy=seekStrategy==null?"":seekStrategy;
            this.mode = mode; this.detail = detail;
        }
    }

    private static InputPlan createInputPlan(Session session, SageApiBridge sage, Object mediaFile) throws IOException {
        int count = sage.getNumberOfSegments(mediaFile);
        List<File> files = new ArrayList<File>();
        boolean allDirect = true;
        for (int i = session.firstSegment; i < count; i++) {
            File f = sage.getFileForSegment(mediaFile, i);
            if (f == null) continue;
            files.add(f);
            if (!(f.isFile() && f.canRead())) allDirect = false;
        }
        if (files.isEmpty()) throw new IOException("SageTV returned no physical media segments");

        // Local completed inputs use FFmpeg's timestamp seek directly. There is
        // no reason to convert time to bytes when the demuxer can seek itself.
        if (allDirect && !session.activeRecording) {
            if (files.size() == 1) return new InputPlan(files.get(0), false, false, 0,
                    Math.round(session.startSeconds*1000.0),0,0,0,"ffmpeg-timestamp",
                    "direct-file", files.get(0).getPath());
            File concat = new File(session.directory, "input.ffconcat");
            StringBuilder body = new StringBuilder("ffconcat version 1.0\n");
            for (File f : files) body.append("file '").append(f.getAbsolutePath().replace("'", "'\\''")).append("'\n");
            Files.write(concat.toPath(), body.toString().getBytes(StandardCharsets.UTF_8));
            return new InputPlan(concat, true, false, 0,Math.round(session.startSeconds*1000.0),0,0,0,
                    "ffmpeg-concat-timestamp","direct-concat", files.size() + " SageTV segments");
        }

        long requestedMs=Math.round(session.startSeconds*1000.0);
        MpegTsSeekIndex.Result seek=MpegTsSeekIndex.resolve(new MpegTsSeekIndex.Sources(){
            public int segmentCount(){return sage.getNumberOfSegments(mediaFile)-session.firstSegment;}
            public SeekableMediaSource open(int index)throws IOException{
                File f=sage.getFileForSegment(mediaFile,session.firstSegment+index);
                return f==null?null:MediaSourceFactory.open(sage,f);
            }
        },requestedMs);
        long offset=seek.validated?seek.absoluteByte:0;
        long anchor=seek.validated?seek.anchorMs:0;
        long residual=seek.validated?seek.residualMs:requestedMs;
        String strategy=seek.validated?"mpegts-pts-index":"pipe-start+ffmpeg-timestamp";
        String detail="SageTV MediaServer/direct source -> FFmpeg stdin; " + files.size() + " segment(s); " +
                (seek.validated?(seek.reason+", anchor="+seek.anchorMs+"ms, residual="+seek.residualMs+"ms"):
                        ("no validated byte seek ("+seek.reason+"); feed from byte zero"));
        return new InputPlan(null,false,true,offset,anchor,residual,seek.samples,seek.packetSize,strategy,
                "sage-mediaserver-pipe",detail);
    }

    /** Apply an output-timeline seek after the pipe input. This discards the
     * small residual between the validated PTS byte anchor and requested time. */
    static void addPostInputSeek(List<String> command,double seconds){
        if(command==null||seconds<=0||!Double.isFinite(seconds))return;
        int i=command.indexOf("-i");if(i<0||i+1>=command.size())return;
        int at=i+2;command.add(at,"-ss");command.add(at+1,String.format(Locale.US,"%.3f",seconds));
    }

    private static void startFeeder(final Session session, final SageApiBridge sage, final Object mediaFile, final long absoluteStart) {
        final Process process = session.process;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                if (process == null) return;
                try (OutputStream out = process.getOutputStream()) {
                    RecordingFollower.copy(new RecordingFollower.Sources() {
                        public int segmentCount() { return sage.getNumberOfSegments(mediaFile); }
                        public boolean isRecording() { return sage.isFileCurrentlyRecording(mediaFile); }
                        public SeekableMediaSource open(int index) throws IOException {
                            File f = sage.getFileForSegment(mediaFile, index);
                            return f == null ? null : MediaSourceFactory.open(sage, f);
                        }
                    }, session.firstSegment, absoluteStart, out,
                        () -> !session.stopRequested && session.process == process && process.isAlive(),
                        n -> { if (session.process == process) session.bytesInput += n; },
                        recording -> {
                            if (session.process == process) {
                                boolean justFinished = session.activeRecording && !recording;
                                session.activeRecording = recording;
                                if (justFinished) {
                                    long completedDuration = sage.getPlaybackDuration(mediaFile);
                                    if (completedDuration > 0) session.durationMs = completedDuration;
                                }
                            }
                        });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    if (!session.stopRequested && session.process == process)
                        session.error = "SageTV input feeder failed: " + compact(e.getMessage());
                }
            }
        }, "webplayer-hls-feed-" + shortId(session.id));
        t.setDaemon(true); session.feederThread = t; t.start();
    }

    private static void startStderrReader(final Session session) {
        final Process sourceProcess = session.process;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                Process p = sourceProcess;
                if (p == null) return;
                StringBuilder tail = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        tail.append(line).append('\n');
                        if (tail.length() > 12000) tail.delete(0, tail.length() - 10000);
                        if (session.process == sourceProcess) session.stderrTail = tail.toString();
                    }
                } catch (Exception ignored) {}
            }
        }, "webplayer-hls-stderr-" + shortId(session.id));
        t.setDaemon(true);
        session.stderrThread = t;
        t.start();
    }

    // Compatibility overload retained for existing tests/tools.
    private static void startMonitor(final Session session, final SageApiBridge sage, final Object mediaFile, final boolean mayFallback) {
        startMonitor(session,sage,mediaFile,session==null?0:session.fallbackStage,mayFallback);
    }

    private static void startMonitor(final Session session, final SageApiBridge sage, final Object mediaFile, final int fallbackStage, final boolean mayFallback) {
        final Process monitored = session.process;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                long deadline = System.currentTimeMillis() + STARTUP_TIMEOUT_MS;
                boolean fallbackTried = fallbackStage>=2;
                try {
                    while (!session.stopRequested && session.process == monitored) {
                        refreshMetrics(session);
                        if(checkResourceLimits(session)) return;
                        Process p = session.process;
                        if (p == null) break;
                        if (session.playlistReady) break;
                        if (!p.isAlive()) {
                            session.exitCode = p.exitValue();
                            break;
                        }
                        if (System.currentTimeMillis() > deadline) break;
                        Thread.sleep(200L);
                    }
                    if (session.process != monitored) return;
                    if (!session.stopRequested && !session.playlistReady && mayFallback && !fallbackTried) {
                        String reason = session.error.length() > 0 ? session.error : "hardware pipeline produced no HLS segment during startup";
                        if(fallbackStage==0 && session.hardwareDecode){
                            session.error = reason + "; retrying hardware encode with software decode/video processing";
                            launch(session, sage, mediaFile, 1);
                        }else{
                            session.error = reason + "; retrying with libx264";
                            launch(session, sage, mediaFile, 2);
                        }
                        return;
                    }
                    Process p = session.process;
                    if (p != null) {
                        // Keep observing the producer after its first segment.
                        // waitFor() alone left ongoing progress invisible until
                        // each browser request did a full directory scan.
                        while(!session.stopRequested&&session.process==monitored&&!p.waitFor(500L,TimeUnit.MILLISECONDS)) {
                            refreshMetrics(session);
                            if(checkResourceLimits(session))return;
                        }
                        if (session.process != monitored || session.stopRequested) return;
                        int rc=p.exitValue();
                        session.exitCode = rc;
                        session.running = false;
                        refreshMetrics(session, true);
                        if (rc != 0 && !session.stopRequested && session.error.length() == 0) {
                            session.error = "FFmpeg exited with code " + rc;
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    if (!session.stopRequested && session.process == monitored) session.error = compact(e.getMessage());
                } finally {
                    if (session.process == monitored) {
                        if (monitored == null || !monitored.isAlive()) session.running = false;
                        session.lastUpdatedAt = System.currentTimeMillis();
                    }
                }
            }
        }, "webplayer-hls-monitor-" + shortId(session.id));
        t.setDaemon(true);
        session.monitorThread = t;
        t.start();
    }

    static void refreshMetrics(Session s) { refreshMetrics(s, false); }

    private static void refreshMetrics(Session s, boolean force) {
        if (s == null) return;
        // Synchronize on the dedicated playlist object, NOT the session lifecycle
        // lock (launch/stop can wait for a process). No thread holds both locks.
        synchronized(s.playlist) {
            long now=System.currentTimeMillis();
            if(!force && now-s.metricsAt<200L)return;
            if(s.isTs()) { refreshTsMetrics(s,now); return; }
            // Read one atomic manifest snapshot. Its published entries, not a
            // filesystem directory listing, are the only playable segments.
            String text="";
            try { if(s.playlist.isFile()) text=new String(Files.readAllBytes(s.playlist.toPath()),StandardCharsets.UTF_8); }
            catch(IOException e) { return; } // retain last complete snapshot on rename races
            int count=0;long bytes=0L;
            for(String line:text.split("\\r?\\n")) {
                line=line.trim();
                if(line.matches("seg_[0-9]{5,}\\.ts")) { count++;bytes+=new File(s.directory,line).length(); }
            }
            double produced=parsePlaylistSeconds(text);
            if(produced>s.hlsSeconds+0.01)s.lastSegmentAt=now;
            s.segments=count;s.bytesProduced=bytes+text.length();
            s.playlistReady=count>0;s.hlsSeconds=produced;s.endList=text.contains("#EXT-X-ENDLIST");
            if(s.playlistReady&&s.firstSegmentAt==0)s.firstSegmentAt=now;
            if(s.rateSampleAt==0){s.rateSampleAt=s.launchedAt>0?s.launchedAt:now;s.rateSampleSeconds=0;}
            if(now-s.rateSampleAt>=2000L) {
                s.recentFillRate=Math.max(0,(produced-s.rateSampleSeconds)*1000/(now-s.rateSampleAt));
                s.rateSampleAt=now;s.rateSampleSeconds=produced;
            }
            Process p=s.process;
            if(p!=null) s.running=p.isAlive();
            // Average rate is frozen at completion instead of decaying throughout
            // playback of an already transcoded recording.
            if(s.running||s.hlsFillRate==0) s.hlsFillRate=s.launchedAt>0?produced*1000/Math.max(1,now-s.launchedAt):0;
            s.metricsAt=now;s.lastUpdatedAt=now;
        }
    }

    private static void refreshTsMetrics(Session s,long now) {
        long bytes=s.continuous.length();
        String progress="";
        File f=new File(s.directory,"progress.log");
        try(RandomAccessFile r=new RandomAccessFile(f,"r")) {
            long from=Math.max(0,r.length()-65536);r.seek(from);byte[] b=new byte[(int)(r.length()-from)];r.readFully(b);
            progress=new String(b,StandardCharsets.UTF_8);
        }catch(IOException ignored){}
        double produced=parseProgressSeconds(progress);
        if(bytes>s.bytesProduced)s.lastSegmentAt=now;
        s.bytesProduced=bytes;s.hlsSeconds=Math.max(s.hlsSeconds,produced);s.segments=0;
        s.playlistReady=bytes>=188*8;
        if(s.playlistReady&&s.firstSegmentAt==0)s.firstSegmentAt=now;
        if(s.rateSampleAt==0){s.rateSampleAt=s.launchedAt>0?s.launchedAt:now;s.rateSampleSeconds=0;}
        if(now-s.rateSampleAt>=2000L){s.recentFillRate=Math.max(0,(produced-s.rateSampleSeconds)*1000/(now-s.rateSampleAt));s.rateSampleAt=now;s.rateSampleSeconds=produced;}
        Process p=s.process;if(p!=null)s.running=p.isAlive();
        if(s.running||s.hlsFillRate==0)s.hlsFillRate=s.launchedAt>0?produced*1000/Math.max(1,now-s.launchedAt):0;
        s.endList=!s.running && s.exitCode==0;
        s.metricsAt=now;s.lastUpdatedAt=now;
    }

    static double parseProgressSeconds(String text) {
        double seconds=0;
        for(String line:text.split("\\r?\\n")) {
            if(line.startsWith("out_time_us="))try{seconds=Math.max(seconds,Long.parseLong(line.substring(12).trim())/1000000.0);}catch(NumberFormatException ignored){}
        }
        return seconds;
    }

    private static boolean checkResourceLimits(Session s) {
        long extra=new File(s.directory,"captions.bin").length()+new File(s.directory,"progress.log").length()+new File(s.directory,"subtitle-source.ts").length()+new File(s.directory,"subtitle-source.mkv").length();
        String reason=null;
        if(s.bytesProduced+extra>s.maxBytes)reason="Session output limit reached (sagetv.webplayer.streamMaxBytes). Seek/restart or raise the administrator limit.";
        else if(s.directory.getUsableSpace()<128L*1024L*1024L)reason="Streaming stopped to protect server disk space (less than 128 MiB free).";
        else if(System.currentTimeMillis()-s.lastAccessAt>Math.max(30000L,Long.getLong("sagetv.webplayer.streamIdleMs",120000L)))reason="Streaming session expired because the browser stopped polling.";
        if(reason==null)return false;
        s.error=reason;stopProcess(s);return true;
    }

    static double parsePlaylistSeconds(String text) {
        double total = 0.0;
        for (String line : text.split("\\r?\\n")) {
            if (!line.startsWith("#EXTINF:")) continue;
            String value = line.substring(8);
            int comma = value.indexOf(','); if (comma >= 0) value = value.substring(0, comma);
            try {
                double seconds = Double.parseDouble(value.trim());
                if (!Double.isNaN(seconds) && !Double.isInfinite(seconds) && seconds > 0) total += seconds;
            } catch (NumberFormatException ignored) {}
        }
        return total;
    }

    private static File root() throws IOException {
        String explicit = firstNonEmpty(System.getProperty("sagetv.webplayer.hlsDir"), System.getenv("SAGETV_WEBPLAYER_HLS_DIR"));
        File root = explicit == null ? new File(System.getProperty("java.io.tmpdir"), "sagetv-webplayer-hls") : new File(explicit);
        if (!root.exists() && !root.mkdirs()) throw new IOException("Could not create HLS root " + root);
        if (!root.isDirectory() || !root.canWrite()) throw new IOException("HLS root is not writable: " + root);
        return root.getCanonicalFile();
    }

    private static void clearHlsFiles(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String n = f.getName();
            if (n.equals("stream.ts") || n.equals("progress.log") || n.equals("captions.bin") || n.equals("stream.m3u8") || n.matches("seg_[0-9]{5,}\\.ts") || n.endsWith(".tmp")) f.delete();
        }
    }

    private static void cleanupStale() {
        try {
            File base = root();
            File[] dirs = base.listFiles();
            if (dirs == null) return;
            long now = System.currentTimeMillis();
            for (File d : dirs) {
                if (!d.isDirectory()) continue;
                Session active = SESSIONS.get(d.getName());
                if (active != null && active.running) continue;
                if (now - d.lastModified() > STALE_MS) { deleteTree(d); SESSIONS.remove(d.getName()); }
            }
        } catch (Exception ignored) {}
    }

    private static void stopProcess(Session s) {
        s.stopRequested = true;
        synchronized (s) { stopProcessOnly(s); s.running = false; }
    }

    private static void stopProcessOnly(Session s) {
        Process p = s.process;
        s.process = null;
        Thread feeder = s.feederThread; s.feederThread = null;
        if (feeder != null) feeder.interrupt();
        if (p != null) {
            if (p.isAlive()) {
                p.destroy();
                try {
                    if (!p.waitFor(2L, TimeUnit.SECONDS) && p.isAlive()) {
                        p.destroyForcibly(); p.waitFor(2L, TimeUnit.SECONDS);
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); p.destroyForcibly(); }
            }
            try { p.getOutputStream().close(); } catch (Exception ignored) {}
            try { p.getInputStream().close(); } catch (Exception ignored) {}
        }
        A53CaptionTap tap=s.captionTap;s.captionTap=null;
        if(tap!=null)try{tap.close();}catch(IOException ignored){}
        SubtitleSourceBranch branch=s.subtitleBranch;s.subtitleBranch=null;if(branch!=null)branch.stop();
        TeletextSourcePump teletextPump=s.teletextPump;s.teletextPump=null;if(teletextPump!=null)teletextPump.stop();
        s.teletext=null;
        DvbSourcePump dvbPump=s.dvbPump;s.dvbPump=null;if(dvbPump!=null)dvbPump.stop();
        OrdinarySubtitleSession ordinary=s.ordinarySubtitles;s.ordinarySubtitles=null;if(ordinary!=null)ordinary.stop();
        s.dvb=null;
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles(); if (children != null) for (File c : children) deleteTree(c);
        }
        try { file.delete(); } catch (Exception ignored) {}
    }

    private static String compact(String s) { return s == null ? "unknown error" : s.replace('\r',' ').replace('\n',' ').trim(); }
    private static String shortId(String s) { return s == null ? "session" : s.substring(0, Math.min(8, s.length())); }
    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return null;
    }
}
