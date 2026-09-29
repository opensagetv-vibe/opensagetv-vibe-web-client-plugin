package org.opensagetv.webplayer;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Media command adapter modeled on Vibe core MediaCmd, with browser TS/HLS as the player backend. */
final class MiniClientMediaBridge {
    private static final int INIT=0, DEINIT=1, OPENURL=16, GETMEDIATIME=17, SETMUTE=18, STOP=19,
            PAUSE=20, PLAY=21, FLUSH=22, PUSHBUFFER=23, GETVIDEORECT=24, SETVIDEORECT=25,
            GETVOLUME=26, SETVOLUME=27, FRAMESTEP=28, SEEK=29, SETRATE=30,
            DVD_NEWCELL=32, DVD_CLUT=33, DVD_SPUCTRL=34, DVD_STC=35, DVD_STREAM=36, DVD_FORMAT=37;

    private final MiniClientSession owner;
    private final SageApiBridge sage;
    private final PlaybackSessionContext playbackContext=new PlaybackSessionContext();
    private final DvdNativeSession dvdSession=new DvdNativeSession();
    private volatile HlsSessionManager.Session hls;
    private volatile long dvdBrowserGeneration=-1L;
    private volatile long dvdStartingGeneration=-1L;
    private long dvdStartSerial;
    private StreamOptions configured = StreamOptions.defaults();
    private StreamOptions activeOptions = configured;
    private boolean transportFallback;
    private volatile int mediaId;
    private volatile int segment;
    private volatile double startSeconds;
    private volatile long durationMs;
    private volatile long browserTimeMs;
    private volatile long lastProvenBrowserTimeMs;
    private volatile long browserClockRegressions;
    private volatile float volume=1.0f;
    private volatile boolean paused;
    private volatile boolean muted;
    private volatile boolean boundsKnown;
    private volatile String openUrl="";
    private volatile int sourceX,sourceY,sourceW,sourceH,destX,destY,destW,destH;
    private final LegacyExtenderCaptionBridge legacyCaptions;
    private volatile long legacyCaptionCursor;
    private volatile boolean subtitleCallbacksEnabled;
    private volatile int sageTvCcState=-1;
    private volatile boolean sageTvCcStateSeen;

    MiniClientMediaBridge(MiniClientSession owner) {
        this.owner=owner;
        SageApiBridge s;
        try { s=SageApiBridge.create(); } catch(Exception e) { s=null; }
        this.sage=s;
        this.legacyCaptions=new LegacyExtenderCaptionBridge((pts,duration,payload,flags)->owner.postSubtitleInfo(pts,duration,payload,flags));
    }

