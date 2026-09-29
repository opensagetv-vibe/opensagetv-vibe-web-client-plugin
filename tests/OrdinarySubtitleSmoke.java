package org.opensagetv.webplayer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class OrdinarySubtitleSmoke {
  public static void main(String[] args)throws Exception{
    File root=new File(args.length>0?args[0]:"tests/parity/fixtures"),ffmpeg=new File(args.length>1?args[1]:"/usr/bin/ffmpeg");
    File tmp=Files.createTempDirectory("p06-sub-").toFile();try{
      File source=new File(tmp,"movie.ts");Files.copy(new File(root,"av-h264-aac.ts").toPath(),source.toPath());
      Files.copy(new File(root,"unicode-position.vtt").toPath(),new File(tmp,"movie.vtt").toPath());
      Files.copy(new File(root,"white-rectangle.sup").toPath(),new File(tmp,"movie.sup").toPath());
      Files.copy(new File(root,"unicode-overlap.srt").toPath(),new File(tmp,"movie-extra.srt").toPath()); // must not be associated
      MediaProbe.Info info=MediaProbe.read(source,ffmpeg.getAbsolutePath());
      MediaProbe.Track text=null,pgs=null;int unrelated=0;for(MediaProbe.Track t:info.ordinarySubtitles()){if("sidecar".equals(t.sourceKind)&&"vtt".equals(t.sidecarFormat))text=t;if("sidecar".equals(t.sourceKind)&&"sup".equals(t.sidecarFormat))pgs=t;if(t.title.contains("movie-extra"))unrelated++;}
      check(text!=null,"exact-basename VTT sidecar discovered");check(pgs!=null,"exact-basename PGS sidecar discovered");check(unrelated==0,"unrelated same-prefix sidecar rejected");
      check(OrdinarySubtitleSession.parseVttTime("00:01.250")==1250,"VTT timestamp");check("<b>x</b>".equals(OrdinarySubtitleSession.stripMarkup("<i>&lt;b&gt;x&lt;/b&gt;</i>")),"markup sanitized to text");

      PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token tok=c.beginSource();
      OrdinarySubtitleSession ts=OrdinarySubtitleSession.start(c,tok,text,source,ffmpeg.getAbsolutePath(),0,0);waitDone(ts);check(ts.error.isEmpty(),"text adapter: "+ts.error);
      List<SubtitleCue> first=c.fileCues().drainDue(tok,1000,64);check(first.size()==1&&first.get(0).kind==SubtitleCue.Kind.TEXT,"first text cue");check(first.get(0).text.contains("Unicode"),"unicode text retained");check(first.get(0).linePercent==80&&first.get(0).positionPercent==50,"basic VTT positioning retained");
      List<SubtitleCue> overlap=c.fileCues().drainDue(tok,2000,64);check(overlap.size()>=1,"overlap second cue delivered");check(overlap.get(0).text.equals("<img src=x onerror=alert(1)>"),"hostile markup decoded as inert text data");

      tok=c.beginSeek();OrdinarySubtitleSession bs=OrdinarySubtitleSession.start(c,tok,pgs,source,ffmpeg.getAbsolutePath(),0,0);waitDone(bs);check(bs.error.isEmpty(),"PGS adapter: "+bs.error);
      List<SubtitleCue> bitmaps=c.fileCues().drainDue(tok,3000,128);boolean rgba=false;for(SubtitleCue q:bitmaps)if(q.kind==SubtitleCue.Kind.BITMAP&&q.owner==SubtitleCue.Owner.LOCAL_FILE&&q.payload.length==q.width*q.height*4&&q.width>0){rgba=true;break;}check(rgba,"PGS decoded to LOCAL_FILE RGBA");
      // Generate an embedded PGS-bearing MKV and validate the same LOCAL_FILE bitmap path.
      File pgsMkv=new File(tmp,"pgs.mkv");Process pmk=new ProcessBuilder(ffmpeg.getAbsolutePath(),"-hide_banner","-loglevel","error","-y","-i",new File(root,"white-rectangle.sup").getAbsolutePath(),"-map","0:0","-c:s","copy",pgsMkv.getAbsolutePath()).start();check(pmk.waitFor()==0,"generate embedded PGS MKV fixture");MediaProbe.Info pi=MediaProbe.read(pgsMkv,ffmpeg.getAbsolutePath());MediaProbe.Track pt=null;for(MediaProbe.Track t:pi.ordinarySubtitles())if(t.codec.contains("hdmv_pgs")||t.codec.contains("pgs")){pt=t;break;}check(pt!=null,"embedded PGS MKV discovered");tok=c.beginSeek();OrdinarySubtitleSession ps=OrdinarySubtitleSession.start(c,tok,pt,pgsMkv,ffmpeg.getAbsolutePath(),0,0);waitDone(ps);check(ps.error.isEmpty(),"embedded PGS adapter: "+ps.error);List<SubtitleCue> pgsEmbedded=c.fileCues().drainDue(tok,3000,128);boolean pgsEmbeddedRgba=false;for(SubtitleCue q:pgsEmbedded)if(q.kind==SubtitleCue.Kind.BITMAP&&q.owner==SubtitleCue.Owner.LOCAL_FILE&&q.payload.length==q.width*q.height*4&&q.width>0)pgsEmbeddedRgba=true;check(pgsEmbeddedRgba,"embedded PGS MKV decoded to LOCAL_FILE RGBA");ps.stop();
      // Generate an embedded DVD/VobSub bitmap track from the open PGS fixture and run the same adapter.
      File dvd=new File(tmp,"dvdsub.mkv");Process mk=new ProcessBuilder(ffmpeg.getAbsolutePath(),"-hide_banner","-loglevel","error","-y","-i",new File(root,"white-rectangle.sup").getAbsolutePath(),"-map","0:0","-c:s","dvdsub",dvd.getAbsolutePath()).start();check(mk.waitFor()==0,"generate embedded dvd_subtitle fixture");MediaProbe.Info di=MediaProbe.read(dvd,ffmpeg.getAbsolutePath());MediaProbe.Track dt=null;for(MediaProbe.Track t:di.ordinarySubtitles())if(t.codec.contains("dvd_subtitle")){dt=t;break;}check(dt!=null,"embedded dvd_subtitle discovered");tok=c.beginSeek();OrdinarySubtitleSession ds=OrdinarySubtitleSession.start(c,tok,dt,dvd,ffmpeg.getAbsolutePath(),0,0);waitDone(ds);check(ds.error.isEmpty(),"DVD/VobSub adapter: "+ds.error);List<SubtitleCue> dvdCues=c.fileCues().drainDue(tok,3000,128);boolean dvdRgba=false;for(SubtitleCue q:dvdCues)if(q.kind==SubtitleCue.Kind.BITMAP&&q.owner==SubtitleCue.Owner.LOCAL_FILE&&q.payload.length==q.width*q.height*4&&q.width>0)dvdRgba=true;check(dvdRgba,"embedded DVD/VobSub decoded to LOCAL_FILE RGBA");
      ds.stop();bs.stop();ts.stop();System.out.println("OrdinarySubtitleSmoke PASS tracks="+info.ordinarySubtitles().size()+" pgsCues="+bitmaps.size()+" dvdCues="+dvdCues.size());
    }finally{delete(tmp);}
  }
  static void waitDone(OrdinarySubtitleSession s)throws Exception{long end=System.currentTimeMillis()+12000;while(s.running&&System.currentTimeMillis()<end)Thread.sleep(20);if(s.running)throw new AssertionError("subtitle worker timeout");}
  static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
  static void delete(File f){if(f==null||!f.exists())return;if(f.isDirectory())for(File x:Objects.requireNonNull(f.listFiles()))delete(x);f.delete();}
}
