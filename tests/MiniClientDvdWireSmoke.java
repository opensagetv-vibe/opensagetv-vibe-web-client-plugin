package org.opensagetv.webplayer;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

/** Socket-level P07 proof against the exact MiniDVDPlayer media command framing. */
public final class MiniClientDvdWireSmoke {
    static void require(boolean x,String m){if(!x)throw new RuntimeException(m);}
    static byte[] readN(InputStream in,int n)throws Exception{byte[] b=new byte[n];int p=0;while(p<n){int r=in.read(b,p,n-p);if(r<0)throw new EOFException();p+=r;}return b;}
    static int i32(byte[] b,int o){return ((b[o]&255)<<24)|((b[o+1]&255)<<16)|((b[o+2]&255)<<8)|(b[o+3]&255);}
    static void putInt(byte[] b,int o,int v){b[o]=(byte)(v>>>24);b[o+1]=(byte)(v>>>16);b[o+2]=(byte)(v>>>8);b[o+3]=(byte)v;}
    static Socket acceptHandshake(ServerSocket ss,int expectedType)throws Exception{
        Socket s=ss.accept();s.setSoTimeout(8000);byte[] h=readN(s.getInputStream(),8);
        require((h[0]&255)==1,"bad protocol byte");require((h[7]&255)==expectedType,"connection type mismatch");
        s.getOutputStream().write(2);s.getOutputStream().flush();return s;
    }
    static void send(OutputStream out,int cmd,byte[] body)throws Exception{
        int n=body==null?0:body.length;out.write(cmd);out.write((n>>>16)&255);out.write((n>>>8)&255);out.write(n&255);if(n>0)out.write(body);out.flush();
    }
    static int command(InputStream in,OutputStream out,int cmd,byte[] body)throws Exception{send(out,cmd,body);return i32(readN(in,4),0);}
    static byte[] intBody(int v){byte[] b=new byte[4];putInt(b,0,v);return b;}
    static byte[] metadata(int size){byte[] b=new byte[4+size];putInt(b,0,size);return b;}
    static byte[] metadataInt(int v){byte[] b=new byte[8];putInt(b,0,4);putInt(b,4,v);return b;}
    static byte[] push(byte[] payload,int flags){byte[] b=new byte[8+payload.length];putInt(b,0,payload.length);putInt(b,4,flags);System.arraycopy(payload,0,b,8,payload.length);return b;}
    static byte[] poll(int flags){byte[] b=new byte[8];putInt(b,4,flags);return b;}

    public static void main(String[] args)throws Exception{
        String transcoder=args.length>0?args[0]:"/usr/bin/ffmpeg";
        require(new File(transcoder).isFile(),"test transcoder missing");
        // Production discovers the required Vibe FFmpeg Plugin launcher. This
        // isolated wire test deliberately uses the documented explicit test
        // override because no SageTV plugin tree is mounted in the build image.
        System.setProperty("sagetv.webplayer.transcoder",transcoder);
        ServerSocket ss=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));
        ExecutorService ex=Executors.newSingleThreadExecutor();
        CountDownLatch ready=new CountDownLatch(1);
        Future<?> server=ex.submit(() -> {try{
            Socket media=acceptHandshake(ss,1); InputStream mi=media.getInputStream(); OutputStream mo=media.getOutputStream();
            Socket gfx=acceptHandshake(ss,0); ready.countDown();

            require(command(mi,mo,0,intBody(0))==1,"INIT reply");
            require(command(mi,mo,17,new byte[0])==0,"pre-data GETMEDIATIME reply");
            require(command(mi,mo,23,poll(0))==4*1024*1024,"initial capacity reply");

            byte[] probe=new byte[16*1024];for(int i=0;i<probe.length;i++)probe[i]=(byte)i;
            require(command(mi,mo,23,push(probe,0))==4*1024*1024,"bandwidth probe must not consume DVD capacity");
            require(command(mi,mo,23,poll(0x100))==-2,"initial native drain sentinel");

            byte[] malformed=new byte[5];putInt(malformed,0,64);malformed[4]=1;
            require(command(mi,mo,32,malformed)==-1,"malformed NEWCELL must be a four-byte error reply");
            require(command(mi,mo,32,metadataInt(45_000))==0,"NEWCELL reply");
            require(command(mi,mo,33,metadata(64))==0,"CLUT reply");
            require(command(mi,mo,34,metadata(20))==0,"SPUCTRL reply");
            require(command(mi,mo,35,intBody(45_000))==0,"STC reply");
            require(command(mi,mo,17,new byte[0])==1000,"STC must map to one second media time");
            byte[] stream=new byte[8];putInt(stream,0,0);putInt(stream,4,0xBD80);
            require(command(mi,mo,36,stream)==0,"DVD STREAM reply");
            require(command(mi,mo,37,intBody(2))==0,"DVD FORMAT reply");

            // SEEK intentionally has no reply on the MiniPlayer protocol. The
            // following GETMEDIATIME must therefore start at the next four bytes.
            byte[] seek=new byte[8];long ms=1000L;for(int i=7;i>=0;i--){seek[i]=(byte)ms;ms>>>=8;}
            send(mo,29,seek);send(mo,17,new byte[0]);
            require(i32(readN(mi,4),0)==1000,"SEEK must not append a reply byte/word");

            byte[] ps=new byte[32*1024];ps[0]=0;ps[1]=0;ps[2]=1;ps[3]=(byte)0xBA;
            int afterPush=command(mi,mo,23,push(ps,0));
            // The attached decoder may consume some or all bytes before the
            // server reads the free-space reply. The wire contract reports the
            // current reusable capacity, not an atomic post-write queue size.
            require(afterPush>=4*1024*1024-ps.length && afterPush<=4*1024*1024,
                    "real MPEG-PS free-space reply: "+afterPush);
            require(command(mi,mo,22,new byte[0])==1,"post-data FLUSH reply");
            require(command(mi,mo,23,poll(0x100))==-2,"post-FLUSH drain sentinel");
            require(command(mi,mo,1,new byte[0])==1,"DEINIT reply");
            media.close();gfx.close();
        }catch(Exception e){throw new RuntimeException(e);}});

        MiniClientSession session=new MiniClientSession("dvdwire","127.0.0.1:"+ss.getLocalPort(),"02:00:00:00:00:37",1280,720);
        session.start(); require(ready.await(5,TimeUnit.SECONDS),"connections not established");
        server.get(15,TimeUnit.SECONDS);session.close();ss.close();ex.shutdownNow();
        System.out.println("MiniClient DVD wire smoke PASS (commands 0/1/17/22/23/29/32-37, exact reply alignment, probes and FLUSH)");
    }
}