    byte[] handle(int cmd, byte[] data) {
        try {
            switch(cmd) {
                case INIT:
                    int videoFormat=data!=null&&data.length>=4?MiniClientSession.readInt(data,0):0;
                    closeDvdPlayback(true);
                    return intBytes(dvdSession.init(videoFormat));
                case DEINIT:
                    closeDvdPlayback(true); dvdSession.close(); closePlayback(); mediaId=0;
                    return intBytes(1);
                case OPENURL:
                    openUrl=readOpenUrl(data);
                    if(openUrl.toLowerCase(Locale.US).startsWith("push:dvd")){
                        // Compatibility transport: it is still the same native DVD
                        // session and must not be resolved as a SageTV MediaFile.
                        return intBytes(1);
                    }
                    dvdSession.cancelPendingForOrdinaryOpenUrl();
                    open(openUrl,0.0);
                    return intBytes(1);
                case GETMEDIATIME:
                    return intBytes((int)Math.max(0L,dvdSession.pending()?dvdSession.mediaTimeMs():browserTimeMs));
                case SETMUTE:
                    muted=data.length>=4 && MiniClientSession.readInt(data,0)!=0;
                    owner.enqueue("{\"type\":\"media\",\"action\":\"mute\",\"value\":"+muted+"}");
                    return intBytes(1);
                case STOP:
                    paused=true;
                    if(dvdSession.pending()) closeDvdPlayback(true);
                    else { closePlayback(); mediaId=0; }
                    owner.enqueue("{\"type\":\"media\",\"action\":\"stop\"}");
                    return intBytes(1);
                case PAUSE:
                    paused=true; owner.enqueue("{\"type\":\"media\",\"action\":\"pause\"}"); return intBytes(1);
                case PLAY:
                    paused=false; owner.enqueue("{\"type\":\"media\",\"action\":\"play\"}"); return intBytes(1);
                case FLUSH:
                    if(dvdSession.pending()){
                        boolean preserveStill=dvdSession.epochHasVideo();
                        if(preserveStill)owner.enqueue("{\"type\":\"dvd\",\"action\":\"freeze\",\"generation\":"+dvdSession.generation()+",\"stillCandidate\":"+dvdSession.epochStillCandidate()+"}");
                        closeDvdPlayback(false);
                        int rv=dvdSession.flush();
                        owner.enqueue("{\"type\":\"dvd\",\"action\":\"flush\",\"generation\":"+dvdSession.generation()+"}");
                        return intBytes(rv);
                    }
                    PlaybackSessionContext.Token flushToken=playbackContext.beginFlush();
                    HlsSessionManager.Session flushSession=hls;if(flushSession!=null){flushSession.playbackToken=flushToken;if(flushSession.teletext!=null)flushSession.teletext.updateToken(flushToken,browserTimeMs);if(flushSession.dvb!=null)flushSession.dvb.updateToken(flushToken,browserTimeMs);}
                    owner.enqueue("{\"type\":\"media\",\"action\":\"flush\"}"); return intBytes(1);
                case PUSHBUFFER:
                    if(dvdSession.pending()) {
                        int rv=dvdSession.push(data);
                        if(dvdSession.active()&&dvdSession.epochPushedBytes()>0&&hls==null)scheduleDvdEpoch();
                        return intBytes(rv);
                    }
                    // Ordinary browser mode remains PULL-only. Returning generous free
                    // space preserves the legacy bandwidth-probe fallback.
                    return intBytes(16*1024*1024);
                case GETVIDEORECT:
                    return shorts((short)owner.browserVideoWidth(),(short)owner.browserVideoHeight());
                case SETVIDEORECT:
                    if(data.length>=32){
                        boundsKnown=true;
                        sourceX=MiniClientSession.readInt(data,0);sourceY=MiniClientSession.readInt(data,4);
                        sourceW=MiniClientSession.readInt(data,8);sourceH=MiniClientSession.readInt(data,12);
                        destX=MiniClientSession.readInt(data,16);destY=MiniClientSession.readInt(data,20);
                        destW=MiniClientSession.readInt(data,24);destH=MiniClientSession.readInt(data,28);
                        videoBoundsEvent();
                    }
                    return intBytes(0);
                case GETVOLUME:
                    return intBytes(Math.round(volume*65535f));
                case SETVOLUME:
                    if(data.length>=4) volume=Math.max(0f,Math.min(1f,MiniClientSession.readInt(data,0)/65535f));
                    owner.enqueue("{\"type\":\"media\",\"action\":\"volume\",\"value\":"+String.format(Locale.US,"%.5f",volume)+"}");
                    return intBytes(Math.round(volume*65535f));
                case FRAMESTEP:
                    return intBytes(0);
                case SEEK:
                    if(data.length>=8){
                        long ms=MiniClientSession.readLong(data,0);
                        if(dvdSession.pending()) owner.enqueue("{\"type\":\"mediaDebug\",\"message\":\"Ignored browser-local DVD SEEK; stock DVD VM owns title/chapter navigation\"}");
                        else seek(ms);
                    }
                    return new byte[0];
                case SETRATE:
                    float rate = data.length >= 4 ? Float.intBitsToFloat(MiniClientSession.readInt(data,0)) : 1.0f;
                    // HTML media has no portable reverse playback. Report the
                    // actual supported rate rather than falsely claiming it.
                    if (Float.isNaN(rate) || Float.isInfinite(rate) || rate < 0.5f || rate > 2.0f) rate = 1.0f;
                    owner.enqueue("{\"type\":\"media\",\"action\":\"rate\",\"value\":" + rate + "}");
                    return intBytes(Float.floatToIntBits(rate));
                case DVD_STREAM:
                    if(dvdSession.pending()) {
                        int rv=dvdSession.setStream(data);
                        if(rv==0&&data!=null&&data.length>=8&&MiniClientSession.readInt(data,0)==0&&hls!=null)
                            owner.enqueue("{\"type\":\"mediaDebug\",\"message\":\"DVD audio selector updated; applies to the next decoder generation\"}");
                        return intBytes(rv);
                    }
                    // Ordinary-video DVB bitmap selection uses the same numeric command
                    // but a different typed contract: stream type 1 + physical TS PID.
                    if(data.length>=8&&hls!=null){
                        int streamType=MiniClientSession.readInt(data,0),streamPos=MiniClientSession.readInt(data,4);
                        if(streamType==1){
                            boolean ok=HlsSessionManager.selectDvbSourcePid(hls,streamPos)==0;
                            if(ok){
                                playbackContext.selectCaption(streamPos==8192?"off":"dvb");
                                owner.enqueue("{\"type\":\"captionState\",\"value\":\""+(streamPos==8192?"off":"dvb")+"\",\"sourcePid\":"+streamPos+"}");
                            }
                            return intBytes(ok?0:-1);
                        }
                    }
                    return intBytes(-1);
                case DVD_NEWCELL: return intBytes(dvdSession.setNewCell(data));
                case DVD_CLUT: return intBytes(dvdSession.setClut(data));
                case DVD_SPUCTRL: return intBytes(dvdSession.setSpuControl(data));
                case DVD_STC: return intBytes(dvdSession.setStc(data));
                case DVD_FORMAT: return intBytes(dvdSession.setFormat(data));
                default:
                    owner.enqueue("{\"type\":\"mediaDebug\",\"message\":\"Unhandled media command "+cmd+"\"}");
                    return new byte[0];
            }
        } catch(Exception e) {
            owner.enqueue("{\"type\":\"error\",\"message\":\"Media command "+cmd+": "+HttpUtil.json(compact(e.getMessage()))+"\"}");
            if(cmd==SEEK)return new byte[0];
            if(cmd>=DVD_NEWCELL&&cmd<=DVD_FORMAT)return intBytes(-1);
            return intBytes(0);
        }
    }

