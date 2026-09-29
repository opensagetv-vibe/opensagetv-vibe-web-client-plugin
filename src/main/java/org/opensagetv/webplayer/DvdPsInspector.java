package org.opensagetv.webplayer;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bounded incremental MPEG-PS/PES observer used for DVD diagnostics and clock evidence.
 * It never owns or rewrites media bytes. Only A/V PES timestamps can advance the mapped
 * presentation clock; private SPU packets are deliberately excluded.
 */
final class DvdPsInspector {
    private final byte[] tail=new byte[32];
    private int tailLen;
    private long packHeaders,videoPes,audioPes,privatePes,spuPes,pictureStarts,sequenceEnds;
    private long mappedAvPts90k=-1,rawAvPts90k=-1;
    private final Set<Integer> audioIds=new LinkedHashSet<Integer>();

    synchronized void resetEpoch(){
        tailLen=0;packHeaders=videoPes=audioPes=privatePes=spuPes=pictureStarts=sequenceEnds=0;
        mappedAvPts90k=rawAvPts90k=-1;audioIds.clear();
    }

    synchronized void observe(byte[] source,int offset,int length,DvdPtsClock clock){
        if(source==null||length<=0||offset<0||offset+length>source.length)return;
        int previousTailLen=tailLen;
        int total=previousTailLen+length;
        byte[] data=new byte[total];
        System.arraycopy(tail,0,data,0,previousTailLen);System.arraycopy(source,offset,data,previousTailLen,length);
        // Rescan only the last three bytes of the previous tail so start codes
        // split across PUSHBUFFER boundaries are seen once without counting
        // already-accounted PES/picture headers a second time.
        int scanStart=Math.max(0,previousTailLen-3);
        for(int i=scanStart;i+3<total;i++){
            if(data[i]!=0||data[i+1]!=0||data[i+2]!=1)continue;
            int id=data[i+3]&0xff;
            if(id==0xba){packHeaders++;continue;}
            if(id==0x00){pictureStarts++;continue;}
            if(id==0xb7){sequenceEnds++;continue;}
            if((id>=0xe0&&id<=0xef)||(id>=0xc0&&id<=0xdf)||id==0xbd){
                boolean video=id>=0xe0&&id<=0xef;
                boolean mpegAudio=id>=0xc0&&id<=0xdf;
                if(video)videoPes++; else if(mpegAudio){audioPes++;audioIds.add(0x100|id);} else privatePes++;
                int payloadStart=pesPayloadStart(data,i,total);
                int sub=-1;
                if(id==0xbd&&payloadStart>=0&&payloadStart<total){
                    sub=data[payloadStart]&0xff;
                    if(sub>=0x20&&sub<=0x3f)spuPes++;
                    else if((sub>=0x80&&sub<=0xaf)){audioPes++;audioIds.add(sub);}
                }
                // SPU-only private packets must not consume the pending A/V clock rebase.
                boolean av=video||mpegAudio||(id==0xbd&&sub>=0x80&&sub<=0xaf);
                if(av){
                    long pts=parsePts90k(data,i,total);
                    if(pts>=0){rawAvPts90k=pts;mappedAvPts90k=clock.mapPesPts(pts);}
                }
            }
        }
        tailLen=Math.min(tail.length,total);
        System.arraycopy(data,total-tailLen,tail,0,tailLen);
    }

    private static int pesPayloadStart(byte[] d,int p,int limit){
        if(p+9>limit)return -1;
        int stream=d[p+3]&0xff;
        if(stream==0xbc||stream==0xbe||stream==0xbf||stream==0xf0||stream==0xf1||stream==0xff||stream==0xf2||stream==0xf8)return p+6;
        if((d[p+6]&0xc0)==0x80){int h=d[p+8]&0xff;return p+9+h;}
        // MPEG-1 PES: skip stuffing and optional STD buffer/PTS fields conservatively.
        int q=p+6;while(q<limit&&(d[q]&0xff)==0xff)q++;
        if(q+1<limit&&((d[q]&0xc0)==0x40))q+=2;
        int tag=q<limit?(d[q]&0xf0):0;
        if(tag==0x20)q+=5;else if(tag==0x30)q+=10;else if(q<limit&&d[q]==0x0f)q++;
        return q<limit?q:-1;
    }

    private static long parsePts90k(byte[] d,int p,int limit){
        if(p+14>limit)return -1;
        if((d[p+6]&0xc0)==0x80){
            int flags=d[p+7]&0xc0;if(flags!=0x80&&flags!=0xc0)return -1;
            int q=p+9;if(q+5>limit)return -1;return decodePts(d,q);
        }
        int q=p+6;while(q<limit&&(d[q]&0xff)==0xff)q++;
        if(q+1<limit&&((d[q]&0xc0)==0x40))q+=2;
        if(q+5<=limit&&((d[q]&0xf0)==0x20||(d[q]&0xf0)==0x30))return decodePts(d,q);
        return -1;
    }

    private static long decodePts(byte[] d,int p){
        return (((long)(d[p]&0x0e))<<29)|(((long)(d[p+1]&0xff))<<22)|(((long)(d[p+2]&0xfe))<<14)|(((long)(d[p+3]&0xff))<<7)|((d[p+4]&0xfe)>>>1);
    }

    synchronized boolean hasVideo(){return videoPes>0||pictureStarts>0;}
    synchronized boolean stillCandidate(){return hasVideo()&&pictureStarts<=1;}
    synchronized long pictureStarts(){return pictureStarts;}
    synchronized long mappedAvPts90k(){return mappedAvPts90k;}
    synchronized String json(){
        StringBuilder ids=new StringBuilder("[");boolean first=true;for(Integer id:audioIds){if(!first)ids.append(',');first=false;ids.append(id);}ids.append(']');
        return "{\"packs\":"+packHeaders+",\"videoPes\":"+videoPes+",\"audioPes\":"+audioPes+",\"privatePes\":"+privatePes+",\"spuPes\":"+spuPes+",\"pictures\":"+pictureStarts+",\"sequenceEnds\":"+sequenceEnds+",\"rawAvPts90k\":"+rawAvPts90k+",\"mappedAvPts90k\":"+mappedAvPts90k+",\"audioIds\":"+ids+"}";
    }
}
