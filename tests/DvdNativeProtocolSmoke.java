package org.opensagetv.webplayer;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.*;

/** Deterministic P07 tests for the stock MiniDVDPlayer wire/input state machine. */
public final class DvdNativeProtocolSmoke {
    private static void require(boolean condition, String message) {
        if (!condition) throw new RuntimeException(message);
    }
    private static byte[] i32(int value) {
        return new byte[] {(byte)(value>>>24),(byte)(value>>>16),(byte)(value>>>8),(byte)value};
    }
    private static byte[] metadata(byte[] payload) {
        byte[] body = new byte[4 + payload.length];
        putInt(body,0,payload.length);
        System.arraycopy(payload,0,body,4,payload.length);
        return body;
    }
    private static byte[] push(byte[] payload, int flags) {
        byte[] body = new byte[8 + payload.length];
        putInt(body,0,payload.length); putInt(body,4,flags);
        System.arraycopy(payload,0,body,8,payload.length);
        return body;
    }
    private static byte[] emptyPush(int flags) {
        byte[] body = new byte[8]; putInt(body,4,flags); return body;
    }
    private static void putInt(byte[] b,int o,int v) {
        b[o]=(byte)(v>>>24); b[o+1]=(byte)(v>>>16); b[o+2]=(byte)(v>>>8); b[o+3]=(byte)v;
    }

    private static void testClock() {
        DvdPtsClock c = new DvdPtsClock();
        c.onNewCell(45_000);
        require(c.ptsOffset90k()==90_000L,"NEWCELL 45k -> 90k offset");
        require(c.mapPesPts(0)==90_000L,"NEWCELL offset not applied to PES PTS");
        c.setServerStc45k(45_000L);
        require(c.currentClock90k()==90_000L && c.currentMillis()==1000L,"STC 45k conversion");
        long nearWrap=DvdPtsClock.MODULO-90_000L;
        long wrapped=45_000L;
        long unwrapped=DvdPtsClock.unwrapNear(wrapped,nearWrap);
        require(unwrapped>nearWrap,"33-bit wrap should unwrap forward near reference");
    }

    private static void testRoleGate() {
        String old = System.getProperty("sagetv.webplayer.nativeDvdProtocol");
        try {
            System.clearProperty("sagetv.webplayer.nativeDvdProtocol");
            require(!MiniClientCapabilityPolicy.inputDevices().contains("MOUSE"),"release default must advertise stock MiniDVDPlayer role");
            require(MiniClientCapabilityPolicy.inputDevices().contains("TOUCH"),"release default must retain browser touch input");
            require("MPEG2-PS".equals(MiniClientCapabilityPolicy.pushContainers()),"release default must advertise MPEG2-PS push");
            System.setProperty("sagetv.webplayer.nativeDvdProtocol","false");
            require(MiniClientCapabilityPolicy.inputDevices().contains("MOUSE"),"explicit false must restore ordinary browser mouse role");
            require("NONE".equals(MiniClientCapabilityPolicy.pushContainers()),"explicit false must disable native DVD push role");
            System.setProperty("sagetv.webplayer.nativeDvdProtocol","true");
            require(!MiniClientCapabilityPolicy.inputDevices().contains("MOUSE"),"explicit true must retain stock MiniDVDPlayer selection");
            require("MPEG2-PS".equals(MiniClientCapabilityPolicy.pushContainers()),"explicit true must advertise MPEG2-PS push");
        } finally {
            if (old==null) System.clearProperty("sagetv.webplayer.nativeDvdProtocol"); else System.setProperty("sagetv.webplayer.nativeDvdProtocol",old);
        }
    }

    private static void testBoundedPipe() throws Exception {
        DvdPushBuffer pipe = new DvdPushBuffer(64*1024);
        byte[] first = new byte[48*1024]; Arrays.fill(first,(byte)0x55);
        pipe.write(first,0,first.length,20);
        require(pipe.queuedBytes()==first.length,"first bounded write");
        long accepted=pipe.totalWritten();
        boolean timedOut=false;
        try { pipe.write(new byte[32*1024],0,32*1024,35); }
        catch (IOException expected) { timedOut=true; }
        require(timedOut,"bounded pipe must time out instead of growing without bound");
        require(pipe.totalWritten()==accepted && pipe.queuedBytes()==first.length,"failed write must not partially accept bytes");

        long oldGen=pipe.generation();
        byte[] drain=new byte[first.length];
        int got=0; while(got<drain.length){int n=pipe.read(drain,got,drain.length-got,oldGen);require(n>0,"pipe drain");got+=n;}
        ExecutorService ex=Executors.newSingleThreadExecutor();
        try {
            Future<Integer> waiter=ex.submit(() -> pipe.read(new byte[1],0,1,oldGen));
            Thread.sleep(20L);
            long newGen=pipe.flush();
            require(newGen!=oldGen,"FLUSH must create a distinct input generation");
            require(waiter.get(2,TimeUnit.SECONDS)==DvdPushBuffer.READ_CANCELLED,"FLUSH must cancel old-generation reader");
        } finally { ex.shutdownNow(); }
    }