    static int applyDvbStreamCommand(DvbSubtitleSession dvb,int streamType,int streamPos) {
        if(dvb==null||streamType!=1)return -1;
        return (streamPos==8192?dvb.selectSourcePid(-1):dvb.selectSourcePid(streamPos))?0:-1;
    }

    synchronized void open(String url,double seekSeconds) throws Exception {
        closePlayback();
        PlaybackSessionContext.Token playbackToken=playbackContext.beginSource();
        if(sage==null) throw new IllegalStateException("sagex API is unavailable");
        File path=mediaPath(url);
        AuthorizedMediaResolver.Resolution resolved=AuthorizedMediaResolver.resolve(sage,path);
        Object mf=resolved.mediaFile;
        owner.enqueue("{\"type\":\"mediaDebug\",\"message\":\"Authorized media path via "+resolved.mode+"\"}");
        mediaId=sage.getMediaFileId(mf);
        segment=0;
        Object airing=sage.getMediaFileAiring(mf);
        durationMs=sage.getPlaybackDuration(mf);
        startSeconds=Math.max(0.0,seekSeconds);
        browserTimeMs=(long)(startSeconds*1000.0);lastProvenBrowserTimeMs=browserTimeMs;
        activeOptions=configured;transportFallback=false;
        playbackContext.selectAudio(activeOptions.audioTrack);playbackContext.selectSubtitle(activeOptions.subtitleTrack);playbackContext.selectCaption(activeOptions.captions);
        hls=HlsSessionManager.start(mediaId,segment,0,startSeconds,activeOptions,playbackContext,playbackToken);
        legacyCaptionCursor=0;legacyCaptions.clearPending();
        paused=false;
        startEvent(false,false);
        videoBoundsEvent();
    }

