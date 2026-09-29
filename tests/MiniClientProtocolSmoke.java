package org.opensagetv.webplayer;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

public final class MiniClientProtocolSmoke {
  static void require(boolean x,String m){if(!x)throw new RuntimeException(m);}
  static byte[] readN(InputStream in,int n)throws Exception{byte[] b=new byte[n];int p=0;while(p<n){int r=in.read(b,p,n-p);if(r<0)throw new EOFException();p+=r;}return b;}
  static int i32(byte[] b,int o){return ((b[o]&255)<<24)|((b[o+1]&255)<<16)|((b[o+2]&255)<<8)|(b[o+3]&255);}
  static byte[] replyPayload(InputStream in,int expectedType)throws Exception{
    byte[] h=readN(in,4);require((h[0]&255)==expectedType,"reply type "+(h[0]&255)+" != "+expectedType);int n=((h[1]&255)<<16)|((h[2]&255)<<8)|(h[3]&255);readN(in,12);return readN(in,n);
  }
  static Socket acceptHandshake(ServerSocket ss,int expectedType)throws Exception{
    Socket s=ss.accept();s.setSoTimeout(8000);byte[] h=readN(s.getInputStream(),8);require((h[0]&255)==1,"bad protocol byte");require((h[7]&255)==expectedType,"connection type "+(h[7]&255)+" != "+expectedType);s.getOutputStream().write(2);s.getOutputStream().flush();return s;
  }
  public static void main(String[] args)throws Exception{
    ServerSocket ss=new ServerSocket(0,10,InetAddress.getByName("127.0.0.1"));
    ExecutorService ex=Executors.newSingleThreadExecutor();
    CountDownLatch initDone=new CountDownLatch(1);
    Future<?> server=ex.submit(new Runnable(){public void run(){try{
      Socket media=acceptHandshake(ss,1);
      Socket gfx=acceptHandshake(ss,0);InputStream gi=gfx.getInputStream();OutputStream go=gfx.getOutputStream();
      for(String[] expected:new String[][]{{"INPUT_DEVICES","IR,KEYBOARD,TOUCH,TV"},{"PUSH_AV_CONTAINERS","MPEG2-PS"},{"GFX_RESOLUTION","640x360"}}){
        byte[] p=expected[0].getBytes(StandardCharsets.ISO_8859_1);go.write(new byte[]{0,(byte)(p.length>>16),(byte)(p.length>>8),(byte)p.length});go.write(p);go.flush();
        byte[] prop=replyPayload(gi,0);require(new String(prop,StandardCharsets.ISO_8859_1).equals(expected[1]),expected[0]+" property mismatch");
      }
      go.write(new byte[]{16,0,0,4, 1,0,0,4});go.flush();byte[] init=replyPayload(gi,16);require(i32(init,0)==1,"INIT return mismatch");
      // INIT is not server UI readiness. No asynchronous event may follow it.
      gfx.setSoTimeout(150);try{int unexpected=gi.read();throw new AssertionError("early UI event "+unexpected);}catch(SocketTimeoutException expected){}finally{gfx.setSoTimeout(8000);}
      go.write(new byte[]{16,0,0,4,31,0,0,0,16,0,0,4,30,0,0,0});go.flush();replyPayload(gi,16);
      byte[] resize=replyPayload(gi,192);require(i32(resize,0)==640&&i32(resize,4)==360,"first-frame resize event mismatch");
      byte[] repaint=replyPayload(gi,193);require(i32(repaint,8)==640&&i32(repaint,12)==360,"first-frame repaint mismatch");initDone.countDown();
      resize=replyPayload(gi,192);require(i32(resize,0)==1600&&i32(resize,4)==800,"explicit resize event mismatch");
      repaint=replyPayload(gi,193);require(i32(repaint,8)==1600&&i32(repaint,12)==800,"explicit repaint mismatch");
      byte[] cmd=replyPayload(gi,136);require(i32(cmd,0)==28,"Sage command mismatch");
      for(int type:new int[]{133,130,131,132}){
        byte[] mouse=replyPayload(gi,type);require(i32(mouse,0)==123&&i32(mouse,4)==45,"click coordinates");
      }
      for(int type:new int[]{133,130,131})replyPayload(gi,type);
      for(char expected:new char[]{'A','é','\n'}){
        byte[] key=replyPayload(gi,129);int ch=((key[4]&255)<<8)|(key[5]&255);require(ch==expected,"UTF-16 key char mismatch");
      }
      media.close();gfx.close();
    }catch(Exception e){throw new RuntimeException(e);}}});
    MiniClientSession s=new MiniClientSession("smoke","127.0.0.1:"+ss.getLocalPort(),"02:00:00:00:00:22",640,360);s.start();require(initDone.await(5,TimeUnit.SECONDS),"INIT not complete");s.sendResize(1600,800);s.sendSageCommand(28);s.sendMouseClick(123,45,1,true);s.sendMouseClick(7,8,3,false);s.sendText("Aé\n");
    boolean invalid=false;try{s.sendSageCommand(0);}catch(IllegalArgumentException e){invalid=true;}require(invalid,"invalid command accepted");
    invalid=false;try{s.sendResize(9000,300);}catch(IllegalArgumentException e){invalid=true;}require(invalid,"invalid resize accepted");
    server.get(10,TimeUnit.SECONDS);s.close();ss.close();ex.shutdownNow();System.out.println("MiniClient protocol smoke PASS (first-frame/explicit resize + repaint, commands, mouse, text and bounds)");
  }
}
