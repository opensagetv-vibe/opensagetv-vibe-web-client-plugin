package org.opensagetv.webplayer;

import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.opensagetv.webplayer.MiniClientRecoverySmoke.*;

/**
 * Real TCP wire regression for the uploaded server log's startup failure.
 * This is a synthetic peer, NOT a running SageTV/SageMC instance. It models
 * the critical boundary explicitly: no UI receiver/root exists until the
 * server has sent its first completed frame. Source-derived reply and event
 * families are used, including the formerly premature resize/repaint pair.
 */
public final class MiniClientStartupSmoke {
    private static int count;
    private static void quiet(Socket s)throws Exception {
        int old=s.getSoTimeout();s.setSoTimeout(130);
        try {int b=s.getInputStream().read();throw new AssertionError("unsolicited byte before permitted boundary: "+b);}
        catch(SocketTimeoutException expected){} finally{s.setSoTimeout(old);}
    }
    private static Socket startPeer(Env e)throws Exception {
        e.accept(1,2);Socket g=e.accept(0,2);
        draw(g,1);check(integer(reply(g,16))==1,"INIT reply missing");return g;
    }
    private static void ready(Socket g,int w,int h)throws Exception {
        draw(g,31);draw(g,30);
        check(integer(reply(g,16))==0,"FLIP ACK must precede events");
        byte[] size=reply(g,192),paint=reply(g,193);
        check(integer(size)==w&&MiniClientSession.readInt(size,4)==h,"latest size not released");
        check(integer(paint)==0&&MiniClientSession.readInt(paint,4)==0&&MiniClientSession.readInt(paint,8)==w&&MiniClientSession.readInt(paint,12)==h,"not full latest-size repaint");
    }
    private static void hold(Env e)throws Exception {
        e.signal.countDown();check(e.release.await(5,TimeUnit.SECONDS),"client inspection timeout");
    }
    private static void client(Env e)throws Exception {
        e.session.start();check(e.signal.await(8,TimeUnit.SECONDS),"peer did not reach assertion boundary");
        check(e.session.isAlive(),e.session.json());e.release.countDown();
    }
    private static void test(String name,Work server,Work client)throws Exception {run(name,server,client);count++;}
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("--expect-early-ui")) {
            test("untouched 3.2.2 sends resize 192 then unsafe repaint 193 before any frame",e->{
                Socket g=startPeer(e);reply(g,192);byte[] paint=reply(g,193);
                check(paint.length==16,"missing premature repaint");
                check(e.session.json().contains("\"firstFrameStarted\":false"),"not a pre-first-frame reproduction");hold(e);
            },MiniClientStartupSmoke::client);
            System.out.println("BASELINE: premature UI-event ordering reproduced with real TCP; no live SageTV used.");return;
        }
        test("INIT replies without resize/repaint while server UI is unconstructed",e->{Socket g=startPeer(e);quiet(g);
            check(e.session.json().contains("\"firstFrameCompleted\":false"),e.session.json());hold(e);
        },MiniClientStartupSmoke::client);
        test("all property and filesystem replies still arrive during startup",e->{Socket g=startPeer(e);
            for(String prop:new String[]{"GFX_RESOLUTION","GFX_SURFACES","CRYPTO_ALGORITHMS","UNKNOWN_EMPTY"}){
                frame(g,0,prop.getBytes(StandardCharsets.ISO_8859_1));byte[] b=reply(g,0);
                if(prop.equals("CRYPTO_ALGORITHMS"))check(new String(b,StandardCharsets.ISO_8859_1).equals("RSA,Blowfish"),"crypto response gated");
            }
            frame(g,2,new byte[0]);check(integer(reply(g,2))==2,"FS reply gated");quiet(g);hold(e);
        },MiniClientStartupSmoke::client);
        test("first frame allocation replies remain live; STARTFRAME alone does not open gate",e->{Socket g=startPeer(e);
            draw(g,26,32,32);check(integer(reply(g,16))>=2,"image reply blocked");draw(g,31);
            await(()->e.session.json().contains("\"firstFrameStarted\":true"),"START not processed");
            e.session.sendRepaint(0,0,640,360);quiet(g);hold(e);
        },MiniClientStartupSmoke::client);
        test("first completed frame ACK precedes one initial resize and repaint",e->{Socket g=startPeer(e);ready(g,640,360);quiet(g);
            check(e.session.json().contains("\"firstFrameCompleted\":true"),e.session.json());hold(e);
        },MiniClientStartupSmoke::client);
        test("startup resize flood coalesces to latest size and one full repaint",e->{Socket g=startPeer(e);
            for(int i=0;i<80;i++){e.session.sendResize(800+i*2,600);e.session.sendRepaint(3,4,100,200);}
            quiet(g);ready(g,958,600);quiet(g);String j=e.session.json();
            check(j.contains("\"deferredUiEvents\":160")&&j.contains("\"sentUiEvents\":2"),j);hold(e);
        },MiniClientStartupSmoke::client);
        test("mouse keyboard text and Sage commands are dropped rather than replayed at login",e->{Socket g=startPeer(e);
            e.session.sendKey(0,0,'P');e.session.sendSageCommand(20);e.session.sendMouseClick(30,40,1,true);e.session.sendText("secret");
            quiet(g);ready(g,640,360);quiet(g);String j=e.session.json();
            check(j.contains("\"droppedEarlyInput\":12")&&!j.contains("secret"),j);hold(e);
        },MiniClientStartupSmoke::client);
        test("media-update events are coalesced until video frame owner exists",e->{Socket g=startPeer(e);
            for(int i=0;i<20;i++)e.session.sendMediaPlayerUpdate();quiet(g);ready(g,640,360);check(reply(g,201).length==0,"bad media update");quiet(g);hold(e);
        },MiniClientStartupSmoke::client);
        test("media protocol replies are independent of GFX readiness",e->{Socket m=e.accept(1,2),g=e.accept(0,2);
            draw(g,1);reply(g,16);frame(m,0,new byte[0]);check(integer(bytes(m.getInputStream(),4))==1,"media INIT blocked");
            frame(m,17,new byte[0]);check(integer(bytes(m.getInputStream(),4))==0,"media time blocked");quiet(g);hold(e);
        },MiniClientStartupSmoke::client);
        test("runtime resize repaint and input still work after readiness",e->{Socket g=startPeer(e);ready(g,640,360);
            e.session.sendResize(1600,900);check(integer(reply(g,192))==1600,"runtime resize");check(MiniClientSession.readInt(reply(g,193),12)==900,"runtime repaint");
            e.session.sendKey(0,0,'K');check(reply(g,129)[5]=='K',"runtime key lost");
            e.session.sendRepaint(3,4,5,6);check(integer(reply(g,193))==3,"explicit repaint lost");hold(e);
        },MiniClientStartupSmoke::client);
        test("later FLIPBUFFER does not repeat initial resize/repaint",e->{Socket g=startPeer(e);ready(g,640,360);
            for(int i=0;i<8;i++){draw(g,31);draw(g,30);reply(g,16);}quiet(g);hold(e);
        },MiniClientStartupSmoke::client);
        test("driver re-INIT closes event gate until another completed frame",e->{Socket g=startPeer(e);ready(g,640,360);
            draw(g,1);reply(g,16);e.session.sendResize(1000,700);e.session.sendSageCommand(20);quiet(g);ready(g,1000,700);quiet(g);hold(e);
        },MiniClientStartupSmoke::client);
        test("close before first frame discards deferred events and cannot reopen",e->{Socket g=startPeer(e);
            e.session.sendResize(1200,800);e.session.sendRepaint(0,0,1200,800);e.session.sendMediaPlayerUpdate();quiet(g);
            e.signal.countDown();check(g.getInputStream().read()==-1,"socket not closed");e.noNewConnection();
        },e->{e.session.start();check(e.signal.await(5,TimeUnit.SECONDS),"no startup");e.session.close();String j=e.session.json();
            check(j.contains("\"ready\":false")&&j.contains("\"pendingResize\":false")&&j.contains("\"sentUiEvents\":0"),j);});
        test("invalid startup sizes are rejected before queuing",e->{Socket g=startPeer(e);
            for(int[] dims:new int[][]{{0,360},{640,0},{1921,360},{640,1081}}){boolean rejected=false;
                try{e.session.sendResize(dims[0],dims[1]);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"bad size queued");}
            quiet(g);ready(g,640,360);hold(e);
        },MiniClientStartupSmoke::client);
        System.out.println("Native startup ordering: "+count+" PASS (real TCP; synthetic server readiness boundary).");
    }
}