    private static void testSessionLifecycle() throws Exception {
        DvdNativeSession s = new DvdNativeSession(128*1024);
        require(s.init(7)==1 && s.pending() && !s.active(),"INIT must create pending/lazy DVD session");
        require(s.mediaTimeMs()==0,"pre-data DVD media time");

        byte[] probePayload=new byte[16*1024];
        for(int i=0;i<probePayload.length;i++) probePayload[i]=(byte)i;
        int free=s.push(push(probePayload,0));
        require(free==128*1024 && !s.active() && s.totalPushedBytes()==0,"startup bandwidth probe must not activate or own bytes");
        require(s.push(emptyPush(0))==128*1024 && !s.active(),"empty capacity poll must stay lazy");
        require(s.push(emptyPush(0x100))==-2 && s.active(),"initial drain poll must return native ready sentinel");

        require(s.setNewCell(metadata(i32(45_000)))==0,"NEWCELL reply");
        require(s.clock().ptsOffset90k()==90_000L,"NEWCELL wire unit");
        require(s.setStc(i32(45_000))==0 && s.mediaTimeMs()==1000L,"STC reply/clock");
        byte[] clut=new byte[64]; require(s.setClut(metadata(clut))==0,"CLUT reply");
        byte[] spu=new byte[20]; require(s.setSpuControl(metadata(spu))==0,"SPUCTRL reply");
        byte[] stream=new byte[8];putInt(stream,0,0);putInt(stream,4,0xbd80);
        require(s.setStream(stream)==0 && s.streamType()==0 && s.streamPosition()==0xbd80,"DVD STREAM physical selector");
        require(s.setFormat(i32(2))==0 && s.dvdFormat()==2,"DVD FORMAT");
        require(s.setNewCell(new byte[]{0,0,0,64,1})==-1,"malformed metadata must fail closed");

        byte[] ps=new byte[32*1024];
        ps[0]=0;ps[1]=0;ps[2]=1;ps[3]=(byte)0xBA;
        long gen=s.generation();
        require(s.push(push(ps,0))==128*1024-ps.length,"real MPEG-PS must be accepted exactly once");
        require(s.totalPushedBytes()==ps.length && s.epochPushedBytes()==ps.length,"DVD byte accounting");
        DvdPushBuffer pipe=s.input();
        ExecutorService ex=Executors.newSingleThreadExecutor();
        Future<Integer> consumer=ex.submit(() -> {
            byte[] out=new byte[ps.length];int p=0;
            while(p<out.length){int n=pipe.read(out,p,out.length-p,gen);if(n<0)return n;p+=n;}
            int eos=pipe.read(new byte[1],0,1,gen);
            if(eos==DvdPushBuffer.READ_EOS) pipe.markDecoderDrained(gen);
            return eos;
        });
        int eosFree=s.push(emptyPush(0x100));
        require(eosFree==-2||(eosFree>=128*1024-ps.length && eosFree<=128*1024),"stock 0x100 drain request must close the current reusable segment");
        require(consumer.get(2,TimeUnit.SECONDS)==DvdPushBuffer.READ_EOS,"consumer must observe segment EOS");
        require(s.push(emptyPush(0x100))==-2,"repeated drain poll must report -2 only after input+decoder drain");
        require(pipe.totalRead()==pipe.totalWritten(),"accepted DVD bytes must be consumed exactly once");

        long beforeFlush=s.generation();
        require(s.flush()==1,"post-data FLUSH reply");
        require(s.generation()!=beforeFlush && s.epochPushedBytes()==0,"FLUSH must create a new byte epoch");
        require(s.push(emptyPush(0x100))==-2,"post-data FLUSH must be immediately drain-ready");
        byte[] ps2=Arrays.copyOf(ps,ps.length); // identical byte count must still be a new generation
        require(s.push(push(ps2,0))==128*1024-ps2.length,"equal-sized replacement cell must be accepted in new generation");
        require(s.totalPushedBytes()==ps.length+ps2.length,"lifetime byte count retained across FLUSH");
        ex.shutdownNow();s.close();
    }

    public static void main(String[] args) throws Exception {
        testClock();
        testRoleGate();
        testBoundedPipe();
        testSessionLifecycle();
        System.out.println("DVD native protocol smoke PASS (role gate, units, bounded input, probe detection, metadata, drain and FLUSH generations)");
    }
}
