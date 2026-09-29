package org.opensagetv.webplayer;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/** FFmpeg-family metadata, deliberately independent of SageTV UI/STV implementations. */
final class MediaProbe {
    static final class Track {
        int index,channels,width,height,sourcePid=-1,programId=-1,teletextType=-1,teletextMagazine=-1,teletextPage=-1,compositionPageId=-1,ancillaryPageId=-1;
        String type="",codec="",language="",title="",field="",rate="",serviceKind="",evidence="metadata",availability="metadata-only",sourceKind="embedded",sidecarFormat="";
        File sidecarFile; int sidecarSubstream=-1;
        boolean defaultTrack,forced,hearingImpaired,visualImpaired;
        boolean interlaced(){return !field.isEmpty()&&!"unknown".equals(field)&&!"progressive".equals(field);}
        boolean subtitleLike(){return "subtitle".equals(type)||!serviceKind.isEmpty();}
        boolean ordinarySubtitle(){String c=codec==null?"":codec.toLowerCase(Locale.ROOT);return "subtitle".equals(type)&&serviceKind.isEmpty()&&(c.contains("subrip")||c.contains("webvtt")||c.equals("ass")||c.equals("ssa")||c.contains("pgs")||c.contains("dvd_subtitle")||c.contains("vobsub"));}
        String json(){return "{\"index\":"+index+",\"sourcePid\":"+sourcePid+",\"programId\":"+programId+",\"type\":\""+HttpUtil.json(type)+"\",\"codec\":\""+HttpUtil.json(codec)+"\",\"channels\":"+channels+
            ",\"language\":\""+HttpUtil.json(language)+"\",\"title\":\""+HttpUtil.json(title)+"\",\"width\":"+width+",\"height\":"+height+",\"field\":\""+HttpUtil.json(field)+"\",\"serviceKind\":\""+HttpUtil.json(serviceKind)+"\",\"teletextType\":"+teletextType+",\"teletextMagazine\":"+teletextMagazine+",\"teletextPage\":"+teletextPage+",\"compositionPageId\":"+compositionPageId+",\"ancillaryPageId\":"+ancillaryPageId+",\"default\":"+defaultTrack+",\"forced\":"+forced+",\"hearingImpaired\":"+hearingImpaired+",\"visualImpaired\":"+visualImpaired+",\"evidence\":\""+HttpUtil.json(evidence)+"\",\"sourceKind\":\""+HttpUtil.json(sourceKind)+"\",\"sidecarFormat\":\""+HttpUtil.json(sidecarFormat)+"\"}";}
    }
    static final class Info {
        final List<Track> tracks=new ArrayList<Track>();
        final List<TsServiceInventoryProbe.Service> services=new ArrayList<TsServiceInventoryProbe.Service>();String warning="",probeMethod="none";
        Track video(){for(Track t:tracks)if("video".equals(t.type))return t;return null;}
        List<Track> audio(){List<Track> a=new ArrayList<Track>();for(Track t:tracks)if("audio".equals(t.type))a.add(t);return a;}
        List<Track> subtitles(){List<Track> a=new ArrayList<Track>();for(Track t:tracks)if(t.subtitleLike())a.add(t);return a;}
        List<Track> ordinarySubtitles(){List<Track>a=new ArrayList<Track>();for(Track t:tracks)if(t.ordinarySubtitle())a.add(t);return a;}
        String json(){StringBuilder b=new StringBuilder("{\"warning\":\"").append(HttpUtil.json(warning)).append("\",\"probeMethod\":\"").append(HttpUtil.json(probeMethod)).append("\",\"tracks\":[");for(int i=0;i<tracks.size();i++){if(i>0)b.append(',');b.append(tracks.get(i).json());}b.append("],\"services\":[");for(int i=0;i<services.size();i++){if(i>0)b.append(',');b.append(services.get(i).json());}return b.append("],\"subtitleCapabilities\":").append(SubtitleCapabilities.json()).append("}").toString();}
    }
    static Info read(File source,String ffmpeg) {
        String probe=ffprobeCommand(ffmpeg);
        String ffprobeFailure="";
        try {
            ProcessCapture.Result r=ProcessCapture.run(Arrays.asList(probe,"-v","error","-analyzeduration","2000000","-probesize","4000000","-show_entries",
                "stream=index,id,codec_type,codec_name,channels,width,height,field_order,r_frame_rate:stream_disposition=default,forced,hearing_impaired,visual_impaired:stream_tags=language,title","-of","flat",source.getAbsolutePath()),10,256*1024);
            if(r.exit==0){Info info=parse(r.text);if(!info.tracks.isEmpty()){info.probeMethod="ffprobe";finish(info,source);return info;}ffprobeFailure="ffprobe returned no source tracks";}
            else ffprobeFailure="ffprobe exited with status "+r.exit;
        }catch(IOException e){ffprobeFailure=compact(e.getMessage());}
        Info fallback=readWithFfmpeg(source,ffmpeg);
        if(!fallback.tracks.isEmpty()){
            fallback.probeMethod="ffmpeg";
            fallback.warning="ffprobe unavailable or failed ("+ffprobeFailure+"); metadata discovered with FFmpeg fallback";
            finish(fallback,source);return fallback;
        }
        if(fallback.warning.isEmpty())fallback.warning="Source metadata unavailable after ffprobe and FFmpeg probing; using conservative transcode";
        else fallback.warning="ffprobe unavailable or failed ("+ffprobeFailure+"); FFmpeg fallback failed ("+fallback.warning+")";
        return fallback;
    }

