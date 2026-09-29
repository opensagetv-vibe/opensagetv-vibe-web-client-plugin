package org.opensagetv.webplayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/** P06 ordinary file subtitle worker. Never accepts a browser supplied path. */
final class OrdinarySubtitleSession {
    final PlaybackSessionContext context; final PlaybackSessionContext.Token token; final MediaProbe.Track track;
    final long anchorMs; final int offsetMs; volatile boolean running,complete,stopRequested; volatile String mode="",error="";
    volatile long cues,bytes; private volatile Process process; private volatile Thread thread;
    private OrdinarySubtitleSession(PlaybackSessionContext c,PlaybackSessionContext.Token t,MediaProbe.Track tr,long anchor,int offset){context=c;token=t;track=tr;anchorMs=Math.max(0,anchor);offsetMs=offset;}

    static OrdinarySubtitleSession start(PlaybackSessionContext c,PlaybackSessionContext.Token token,MediaProbe.Track track,File source,String ffmpeg,long anchorMs,int offsetMs){
        if(c==null||token==null||track==null||source==null)return null;OrdinarySubtitleSession s=new OrdinarySubtitleSession(c,token,track,anchorMs,offsetMs);s.running=true;
        String codec=(track.codec==null?"":track.codec).toLowerCase(Locale.ROOT);
        if(codec.contains("subrip")||codec.contains("webvtt")||codec.equals("ass")||codec.equals("ssa"))s.startText(source,ffmpeg);else if(codec.contains("pgs")||codec.contains("dvd_subtitle")||codec.contains("vobsub"))s.startBitmap(source,ffmpeg);else{s.error="unsupported ordinary subtitle codec: "+codec;s.running=false;}
        return s;
    }
    void stop(){stopRequested=true;Process p=process;if(p!=null&&p.isAlive())p.destroyForcibly();Thread t=thread;if(t!=null)t.interrupt();context.fileCues().clearAll();}
    String stateJson(){return "{\"active\":"+running+",\"complete\":"+complete+",\"mode\":\""+HttpUtil.json(mode)+"\",\"trackIndex\":"+track.index+",\"codec\":\""+HttpUtil.json(track.codec)+"\",\"sourceKind\":\""+HttpUtil.json(track.sourceKind)+"\",\"cues\":"+cues+",\"bytes\":"+bytes+",\"error\":\""+HttpUtil.json(error)+"\"}";}

    private File input(File source){return track.sidecarFile!=null?track.sidecarFile:source;}
    private String mapSpec(){return track.sidecarFile!=null?"0:"+(track.sidecarSubstream>=0?track.sidecarSubstream:0):"0:"+track.index;}
    private List<String> baseCommand(String ffmpeg,File input){if(ffmpeg==null||ffmpeg.trim().isEmpty()||!new File(ffmpeg).isFile())throw new IllegalArgumentException("FFmpeg unavailable for ordinary subtitles");List<String>a=new ArrayList<String>();Collections.addAll(a,ffmpeg,"-hide_banner","-loglevel","error","-nostdin");if(anchorMs>0)Collections.addAll(a,"-ss",String.format(Locale.US,"%.3f",anchorMs/1000.0));Collections.addAll(a,"-i",input.getAbsolutePath(),"-map",mapSpec());return a;}

    private void startText(final File source,final String ffmpeg){mode="text";thread=new Thread(()->{try{
        String text;
        if(track.sidecarFile!=null&&("vtt".equals(track.sidecarFormat)||"srt".equals(track.sidecarFormat))){byte[] raw=Files.readAllBytes(track.sidecarFile.toPath());bytes=raw.length;if(bytes>SubtitleLimits.MAX_SUBTITLE_TAP_BYTES)throw new IOException("ordinary subtitle text limit exceeded");text=new String(raw,StandardCharsets.UTF_8);if("srt".equals(track.sidecarFormat))text=srtToVtt(text);mode="text-direct-"+track.sidecarFormat;}
        else{mode="text-webvtt-adapter";List<String> cmd=baseCommand(ffmpeg,input(source));Collections.addAll(cmd,"-c:s","webvtt","-f","webvtt","pipe:1");ProcessBuilder pb=new ProcessBuilder(cmd);process=pb.start();ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;while(!stopRequested&&(n=process.getInputStream().read(buf))>=0){if(n==0)continue;bytes+=n;if(bytes>SubtitleLimits.MAX_SUBTITLE_TAP_BYTES)throw new IOException("ordinary subtitle text limit exceeded");out.write(buf,0,n);}if(stopRequested)return;String stderr=readSmall(process.getErrorStream(),65536);int exit=process.waitFor();if(exit!=0)throw new IOException(stderr.isEmpty()?"FFmpeg text subtitle adapter exited "+exit:stderr);text=new String(out.toByteArray(),StandardCharsets.UTF_8);}
        parseWebVtt(text,!(track.sidecarFile!=null&&("vtt".equals(track.sidecarFormat)||"srt".equals(track.sidecarFormat))));complete=true;
    }catch(Exception e){if(!stopRequested)error=compact(e);}finally{running=false;}},"webplayer-file-sub-text");thread.setDaemon(true);thread.start();}

