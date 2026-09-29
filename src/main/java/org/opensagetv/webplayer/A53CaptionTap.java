package org.opensagetv.webplayer;

import java.io.*;
import java.util.Arrays;

/**
 * Extracts only A/53 cc_data triples from an original-video MPEG-TS copy.
 * MPEG-2 user_data and AVC/HEVC registered SEI are supported. There is no video
 * decoder here. MPEG-TS/PES/NAL buffers and HTTP read batches are bounded.
 * The compact sidecar lets the producer run ahead without growing Java heap.
 */
final class A53CaptionTap implements Closeable {
    interface Sink { void packet(long ptsMs,byte[] triples) throws IOException; }
    final File file;
    volatile long records,bytes,cea608,cea708;
    volatile String error="";
    volatile boolean finished;
    volatile Thread thread;
    private volatile InputStream source;
    private final DataOutputStream out;
    private final Parser parser;
    A53CaptionTap(File file) throws IOException {
        this.file=file;out=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(file),16384));
        parser=new Parser((pts,data)->{
            out.writeLong(pts);out.writeByte(data.length);out.write(data);out.flush();
            records++;bytes+=data.length;
            for(int i=0;i+2<data.length;i+=3){if((data[i]&4)!=0){if((data[i]&3)<=1)cea608++;else cea708++;}}
        });
    }
    void start(final InputStream input) {
        source=input;
        Thread t=new Thread(()->{
            try(InputStream in=input){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)parser.push(b,0,n);parser.finish();}
            catch(IOException e){error=e.getMessage()==null?"Caption tap failed":e.getMessage();}
            finally{try{out.close();}catch(IOException ignored){}finished=true;}
        },"webplayer-caption-tap");t.setDaemon(true);thread=t;t.start();
    }
    public void close() throws IOException {
        Thread t=thread;
        if(t==null){parser.finish();out.close();finished=true;return;}
        InputStream in=source;if(in!=null)try{in.close();}catch(IOException ignored){}
        if(t!=Thread.currentThread())try{t.join(2000L);}catch(InterruptedException e){Thread.currentThread().interrupt();}
    }
    String json() {return "{\"records\":"+records+",\"ccBytes\":"+bytes+",\"cea608Pairs\":"+cea608+",\"cea708Triples\":"+cea708+",\"finished\":"+finished+
        ",\"error\":\""+HttpUtil.json(error)+"\",\"source\":\"original video before transcoding\"}";}
    static long readPackets(File file,long cursor,long untilMs,Sink sink) throws IOException {
        if(cursor<0||untilMs<0)throw new IllegalArgumentException("Invalid caption cursor/time");
        if(!file.isFile())return 0L;
        try(RandomAccessFile r=new RandomAccessFile(file,"r")){
            long end=r.length();if(cursor>end)cursor=0;r.seek(cursor);int count=0;
            while(count<2048&&r.getFilePointer()+9<=end){long before=r.getFilePointer(),pts=r.readLong();int n=r.readUnsignedByte();
                if(n>93||n%3!=0||pts<0||pts>7L*86400000L){r.seek(before);break;}
                if(r.getFilePointer()+n>end||pts>untilMs+1000L){r.seek(before);break;}
                byte[] data=new byte[n];r.readFully(data);if(sink!=null)sink.packet(pts,data);count++;
            }
            return r.getFilePointer();
        }
    }

    static String read(File file,long cursor,long untilMs) throws IOException {
        if(cursor<0||untilMs<0)throw new IllegalArgumentException("Invalid caption cursor/time");
        if(!file.isFile())return "{\"cursor\":0,\"packets\":[]}";
        StringBuilder b=new StringBuilder("{\"packets\":[");int count=0;
        try(RandomAccessFile r=new RandomAccessFile(file,"r")) {
            long end=r.length();if(cursor>end)throw new IllegalArgumentException("Caption cursor is beyond this session");r.seek(cursor);
            while(count<1024&&r.getFilePointer()+9<=end) {
                long before=r.getFilePointer(),pts=r.readLong();int n=r.readUnsignedByte();
                if(n>93||n%3!=0||pts<0||pts>7L*86400000L)throw new IllegalArgumentException("Invalid caption cursor");
                if(r.getFilePointer()+n>end||pts>untilMs+1000L){r.seek(before);break;}
                byte[] data=new byte[n];r.readFully(data);if(count++>0)b.append(',');
                b.append('[').append(pts).append(",\"");for(byte v:data){int x=v&255;b.append("0123456789abcdef".charAt(x>>4)).append("0123456789abcdef".charAt(x&15));}b.append("\"]");
            }
            cursor=r.getFilePointer();
        }
        return b.append("],\"cursor\":").append(cursor).append('}').toString();
    }
    static final class Parser {
        private final Sink sink;
        private byte[] carry=new byte[0];
        private final ByteArrayOutputStream pes=new ByteArrayOutputStream();
        private int videoPid=-1,lastCc=-1;
        private long basePts=-1,lastRaw=-1,wrap;
        private static final int MAX_PES=4*1024*1024;
        Parser(Sink sink){this.sink=sink;}
        void push(byte[] bytes,int offset,int count) throws IOException {
            byte[] b=new byte[carry.length+count];System.arraycopy(carry,0,b,0,carry.length);System.arraycopy(bytes,offset,b,carry.length,count);
            int i=0;
            while(i+188<=b.length) {
                if((b[i]&255)!=0x47 || (i+376<=b.length&&(b[i+188]&255)!=0x47)){i++;continue;}
                packet(b,i);i+=188;
            }
            carry=Arrays.copyOfRange(b,i,b.length);
        }
        void packet(byte[] b,int o) throws IOException {
            if((b[o+1]&128)!=0)return;
            int pid=((b[o+1]&31)<<8)|(b[o+2]&255),af=(b[o+3]>>4)&3,cc=b[o+3]&15;
            if(af==0||af==2)return;int start=o+4;
            if(af==3){int len=b[start]&255;if(len>0&&(b[start+1]&128)!=0&&pid==videoPid){flushPes();lastCc=-1;}start+=len+1;}
            if(start>=o+188)return;
            boolean pusi=(b[o+1]&64)!=0;
            if(videoPid<0&&pusi&&start+4<=o+188&&b[start]==0&&b[start+1]==0&&b[start+2]==1&&(b[start+3]&240)==224)videoPid=pid;
            if(pid!=videoPid)return;
            if(lastCc==cc)return;
            if(lastCc>=0&&cc!=((lastCc+1)&15))pes.reset();lastCc=cc;
            if(pusi)flushPes();
            if(pes.size()+o+188-start>MAX_PES){pes.reset();return;}
            // Ignore continuation bytes until a complete PES header has begun.
            if(pes.size()==0&&!pusi)return;
            pes.write(b,start,o+188-start);
        }
        void finish() throws IOException {flushPes();}
        void flushPes() throws IOException {
            byte[] p=pes.toByteArray();pes.reset();
            if(p.length<14||p[0]!=0||p[1]!=0||p[2]!=1||(p[7]&128)==0)return;
            int es=9+(p[8]&255);if(es>p.length)return;
            long raw=pts(p,9);
            if(lastRaw>=0&&raw<lastRaw-0x100000000L)wrap+=0x200000000L;
            lastRaw=raw;long absolute=raw+wrap;if(basePts<0)basePts=absolute;
            long ms=Math.max(0,(absolute-basePts)/90L);
            for(int i=es;i+4<p.length;i++) {
                if(p[i]!=0||p[i+1]!=0||p[i+2]!=1)continue;
                int code=p[i+3]&255;
                if(code==0xb2){extractGa94(p,i+4,p.length,ms);continue;}
                int avc=code&31,hevc=(code>>1)&63;
                if(avc!=6&&hevc!=39&&hevc!=40)continue;
                int head=i+4+(avc==6?0:1),end=p.length;
                for(int j=head;j+3<p.length;j++)if(p[j]==0&&p[j+1]==0&&p[j+2]==1){end=j;break;}
                byte[] rbsp=unescape(p,head,end);int q=0;
                while(q+2<=rbsp.length){
                    int type=0,size=0;while(q<rbsp.length&&(rbsp[q]&255)==255){type+=255;q++;}if(q>=rbsp.length)break;type+=rbsp[q++]&255;
                    while(q<rbsp.length&&(rbsp[q]&255)==255){size+=255;q++;}if(q>=rbsp.length)break;size+=rbsp[q++]&255;
                    if(size<0||size>rbsp.length-q)break;
                    if(type==4&&size>=10&&(rbsp[q]&255)==181&&rbsp[q+1]==0&&rbsp[q+2]==49)extractGa94(rbsp,q+3,q+size,ms);
                    q+=size;
                }
                i=end-1;
            }
        }
        private void extractGa94(byte[] p,int i,int end,long ms) throws IOException {
            if(i+7>end||p[i]!=0x47||p[i+1]!=0x41||p[i+2]!=0x39||p[i+3]!=0x34||p[i+4]!=3)return;
            int flags=p[i+5]&255,count=flags&31;
            if((flags&64)==0||count==0||i+7+count*3>end)return;
            sink.packet(ms,Arrays.copyOfRange(p,i+7,i+7+count*3));
        }
        static long pts(byte[] b,int o){return ((long)(b[o]&14)<<29)|((long)(b[o+1]&255)<<22)|((long)(b[o+2]&254)<<14)|((long)(b[o+3]&255)<<7)|((b[o+4]&254)>>1);}
        static byte[] unescape(byte[] p,int start,int end){ByteArrayOutputStream b=new ByteArrayOutputStream();int zero=0;for(int i=start;i<end;i++){int x=p[i]&255;if(zero>=2&&x==3){zero=0;continue;}b.write(x);zero=x==0?zero+1:0;}return b.toByteArray();}
    }
}
