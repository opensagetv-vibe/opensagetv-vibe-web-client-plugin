package org.opensagetv.webplayer;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.servlet.*;
import javax.servlet.http.*;

/** Real servlets/file pump with mock Servlet 3.1 request/response/async objects. */
public final class StreamingServletSmoke {
    static int passed;
    static void check(boolean ok,String what){if(!ok)throw new AssertionError(what);passed++;System.out.println("PASS "+what);}
    static final class Exchange {
        String path,range;boolean asyncSupported=true,asyncStarted=false,failWrite=false;
        int status=200;long timeout=-1;String contentType="";
        Map<String,String> params=new HashMap<>(),headers=new HashMap<>();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();StringWriter text=new StringWriter();
        CountDownLatch complete=new CountDownLatch(1);
        ServletOutputStream output=new ServletOutputStream(){
            public boolean isReady(){return true;}public void setWriteListener(WriteListener l){}
            public void write(int b)throws IOException{if(failWrite)throw new IOException("client closed");bytes.write(b);}
            public void write(byte[] b,int o,int n)throws IOException{if(failWrite)throw new IOException("client closed");bytes.write(b,o,n);}
        };
        HttpServletResponse resp=(HttpServletResponse)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletResponse.class},(p,m,a)->{
            switch(m.getName()){
                case "setStatus":status=(int)a[0];break;case "setContentType":contentType=(String)a[0];break;
                case "setHeader":headers.put((String)a[0],(String)a[1]);break;
                case "getWriter":return new PrintWriter(text);case "getOutputStream":return output;
            }return zero(m.getReturnType());
        });
        AsyncContext async=(AsyncContext)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{AsyncContext.class},(p,m,a)->{
            switch(m.getName()){
                case "complete":complete.countDown();break;case "setTimeout":timeout=(long)a[0];break;
                case "getResponse":return resp;
            }return zero(m.getReturnType());
        });
        HttpServletRequest req=(HttpServletRequest)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{HttpServletRequest.class},(p,m,a)->{
            switch(m.getName()){
                case "getPathInfo":return path;case "getParameter":return params.get(a[0]);
                case "getHeader":return "Range".equals(a[0])?range:null;
                case "isAsyncSupported":return asyncSupported;case "startAsync":asyncStarted=true;return async;
            }return zero(m.getReturnType());
        });
        void await()throws Exception{if(!complete.await(5,TimeUnit.SECONDS))throw new AssertionError("async completion timed out");}
    }
    static Object zero(Class<?> t){if(!t.isPrimitive()||t==void.class)return null;if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;throw new IllegalStateException(t.toString());}
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        Field f=HlsSessionManager.class.getDeclaredField("SESSIONS");f.setAccessible(true);
        Map<String,HlsSessionManager.Session> sessions=(Map<String,HlsSessionManager.Session>)f.get(null);
        File dir=Files.createTempDirectory("sagetv-servlet-test-").toFile();String id="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        HlsSessionManager.Session s=new HlsSessionManager.Session(id,1,0,0,0,60000,false,dir,StreamOptions.defaults());
        sessions.put(id,s);byte[] payload=new byte[40000];new Random(21).nextBytes(payload);Files.write(s.continuous.toPath(),payload);
        ContinuousStreamServlet servlet=new ContinuousStreamServlet();
        try {
            Exchange e=new Exchange();e.path="/"+id+"/stream.ts";servlet.doGet(e.req,e.resp);e.await();
            check(e.status==200&&Arrays.equals(payload,e.bytes.toByteArray())&&e.asyncStarted&&e.timeout==0,"async servlet streams complete original bytes, 200/no invented Content-Length");
            check("video/mp2t".equals(e.contentType)&&"no-store, no-transform".equals(e.headers.get("Cache-Control"))&&"no".equals(e.headers.get("X-Accel-Buffering")),"continuous transport has no-cache/no-proxy-buffer headers");
            e=new Exchange();e.path="/"+id+"/stream.ts";e.params.put("bstart","19999");servlet.doGet(e.req,e.resp);e.await();
            check(e.status==200&&Arrays.equals(Arrays.copyOfRange(payload,19999,payload.length),e.bytes.toByteArray())&&"19999".equals(e.headers.get("X-SageTV-Byte-Start")),"lazy-load reconnect resumes exact byte suffix without restarting producer");
            for(String offset:new String[]{"-1","NaN","9223372036854775808"}){e=new Exchange();e.path="/"+id+"/stream.ts";e.params.put("bstart",offset);servlet.doGet(e.req,e.resp);if(e.status!=400||e.asyncStarted)throw new AssertionError(offset);}
            check(true,"negative, nonnumeric and overflowing resume offsets rejected before async work");
            e=new Exchange();e.path="/"+id+"/stream.ts";e.params.put("bstart","50000");servlet.doGet(e.req,e.resp);check(e.status==416,"unproduced resume offset rejected with 416");
            e=new Exchange();e.path="/"+id+"/stream.ts";e.range="bytes=1-";servlet.doGet(e.req,e.resp);check(e.status==400&&!e.headers.containsKey("Content-Range"),"HTTP Range rejected rather than fabricating total growing length");
            e=new Exchange();e.path="/../secret/stream.ts";servlet.doGet(e.req,e.resp);check(e.status==404,"path traversal and non-session stream URL rejected");
            e=new Exchange();e.path="/"+id+"/stream.ts";e.asyncSupported=false;servlet.doGet(e.req,e.resp);check(e.status==503&&!e.asyncStarted,"unsupported Jetty async deployment gives actionable HLS fallback error");
            e=new Exchange();e.path="/"+id+"/stream.ts";e.failWrite=true;servlet.doGet(e.req,e.resp);e.await();check(!s.stopRequested&&sessions.containsKey(id),"closing a full client buffer retains producer session for later reconnect");
            CaptionsServlet cc=new CaptionsServlet();e=new Exchange();e.params.put("session",id);cc.doGet(e.req,e.resp);check(e.status==409,"caption endpoint refuses disabled caption stream");
            e=new Exchange();e.params.put("session","not-a-session");cc.doGet(e.req,e.resp);check(e.status==404,"caption endpoint rejects unknown session instead of accepting arbitrary media path");
            String cueId="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";PlaybackSessionContext cueCtx=new PlaybackSessionContext();PlaybackSessionContext.Token cueToken=cueCtx.beginSource();
            HlsSessionManager.Session cueSession=new HlsSessionManager.Session(cueId,2,0,0,0,60000,false,Files.createTempDirectory("sagetv-cue-test-").toFile(),StreamOptions.defaults(),cueCtx,cueToken);sessions.put(cueId,cueSession);
            cueCtx.cues().offer(SubtitleCue.text(cueToken,SubtitleCue.Owner.LOCAL_FILE,1000,500,"safe <caption>",false,false));
            SubtitleCueServlet cueServlet=new SubtitleCueServlet();e=new Exchange();e.params.put("session",cueId);e.params.put("timeMs","999");cueServlet.doGet(e.req,e.resp);check(e.status==200&&e.text.toString().contains("\"cues\":[]"),"versioned subtitle cue endpoint does not drain future cues");
            e=new Exchange();e.params.put("session",cueId);e.params.put("timeMs","1000");cueServlet.doGet(e.req,e.resp);check(e.status==200&&e.text.toString().contains("safe <caption>")&&e.text.toString().contains("\"contractVersion\":1"),"versioned subtitle cue endpoint drains only current-session due cues");
            cueCtx.beginSeek();check(cueCtx.cues().size()==0,"seek generation clears queued subtitle cues");sessions.remove(cueId);Files.deleteIfExists(cueSession.directory.toPath());
            AssetStatusServlet assets=new AssetStatusServlet();e=new Exchange();e.params.put("family","https://untrusted.invalid/");assets.doPost(e.req,e.resp);check(e.status==400,"asset preparation rejects arbitrary families/URLs without network access");
            check(AssetCache.streamingList().size()==2&&AssetCache.streamingList().contains("mpegts-1.8.0/mpegts.min.js"),"streaming prefetch allowlist is limited to two pinned player assets");
        } finally {servlet.destroy();sessions.remove(id);Files.deleteIfExists(s.continuous.toPath());Files.deleteIfExists(dir.toPath());HlsSessionManager.stopAll();}
        System.out.println("Streaming servlet tests: "+passed+" PASS (mock Servlet API; real async workers/file IO)");
    }
}
