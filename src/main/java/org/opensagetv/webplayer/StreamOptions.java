package org.opensagetv.webplayer;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Immutable, per-browser/per-session policy. Never changes JVM-wide FFmpeg or MIM configuration. */
final class StreamOptions {
    final String player, videoMode, encoder, resolution, fps, deinterlace, audioCodec, audioChannels, audioFallback;
    final String audioLanguage, captions, captionLanguage, subtitleLanguage, captionAuthority, subtitleMode;
    final int profileSchemaVersion, videoKbps, keyFrameSeconds, bFrames, audioKbps, audioTrack, audioOffsetMs, captionService, captionPage, captionPid, captionCompositionPage, captionOffsetMs, subtitleTrack, subtitleOffsetMs;
    final boolean allowFallback, hevcSupported, ac3Supported, subtitleForcedOnly;
    private final Map<String,String> values;

    private StreamOptions(Function<String,String> read) {
        Map<String,String> v = new LinkedHashMap<String,String>();
        profileSchemaVersion=number(read,v,"profileSchemaVersion",8,1,8);
        player = choice(read,v,"player","auto","auto","mpegts","hls");
        String requestedVideoMode = choice(read,v,"videoMode","auto","auto","transcode","copy","mpeg2copy");
        // v3.2.18/early-3.2.19 test builds exposed mpeg2copy separately.  Keep
        // accepting it so saved browser profiles migrate cleanly, but the
        // public policy is now one explicit Copy mode.
        videoMode = "mpeg2copy".equals(requestedVideoMode) ? "copy" : requestedVideoMode;
        v.put("videoMode",videoMode);
        encoder = choice(read,v,"encoder","auto","auto","software","qsv","nvenc","vaapi","amf","videotoolbox");
        resolution = choice(read,v,"resolution","source","source","480","720","1080","2160");
        fps = choice(read,v,"fps","source","source","23.976","24","25","29.97","30","50","59.94","60");
        deinterlace = choice(read,v,"deinterlace","auto","auto","on","off");
        videoKbps = number(read,v,"videoKbps",8000,250,50000);
        keyFrameSeconds = number(read,v,"keyFrameSeconds",1,1,10);
        bFrames = number(read,v,"bFrames",0,0,3);
        audioCodec = choice(read,v,"audioCodec","auto","auto","aac","ac3","copy");
        audioChannels = choice(read,v,"audioChannels","2","source","1","2","6");
        audioFallback = choice(read,v,"audioFallback","default","default","first","strict");
        audioKbps = number(read,v,"audioKbps",192,64,640);
        audioTrack = number(read,v,"audioTrack",-1,-1,127);
        audioOffsetMs = number(read,v,"audioOffsetMs",0,-4000,4000);
        String lang = get(read,"audioLanguage","").trim().toLowerCase(Locale.ROOT);
        if (!lang.matches("[a-z]{2,3}(?:-[a-z0-9]{2,8})*") && !lang.isEmpty()) throw new IllegalArgumentException("Audio language must be a BCP-47 language such as en, eng, es or en-US");
        if (lang.length()>35) throw new IllegalArgumentException("Audio language is too long");
        audioLanguage=lang;v.put("audioLanguage",lang);
        captionAuthority = choice(read,v,"captionAuthority","off","off","cc1","cc2","stv","dvb");
        captions = choice(read,v,"captions","off","off","608","708","teletext","dvb");
        captionService = number(read,v,"captionService",1,1,"608".equals(captions)?4:63);
        captionPage = number(read,v,"captionPage",888,100,899);
        captionPid = number(read,v,"captionPid",-1,-1,8191);
        captionCompositionPage = number(read,v,"captionCompositionPage",-1,-1,65535);
        String capLang=get(read,"captionLanguage","").trim().toLowerCase(Locale.ROOT);
        if(!capLang.matches("[a-z]{2,3}(?:-[a-z0-9]{2,8})*")&&!capLang.isEmpty())throw new IllegalArgumentException("Caption language must be a BCP-47 language such as en, eng, es or en-US");
        if(capLang.length()>35)throw new IllegalArgumentException("Caption language is too long");captionLanguage=capLang;v.put("captionLanguage",capLang);
        captionOffsetMs = number(read,v,"captionOffsetMs",0,-5000,5000);
        subtitleMode=choice(read,v,"subtitleMode","off","off","auto","track");
        subtitleTrack=number(read,v,"subtitleTrack",-1,-1,4095);
        subtitleOffsetMs=number(read,v,"subtitleOffsetMs",0,-10000,10000);
        String subLang=get(read,"subtitleLanguage","").trim().toLowerCase(Locale.ROOT);
        if(!subLang.matches("[a-z]{2,3}(?:-[a-z0-9]{2,8})*")&&!subLang.isEmpty())throw new IllegalArgumentException("Subtitle language must be a BCP-47 language such as en, eng, es or en-US");
        if(subLang.length()>35)throw new IllegalArgumentException("Subtitle language is too long");subtitleLanguage=subLang;v.put("subtitleLanguage",subLang);
        subtitleForcedOnly=bool(read,v,"subtitleForcedOnly",false);
        allowFallback = bool(read,v,"allowFallback",true);
        hevcSupported = bool(read,v,"hevcSupported",false);
        ac3Supported = bool(read,v,"ac3Supported",false);
        values=java.util.Collections.unmodifiableMap(v);
    }
    static StreamOptions defaults() { return new StreamOptions(k -> null); }
    static StreamOptions fromRequest(Function<String,String> read) { return new StreamOptions(k -> read.apply("stream_"+k)); }
    static StreamOptions fromMap(Map<String,String> map) { return new StreamOptions(map::get); }
    StreamOptions hlsFallback() {
        Map<String,String> next=new LinkedHashMap<String,String>(values);
        next.put("player","hls");if(!"copy".equals(videoMode))next.put("videoMode","transcode");if(!"copy".equals(audioCodec))next.put("audioCodec","aac");
        return fromMap(next);
    }
    StreamOptions transcodeFallback() {
        Map<String,String> next=new LinkedHashMap<String,String>(values);
        next.put("player","hls");next.put("videoMode","transcode");if(!"copy".equals(audioCodec))next.put("audioCodec","aac");
        return fromMap(next);
    }
    /** DVD-Video is MPEG-2 pushed by stock MiniDVDPlayer and browsers do not
     * reliably decode that video codec.  Always convert DVD video to H.264 and
     * audio to AAC, while preserving the user's encoder/quality choices. */
    StreamOptions forDvdTranscode() {
        Map<String,String> next=new LinkedHashMap<String,String>(values);
        next.put("player","hls");
        next.put("videoMode","transcode");
        next.put("audioCodec","aac");
        return fromMap(next);
    }
    StreamOptions forNextRecording(){Map<String,String> m=new LinkedHashMap<String,String>(values);m.put("audioTrack","-1");m.put("subtitleTrack","-1");return fromMap(m);}
    boolean ts() { return !"hls".equals(player); }
    boolean captionsEnabled() { return "608".equals(captions)||"708".equals(captions); }
    boolean teletextEnabled() { return "teletext".equals(captions); }
    boolean dvbEnabled() { return "dvb".equals(captions); }
    String encoderName() { return "software".equals(encoder)?"libx264":"auto".equals(encoder)?"auto":"h264_"+encoder; }
    String json() {
        StringBuilder b=new StringBuilder("{");boolean first=true;
        for(Map.Entry<String,String> e:values.entrySet()) {
            if(!first)b.append(',');first=false;
            b.append('"').append(e.getKey()).append("\":\"").append(HttpUtil.json(e.getValue())).append('"');
        }
        return b.append('}').toString();
    }
    private static String get(Function<String,String> f,String key,String def) { String s=f.apply(key);return s==null?def:s; }
    private static String choice(Function<String,String> f,Map<String,String> m,String k,String d,String... options) {
        String value=get(f,k,d).trim();for(String s:options)if(s.equals(value)){m.put(k,value);return value;}
        throw new IllegalArgumentException("Invalid streaming "+k+": "+value);
    }
    private static int number(Function<String,String> f,Map<String,String> m,String k,int d,int min,int max) {
        String s=get(f,k,String.valueOf(d)).trim();int n;
        try{n=Integer.parseInt(s);}catch(Exception e){throw new IllegalArgumentException("Invalid streaming "+k);}
        if(n<min||n>max)throw new IllegalArgumentException(k+" must be "+min+".."+max);
        m.put(k,String.valueOf(n));return n;
    }
    private static boolean bool(Function<String,String> f,Map<String,String> m,String k,boolean d) {
        String s=get(f,k,String.valueOf(d));if(!"true".equals(s)&&!"false".equals(s))throw new IllegalArgumentException("Invalid streaming "+k);
        m.put(k,s);return "true".equals(s);
    }
}