    private void parseWebVtt(String text,boolean relative){String normalized=text.replace("\r\n","\n").replace('\r','\n');String[] blocks=normalized.split("\n\\s*\n");long lastEnd=-1;for(String block:blocks){if(stopRequested||!context.isCurrent(token))break;String[] lines=block.split("\n");int timeLine=-1;for(int i=0;i<lines.length;i++)if(lines[i].contains("-->")){timeLine=i;break;}if(timeLine<0)continue;String[] times=lines[timeLine].split("-->",2);String rhs=times[1].trim();String[] rhsParts=rhs.split("\\s+");long start=parseVttTime(times[0].trim()),end=parseVttTime(rhsParts[0]);if(start<0||end<start)continue;if(!relative&&end<anchorMs)continue;int linePercent=-1,positionPercent=50;String align="center";for(int z=1;z<rhsParts.length;z++){String q=rhsParts[z];try{if(q.startsWith("line:")&&q.endsWith("%"))linePercent=Integer.parseInt(q.substring(5,q.length()-1).split(",")[0]);else if(q.startsWith("position:")&&q.endsWith("%"))positionPercent=Integer.parseInt(q.substring(9,q.length()-1).split(",")[0]);else if(q.startsWith("align:"))align=q.substring(6);}catch(Exception ignored){}}
            StringBuilder body=new StringBuilder();for(int i=timeLine+1;i<lines.length;i++){if(body.length()>0)body.append('\n');body.append(stripMarkup(lines[i]));}String value=body.toString();if(value.length()>SubtitleLimits.MAX_TEXT_CHARS)value=value.substring(0,SubtitleLimits.MAX_TEXT_CHARS);long pts=Math.max(0,(relative?anchorMs:0)+start+offsetMs),dur=Math.max(0,end-start);if(context.fileCues().offer(SubtitleCue.textPositioned(token,SubtitleCue.Owner.LOCAL_FILE,pts,dur,value,value.isEmpty(),false,linePercent,positionPercent,align)))cues++;lastEnd=Math.max(lastEnd,end);}
        if(lastEnd>=0&&context.isCurrent(token))context.fileCues().offer(SubtitleCue.text(token,SubtitleCue.Owner.LOCAL_FILE,Math.max(0,(relative?anchorMs:0)+lastEnd+offsetMs),0,"",true,true));}
    static long parseVttTime(String s){try{String[] p=s.trim().replace(',','.').split(":");double sec;if(p.length==3)sec=Integer.parseInt(p[0])*3600+Integer.parseInt(p[1])*60+Double.parseDouble(p[2]);else if(p.length==2)sec=Integer.parseInt(p[0])*60+Double.parseDouble(p[1]);else return -1;return Math.max(0,Math.round(sec*1000));}catch(Exception e){return -1;}}
    static String stripMarkup(String s){if(s==null)return"";StringBuilder b=new StringBuilder();boolean tag=false;for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c=='<'){tag=true;continue;}if(c=='>'&&tag){tag=false;continue;}if(!tag)b.append(c);}return b.toString().replace("&lt;","<").replace("&gt;",">").replace("&amp;","&").replace("&nbsp;"," ");}
    static String srtToVtt(String s){String n=s.replace("\r\n","\n").replace('\r','\n');StringBuilder out=new StringBuilder("WEBVTT\n\n");for(String block:n.split("\n\\s*\n")){String[] lines=block.split("\n");int t=-1;for(int i=0;i<lines.length;i++)if(lines[i].contains("-->")){t=i;break;}if(t<0)continue;out.append(lines[t].replace(',','.')).append('\n');for(int i=t+1;i<lines.length;i++)out.append(lines[i]).append('\n');out.append('\n');}return out.toString();}

    private void startBitmap(final File source,final String ffmpeg){mode="bitmap-via-dvb-adapter";thread=new Thread(()->{DvbSubtitleSession decoder=null;try{
        List<String> cmd=baseCommand(ffmpeg,input(source));Collections.addAll(cmd,"-c:s","dvbsub","-f","mpegts","-muxdelay","0","-muxpreload","0","pipe:1");ProcessBuilder pb=new ProcessBuilder(cmd);process=pb.start();decoder=new DvbSubtitleSession(context,token,anchorMs,SubtitleCue.Owner.LOCAL_FILE,context.fileCues());decoder.configure(true,-1,-1,track.language);byte[] buf=new byte[188*64];long pos=0;int n;while(!stopRequested&&(n=process.getInputStream().read(buf))>=0){if(n==0)continue;bytes+=n;if(bytes>SubtitleLimits.MAX_SUBTITLE_TAP_BYTES)throw new IOException("ordinary bitmap subtitle limit exceeded");decoder.observe("ordinary-file-adapter",pos,buf,0,n);pos+=n;}if(stopRequested)return;String stderr=readSmall(process.getErrorStream(),65536);int exit=process.waitFor();decoder.finish();if(exit!=0)throw new IOException(stderr.isEmpty()?"FFmpeg bitmap subtitle adapter exited "+exit:stderr);complete=true;
    }catch(Exception e){if(!stopRequested)error=compact(e);}finally{running=false;}},"webplayer-file-sub-bitmap");thread.setDaemon(true);thread.start();}

    private static String readSmall(InputStream in,int limit)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[2048];int n;while(b.size()<limit&&(n=in.read(x,0,Math.min(x.length,limit-b.size())))>0)b.write(x,0,n);return new String(b.toByteArray(),StandardCharsets.UTF_8).trim();}
    private static String compact(Exception e){String s=e.getMessage();return(s==null?e.getClass().getSimpleName():s).replace('\n',' ').replace('\r',' ');}
}