    private static String ffprobeCommand(String ffmpeg){
        String explicit=System.getProperty("sagetv.webplayer.ffprobe",System.getenv("SAGETV_WEBPLAYER_FFPROBE"));
        if(explicit!=null&&!explicit.trim().isEmpty())return explicit.trim();
        File ff=ffmpeg==null?null:new File(ffmpeg),parent=ff==null?null:ff.getParentFile();String name=System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")?"ffprobe.exe":"ffprobe";
        if(parent!=null){
            File pluginRuntime=new File(parent,"plugins/SageTVFFmpegPlugin/runtime/"+name);
            if(pluginRuntime.isFile()&&pluginRuntime.canExecute())return pluginRuntime.getAbsolutePath();
            File canonicalRuntime=new File(parent,"../runtime/"+name);
            if(canonicalRuntime.isFile()&&canonicalRuntime.canExecute())return canonicalRuntime.getAbsolutePath();
            File sibling=new File(parent,name);if(sibling.isFile()&&sibling.canExecute())return sibling.getAbsolutePath();
        }
        return name; // final ffprobe attempt uses PATH before falling back to FFmpeg itself
    }

    private static Info readWithFfmpeg(File source,String ffmpeg){
        Info out=new Info();
        if(ffmpeg==null||ffmpeg.trim().isEmpty()){out.warning="OpenSageTV Vibe FFmpeg Plugin transcoder is unavailable";return out;}
        try{
            ProcessCapture.Result r=ProcessCapture.run(Arrays.asList(ffmpeg,"-hide_banner","-nostdin","-analyzeduration","2000000","-probesize","4000000","-i",source.getAbsolutePath()),10,256*1024);
            out=parseFfmpeg(r.text);out.probeMethod="ffmpeg";
            if(out.tracks.isEmpty())out.warning="FFmpeg input probe returned no source tracks";
            return out;
        }catch(IOException e){out.warning=compact(e.getMessage());return out;}
    }

    static Info parseFfmpeg(String text){
        Info out=new Info();Map<Integer,Track> by=new TreeMap<Integer,Track>();
        Pattern stream=Pattern.compile("^\\s*Stream #\\d+:(\\d+)(?:\\[0x([0-9A-Fa-f]+)\\])?(?:\\(([^)]*)\\))?(?:\\[[^]]+\\])?:\\s*(Video|Audio|Subtitle|Data|Attachment):\\s*([^,\\s]+)(.*)$",Pattern.CASE_INSENSITIVE);
        Pattern size=Pattern.compile("(?:^|[ ,])([1-9][0-9]{1,4})x([1-9][0-9]{1,4})(?:[ ,\\[]|$)");
        Pattern fps=Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s+fps\\b",Pattern.CASE_INSENSITIVE);
        for(String line:text.split("\\r?\\n")){
            if(line.startsWith("Output #")||line.startsWith("Stream mapping:"))break;
            Matcher m=stream.matcher(line);if(!m.matches())continue;int index=integer(m.group(1));if(index>127)continue;
            Track t=by.get(index);if(t==null){t=new Track();t.index=index;by.put(index,t);}t.sourcePid=m.group(2)==null?-1:pid("0x"+m.group(2));
            t.language=m.group(3)==null?"":m.group(3).trim();t.type=m.group(4).toLowerCase(Locale.ROOT);t.codec=m.group(5).trim().toLowerCase(Locale.ROOT);t.evidence="ffmpeg-probe";
            String rest=m.group(6)==null?"":m.group(6);t.defaultTrack=rest.toLowerCase(Locale.ROOT).contains("(default)");t.forced=rest.toLowerCase(Locale.ROOT).contains("(forced)");
            if("video".equals(t.type)){
                Matcher sm=size.matcher(rest);if(sm.find()){t.width=integer(sm.group(1));t.height=integer(sm.group(2));}
                String low=rest.toLowerCase(Locale.ROOT);if(low.contains("progressive"))t.field="progressive";else if(low.contains("top first")||low.contains("tff"))t.field="tt";else if(low.contains("bottom first")||low.contains("bff"))t.field="bb";else t.field="unknown";
                Matcher fm=fps.matcher(rest);if(fm.find())t.rate=fm.group(1);
            }else if("audio".equals(t.type))t.channels=channels(rest);
        }
        out.tracks.addAll(by.values());if(out.tracks.isEmpty())out.warning="No source tracks discovered from FFmpeg input probe";return out;
    }

