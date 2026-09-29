package org.opensagetv.webplayer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;

/** Actual loopback TCP resets/handshakes. The peer is a fake, NOT a live SageTV server. */
public final class MiniClientRecoverySmoke {
    static int passed;
    interface Work { void run(Env e) throws Exception; }
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void await(BooleanSupplier ok,String why)throws Exception{
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        while(!ok.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(10);
        check(ok.getAsBoolean(),why);
    }
    static final class Env implements AutoCloseable {
        final ServerSocket server=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));
        final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"fake-sagetv");t.setDaemon(true);return t;});
        final List<Socket> peers=Collections.synchronizedList(new ArrayList<Socket>());
        final CountDownLatch signal=new CountDownLatch(1), release=new CountDownLatch(1);
        MiniClientSession session;
        Env()throws Exception { server.setSoTimeout(4000);session=new MiniClientSession("recovery-test",address(),"02:00:00:00:00:55",640,360); }
        String address(){return "127.0.0.1:"+server.getLocalPort();}
        Socket accept(int type,int reply)throws Exception{
            Socket s=server.accept();peers.add(s);s.setSoTimeout(4000);
            byte[] h=bytes(s.getInputStream(),8);check((h[0]&255)==1,"bad handshake version");check((h[7]&255)==type,"expected handshake "+type+" got "+(h[7]&255));
            if(reply>=0){s.getOutputStream().write(reply);s.getOutputStream().flush();}return s;
        }
        void noNewConnection()throws Exception{
            server.setSoTimeout(350);
            try {Socket s=server.accept();peers.add(s);throw new AssertionError("unexpected replacement connection");}
            catch(SocketTimeoutException expected){} finally{server.setSoTimeout(4000);}
        }
        public void close(){session.close();release.countDown();MiniClientSessionManager.stopAll();try{server.close();}catch(Exception ignored){}
            synchronized(peers){for(Socket s:peers)try{s.close();}catch(Exception ignored){}}worker.shutdownNow();}
    }
    static byte[] bytes(InputStream in,int n)throws Exception{byte[] b=new byte[n];new DataInputStream(in).readFully(b);return b;}
    static byte[] reply(Socket s,int type)throws Exception{
        byte[] h=bytes(s.getInputStream(),16);check((h[0]&255)==type,"reply type "+(h[0]&255)+" != "+type);
        int n=((h[1]&255)<<16)|((h[2]&255)<<8)|(h[3]&255);return bytes(s.getInputStream(),n);
    }
    static int integer(byte[] b){return MiniClientSession.readInt(b,0);}
    static void frame(Socket s,int type,byte[] b)throws Exception{
        DataOutputStream out=new DataOutputStream(s.getOutputStream());out.writeByte(type);out.writeByte(b.length>>>16);out.writeShort(b.length);out.write(b);out.flush();
    }
    static byte[] gfxBody(int cmd,int... args)throws Exception{
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);
        out.writeByte(cmd);out.writeByte(args.length*4>>>16);out.writeShort(args.length*4);for(int n:args)out.writeInt(n);return bytes.toByteArray();
    }
    static void draw(Socket s,int cmd,int... args)throws Exception{frame(s,16,gfxBody(cmd,args));}
    static void property(Socket s,String name,String value)throws Exception{
        ByteArrayOutputStream b=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(b);
        out.writeShort(name.length());out.writeShort(value.length());out.writeBytes(name);out.writeBytes(value);frame(s,1,b.toByteArray());
        check(integer(reply(s,1))==0,"property not accepted: "+name);
    }
    // Only the explicit historical authentication baseline mode enables this.
    static boolean legacyInitEvents;
    static void init(Socket s,boolean negotiate,boolean first)throws Exception{
        if(negotiate)property(s,"RECONNECT_SUPPORTED","TRUE");
        draw(s,1);check(integer(reply(s,16))==1,"GFX INIT reply");
        if(legacyInitEvents){reply(s,192);reply(s,193);}
        if(first){draw(s,31);draw(s,30);reply(s,16);if(!legacyInitEvents){reply(s,192);reply(s,193);}}
    }
    static void reset(Socket s)throws Exception{s.setSoLinger(true,0);s.close();}
    static void run(String name,Work server,Work client)throws Exception{
        try(Env e=new Env()){
            Future<?> f=e.worker.submit(()->{try{server.run(e);}catch(Exception x){throw new RuntimeException(x);}});
            client.run(e);f.get(15,TimeUnit.SECONDS);passed++;System.out.println("PASS "+name);
        }
    }
    static void recover(int rejected,boolean partialHeader)throws Exception{
        run("type-5 recovery, "+rejected+" initial rejection(s), partial "+(partialHeader?"header":"body")+", retained media/cache",e->{
            Socket media=e.accept(1,2),gfx=e.accept(0,2);init(gfx,true,true);
            draw(gfx,26,20,20);check(integer(reply(gfx,16))==2,"initial image handle");
            // Break a frame deliberately. No bytes from it may be dispatched or
            // replayed on the replacement connection.
            OutputStream out=gfx.getOutputStream();
            out.write(partialHeader?new byte[]{16,0}:new byte[]{16,0,0,12,26,0,0,8,0});out.flush();reset(gfx);
            for(int n=0;n<rejected;n++)e.accept(5,0).close();
            Socket next=e.accept(5,2);frame(next,0,"GFX_RESOLUTION".getBytes(StandardCharsets.ISO_8859_1));
            check(new String(reply(next,0),StandardCharsets.ISO_8859_1).equals("640x360"),"property after recovery");
            draw(next,26,30,30);check(integer(reply(next,16))==3,"cache/handle state reset or partial frame dispatched");
            frame(media,17,new byte[0]);check(integer(bytes(media.getInputStream(),4))==0,"original media channel did not survive");
            e.signal.countDown();check(e.release.await(5,TimeUnit.SECONDS),"release timeout");
        },e->{e.session.start();check(e.signal.await(8,TimeUnit.SECONDS),"recovery not completed");
            check(e.session.isAlive(),"session died");String j=e.session.json();
            check(j.contains("\"reconnectSuccesses\":1"),j);check(j.contains("\"reconnectAttempts\":"+(rejected+1)),j);
            check(j.contains("Connection reset"),"original reset evidence lost");check(e.session.lastError().isEmpty(),"recovered reset marked fatal");
            List<String> events=e.session.pollEvents(500,0);for(String x:events)check(!x.contains("\"type\":\"videoStop\""),"recovery stopped media");
            e.release.countDown();});
    }
    public static void main(String[] args)throws Exception{
        recover(0,true);recover(2,false);
        for(boolean first:new boolean[]{false,true})run(first?"unnegotiated reset is terminal":"pre-first-frame reset is terminal",e->{
            e.accept(1,2);Socket gfx=e.accept(0,2);init(gfx,!first,first);reset(gfx);e.noNewConnection();
        },e->{e.session.start();await(()->!e.session.isAlive(),"failed session still alive");
            check(e.session.lastError().contains("GFX connection ended"),e.session.json());
            check(e.session.json().contains("\"reconnectAttempts\":0"),e.session.json());});
        run("three rejected reconnects fail visibly without a fourth handshake",e->{
            e.accept(1,2);Socket g=e.accept(0,2);init(g,true,true);reset(g);
            for(int i=0;i<3;i++)e.accept(5,0).close();e.noNewConnection();
        },e->{e.session.start();await(()->!e.session.isAlive(),"retries did not end");check(e.session.json().contains("\"reconnectAttempts\":3"),e.session.json());
            List<String> events=e.session.pollEvents(500,0);boolean fatal=false;for(String x:events)fatal|=x.contains("\"fatal\":true");check(fatal,"no terminal browser event");});
        run("close cancels an in-flight reconnect handshake and prevents resurrection",e->{
            e.accept(1,2);Socket g=e.accept(0,2);init(g,true,true);reset(g);Socket pending=e.accept(5,-1);e.signal.countDown();
            check(pending.getInputStream().read()==-1,"pending handshake not closed");e.noNewConnection();
        },e->{e.session.start();check(e.signal.await(5,TimeUnit.SECONDS),"reconnect handshake did not start");e.session.close();
            await(()->e.session.json().contains("\"gfxReader\":false"),"GFX worker survived close");check(!e.session.isAlive(),"session resurrected");});
        run("explicit close terminates media/GFX without reconnect",e->{
            Socket m=e.accept(1,2),g=e.accept(0,2);init(g,true,true);e.signal.countDown();
            check(g.getInputStream().read()==-1,"GFX socket not closed");check(m.getInputStream().read()==-1,"media socket not closed");e.noNewConnection();
        },e->{e.session.start();check(e.signal.await(5,TimeUnit.SECONDS),"init missing");e.session.close();check(e.session.lastError().isEmpty(),"normal close became an error");});
        run("server DEINIT closes cleanly instead of reconnecting",e->{
            e.accept(1,2);Socket g=e.accept(0,2);init(g,true,true);draw(g,2);check(g.getInputStream().read()==-1,"DEINIT not closed");e.noNewConnection();
        },e->{e.session.start();await(()->!e.session.isAlive(),"DEINIT still alive");check(e.session.json().contains("\"reconnectAttempts\":0"),e.session.json());});
        run("rejected startup cleans up both media and pending GFX socket",e->{
            Socket m=e.accept(1,2),g=e.accept(0,0);check(g.getInputStream().read()==-1,"rejected GFX socket leaked");check(m.getInputStream().read()==-1,"startup media socket leaked");
        },e->{boolean rejected=false;try{e.session.start();}catch(IOException expected){rejected=true;}check(rejected,"startup rejection hidden");check(!e.session.isAlive(),"rejected session alive");});
        run("diagnostic property trace is bounded and omits property values",e->{
            e.accept(1,2);Socket g=e.accept(0,2);init(g,false,false);
            for(int n=0;n<100;n++)property(g,"MENU_HINT","private-path-should-not-appear");e.signal.countDown();e.release.await(5,TimeUnit.SECONDS);
        },e->{e.session.start();check(e.signal.await(5,TimeUnit.SECONDS),"properties timed out");String j=e.session.json();check(!j.contains("private-path"),"diagnostics leaked a property value");
            check(j.split("atMs",-1).length-1==64,"trace was not bounded");e.release.countDown();});
        run("same-ID refresh retires old native sockets; stale stop cannot kill replacement",e->{
            Socket m1=e.accept(1,2),g1=e.accept(0,2);init(g1,true,true);e.signal.countDown();
            check(g1.getInputStream().read()==-1,"old GFX not closed before replacement");check(m1.getInputStream().read()==-1,"old media not closed");
            e.accept(1,2);Socket g2=e.accept(0,2);init(g2,true,true);e.release.countDown();Thread.sleep(200);
        },e->{MiniClientSession old=MiniClientSessionManager.start(e.address(),"02:00:00:00:00:66",640,360);check(e.signal.await(5,TimeUnit.SECONDS),"first managed session missing");
            MiniClientSession next=MiniClientSessionManager.start(e.address(),"020000000066",640,360);check(e.release.await(5,TimeUnit.SECONDS),"replacement missing");
            check(!old.isAlive()&&old.lastError().contains("another browser"),"old session not explicitly superseded");
            MiniClientSessionManager.stop(old.id());check(next.isAlive(),"stale stop killed replacement");check(!next.id().equals(old.id()),"replacement identity not unique");});
        System.out.println("Native recovery tests: "+passed+" PASS (real loopback sockets, synthetic SageTV peer)");
    }
}
