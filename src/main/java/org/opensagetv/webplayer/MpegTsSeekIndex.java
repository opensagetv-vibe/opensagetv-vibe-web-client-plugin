package org.opensagetv.webplayer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded timestamp-to-byte resolver for MPEG transport streams.
 *
 * Unlike the historical duration*file-size fallback, every returned byte
 * offset is backed by an observed PES PTS.  The resolver samples the virtual
 * SageTV segment stream and then refines a PTS bracket.  If it cannot prove a
 * monotonic MPEG-TS timeline it fails closed to byte zero.
 */
final class MpegTsSeekIndex {
    interface Sources {
        int segmentCount();
        SeekableMediaSource open(int index) throws IOException;
    }

    static final class Result {
        final boolean validated;
        final long absoluteByte;
        final long anchorMs;
        final long requestedMs;
        final long residualMs;
        final long errorMs;
        final int samples;
        final int packetSize;
        final String reason;
        Result(boolean validated,long absoluteByte,long anchorMs,long requestedMs,
               int samples,int packetSize,String reason){
            this.validated=validated;this.absoluteByte=Math.max(0,absoluteByte);
            this.anchorMs=Math.max(0,anchorMs);this.requestedMs=Math.max(0,requestedMs);
            this.residualMs=Math.max(0,this.requestedMs-this.anchorMs);
            this.errorMs=Math.abs(this.requestedMs-this.anchorMs);
            this.samples=samples;this.packetSize=packetSize;this.reason=reason==null?"":reason;
        }
        static Result unavailable(long requested,String reason){return new Result(false,0,0,requested,0,0,reason);}
    }

    private static final int WINDOW=256*1024;
    private static final int COARSE=24;
    private static final int REFINE=14;
    private static final long PTS_MOD=1L<<33;
    private static final long PTS_HALF=PTS_MOD>>>1;

    private MpegTsSeekIndex(){}

    static Result resolve(Sources sources,long requestedMs) throws IOException {
        requestedMs=Math.max(0,requestedMs);
        if(requestedMs==0)return new Result(true,0,0,0,1,188,"recording start");
        int count=Math.max(0,sources.segmentCount());
        if(count<=0)return Result.unavailable(requestedMs,"no media segments");
        ArrayList<Long> lengths=new ArrayList<Long>(count);long total=0;
        for(int i=0;i<count;i++){
            long n=0;try(SeekableMediaSource s=sources.open(i)){if(s!=null)n=Math.max(0,s.length());}
            lengths.add(Long.valueOf(n));if(Long.MAX_VALUE-total<n)return Result.unavailable(requestedMs,"source too large");total+=n;
        }
        if(total<188*8)return Result.unavailable(requestedMs,"source too small for MPEG-TS timestamp seek");

        Sample first=sampleAt(sources,lengths,0,total,0,-1);
        if(first==null)return Result.unavailable(requestedMs,"no video PES PTS near recording start");
        long firstPts=first.pts90k;
        ArrayList<Sample> samples=new ArrayList<Sample>();samples.add(first.relative(firstPts));
        for(int i=1;i<=COARSE;i++){
            long pos=(total-1)*i/COARSE;
            Sample s=sampleAt(sources,lengths,pos,total,firstPts,first.packetSize);
            if(s!=null)addMonotonic(samples,s.relative(firstPts));
        }
        if(samples.size()<2)return Result.unavailable(requestedMs,"insufficient monotonic MPEG-TS PTS samples");
        long target90=requestedMs*90L;
        Sample low=samples.get(0), high=null;
        for(Sample s:samples){if(s.rel90k<=target90)low=s;else{high=s;break;}}
        if(high==null){
            Sample last=samples.get(samples.size()-1);
            // Tail seeks may legitimately target beyond the last PTS-bearing
            // video packet. Use the last validated anchor and let FFmpeg make
            // the bounded residual seek; never extrapolate a byte offset.
            return result(low,requestedMs,samples.size(),"validated tail anchor");
        }
        int observed=samples.size();
        long loByte=low.absoluteByte,hiByte=high.absoluteByte;
        for(int i=0;i<REFINE && hiByte-loByte>188*8;i++){
            long mid=loByte+(hiByte-loByte)/2;
            Sample s=sampleAt(sources,lengths,mid,total,firstPts,first.packetSize);
            if(s==null){hiByte=mid;continue;}
            s=s.relative(firstPts);observed++;
            if(s.rel90k<=low.rel90k||s.rel90k>=high.rel90k){
                // A discontinuity or another program broke the monotonic
                // bracket. Keep the already-proven anchors rather than guess.
                if(s.rel90k<=target90 && s.rel90k>low.rel90k)low=s;
                else if(s.rel90k>target90 && s.rel90k<high.rel90k)high=s;
                else break;
            } else if(s.rel90k<=target90){low=s;loByte=s.absoluteByte;}
            else {high=s;hiByte=s.absoluteByte;}
            if(Math.abs(target90-low.rel90k)<=45_000L)break; // <= 500 ms anchor error.
        }
        return result(low,requestedMs,observed,"validated MPEG-TS PTS anchor");
    }