    synchronized void seek(long ms) throws Exception { seek(ms, false); }

    private synchronized void seek(long ms, boolean recovery) throws Exception {
        ms=Math.max(0L, ms);
        if(durationMs>0) ms=Math.min(ms, Math.max(0L, durationMs-250L));
        double sec=Math.max(0.0,ms/1000.0);
        if(mediaId<=0){ browserTimeMs=ms; return; }
        PlaybackSessionContext.Token playbackToken=playbackContext.beginSeek();
        HlsSessionManager.Session replacement=HlsSessionManager.start(mediaId,segment,0,sec,activeOptions,playbackContext,playbackToken);
        HlsSessionManager.Session old=hls;hls=replacement;
        legacyCaptionCursor=0;legacyCaptions.clearPending();if(subtitleCallbacksEnabled)legacyCaptions.postFlush();
        startSeconds=sec;browserTimeMs=ms;lastProvenBrowserTimeMs=ms;
        if(old!=null)HlsSessionManager.stop(old.id);
        startEvent(true,recovery);
    }

    private void startEvent(boolean seek,boolean recovery) {
        owner.enqueue("{\"type\":\"videoStart\",\"mediaId\":"+mediaId+
            ",\"hlsSession\":\""+HttpUtil.json(hls.id)+"\",\"playlistUrl\":\""+HttpUtil.json(hls.playlistUrl())+
            "\",\"transport\":\""+hls.transport()+"\",\"streamUrl\":\""+HttpUtil.json(hls.mediaUrl())+
            "\",\"startSeconds\":"+String.format(Locale.US,"%.3f",startSeconds)+
            ",\"durationMs\":"+durationMs+",\"seek\":"+seek+",\"recovery\":"+recovery+",\"paused\":"+paused+
            ",\"transportFallback\":"+transportFallback+",\"streamSettings\":"+hls.settingsJson()+"}");
    }


    /**
     * Start conversion outside the MiniClient media reader.  Capability probes
     * and process creation are allowed to be slow, but every stock media
     * command still needs its reply before SageTV's 30-second socket timeout.
     */
    private void scheduleDvdEpoch() {
        final long generation;
        final long serial;
        final StreamOptions options;
        synchronized(this){
            if(!dvdSession.pending()||dvdSession.epochPushedBytes()<=0)return;
            generation=dvdSession.generation();
            if(hls!=null&&hls.dvdSource!=null&&hls.dvdGeneration==generation)return;
            if(dvdStartingGeneration==generation)return;
            dvdStartingGeneration=generation;
            serial=++dvdStartSerial;
            // A DVD is a fresh source epoch. Use the current browser profile,
            // but force MPEG-2/PS to browser-compatible H.264/AAC HLS.
            activeOptions=configured.forDvdTranscode();
            options=activeOptions;
        }
        Thread starter=new Thread(new Runnable(){public void run(){
            HlsSessionManager.Session created=null;
            try{
                created=HlsSessionManager.startDvd(dvdSession,options);
                boolean stale;
                synchronized(MiniClientMediaBridge.this){
                    stale=serial!=dvdStartSerial||!dvdSession.pending()||dvdSession.generation()!=generation||hls!=null;
                    if(!stale){hls=created;dvdBrowserGeneration=created.dvdGeneration;dvdStartingGeneration=-1L;}
                }
                if(stale){HlsSessionManager.stop(created.id);return;}
                watchDvdReady(created,serial);
            }catch(Exception e){
                if(created!=null)try{HlsSessionManager.stop(created.id);}catch(Exception ignored){}
                synchronized(MiniClientMediaBridge.this){if(serial==dvdStartSerial)dvdStartingGeneration=-1L;}
                owner.enqueue("{\"type\":\"error\",\"message\":\"DVD decoder startup: "+HttpUtil.json(compact(e.getMessage()))+"\"}");
            }
        }},"webplayer-dvd-start-"+generation);starter.setDaemon(true);starter.start();
    }