    private static int channels(String text){String s=text==null?"":text.toLowerCase(Locale.ROOT);if(s.matches(".*\\bmono\\b.*"))return 1;if(s.matches(".*\\bstereo\\b.*"))return 2;Matcher m=Pattern.compile("(?:^|[, ])(\\d+)\\.(\\d+)(?:[ ,(]|$)").matcher(s);if(m.find())return integer(m.group(1))+integer(m.group(2));m=Pattern.compile("(?:^|[, ])(\\d+) channels?(?:[ ,]|$)").matcher(s);return m.find()?integer(m.group(1)):0;}
    private static String compact(String s){if(s==null)return "unknown error";s=s.replace('\r',' ').replace('\n',' ').trim();return s.length()>240?s.substring(0,240):s;}
    private static void finish(Info info,File source){
        try{mergeTransportServices(info,TsServiceInventoryProbe.scan(source,SubtitleLimits.MAX_TS_DISCOVERY_BYTES));}
        catch(IOException e){String note="transport-service scan failed: "+compact(e.getMessage());info.warning=info.warning.isEmpty()?note:info.warning+"; "+note;}
        discoverSidecars(info,source);
    }
    static Info parse(String text) {
        Info out=new Info();Map<Integer,Track> by=new TreeMap<Integer,Track>();
        Pattern p=Pattern.compile("^streams\\.stream\\.(\\d+)\\.(.+?)=(.*)$");
        for(String line:text.split("\\r?\\n")){
            Matcher m=p.matcher(line);if(!m.matches())continue;
            int ordinal=Integer.parseInt(m.group(1));if(ordinal>127)continue;
            Track t=by.get(ordinal);if(t==null){t=new Track();t.index=ordinal;by.put(ordinal,t);}
            String k=m.group(2),v=m.group(3);if(v.startsWith("\"")&&v.endsWith("\""))v=v.substring(1,v.length()-1).replace("\\\"","\"").replace("\\\\","\\");
            if("index".equals(k))t.index=integer(v);else if("id".equals(k))t.sourcePid=pid(v);else if("codec_type".equals(k))t.type=v;
            else if("codec_name".equals(k))t.codec=v;else if("channels".equals(k))t.channels=integer(v);
            else if("width".equals(k))t.width=integer(v);else if("height".equals(k))t.height=integer(v);
            else if("field_order".equals(k))t.field=v;else if("r_frame_rate".equals(k))t.rate=v;
            else if("tags.language".equalsIgnoreCase(k))t.language=v;else if("tags.title".equalsIgnoreCase(k))t.title=v;
            else if("disposition.default".equalsIgnoreCase(k))t.defaultTrack=integer(v)!=0;else if("disposition.forced".equalsIgnoreCase(k))t.forced=integer(v)!=0;
            else if("disposition.hearing_impaired".equalsIgnoreCase(k))t.hearingImpaired=integer(v)!=0;else if("disposition.visual_impaired".equalsIgnoreCase(k))t.visualImpaired=integer(v)!=0;
        }
        out.tracks.addAll(by.values());for(Track t:out.tracks)if(isCea608(t.codec))t.language="";if(out.tracks.isEmpty())out.warning="No source tracks discovered; using conservative transcode";return out;
    }
    static void mergeTransportServices(Info info,List<TsServiceInventoryProbe.Service> services){
        info.services.clear();info.services.addAll(services);
        for(TsServiceInventoryProbe.Service s:services){Track target=null;for(Track t:info.tracks)if(t.sourcePid==s.pid){target=t;break;}if(target==null){target=new Track();target.index=nextSyntheticIndex(info);target.sourcePid=s.pid;target.type="subtitle";target.codec="teletext".equals(s.kind)?"dvb_teletext":"dvb_subtitle";info.tracks.add(target);}target.programId=s.program;target.serviceKind=s.kind;target.language=s.language;target.teletextType=s.teletextType;target.teletextMagazine=s.magazine;target.teletextPage=s.page;target.compositionPageId=s.compositionPageId;target.ancillaryPageId=s.ancillaryPageId;target.hearingImpaired=target.hearingImpaired||s.hearingImpaired;target.evidence="observed-pmt";target.availability="observed";}
    }

