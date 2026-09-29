package org.opensagetv.webplayer;
import java.util.*;
public final class LegacyCaptionBridgeSmoke {
  static final class E{long p;int f;byte[] d;E(long p,int f,byte[] d){this.p=p;this.f=f;this.d=d;}}
  public static void main(String[] args)throws Exception{
    List<E> e=new ArrayList<E>();LegacyExtenderCaptionBridge b=new LegacyExtenderCaptionBridge((p,d,x,f)->e.add(new E(p,f,x)));
    if(!b.onCeaSample(2000,new byte[]{4,(byte)0x94,0x25,7,0x45,0x46}))throw new AssertionError();
    b.drainTo(1000);if(!e.isEmpty())throw new AssertionError("read ahead leaked");
    b.drainTo(2000);if(e.size()!=1||e.get(0).p!=90000||e.get(0).d.length!=16)throw new AssertionError("bad legacy packet");
    b.clearPending();b.postFlush();if(e.size()!=2||e.get(1).f!=(LegacyExtenderCaptionBridge.CC_SUBTITLE|LegacyExtenderCaptionBridge.FLUSH_SUBTITLE_QUEUE)||e.get(1).d.length!=128)throw new AssertionError("bad flush");
    System.out.println("legacy event-225 caption bridge: 4 PASS");
  }
}
