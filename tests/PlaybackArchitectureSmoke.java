package org.opensagetv.webplayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** P02 contracts: ownership, inventory, cue/clock bounds, migration and source preservation. */
public final class PlaybackArchitectureSmoke {
    static int tests;
    static void check(boolean b,String m){if(!b)throw new AssertionError(m);tests++;System.out.println("PASS "+m);}
    static void rejects(Runnable r,String m){try{r.run();throw new AssertionError("Expected failure: "+m);}catch(IllegalArgumentException ok){tests++;System.out.println("PASS "+m);}}
    static StreamOptions opts(String... kv){Map<String,String> m=new HashMap<String,String>();for(int i=0;i<kv.length;i+=2)m.put(kv[i],kv[i+1]);return StreamOptions.fromMap(m);}

    public static void main(String[] args)throws Exception{
        ownership();cueAndClock();inventory();migration();sourceBranch();containerSubtitleBranch();capabilities();
        System.out.println("P02 playback architecture: "+tests+" PASS");
    }
    static void ownership(){
        PlaybackSessionContext a=new PlaybackSessionContext(),b=new PlaybackSessionContext();PlaybackSessionContext.Token a1=a.beginSource(),b1=b.beginSource();
        check(a.isCurrent(a1)&&b.isCurrent(b1)&&!a.isCurrent(b1),"two browser playback sessions are isolated");
        PlaybackSessionContext.Token a2=a.beginSeek();check(!a.isCurrent(a1)&&a.isCurrent(a2)&&b.isCurrent(b1),"seek retires only its own prior epoch");
        PlaybackSessionContext.Token a3=a.beginFlush();check(!a.isCurrent(a2)&&a.isCurrent(a3),"FLUSH has an independent generation");
        a.invalidateSource();check(!a.isCurrent(a3)&&b.isCurrent(b1),"source replacement/stop invalidates stale callbacks without touching another client");
    }
    static void cueAndClock(){
        PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t=c.beginSource();SubtitleCueQueue q=c.cues();
        check(q.offer(SubtitleCue.text(t,SubtitleCue.Owner.LOCAL_FILE,1000,800,"hello",false,false)),"text cue accepted inside current epoch");
        check(q.drainDue(t,999,20).isEmpty()&&q.size()==1,"read-ahead cue does not render before presentation clock");
        check(q.drainDue(t,1000,20).size()==1&&q.size()==0,"due cue drains on presentation time");
        PlaybackSessionContext.Token old=t;t=c.beginSeek();check(!q.offer(SubtitleCue.text(old,SubtitleCue.Owner.LOCAL_FILE,0,0,"stale",false,false)),"stale cue is rejected after seek");
        final PlaybackSessionContext.Token current=t;
        byte[] rgba=new byte[16];check(q.offer(SubtitleCue.bitmap(current,SubtitleCue.Owner.LOCAL_DVB,0,500,1920,1080,10,20,2,2,"rgba-straight",rgba,false,false)),"straight-alpha bitmap cue shares the versioned queue");
        rejects(()->SubtitleCue.bitmap(current,SubtitleCue.Owner.LOCAL_DVB,0,0,5000,3000,0,0,1,1,"rgba-straight",new byte[4],false,false),"bitmap dimension limit fails closed");
        rejects(()->SubtitleCue.text(current,SubtitleCue.Owner.LOCAL_FILE,0,0,new String(new char[SubtitleLimits.MAX_TEXT_CHARS+1]),false,false),"text payload limit fails closed");
        SubtitleClock clock=new SubtitleClock();long wrap=(1L<<33);clock.anchor(wrap-90000,5000);check(clock.sourcePtsToRecordingMs(0)==6000,"33-bit source PTS wrap maps to monotonic recording time");
        check(SubtitleClock.presentationMs(5000,750)==4250&&SubtitleClock.presentationMs(200,-500)==700,"caption delay maps independently from source timestamps");
        long bytes=0;for(int i=0;i<SubtitleLimits.MAX_CUES+10;i++){SubtitleCue cue=SubtitleCue.text(t,SubtitleCue.Owner.LOCAL_FILE,10000+i,0,"x",false,false);if(q.offer(cue))bytes++;}
        check(q.size()<=SubtitleLimits.MAX_CUES&&bytes<=SubtitleLimits.MAX_CUES,"cue-count backpressure is bounded");
    }
    static void inventory()throws Exception{
        String flat="streams.stream.0.index=0\nstreams.stream.0.id=\"0x101\"\nstreams.stream.0.codec_type=\"video\"\nstreams.stream.0.codec_name=\"h264\"\n"+
                "streams.stream.1.index=2\nstreams.stream.1.id=\"0x120\"\nstreams.stream.1.codec_type=\"subtitle\"\nstreams.stream.1.codec_name=\"dvb_teletext\"\nstreams.stream.1.tags.language=\"eng\"\nstreams.stream.1.disposition.default=1\n"+
                "streams.stream.2.index=3\nstreams.stream.2.codec_type=\"subtitle\"\nstreams.stream.2.codec_name=\"eia_608\"\nstreams.stream.2.tags.language=\"eng\"\n";
        MediaProbe.Info info=MediaProbe.parse(flat);check(info.tracks.get(1).sourcePid==0x120&&info.tracks.get(1).defaultTrack,"ffprobe inventory preserves source PID and disposition");
        check(info.tracks.get(2).language.isEmpty(),"CEA-608 language remains Unknown instead of inheriting guessed metadata");
        List<TsServiceInventoryProbe.Service> services=TsServiceInventoryProbe.scan(tsFixture());check(services.size()==2,"bounded PAT/PMT inventory discovers Teletext and DVB subtitle services");
        MediaProbe.mergeTransportServices(info,services);check(info.services.size()==2,"service inventory retains distinct descriptor identities instead of collapsing by UI ordinal");MediaProbe.Track tele=null,dvb=null;for(MediaProbe.Track x:info.subtitles()){if("teletext".equals(x.serviceKind))tele=x;if("dvb-subtitle".equals(x.serviceKind))dvb=x;}
        check(tele!=null&&tele.sourcePid==0x120&&tele.teletextPage==288&&"observed-pmt".equals(tele.evidence),"Teletext inventory preserves PID/language/type/magazine/page and observed evidence");
        check(dvb!=null&&dvb.sourcePid==0x121&&dvb.compositionPageId==1&&dvb.ancillaryPageId==2,"DVB inventory preserves PID/composition/ancillary page identity");
        check(info.json().contains("subtitleCapabilities")&&info.json().contains("implemented-webvtt-adapter"),"inventory reports completed P06 subtitle capabilities");
    }
    static void migration(){
        StreamOptions old=StreamOptions.fromMap(new HashMap<String,String>());check(old.profileSchemaVersion==8&&old.captions.equals("off")&&old.captionAuthority.equals("off")&&old.subtitleMode.equals("off"),"legacy profile with no schema migrates through schema 8 without enabling ordinary subtitles");
        StreamOptions schema4=opts("profileSchemaVersion","4","captions","teletext","captionPage","888","captionLanguage","eng");
        check(schema4.profileSchemaVersion==4&&schema4.captionAuthority.equals("off")&&schema4.captions.equals("teletext")&&schema4.captionPage==888,"schema 4 profile remains compatible and gains a safe OFF authority default");
        StreamOptions cea=opts("captions","608","captionService","4","audioTrack","5","subtitleMode","track","subtitleTrack","7","videoKbps","9000");StreamOptions next=cea.forNextRecording();
        check(next.captions.equals("608")&&next.captionService==4&&next.videoKbps==9000,"literal CEA-608 CC4 and unrelated stream settings survive migration");
        check(next.audioTrack==-1&&next.subtitleTrack==-1,"per-recording audio/subtitle indices do not leak into the next recording");
        StreamOptions c708=opts("captions","708","captionService","63");check(c708.captionService==63,"literal CEA-708 service selections remain addressable");
    }
    static void sourceBranch()throws Exception{
        Path root=Files.createTempDirectory("p02-subbranch-");try{
            File ts=root.resolve("source.ts").toFile();Files.write(ts.toPath(),tsPayloadForPidCopy());MediaProbe.Track t=new MediaProbe.Track();t.index=2;t.sourcePid=0x120;t.type="subtitle";t.codec="dvb_teletext";
            PlaybackSessionContext ctx=new PlaybackSessionContext();PlaybackSessionContext.Token token=ctx.beginSource();SubtitleSourceBranch branch=SubtitleSourceBranch.start(ts,t,root.toFile(),"/not-needed",token,()->false);
            long end=System.currentTimeMillis()+3000;while(branch.running&&System.currentTimeMillis()<end)Thread.sleep(20);check(!branch.running&&branch.error.isEmpty()&&branch.bytes==376,"TS source branch copies only selected original PID packets");
            byte[] copied=Files.readAllBytes(branch.output.toPath());for(int off=0;off<copied.length;off+=188){int pid=((copied[off+1]&31)<<8)|(copied[off+2]&255);if(pid!=0x120)throw new AssertionError("wrong PID copied");}check(true,"subtitle branch does not duplicate source video packets");
            MediaProbe.Info info=new MediaProbe.Info();MediaProbe.Track v=new MediaProbe.Track();v.index=0;v.type="video";v.codec="mpeg2video";v.rate="30/1";info.tracks.add(v);info.tracks.add(t);MediaProbe.Track ordinary=new MediaProbe.Track();ordinary.index=4;ordinary.type="subtitle";ordinary.codec="subrip";info.tracks.add(ordinary);
            StreamPlan tsPlan=new StreamPlan(opts("player","mpegts","videoMode","transcode","subtitleMode","track","subtitleTrack","4"),info);StreamPlan hlsPlan=new StreamPlan(opts("player","hls","videoMode","transcode","subtitleMode","track","subtitleTrack","4"),info);
            TranscoderManager.Probe fake=new TranscoderManager.Probe(true,"/usr/bin/ffmpeg","test","libx264",false,false,"none","test");
            String tsCmd=StreamCommand.build(fake,ts,false,false,0,root.resolve("tsout").toFile(),tsPlan).toString();String hlsCmd=StreamCommand.build(fake,ts,false,false,0,root.resolve("hlsout").toFile(),hlsPlan).toString();
            check(tsCmd.contains("-sn")&&hlsCmd.contains("-sn")&&tsPlan.subtitle==ordinary&&hlsPlan.subtitle==ordinary,"subtitle preservation is transport-independent from TS/HLS A/V outputs and video encoder stripping");
            MediaProbe.Track missing=new MediaProbe.Track();missing.index=9;missing.type="subtitle";missing.codec="subrip";info.tracks.remove(ordinary);rejects(()->new StreamPlan(opts("subtitleMode","track","subtitleTrack","4"),info),"unavailable selected subtitle track fails before replacing playback");
        }finally{delete(root);}
    }