    static void discoverSidecars(Info info,File source){
        if(source==null||!source.isFile()||source.getParentFile()==null)return;String name=source.getName(),base=name;int dot=name.lastIndexOf('.');if(dot>0)base=name.substring(0,dot);
        File dir=source.getParentFile();String[] exts={"srt","vtt","ass","ssa","sup","idx"};int idx=128;for(Track t:info.tracks)idx=Math.max(idx,t.index+1);
        for(String ext:exts){File f=new File(dir,base+"."+ext);if(!f.isFile())continue;
            if("idx".equals(ext)){
                File sub=new File(dir,base+".sub");if(!sub.isFile())continue;List<String> lines=readTextLines(f,256*1024);int defaultStream=-1;boolean forced=false;for(String line:lines){String v=line.trim().toLowerCase(Locale.ROOT);if(v.startsWith("langidx:"))defaultStream=integer(v.substring(8).trim());else if(v.startsWith("forced subs:"))forced=v.contains("on")||v.contains("yes")||v.endsWith("1");}
                boolean any=false;Pattern id=Pattern.compile("(?i)^\\s*id:\\s*([a-z]{2,3})(?:\\s*,\\s*index:\\s*(\\d+))?.*$");for(String line:lines){Matcher m=id.matcher(line);if(!m.matches())continue;Track t=new Track();t.index=idx++;t.type="subtitle";t.codec="dvd_subtitle";t.sourceKind="sidecar";t.sidecarFile=f;t.sidecarFormat="idx";t.sidecarSubstream=m.group(2)==null?0:integer(m.group(2));t.language=m.group(1).toLowerCase(Locale.ROOT);t.defaultTrack=t.sidecarSubstream==defaultStream;t.forced=forced;t.evidence="associated-idx-sub";t.availability="observed";t.title=f.getName()+" · stream "+t.sidecarSubstream;info.tracks.add(t);any=true;}if(!any){Track t=new Track();t.index=idx++;t.type="subtitle";t.codec="dvd_subtitle";t.sourceKind="sidecar";t.sidecarFile=f;t.sidecarFormat="idx";t.sidecarSubstream=0;t.forced=forced;t.evidence="associated-idx-sub";t.availability="observed";t.title=f.getName();info.tracks.add(t);}continue;
            }
            Track t=new Track();t.index=idx++;t.type="subtitle";t.sourceKind="sidecar";t.sidecarFile=f;t.sidecarFormat=ext;t.evidence="associated-sidecar";t.availability="observed";t.title=f.getName();
            if("srt".equals(ext))t.codec="subrip";else if("vtt".equals(ext))t.codec="webvtt";else if("ass".equals(ext))t.codec="ass";else if("ssa".equals(ext))t.codec="ssa";else if("sup".equals(ext))t.codec="hdmv_pgs_subtitle";info.tracks.add(t);
        }
    }
    private static List<String> readTextLines(File f,int max){List<String> out=new ArrayList<String>();try{BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(f),"UTF-8"));String line;int total=0;while((line=r.readLine())!=null&&(total+=line.length()+1)<=max)out.add(line);r.close();}catch(Exception ignored){}return out;}
    private static int nextSyntheticIndex(Info i){int n=0;for(Track t:i.tracks)n=Math.max(n,t.index+1);return n;}
    private static boolean isCea608(String c){return c!=null&&(c.toLowerCase(Locale.ROOT).contains("eia_608")||c.toLowerCase(Locale.ROOT).contains("cea608"));}
    private static int integer(String s){try{return Integer.parseInt(s);}catch(Exception e){return 0;}}
    private static int pid(String s){try{String v=s.trim().toLowerCase(Locale.ROOT);return v.startsWith("0x")?Integer.parseInt(v.substring(2),16):Integer.parseInt(v);}catch(Exception e){return -1;}}
    static String languageKey(String s){
        if(s==null)return "";s=s.toLowerCase(Locale.ROOT).split("-")[0];
        if(s.length()==2)try{return new Locale(s).getISO3Language();}catch(Exception ignored){}
        if("fre".equals(s))return "fra";if("ger".equals(s))return "deu";if("dut".equals(s))return "nld";return s;
    }
}