    private void watchDvdReady(final HlsSessionManager.Session created,final long serial){
        // A DVD is a fresh source epoch. Use the current browser profile, but
        // notify the browser only after FFmpeg publishes playable output.
        Thread watcher=new Thread(new Runnable(){public void run(){
            try{
                while(hls==created&&!created.stopRequested&&!created.playlistReady&&!created.dvdConverterEnded)Thread.sleep(50L);
                synchronized(MiniClientMediaBridge.this){
                    if(hls!=created||serial!=dvdStartSerial)return;
                    if(created.playlistReady){startDvdEvent(created);videoBoundsEvent();}
                    else if(created.dvdConverterEnded){HlsSessionManager.updateDvdBrowserProgress(created,0L);owner.enqueue("{\"type\":\"mediaDebug\",\"message\":\"DVD generation "+created.dvdGeneration+" contained no playable A/V\"}");}
                }
            }catch(InterruptedException e){Thread.currentThread().interrupt();}
        }},"webplayer-dvd-ready-"+created.id.substring(0,Math.min(8,created.id.length())));watcher.setDaemon(true);watcher.start();
    }

    private void startDvdEvent(HlsSessionManager.Session s){
        owner.enqueue("{\"type\":\"videoStart\",\"dvd\":true,\"dvdGeneration\":"+s.dvdGeneration+
            ",\"hlsSession\":\""+HttpUtil.json(s.id)+"\",\"playlistUrl\":\""+HttpUtil.json(s.playlistUrl())+
            "\",\"transport\":\"hls\",\"streamUrl\":\""+HttpUtil.json(s.mediaUrl())+
            "\",\"startSeconds\":0,\"durationMs\":0,\"seek\":false,\"recovery\":false,\"paused\":"+paused+
            ",\"streamSettings\":"+s.settingsJson()+"}");
    }

    private synchronized void closeDvdPlayback(boolean stopVideo){
        dvdStartSerial++;
        dvdStartingGeneration=-1L;
        HlsSessionManager.Session old=hls;
        if(old!=null&&old.dvdSource!=null){hls=null;try{HlsSessionManager.stop(old.id);}catch(Exception ignored){}}
        dvdBrowserGeneration=-1L;
        if(stopVideo)owner.enqueue("{\"type\":\"videoStop\"}");
    }

    synchronized void configure(StreamOptions options) { configured=options; }
    synchronized String streamingJson() {
        return "{\"configured\":"+configured.json()+",\"active\":"+(hls==null?"null":hls.settingsJson())+
            ",\"session\":\""+(hls==null?"":hls.id)+"\",\"browserClockRegressions\":"+browserClockRegressions+
            ",\"lastProvenBrowserTimeMs\":"+lastProvenBrowserTimeMs+",\"dvd\":"+dvdSession.json()+"}";
    }
    synchronized boolean applySettings(StreamOptions options,boolean now,String expected,long time) throws Exception {
        if(now&&hls!=null) {
            if(!matchesHls(expected))return false;
            StreamOptions previous=activeOptions;boolean fallbackWas=transportFallback;
            activeOptions=options;transportFallback=false;
            try {seek(time>=0?time:browserTimeMs,false);}catch(Exception e){activeOptions=previous;transportFallback=fallbackWas;throw e;}
        }
        configured=options.forNextRecording();return true;
    }
    synchronized boolean fallback(String expected,long time) throws Exception {
        if(hls==null||!matchesHls(expected))return false;
        if(!activeOptions.allowFallback)throw new IllegalArgumentException("Automatic fallback is disabled in streaming settings");
        if(transportFallback)throw new IllegalArgumentException("This recording has already used its compatibility fallback");
        StreamOptions previous=activeOptions;
        if(hls.isTs())activeOptions=activeOptions.hlsFallback();
        else if(activeExplicitVideoCopy())activeOptions=activeOptions.transcodeFallback();
        else throw new IllegalArgumentException("No additional compatibility fallback is available for this HLS session");
        transportFallback=true;
        try {seek(time>=0?time:browserTimeMs,true);}catch(Exception e){activeOptions=previous;transportFallback=false;throw e;}
        return true;
    }