    static void containerSubtitleBranch()throws Exception{
        String ff=System.getenv("TEST_FFMPEG");if(ff==null||ff.isEmpty())ff="/usr/bin/ffmpeg";File exe=new File(ff);if(!exe.isFile()||!exe.canExecute()){System.out.println("SKIP container subtitle branch: FFmpeg unavailable");return;}
        Path root=Files.createTempDirectory("p02-container-sub-");try{
            Path srt=root.resolve("sub.srt");Files.write(srt,Arrays.asList("1","00:00:00,000 --> 00:00:01,500","P02 subtitle branch",""),StandardCharsets.UTF_8);
            File src=root.resolve("source.mkv").toFile();Process p=new ProcessBuilder(ff,"-v","error","-y","-f","lavfi","-i","testsrc2=size=160x90:rate=10","-f","srt","-i",srt.toString(),"-t","2","-map","0:v:0","-map","1:0","-c:v","mpeg2video","-c:s","srt",src.getAbsolutePath()).redirectErrorStream(true).start();
            if(!p.waitFor(20,TimeUnit.SECONDS)||p.exitValue()!=0)throw new AssertionError("could not generate subtitle fixture");
            MediaProbe.Info info=MediaProbe.read(src,ff);MediaProbe.Track sub=null;for(MediaProbe.Track t:info.subtitles())if("subrip".equals(t.codec)){sub=t;break;}check(sub!=null,"ffprobe typed inventory discovers container subtitle track");
            PlaybackSessionContext ctx=new PlaybackSessionContext();SubtitleSourceBranch branch=SubtitleSourceBranch.start(src,sub,root.toFile(),ff,ctx.beginSource(),()->false);long end=System.currentTimeMillis()+8000;while(branch.running&&System.currentTimeMillis()<end)Thread.sleep(25);
            check(!branch.running&&branch.error.isEmpty()&&branch.output.isFile()&&branch.output.length()>0,"container subtitle stream-copy branch preserves original subtitle stream independently of video encode");
            MediaProbe.Info copied=MediaProbe.read(branch.output,ff);check(!copied.subtitles().isEmpty(),"preserved container side channel remains probeable as subtitle media");
        }finally{delete(root);}
    }
    static void capabilities(){String c=SubtitleCapabilities.json();check(c.contains("\"teletext\":{\"state\":\"implemented-level1-subtitle\",\"advertised\":true")&&c.contains("\"dvbBitmap\":{\"state\":\"implemented-generated-vectors\",\"advertised\":true")&&c.contains("\"pgs\":{\"state\":\"implemented-ffmpeg-bitmap-adapter\",\"advertised\":true")&&c.contains("\"vobsub\":{\"state\":\"implemented-ffmpeg-bitmap-adapter\",\"advertised\":true")&&c.contains("\"textFile\":{\"state\":\"implemented-webvtt-adapter\",\"advertised\":true")&&c.contains("maxSubtitleTapBytes"),"capability/resource contract advertises completed P06 ordinary subtitle families");}

