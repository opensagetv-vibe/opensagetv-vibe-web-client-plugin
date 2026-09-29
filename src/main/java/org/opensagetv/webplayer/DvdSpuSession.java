package org.opensagetv.webplayer;

import java.io.*;import java.util.*;

/** DVD private_stream_1 SPU extraction, decode, scheduling and browser cue adapter. */
final class DvdSpuSession {
    private final DvdNativeSession dvd; private final DvdSpuCore.Diagnostics diag=new DvdSpuCore.Diagnostics();
    private final DvdSpuCore.Assembler assembler=new DvdSpuCore.Assembler(diag); private final DvdSpuCore.Decoder decoder=new DvdSpuCore.Decoder(diag);
    private final PlaybackSessionContext context=new PlaybackSessionContext(); private PlaybackSessionContext.Token token=context.beginSource(); private final SubtitleCueQueue queue=context.cues();
    private final ByteArrayOutputStream ps=new ByteArrayOutputStream(); private final int[] clut=new int[16]; private boolean clutValid; private DvdSpuCore.Highlight highlight=DvdSpuCore.Highlight.hidden();
    private int selected=62; private DvdSpuCore.Frame presented; private long generation; private long cues,clears;
    DvdSpuSession(DvdNativeSession dvd){this.dvd=dvd;generation=dvd.generation();}
    synchronized PlaybackSessionContext.Token token(){return token;} synchronized SubtitleCueQueue queue(){return queue;}
    synchronized void onGeneration(long g){if(g==generation)return;generation=g;token=context.beginFlush();assembler.reset();ps.reset();presented=null;offerClear(dvd.mediaTimeMs());}
    synchronized void reset(){token=context.beginSource();assembler.reset();ps.reset();presented=null;highlight=DvdSpuCore.Highlight.hidden();clutValid=false;selected=62;}
    synchronized void setClut(byte[] p){if(p==null||p.length<64)return;for(int i=0;i<16;i++)clut[i]=MiniClientSession.readInt(p,i*4);clutValid=true;recomposeCurrent();}
    synchronized void setHighlight(byte[] p){if(p==null||p.length<20)return;highlight=new DvdSpuCore.Highlight(!(allZero(p)),MiniClientSession.readInt(p,0),MiniClientSession.readInt(p,4),MiniClientSession.readInt(p,8),MiniClientSession.readInt(p,12),MiniClientSession.readInt(p,16));recomposeCurrent();}
    synchronized void select(int value){selected=value; if(value==62||value==8192){offerClear(dvd.mediaTimeMs());} else if(presented!=null)recomposeCurrent();}
    synchronized void observe(byte[] b,int off,int len){if(b==null||len<=0)return;try{ps.write(b,off,len);}catch(Exception ignored){}parse();}
    private void parse(){byte[] d=ps.toByteArray();int c=0;while(c+6<=d.length){int s=find(d,c);if(s<0){c=Math.max(0,d.length-3);break;}if(s+6>d.length){c=s;break;}int id=d[s+3]&255;if(id!=0xbd){c=s+4;continue;}int n=((d[s+4]&255)<<8)|(d[s+5]&255);if(n==0||s+6+n>d.length){c=s;break;}int payload=payloadStart(d,s,s+6+n);if(payload>=0&&payload<s+6+n){int sub=d[payload]&255;if(sub>=0x20&&sub<=0x3f){long pts=parsePts(d,s,s+6+n);byte[] frag=Arrays.copyOfRange(d,payload+1,s+6+n);for(DvdSpuCore.Packet packet:assembler.feed(new DvdSpuCore.Packet(sub,mapPts(pts),frag)))for(DvdSpuCore.Frame f:decoder.decode(packet))schedule(f);}}c=s+6+n;}ps.reset();if(c<d.length){int keep=Math.min(d.length-c,256*1024);ps.write(d,d.length-keep,keep);}}
    private long mapPts(long raw){if(raw<0)return -1;long wrapped=DvdPtsClock.wrap33(raw+dvd.clock().ptsOffset90k()),ref=dvd.clock().currentClock90k();return ref>=0?DvdPtsClock.unwrapNear(wrapped,ref):wrapped;}
    private void schedule(DvdSpuCore.Frame f){if(f.event90k<0)return;long ms=Math.max(0,f.event90k/90);if(!f.display){if(presented!=null&&presented.streamId==f.streamId){presented=null;offerClear(ms);}return;}presented=f;boolean menu=highlight.visible;boolean selectedNow=selected>=0x20&&selected<=0x3f&&f.streamId==selected;boolean show=menu||selectedNow||(f.forced&&selected!=62&&selected!=8192);if(show)offerComposed(f,ms,true);}
    private void recomposeCurrent(){if(presented==null)return;offerComposed(presented,dvd.mediaTimeMs(),true);}
    private void offerComposed(DvdSpuCore.Frame f,long ms,boolean clear){DvdSpuCore.Argb a=DvdSpuCore.compose(f,clut,clutValid,highlight);if(!a.display){offerClear(ms);return;}int ch=dvd.videoFormat()==1?576:480;SubtitleCue cue=SubtitleCue.bitmap(token,SubtitleCue.Owner.DVD_SPU,ms,0,720,ch,a.x,a.y,a.w,a.h,"rgba-straight",a.rgba,clear,false);if(queue.offer(cue)){cues++;diag.presented++;}}
    private void offerClear(long ms){SubtitleCue cue=SubtitleCue.bitmap(token,SubtitleCue.Owner.DVD_SPU,Math.max(0,ms),0,720,dvd.videoFormat()==1?576:480,0,0,0,0,"rgba-straight",new byte[0],true,false);if(queue.offer(cue)){cues++;clears++;diag.cleared++;}}
    synchronized String json(){return "{\"selected\":"+selected+",\"clutValid\":"+clutValid+",\"highlight\":"+highlight.visible+",\"pending\":"+queue.size()+",\"queue\":"+queue.json(dvd.mediaTimeMs())+",\"cues\":"+cues+",\"clears\":"+clears+",\"fragments\":"+diag.fragments+",\"completed\":"+diag.completed+",\"decoded\":"+diag.decoded+",\"malformed\":"+diag.malformed+"}";}
    private static boolean allZero(byte[] a){for(int i=0;i<20&&i<a.length;i++)if(a[i]!=0)return false;return true;}private static int find(byte[] d,int p){for(int i=p;i+3<d.length;i++)if(d[i]==0&&d[i+1]==0&&d[i+2]==1)return i;return -1;}
    private static int payloadStart(byte[] d,int p,int e){if(p+9>e)return-1;if((d[p+6]&0xc0)==0x80)return p+9+(d[p+8]&255);int q=p+6;while(q<e&&(d[q]&255)==255)q++;if(q+1<e&&(d[q]&0xc0)==0x40)q+=2;int t=q<e?d[q]&0xf0:0;if(t==0x20)q+=5;else if(t==0x30)q+=10;else if(q<e&&d[q]==0x0f)q++;return q<e?q:-1;}
    private static long parsePts(byte[] d,int p,int e){if(p+14>e)return-1;if((d[p+6]&0xc0)==0x80){int q=p+9;if(q+5>e)return-1;return dec(d,q);}return-1;}private static long dec(byte[] d,int p){return(((long)(d[p]&14))<<29)|(((long)(d[p+1]&255))<<22)|(((long)(d[p+2]&254))<<14)|(((long)(d[p+3]&255))<<7)|((d[p+4]&254)>>>1);}
}