    private boolean activeExplicitVideoCopy(){
        StreamPlan p=hls==null?null:hls.streamPlan;
        return p!=null&&p.copyVideo&&"copy".equals(p.options.videoMode);
    }

    synchronized boolean reportBrowserState(String expectedHlsSession,long timeMs,int videoWidth,int videoHeight,float volume,boolean paused,boolean ended){
        if(dvdSession.pending()){
            if(hls!=null&&expectedHlsSession!=null&&expectedHlsSession.length()>0&&!matchesHls(expectedHlsSession))return false;
            if(timeMs>=0)dvdSession.updatePlayerClock(timeMs);
            if(hls!=null)HlsSessionManager.updateDvdBrowserProgress(hls,timeMs,ended);
        } else if(!matchesHls(expectedHlsSession)) return false;
        if(hls!=null && hls.durationMs>0) durationMs=hls.durationMs;
        if(timeMs>=0) {
            long candidate=Math.max(0,timeMs);
            if(!dvdSession.pending() && candidate+1500L<lastProvenBrowserTimeMs) {
                browserClockRegressions++;
                candidate=lastProvenBrowserTimeMs;
            } else if(candidate>lastProvenBrowserTimeMs) lastProvenBrowserTimeMs=candidate;
            browserTimeMs=candidate;
        }
        if(!Float.isNaN(volume) && !Float.isInfinite(volume) && volume>=0) this.volume=Math.max(0f,Math.min(1f,volume));
        this.paused=paused;
        pumpLegacyCaptions();
        return true;
    }

    synchronized void setSubtitleCallbacksEnabled(boolean enabled){
        subtitleCallbacksEnabled=enabled;legacyCaptionCursor=0;legacyCaptions.clearPending();
        if(enabled)legacyCaptions.postFlush();
    }

    synchronized void setSageTvClosedCaptionState(int state){
        sageTvCcStateSeen=true;sageTvCcState=Math.max(0,state);
        // Stock property values are authoritative only for STV ownership. Local
        // caption modes keep their own renderer and never mirror event-225.
        if(sageTvCcState==0){
            legacyCaptions.clearPending();
            playbackContext.cues().clearAll();
            if(subtitleCallbacksEnabled)legacyCaptions.postFlush();
        }
    }

    private void pumpLegacyCaptions(){
        HlsSessionManager.Session s=hls;if(!subtitleCallbacksEnabled||s==null)return;
        // If VIDEO_CC_STATE is present, state zero means stock STV captions are
        // off. On older stock servers that omit the property, callbacks are
        // still delivered and the STV's own CC1/CC2 selection remains the
        // authority (the proven extender fallback contract).
        if(sageTvCcStateSeen&&sageTvCcState==0)return;
        File f=new File(s.directory,"captions.bin");
        try{
            boolean preferTeletext=activeOptions!=null&&"stv".equals(activeOptions.captionAuthority)&&hasObservedTeletext(s.sourceInfo);
            if(!preferTeletext){legacyCaptionCursor=A53CaptionTap.readPackets(f,legacyCaptionCursor,Math.max(0,browserTimeMs)+10000L,(pts,data)->legacyCaptions.onCeaSample(pts,data));legacyCaptions.drainTo(Math.max(0,browserTimeMs));}
            if(activeOptions!=null&&"stv".equals(activeOptions.captionAuthority)&&hls.playbackToken!=null){
                java.util.List<SubtitleCue> due=playbackContext.cues().drainDue(hls.playbackToken,Math.max(0,browserTimeMs)+80L,128);
                int channel=sageTvCcState==2?1:0;
                for(SubtitleCue cue:due)if(cue.kind==SubtitleCue.Kind.TEXT&&cue.owner==SubtitleCue.Owner.LOCAL_BROADCAST){
                    byte[] payload=TeletextCea608Bridge.encode(cue.clear?"":cue.text,channel,cue.ptsMs);
                    owner.postSubtitleInfo(cue.ptsMs*45L,0,payload,LegacyExtenderCaptionBridge.CC_SUBTITLE|LegacyExtenderCaptionBridge.PTS_VALID);
                    // Older stock servers do not publish VIDEO_CC_STATE. Mirror
                    // the selected Teletext service onto both legacy CEA-608
                    // channels so either stock CC1 or CC2 can render it.
                    if(!sageTvCcStateSeen){
                        byte[] cc2=TeletextCea608Bridge.encode(cue.clear?"":cue.text,1,cue.ptsMs);
                        owner.postSubtitleInfo(cue.ptsMs*45L,0,cc2,LegacyExtenderCaptionBridge.CC_SUBTITLE|LegacyExtenderCaptionBridge.PTS_VALID);
                    }
                }
            }
        }catch(Exception e){owner.enqueue("{\"type\":\"mediaDebug\",\"message\":\"legacy caption bridge: "+HttpUtil.json(compact(e.getMessage()))+"\"}");}
    }

