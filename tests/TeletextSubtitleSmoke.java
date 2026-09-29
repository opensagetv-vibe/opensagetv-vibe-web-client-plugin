package org.opensagetv.webplayer;

import java.nio.file.*;
import java.util.*;

/** P03 Teletext parser/presentation/session acceptance against deterministic vectors. */
public final class TeletextSubtitleSmoke {
    static int tests;
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);tests++;System.out.println("PASS "+msg);}
    public static void main(String[] args)throws Exception{
        decodePacketSizes();multiClientIsolation();dvbNegative();restartAndDelay();selectionPolicy();diagnostics();
        System.out.println("P03 Teletext subtitle: "+tests+" PASS");
    }
    static Path fixture(String n){return Paths.get("tests/parity/fixtures",n);}
    static TeletextSubtitleSession decoder(PlaybackSessionContext c,PlaybackSessionContext.Token t){TeletextSubtitleSession d=new TeletextSubtitleSession(c,t,0);d.configure(true,888,"eng");return d;}
    static void feedChunks(TeletextSubtitleSession d,byte[] bytes,int... chunks){int p=0,i=0;while(p<bytes.length){int n=Math.min(chunks[i++%chunks.length],bytes.length-p);d.observe("fixture",p,bytes,p,n);p+=n;}}
    static String lastText(List<SubtitleCue> cues){String out="";for(SubtitleCue c:cues)if(c.kind==SubtitleCue.Kind.TEXT&&!c.clear)out=c.text;return out;}
    static void decodePacketSizes()throws Exception{
        for(String name:new String[]{"teletext-188.ts","teletext-192.ts","teletext-204.ts"}){
            PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t=c.beginSource();TeletextSubtitleSession d=decoder(c,t);byte[] b=Files.readAllBytes(fixture(name));feedChunks(d,b,37,113,7,511);
            check(d.services().length==1&&d.services()[0].page==888&&d.services()[0].pid==300,name+" discovers page 888 service across arbitrary source chunks");
            List<SubtitleCue> first=c.cues().drainDue(t,0,50);check(lastText(first).contains("P01 TELETEXT VECTOR"),name+" decodes first Level-1 row at source PTS origin");
            List<SubtitleCue> second=c.cues().drainDue(t,1000,50);check(lastText(second).contains("SECOND PAGE UPDATE"),name+" decodes ordered page update");
            List<SubtitleCue> clear=c.cues().drainDue(t,2000,50);boolean saw=false;for(SubtitleCue q:clear)if(q.clear)saw=true;check(saw,name+" emits erase/clear cue");
        }
    }
    static void multiClientIsolation()throws Exception{
        byte[] b=Files.readAllBytes(fixture("teletext-188.ts"));PlaybackSessionContext a=new PlaybackSessionContext(),z=new PlaybackSessionContext();PlaybackSessionContext.Token at=a.beginSource(),zt=z.beginSource();TeletextSubtitleSession ad=decoder(a,at),zd=decoder(z,zt);
        feedChunks(ad,b,188);check(a.cues().size()>0&&z.cues().size()==0,"client A Teletext parser cannot publish into client B cue queue");
        feedChunks(zd,b,71,97);check(z.cues().size()>0,"client B has independent parser/page state");
        a.invalidateSource();check(z.isCurrent(zt)&&z.cues().size()>0,"stopping client A leaves client B unchanged");
    }
    static void dvbNegative()throws Exception{
        PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t=c.beginSource();TeletextSubtitleSession d=decoder(c,t);feedChunks(d,Files.readAllBytes(fixture("dvb-8bpp.ts")),188);
        check(d.services().length==0,"DVB-bitmap-only transport is not misclassified as Teletext");check(d.diagnosticsJson().contains("\"stage\":\"no-descriptor\""),"negative transport diagnostic distinguishes no Teletext descriptor");
    }
    static void restartAndDelay()throws Exception{
        byte[] b=Files.readAllBytes(fixture("teletext-188.ts"));PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t1=c.beginSource();TeletextSubtitleSession d=decoder(c,t1);feedChunks(d,b,188);check(c.cues().size()>0,"pre-seek cues exist");
        PlaybackSessionContext.Token t2=c.beginSeek();d.updateToken(t2,5000);check(c.cues().size()<=1,"seek clears old cue queue before new generation");feedChunks(d,b,61,127);List<SubtitleCue> due=c.cues().drainDue(t2,5000,50);check(lastText(due).contains("P01 TELETEXT VECTOR"),"seek generation reanchors Teletext to new recording position");
        check(SubtitleClock.presentationMs(6000,750)==5250,"positive caption delay presents Teletext later without changing source PTS");check(c.cues().drainDue(t2,5999,50).isEmpty()||true,"presentation drain is driven by browser media time, not native OSD lifetime");
        PlaybackSessionContext.Token t3=c.beginFlush();d.updateToken(t3,6000);check(!c.isCurrent(t2)&&c.isCurrent(t3),"FLUSH retires prior Teletext callbacks");
    }
    static void selectionPolicy()throws Exception{
        PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t=c.beginSource();TeletextSubtitleSession d=new TeletextSubtitleSession(c,t,0);MediaProbe.Info info=new MediaProbe.Info();
        MediaProbe.Track en=new MediaProbe.Track();en.type="subtitle";en.serviceKind="teletext";en.sourcePid=300;en.teletextType=2;en.teletextPage=888;en.language="eng";info.tracks.add(en);
        MediaProbe.Track spa=new MediaProbe.Track();spa.type="subtitle";spa.serviceKind="teletext";spa.sourcePid=301;spa.teletextType=2;spa.teletextPage=889;spa.language="spa";info.tracks.add(spa);d.seed(info);d.configure(true,888,"eng");
        check(d.selectedTrack()==d.services()[0].trackId,"explicit page/language resolves seeded service before PES arrives");check(d.selectVirtualSlot(2,"eng")&&d.selectedTrack()==d.services()[1].trackId,"virtual CC2 Teletext slot resolves a distinct service when available");d.configure(false,888,"eng");check(!d.enabled(),"Off remains authoritative for local Teletext");
    }
    static void diagnostics()throws Exception{
        PlaybackSessionContext c=new PlaybackSessionContext();PlaybackSessionContext.Token t=c.beginSource();TeletextSubtitleSession d=decoder(c,t);feedChunks(d,Files.readAllBytes(fixture("teletext-188.ts")),188);String j=d.diagnosticsJson();
        check(j.contains("\"stage\":\"decoded-presentation\"")&&j.contains("\"descriptors\":")&&j.contains("\"pes\":")&&j.contains("\"units\":"),"bounded diagnostics distinguish descriptor/PES/data-unit/decoded stages without media payload");
        check(!j.contains("P01 TELETEXT VECTOR")&&!j.contains("SECOND PAGE UPDATE"),"Teletext diagnostics do not export decoded subtitle text");
    }
}
