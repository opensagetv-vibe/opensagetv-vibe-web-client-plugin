package org.opensagetv.webplayer;

import java.io.ByteArrayOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * P05 stock-SageTV caption bridge. Converts A/53 cc_data triples into the
 * legacy MiniClient subtitle callback record format consumed by SageTV's
 * event-225 CC path. Presentation is clock-gated so extractor read-ahead never
 * makes captions run ahead of browser playback.
 */
final class LegacyExtenderCaptionBridge {
    static final int PTS_VALID=0x01, FLUSH_SUBTITLE_QUEUE=0x02, CC_SUBTITLE=0x10;
    interface Sink { void postSubtitleInfo(long pts45Khz,long duration45Khz,byte[] data,int flags); }
    private final Sink sink;
    private final ArrayDeque<Pending> pending=new ArrayDeque<Pending>();
    private final ArrayDeque<Recent> recent=new ArrayDeque<Recent>();
    private static final int MAX_RECENT=256;
    private long forwardedBytes; private int forwardedSamples;

    LegacyExtenderCaptionBridge(Sink sink){if(sink==null)throw new IllegalArgumentException("sink");this.sink=sink;}

    synchronized boolean onCeaSample(long timeMs,byte[] sample){
        if(sample==null||sample.length<3)return false;
        int hash=Arrays.hashCode(sample);
        for(Recent r:recent)if(r.timeMs==timeMs&&r.hash==hash&&Arrays.equals(r.payload,sample))return false;
        int pts=(int)Math.min(0xffffffffL,Math.max(0L,timeMs)*45L);
        ByteArrayOutputStream records=new ByteArrayOutputStream((sample.length/3)*8);
        for(int o=0;o+2<sample.length;o+=3){int h=sample[o]&255;if((h&4)==0)continue;int type=h&3;
            records.write((pts>>>24)&255);records.write((pts>>>16)&255);records.write((pts>>>8)&255);records.write(pts&255);
            records.write(type);records.write(sample[o+1]&255);records.write(sample[o+2]&255);records.write(1);
        }
        byte[] payload=records.toByteArray();if(payload.length==0)return false;
        recent.addLast(new Recent(timeMs,hash,Arrays.copyOf(sample,sample.length)));while(recent.size()>MAX_RECENT)recent.removeFirst();
        Pending p=new Pending(timeMs,pts,payload);if(pending.isEmpty()||pending.peekLast().timeMs<=timeMs)pending.addLast(p);else{
            ArrayDeque<Pending> q=new ArrayDeque<Pending>();boolean inserted=false;while(!pending.isEmpty()){Pending old=pending.removeFirst();if(!inserted&&p.timeMs<old.timeMs){q.addLast(p);inserted=true;}q.addLast(old);}if(!inserted)q.addLast(p);pending.addAll(q);
        }
        return true;
    }

    void drainTo(long playbackMs){List<Pending> due=new ArrayList<Pending>();synchronized(this){while(!pending.isEmpty()&&pending.peekFirst().timeMs<=playbackMs+80L){Pending p=pending.removeFirst();due.add(p);forwardedSamples++;forwardedBytes+=p.payload.length;}}for(Pending p:due)sink.postSubtitleInfo(p.pts45,0,p.payload,CC_SUBTITLE|PTS_VALID);}
    synchronized void clearPending(){pending.clear();recent.clear();}
    void postFlush(){sink.postSubtitleInfo(0,0,buildCea608ResetRecords(),CC_SUBTITLE|FLUSH_SUBTITLE_QUEUE);}
    synchronized int pendingSamples(){return pending.size();}
    synchronized int forwardedSamples(){return forwardedSamples;}
    synchronized long forwardedBytes(){return forwardedBytes;}

    static byte[] buildCea608ResetRecords(){ByteArrayOutputStream out=new ByteArrayOutputStream(128);for(int type=0;type<=1;type++)for(int channel=0;channel<=1;channel++){int first=0x14|(type==1?1:0)|(channel==1?8:0);writeControl(out,type,first,0x2c);writeControl(out,type,first,0x2e);}return out.toByteArray();}
    private static void writeControl(ByteArrayOutputStream out,int type,int first,int second){for(int d=0;d<2;d++){out.write(0);out.write(0);out.write(0);out.write(0);out.write(type);out.write(parity(first));out.write(parity(second));out.write(1);}}
    private static int parity(int v){int x=v&0x7f;return (Integer.bitCount(x)&1)==0?x|0x80:x;}
    private static final class Pending{final long timeMs;final int pts45;final byte[] payload;Pending(long t,int p,byte[] b){timeMs=t;pts45=p;payload=b;}}
    private static final class Recent{final long timeMs;final int hash;final byte[] payload;Recent(long t,int h,byte[] b){timeMs=t;hash=h;payload=b;}}
}