    static byte[] tsFixture()throws Exception{
        byte[] pat={0,(byte)0xb0,0x0d,0,1,(byte)0xc1,0,0,0,1,(byte)0xe0,0x64,0,0,0,0};
        ByteArrayOutputStream pmt=new ByteArrayOutputStream();int desc1=7,desc2=10;int sectionLength=9+(5+desc1)+(5+desc2)+4;
        write(pmt,2,0xb0|(sectionLength>>8),sectionLength,0,1,0xc1,0,0,0xe1,1,0xf0,0);
        write(pmt,6,0xe1,0x20,0xf0,desc1,0x56,5,'e','n','g',(2<<3)|2,0x88);
        write(pmt,6,0xe1,0x21,0xf0,desc2,0x59,8,'e','n','g',0x10,0,1,0,2);
        write(pmt,0,0,0,0);
        return join(packet(0,true,pointer(pat)),packet(0x64,true,pointer(pmt.toByteArray())),packet(0x1fff,false,new byte[0]),packet(0x1fff,false,new byte[0]),packet(0x1fff,false,new byte[0]));
    }
    static byte[] tsPayloadForPidCopy()throws Exception{return join(packet(0x101,false,new byte[]{1}),packet(0x120,false,new byte[]{2}),packet(0x101,false,new byte[]{3}),packet(0x120,false,new byte[]{4}),packet(0x1fff,false,new byte[0]));}
    static byte[] pointer(byte[] sec){byte[] r=new byte[sec.length+1];System.arraycopy(sec,0,r,1,sec.length);return r;}
    static byte[] packet(int pid,boolean start,byte[] payload){byte[] r=new byte[188];Arrays.fill(r,(byte)0xff);r[0]=0x47;r[1]=(byte)((start?0x40:0)|((pid>>8)&31));r[2]=(byte)pid;r[3]=0x10;System.arraycopy(payload,0,r,4,Math.min(184,payload.length));return r;}
    static byte[] join(byte[]... a)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();for(byte[] b:a)o.write(b);return o.toByteArray();}
    static void write(ByteArrayOutputStream o,int...v){for(int x:v)o.write(x&255);}
    static void delete(Path root)throws Exception{if(root==null||!Files.exists(root))return;try(java.util.stream.Stream<Path> s=Files.walk(root)){s.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
}