    synchronized String captionAuthorityJson(){return "{\"callbacks\":"+subtitleCallbacksEnabled+",\"ccStateSeen\":"+sageTvCcStateSeen+",\"ccState\":"+sageTvCcState+",\"cursor\":"+legacyCaptionCursor+",\"pending\":"+legacyCaptions.pendingSamples()+",\"forwardedSamples\":"+legacyCaptions.forwardedSamples()+",\"forwardedBytes\":"+legacyCaptions.forwardedBytes()+"}";}
    synchronized String dvdJson(){return dvdSession.json();}
    DvdNativeSession dvdSession(){return dvdSession;}

    private static boolean hasObservedTeletext(MediaProbe.Info info){if(info==null)return false;for(MediaProbe.Track t:info.tracks)if("teletext".equals(t.serviceKind)&&"observed-pmt".equals(t.evidence))return true;return false;}

    private boolean matchesHls(String expected) {
        return expected == null || expected.length() == 0 || (hls != null && expected.equals(hls.id));
    }

    synchronized boolean recoverFromBrowserPosition(String expected, long timeMs) throws Exception {
        if (mediaId <= 0 || !matchesHls(expected)) return false;
        if (timeMs >= 0) browserTimeMs=timeMs;
        seek(Math.max(0L, browserTimeMs), true);
        return true;
    }

    synchronized boolean seekFromBrowser(String expected, long timeMs) throws Exception {
        if (mediaId <= 0 || !matchesHls(expected)) return false;
        seek(timeMs, false); return true;
    }

    synchronized void close(){ closeDvdPlayback(true); dvdSession.close(); closePlayback(); }
    private void closePlayback(){
        legacyCaptionCursor=0;legacyCaptions.clearPending();
        playbackContext.invalidateSource();
        HlsSessionManager.Session old=hls;hls=null;
        if(old!=null) try{HlsSessionManager.stop(old.id);}catch(Exception ignored){}
        owner.enqueue("{\"type\":\"videoStop\"}");
    }

    private void videoBoundsEvent(){
        owner.enqueue("{\"type\":\"videoBounds\",\"src\":["+sourceX+","+sourceY+","+sourceW+","+sourceH+"]"+
                ",\"dst\":["+(boundsKnown?destX:0)+","+(boundsKnown?destY:0)+","+(boundsKnown?destW:owner.width())+","+(boundsKnown?destH:owner.height())+"]}");
    }

    private static String readOpenUrl(byte[] data)throws Exception{
        if(data==null||data.length<4)return "";
        int n=MiniClientSession.readInt(data,0); if(n<=1)return "";
        n=Math.min(n-1,data.length-4);
        return new String(data,4,n,StandardCharsets.ISO_8859_1).trim();
    }

    static File mediaPath(String url)throws Exception{return MediaUrlPath.decode(url);}

    private static byte[] intBytes(int v){return new byte[]{(byte)(v>>>24),(byte)(v>>>16),(byte)(v>>>8),(byte)v};}
    private static byte[] shorts(short a,short b){return new byte[]{(byte)(a>>>8),(byte)a,(byte)(b>>>8),(byte)b};}
    private static String compact(String s){return s==null?"unknown error":s.replace('\r',' ').replace('\n',' ').trim();}
}
