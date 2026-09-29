package org.opensagetv.webplayer;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** P04 DVB bitmap parser/presentation/session acceptance against deterministic vectors. */
public final class DvbSubtitleSmoke {
    static int tests;
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);tests++;System.out.println("PASS "+msg);}
    public static void main(String[] args)throws Exception{decodeDepths();packetSizeVariants();selectionAndDisable();generationLifetime();teletextNegative();diagnostics();System.out.println("P04 DVB bitmap subtitle: "+tests+" PASS");}
    static Path fixture(String n){return Paths.get("tests/parity/fixtures",n);}
    static DvbSubtitleSession decoder(PlaybackSessionContext c,PlaybackSessionContext.Token t){DvbSubtitleSession d=new DvbSubtitleSession(c,t,0);d.configure(true,-1,-1,"eng");return d;}
    static void feed(DvbSubtitleSession d,byte[] b,int... chunks){int p=0,i=0;while(p<b.length){int n=Math.min(chunks[i++%chunks.length],b.length-p);d.observe("fixture",p,b,p,n);p+=n;}}
    static SubtitleCue bitmap(List<SubtitleCue> cues){for(SubtitleCue c:cues)if(c.kind==SubtitleCue.Kind.BITMAP&&c.width>0)return c;return null;}
    static String sha(byte[] b)throws Exception{byte[] h=MessageDigest.getInstance("SHA-256").digest(b);StringBuilder s=new StringBuilder();for(byte x:h)s.append(String.format("%02x",x&255));return s.toString();}
    static void decodeDepths()throws Exception{
        String expected="d8c6f9e2b3919c02f0d1bbca24441003f73b067d4f0eb5261ee16a5bb801df90";
        // Keep the expected hash grounded in the fixture registry, not in decoder implementation.
        String registry=new String(Files.readAllBytes(Paths.get("tests/parity/fixtures/fixtures.generated.json")),java.nio.charset.StandardCharsets.UTF_8);
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("\\\"rgba_sha256\\\"\\s*:\\s*\\\"([0-9a-f]{64})\\\"").matcher(registry);if(m.find())expected=m.group(1);
        for(int depth:new int[]{2,4,8}){
            PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t=c.beginSource();DvbSubtitleSession d=decoder(c,t);feed(d,Files.readAllBytes(fixture("dvb-"+depth+"bpp.ts")),31,157,9,401);
            DvbSubtitleSession.Service[] ss=d.services();check(ss.length==1&&ss[0].pid==336&&ss[0].compositionPageId==1&&ss[0].ancillaryPageId==1,"DVB "+depth+"bpp discovers descriptor 0x59 identity");
            SubtitleCue q=bitmap(c.cues().drainDue(t,0,64));check(q!=null,"DVB "+depth+"bpp emits RGBA bitmap cue at first PTS");
            check(q.canvasWidth==720&&q.canvasHeight==576&&q.x==100&&q.y==400&&q.width==6&&q.height==2,"DVB "+depth+"bpp preserves canvas and region placement");
            check(q.payload.length==48&&sha(q.payload).equals(expected),"DVB "+depth+"bpp RGBA/alpha pixels match independent golden hash");
            List<SubtitleCue> later=c.cues().drainDue(t,2000,64);boolean clear=false;for(SubtitleCue x:later)if(x.kind==SubtitleCue.Kind.BITMAP&&x.clear)clear=true;check(clear,"DVB "+depth+"bpp page clear emits a timed bitmap clear");
        }
    }
    static void packetSizeVariants()throws Exception{
        byte[] raw=Files.readAllBytes(fixture("dvb-2bpp.ts"));
        for(int size:new int[]{192,204}){java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();for(int p=0;p<raw.length;p+=188){if(size==192)out.write(new byte[4]);out.write(raw,p,188);if(size==204)out.write(new byte[16]);}
            PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t=c.beginSource();DvbSubtitleSession d=decoder(c,t);feed(d,out.toByteArray(),17,233,91);check(bitmap(c.cues().drainDue(t,0,64))!=null,"DVB parser accepts "+size+"-byte transport framing");}
    }
    static void selectionAndDisable()throws Exception{
        PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t=c.beginSource();DvbSubtitleSession d=new DvbSubtitleSession(c,t,0);d.configure(true,336,1,"eng");check(d.selectedTrack()==-1,"saved DVB PID may be configured before asynchronous discovery");feed(d,Files.readAllBytes(fixture("dvb-2bpp.ts")),188);check(d.selectedTrack()>=0,"late PMT discovery reapplies saved DVB PID/composition selection");
        check(!d.selectSourcePid(999)&&d.selectedTrack()==-1,"unknown ordinary-video DVB PID fails closed instead of choosing another track");
        PlaybackSessionContext lateContext=new PlaybackSessionContext();PlaybackSessionContext.Token lateToken=lateContext.beginSource();DvbSubtitleSession late=new DvbSubtitleSession(lateContext,lateToken,0);check(late.requestSourcePid(999),"media command PID may be accepted pending asynchronous PMT discovery");feed(late,Files.readAllBytes(fixture("dvb-2bpp.ts")),188);check(late.selectedTrack()==-1,"late discovery of other DVB services does not fall back from an explicitly requested unknown PID");
        check(MiniClientMediaBridge.applyDvbStreamCommand(d,1,336)==0&&d.selectedTrack()>=0,"media command 36/type 1 adapter selects DVB by physical source PID");check(MiniClientMediaBridge.applyDvbStreamCommand(d,1,8192)==0&&d.selectedTrack()==-1,"media command 36 DVB disable sentinel 8192 clears local bitmap selection");check(MiniClientMediaBridge.applyDvbStreamCommand(d,0,336)==-1,"DVB adapter rejects audio/DVD-unrelated stream type instead of reusing subtitle semantics");
    }
    static void generationLifetime()throws Exception{
        byte[] b=Files.readAllBytes(fixture("dvb-4bpp.ts"));PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t1=c.beginSource();DvbSubtitleSession d=decoder(c,t1);feed(d,b,188);check(c.cues().size()>0,"DVB cues exist before seek");PlaybackSessionContext.Token t2=c.beginSeek();d.updateToken(t2,5000);feed(d,b,67,121);SubtitleCue q=bitmap(c.cues().drainDue(t2,5000,64));check(q!=null&&q.ptsMs==5000,"seek generation reanchors DVB display set and retires stale cues");PlaybackSessionContext.Token t3=c.beginFlush();d.updateToken(t3,6000);check(!c.isCurrent(t2)&&c.isCurrent(t3),"FLUSH retires prior DVB callbacks");d.finish();boolean clear=false;for(SubtitleCue x:c.cues().drainDue(t3,6000,64))if(x.clear)clear=true;check(clear,"EOS/stop emits final DVB clear");
    }
    static void teletextNegative()throws Exception{PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t=c.beginSource();DvbSubtitleSession d=decoder(c,t);feed(d,Files.readAllBytes(fixture("teletext-188.ts")),188);check(d.services().length==0,"Teletext-only transport is not misclassified as DVB bitmap");}
    static void diagnostics()throws Exception{PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t=c.beginSource();DvbSubtitleSession d=decoder(c,t);feed(d,Files.readAllBytes(fixture("dvb-8bpp.ts")),188);String j=d.diagnosticsJson();check(j.contains("\"stage\":\"decoded-presentation\"")&&j.contains("\"descriptors\":")&&j.contains("\"displaySets\":"),"DVB diagnostics expose bounded decode stages");check(!j.contains("data\"")&&!j.contains("rgba"),"DVB diagnostics do not export media or decoded pixel payloads");}
}
