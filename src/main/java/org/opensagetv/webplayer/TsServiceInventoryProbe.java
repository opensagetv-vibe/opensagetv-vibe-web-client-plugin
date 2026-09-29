package org.opensagetv.webplayer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded MPEG-TS PAT/PMT descriptor inventory only; no subtitle decoding. */
final class TsServiceInventoryProbe {
    static final class Service {
        int program=-1,pid=-1,streamType=-1,teletextType=-1,magazine=-1,page=-1,compositionPageId=-1,ancillaryPageId=-1;
        String kind="",language="",evidence="observed-pmt"; boolean hearingImpaired;
        String key(){return kind+":"+program+":"+pid+":"+language+":"+page+":"+compositionPageId+":"+ancillaryPageId;}
        String json(){return "{\"kind\":\""+HttpUtil.json(kind)+"\",\"program\":"+program+",\"pid\":"+pid+",\"streamType\":"+streamType+
                ",\"language\":\""+HttpUtil.json(language)+"\",\"teletextType\":"+teletextType+",\"magazine\":"+magazine+",\"page\":"+page+
                ",\"compositionPageId\":"+compositionPageId+",\"ancillaryPageId\":"+ancillaryPageId+",\"hearingImpaired\":"+hearingImpaired+
                ",\"evidence\":\""+evidence+"\"}";}
    }
    private static final class SectionAssembler {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();int expected=-1;
        List<byte[]> push(byte[] data,int off,int len,boolean start){
            List<byte[]> out=new ArrayList<byte[]>();
            if(start){if(len<=0)return out;int pointer=data[off]&255;off++;len--;if(pointer>len)return out;off+=pointer;len-=pointer;bytes.reset();expected=-1;}
            while(len>0){
                if(expected<0&&bytes.size()<3){int need=Math.min(len,3-bytes.size());bytes.write(data,off,need);off+=need;len-=need;if(bytes.size()==3){byte[] h=bytes.toByteArray();expected=3+(((h[1]&15)<<8)|(h[2]&255));if(expected<3||expected>4096){bytes.reset();expected=-1;return out;}}}
                if(expected>0){int need=Math.min(len,expected-bytes.size());bytes.write(data,off,need);off+=need;len-=need;if(bytes.size()==expected){out.add(bytes.toByteArray());bytes.reset();expected=-1;}}
                else if(bytes.size()<3)continue; else break;
                if(!start)break; // continuation packet cannot contain a new pointer-framed section safely.
            }
            return out;
        }
    }
    private TsServiceInventoryProbe(){}
    static List<Service> scan(File file,long maxBytes)throws IOException{
        if(file==null||!file.isFile())return new ArrayList<Service>();
        long limit=Math.min(Math.max(188*5,maxBytes),SubtitleLimits.MAX_TS_DISCOVERY_BYTES);byte[] data;
        try(FileInputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[32768];long left=limit;while(left>0){int n=in.read(b,0,(int)Math.min(b.length,left));if(n<0)break;if(n==0)continue;out.write(b,0,n);left-=n;}data=out.toByteArray();
        }
        return scan(data);
    }
    static List<Service> scan(byte[] data){
        PacketLayout layout=detect(data);if(layout==null)return new ArrayList<Service>();
        Map<Integer,SectionAssembler> asm=new HashMap<Integer,SectionAssembler>();asm.put(0,new SectionAssembler());
        Set<Integer> pmtPids=new LinkedHashSet<Integer>();Map<Integer,Integer> programForPid=new HashMap<Integer,Integer>();List<Service> services=new ArrayList<Service>();Set<String> keys=new LinkedHashSet<String>();
        for(int packet=layout.start;packet+layout.size<=data.length;packet+=layout.size){int p=packet+layout.syncOffset;if((data[p]&255)!=0x47)continue;
            boolean start=(data[p+1]&0x40)!=0;int pid=((data[p+1]&31)<<8)|(data[p+2]&255);int afc=(data[p+3]>>4)&3;if(afc==0||afc==2)continue;int pos=p+4;if(afc==3){int al=data[pos]&255;pos+=1+al;}int end=Math.min(packet+layout.size,p+188);if(pos>=end)continue;
            SectionAssembler sa=asm.get(pid);if(sa==null&&!pmtPids.contains(pid))continue;if(sa==null){sa=new SectionAssembler();asm.put(pid,sa);}
            for(byte[] sec:sa.push(data,pos,end-pos,start)){
                int table=sec[0]&255;if(pid==0&&table==0){parsePat(sec,pmtPids,programForPid,asm);}
                else if(table==2&&pmtPids.contains(pid))parsePmt(sec,programForPid.get(pid)==null?-1:programForPid.get(pid),services,keys);
                if(services.size()>=SubtitleLimits.MAX_DISCOVERED_SERVICES)return services;
            }
        }
        return services;
    }
    private static void parsePat(byte[] s,Set<Integer> pids,Map<Integer,Integer> programs,Map<Integer,SectionAssembler> asm){
        if(s.length<12)return;int end=s.length-4;for(int i=8;i+3<end;i+=4){int program=((s[i]&255)<<8)|(s[i+1]&255);int pid=((s[i+2]&31)<<8)|(s[i+3]&255);if(program!=0){pids.add(pid);programs.put(pid,program);if(!asm.containsKey(pid))asm.put(pid,new SectionAssembler());}}
    }
    private static void parsePmt(byte[] s,int program,List<Service> out,Set<String> keys){
        if(s.length<16)return;int programInfo=(((s[10]&15)<<8)|(s[11]&255));int pos=12+programInfo,end=s.length-4;
        while(pos+4<end){int streamType=s[pos]&255,pid=((s[pos+1]&31)<<8)|(s[pos+2]&255),esInfo=((s[pos+3]&15)<<8)|(s[pos+4]&255);int d=pos+5,dend=Math.min(end,d+esInfo);
            while(d+1<dend){int tag=s[d]&255,len=s[d+1]&255;int v=d+2,ve=Math.min(dend,v+len);if(ve-v<len)break;
                if(tag==0x56){for(int q=v;q+4<ve;q+=5){int t=(s[q+3]>>3)&31;if(t==2||t==5){Service x=new Service();x.program=program;x.pid=pid;x.streamType=streamType;x.kind="teletext";x.language=ascii3(s,q);x.teletextType=t;x.hearingImpaired=t==5;int mag=s[q+3]&7;x.magazine=mag==0?8:mag;int b=s[q+4]&255;x.page=x.magazine*100+((b>>4)&15)*10+(b&15);add(out,keys,x);}}}
                else if(tag==0x59){for(int q=v;q+7<ve;q+=8){Service x=new Service();x.program=program;x.pid=pid;x.streamType=streamType;x.kind="dvb-subtitle";x.language=ascii3(s,q);int type=s[q+3]&255;x.hearingImpaired=(type>=0x20&&type<=0x24);x.compositionPageId=((s[q+4]&255)<<8)|(s[q+5]&255);x.ancillaryPageId=((s[q+6]&255)<<8)|(s[q+7]&255);add(out,keys,x);}}
                d=ve;
            }
            pos=dend;
        }
    }
    private static void add(List<Service> out,Set<String> keys,Service x){if(out.size()<SubtitleLimits.MAX_DISCOVERED_SERVICES&&keys.add(x.key()))out.add(x);}
    private static String ascii3(byte[] b,int o){char[] c=new char[3];for(int i=0;i<3;i++){int v=b[o+i]&255;c[i]=(char)(v>=32&&v<=126?v:'?');}return new String(c).toLowerCase();}
    private static final class PacketLayout {final int size,syncOffset,start;PacketLayout(int s,int o,int st){size=s;syncOffset=o;start=st;}}
    private static PacketLayout detect(byte[] b){int[] sizes={188,192,204};for(int size:sizes)for(int start=0;start<Math.min(size,16);start++)for(int sync=0;sync<Math.min(size,8);sync++){int ok=0;for(int n=0;n<5;n++){int p=start+sync+n*size;if(p<b.length&&(b[p]&255)==0x47)ok++;}if(ok>=4)return new PacketLayout(size,sync,start);}return null;}
}