    private static Result result(Sample low,long requestedMs,int samples,String reason){
        long anchorMs=Math.max(0,low.rel90k/90L);
        return new Result(true,low.absoluteByte,anchorMs,requestedMs,samples,low.packetSize,reason);
    }

    private static void addMonotonic(List<Sample> list,Sample s){
        if(s==null)return;Sample last=list.get(list.size()-1);
        if(s.absoluteByte>last.absoluteByte && s.rel90k>last.rel90k)list.add(s);
    }

    private static Sample sampleAt(Sources sources,List<Long> lengths,long virtual,long total,long firstPts,int preferredPacket) throws IOException {
        long base=0;int seg=0;long local=virtual;
        for(;seg<lengths.size();seg++){
            long len=lengths.get(seg).longValue();
            if(local<len||seg==lengths.size()-1)break;
            local-=len;base+=len;
        }
        if(seg>=lengths.size())return null;
        long len=lengths.get(seg).longValue();if(len<=0)return null;
        long start=Math.max(0,Math.min(local,len-1)-WINDOW/4);
        int amount=(int)Math.min(WINDOW,len-start);if(amount<188*4)return null;
        byte[] b=new byte[amount];int n=0;
        try(SeekableMediaSource source=sources.open(seg)){
            if(source==null)return null;source.seek(start);
            while(n<amount){int r=source.read(b,n,amount-n);if(r<=0)break;n+=r;}
        }
        if(n<188*4)return null;
        PacketLayout layout=detect(b,n,preferredPacket);if(layout==null)return null;
        Sample preceding=null;
        for(int p=layout.start;p+layout.packetSize<=n;p+=layout.packetSize){
            int sync=p+layout.syncOffset;if(sync+188>n||(b[sync]&255)!=0x47)continue;
            int b1=b[sync+1]&255;if((b1&0x40)==0||(b1&0x80)!=0)continue;
            int afc=(b[sync+3]>>>4)&3;if(afc==0||afc==2)continue;
            int q=sync+4;if(afc==3){if(q>=sync+188)continue;q+=1+(b[q]&255);}int end=sync+188;
            if(q+14>end||b[q]!=0||b[q+1]!=0||b[q+2]!=1)continue;
            int stream=b[q+3]&255;if(stream<0xe0||stream>0xef)continue;
            long pts=pesPts(b,q,end);if(pts<0)continue;
            Sample found=new Sample(base+start+p,pts,layout.packetSize,0,firstPts);
            // The read window deliberately begins before the requested byte so
            // packet sync and a preceding PES header remain available.  Do not
            // return that window's first timestamp: on VBR media it can be far
            // behind the requested byte and can repeatedly collapse binary
            // refinement onto the same old anchor. Prefer the first timestamp
            // at/after the requested byte, retaining the nearest preceding PTS
            // only for a true tail window with no later timestamp.
            if(found.absoluteByte>=virtual)return found;
            preceding=found;
        }
        return preceding;
    }

    private static long pesPts(byte[] b,int p,int end){
        if(p+14>end)return -1;int flags=b[p+7]&0xc0,header=b[p+8]&255;if((flags&0x80)==0||p+9+header>end)return -1;
        int q=p+9;if(q+5>end)return -1;
        return (((long)(b[q]&0x0e))<<29)|(((long)b[q+1]&255)<<22)|(((long)(b[q+2]&0xfe))<<14)|(((long)b[q+3]&255)<<7)|(((long)(b[q+4]&0xfe))>>1);
    }

    private static PacketLayout detect(byte[] b,int n,int preferred){
        int[] sizes=preferred>0?new int[]{preferred,188,192,204}:new int[]{188,192,204};
        for(int size:sizes){if(size!=188&&size!=192&&size!=204)continue;int sync=size==192?4:0;
            // Windows begin at arbitrary byte offsets, so the next packet may
            // be anywhere within a complete packet width. Searching only the
            // first 32 bytes made most valid VBR refinement windows look empty.
            for(int start=0;start<Math.min(size,n);start++){int ok=0;for(int k=0;k<5;k++){int p=start+sync+k*size;if(p<n&&(b[p]&255)==0x47)ok++;}if(ok>=4)return new PacketLayout(size,sync,start);}}
        return null;
    }

    private static long unwrap(long pts,long first){long d=pts-first;if(d>PTS_HALF)d-=PTS_MOD;else if(d<-PTS_HALF)d+=PTS_MOD;return first+d;}

    private static final class PacketLayout{final int packetSize,syncOffset,start;PacketLayout(int p,int s,int st){packetSize=p;syncOffset=s;start=st;}}
    private static final class Sample{
        final long absoluteByte,pts90k,rel90k;final int packetSize;
        Sample(long b,long p,int size,long rel,long first){absoluteByte=b;pts90k=p;packetSize=size;rel90k=rel;}
        Sample relative(long first){long u=unwrap(pts90k,first);return new Sample(absoluteByte,pts90k,packetSize,Math.max(0,u-first),first);}
    }
}
