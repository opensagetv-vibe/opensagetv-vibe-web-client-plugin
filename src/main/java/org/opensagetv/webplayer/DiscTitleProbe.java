package org.opensagetv.webplayer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Explicit-title DVD-Video metadata probe using FFmpeg's dvdvideo/libdvdnav path. */
final class DiscTitleProbe {
    static final class Chapter { final int id;final double start,end;Chapter(int id,double start,double end){this.id=id;this.start=start;this.end=end;} String json(){return "{\"id\":"+id+",\"startSeconds\":"+fmt(start)+",\"endSeconds\":"+fmt(end)+"}";} }
    static final class Title {
        final int title;final boolean available;final double duration;final int videoStreams;final int audioStreams;final int subtitleStreams;final List<Chapter> chapters;final String error;
        Title(int title,boolean available,double duration,int videoStreams,int audioStreams,int subtitleStreams,List<Chapter> chapters,String error){this.title=title;this.available=available;this.duration=duration;this.videoStreams=videoStreams;this.audioStreams=audioStreams;this.subtitleStreams=subtitleStreams;this.chapters=chapters;this.error=error;}
        String json(){StringBuilder b=new StringBuilder("{\"title\":").append(title).append(",\"available\":").append(available).append(",\"durationSeconds\":").append(fmt(duration)).append(",\"timelineBase\":\"dvd-title\",\"chapterTimesRelativeToTitle\":true,\"videoStreams\":").append(videoStreams).append(",\"audioStreams\":").append(audioStreams).append(",\"subtitleStreams\":").append(subtitleStreams).append(",\"chapters\":[");for(int i=0;i<chapters.size();i++){if(i>0)b.append(',');b.append(chapters.get(i).json());}b.append(']');if(error!=null)b.append(",\"error\":\"").append(HttpUtil.json(error)).append('"');return b.append('}').toString();}
    }
    private DiscTitleProbe() {}

    static Title probe(File videoTs,int title,DiscCapabilityProbe cap){
        if(title<1||title>99)return unavailable(title,"DVD title must be 1-99");
        if(videoTs==null||!videoTs.isDirectory())return unavailable(title,"VIDEO_TS directory is unavailable to the plugin JVM");
        if(cap==null||!cap.movieOnlyAvailable())return unavailable(title,"FFmpeg dvdvideo/libdvdread/libdvdnav capability is unavailable");
        String ffprobe=ffprobe(cap.ffmpeg);
        try{
            List<String> cmd=Arrays.asList(ffprobe,"-v","error","-f","dvdvideo","-title",String.valueOf(title),"-preindex","1","-show_entries","format=duration:chapter=id,start_time,end_time:stream=index,codec_type,codec_name","-of","flat",videoTs.getAbsolutePath());
            ProcessCapture.Result r=ProcessCapture.run(cmd,8,512*1024);if(r.exit!=0)return unavailable(title,compact(r.text));return parseFlat(title,r.text);
        }catch(IOException e){return unavailable(title,compact(e.getMessage()));}
    }

    static Title parseFlat(int title,String text){
        double duration=number(value(text,"format.duration"),0);int v=0,a=0,s=0;
        Pattern st=Pattern.compile("(?m)^streams\\.stream\\.\\d+\\.codec_type=\\\"?([^\\\"\\r\\n]+)\\\"?$");Matcher sm=st.matcher(text==null?"":text);while(sm.find()){String t=sm.group(1).toLowerCase(Locale.ROOT);if("video".equals(t))v++;else if("audio".equals(t))a++;else if("subtitle".equals(t))s++;}
        List<Chapter> chapters=new ArrayList<Chapter>();Pattern cp=Pattern.compile("(?m)^chapters\\.chapter\\.(\\d+)\\.(id|start_time|end_time)=(.*)$");Matcher cm=cp.matcher(text==null?"":text);java.util.Map<Integer,double[]> map=new java.util.TreeMap<Integer,double[]>();while(cm.find()){int idx=Integer.parseInt(cm.group(1));double[] d=map.get(idx);if(d==null){d=new double[]{idx,0,0};map.put(idx,d);}String key=cm.group(2),raw=unquote(cm.group(3));if("id".equals(key))d[0]=number(raw,idx);else if("start_time".equals(key))d[1]=number(raw,0);else d[2]=number(raw,0);}for(double[] d:map.values())chapters.add(new Chapter((int)d[0],d[1],d[2]));
        boolean ok=duration>0||v+a+s>0||!chapters.isEmpty();return new Title(title,ok,duration,v,a,s,chapters,ok?null:"dvdvideo returned no streams/chapters for this title");
    }

    static String movieOnlyPlanJson(DiscSourceInfo source,DiscCapabilityProbe cap,int title,Title probe){
        boolean ok=source!=null&&source.canProbeTitles()&&cap!=null&&cap.movieOnlyAvailable()&&title>=1&&title<=99&&probe!=null&&probe.available;
        String reason=ok?"Ready for explicit-title movie-only conversion planning":(probe!=null&&probe.error!=null?probe.error:(cap==null||!cap.movieOnlyAvailable()?"FFmpeg dvdvideo/libdvdread/libdvdnav capability is unavailable":"Explicit DVD title metadata is unavailable"));
        return "{\"available\":"+ok+",\"label\":\"Movie-only compatibility — explicit DVD title\",\"title\":"+title+",\"interactiveMenus\":false,\"automaticMainFeatureSelection\":false,\"reason\":\""+HttpUtil.json(reason)+"\",\"note\":\"This path converts only the explicitly selected DVD title. It never claims menu parity and never guesses title 1 or the largest VOB as the main feature.\"}";
    }

    private static String value(String text,String key){Pattern p=Pattern.compile("(?m)^"+Pattern.quote(key)+"=(.*)$");Matcher m=p.matcher(text==null?"":text);return m.find()?unquote(m.group(1)):"";}
    private static String unquote(String s){s=s==null?"":s.trim();return s.length()>=2&&s.startsWith("\"")&&s.endsWith("\"")?s.substring(1,s.length()-1):s;}
    private static double number(String s,double d){try{return Double.parseDouble(unquote(s));}catch(Exception e){return d;}}
    private static Title unavailable(int title,String e){return new Title(title,false,0,0,0,0,new ArrayList<Chapter>(),e);}
    private static String ffprobe(String ffmpeg){File f=new File(ffmpeg);if(f.getParentFile()!=null){String n=System.getProperty("os.name","").toLowerCase(Locale.ROOT).contains("win")?"ffprobe.exe":"ffprobe";File p=new File(f.getParentFile(),n);if(p.isFile())return p.getAbsolutePath();}return "ffprobe";}
    private static String compact(String s){if(s==null||s.trim().isEmpty())return "dvdvideo probe failed";s=s.replace('\r',' ').replace('\n',' ').trim();return s.length()>400?s.substring(0,400):s;}
    private static String fmt(double v){return String.format(Locale.US,"%.3f",Math.max(0,v));}
}
