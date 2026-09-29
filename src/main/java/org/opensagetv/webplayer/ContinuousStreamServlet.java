package org.opensagetv.webplayer;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Same-origin chunked MPEG-TS delivery with explicit byte resume for mpegts.js lazy loading. */
public final class ContinuousStreamServlet extends HttpServlet {
    private final AtomicInteger number=new AtomicInteger();
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(0,16,60,TimeUnit.SECONDS,
        new SynchronousQueue<Runnable>(),r->{Thread t=new Thread(r,"webplayer-ts-http-"+number.incrementAndGet());t.setDaemon(true);return t;});
    @Override protected void doGet(HttpServletRequest req,HttpServletResponse resp)throws IOException {
        String path=req.getPathInfo();
        if(path==null||!path.matches("/[a-f0-9]{32}/stream\\.ts")){HttpUtil.jsonError(resp,404,"Unknown continuous stream");return;}
        HlsSessionManager.Session s=HlsSessionManager.get(path.substring(1,33));
        if(s==null||!s.isTs()||s.stopRequested){HttpUtil.jsonError(resp,404,"Continuous stream session has ended");return;}
        final long start;
        try{start=parseOffset(req.getParameter("bstart"));}
        catch(IllegalArgumentException e){HttpUtil.jsonError(resp,400,e.getMessage());return;}
        if(start>s.continuous.length()||start>s.maxBytes){HttpUtil.jsonError(resp,416,"Byte resume is outside the produced stream");return;}
        // An unbounded growing response has no known complete representation length.
        // ParamSeekHandler expects 200, so do not fabricate an invalid 206 Content-Range.
        if(req.getHeader("Range")!=null){HttpUtil.jsonError(resp,400,"Use the configured bstart byte-resume parameter, not HTTP Range");return;}
        resp.setContentType("video/mp2t");resp.setHeader("Cache-Control","no-store, no-transform");
        resp.setHeader("Accept-Ranges","none");resp.setHeader("X-Accel-Buffering","no");
        resp.setHeader("X-SageTV-Byte-Start",String.valueOf(start));
        if(!req.isAsyncSupported()){HttpUtil.jsonError(resp,503,"Jetty asynchronous servlet support is required for continuous streaming; select HLS");return;}
        final AsyncContext async=req.startAsync();async.setTimeout(0L);
        final AtomicBoolean cancelled=new AtomicBoolean();
        async.addListener(new AsyncListener(){
            public void onComplete(AsyncEvent e){}
            public void onStartAsync(AsyncEvent e){}
            public void onTimeout(AsyncEvent e){cancelled.set(true);}
            public void onError(AsyncEvent e){cancelled.set(true);}
        });
        try {
            workers.execute(()->{
                try {
                    ContinuousFilePump.copy(s.continuous,start,resp.getOutputStream(),
                        ()->!s.stopRequested&&s.process!=null&&s.process.isAlive(),
                        ()->cancelled.get()||s.stopRequested,
                        n->{s.deliveredBytes+=n;s.lastAccessAt=System.currentTimeMillis();});
                }catch(InterruptedException e){Thread.currentThread().interrupt();}
                catch(IOException e){/* Closing a full lazy-load buffer is normal; keep the producer/file for byte resume. */}
                finally{try{async.complete();}catch(IllegalStateException ignored){}}
            });
        }catch(RejectedExecutionException e){HttpUtil.jsonError(resp,503,"Continuous HTTP worker limit reached; stop another client");async.complete();}
    }
    static long parseOffset(String value){try{long v=value==null?0:Long.parseLong(value);if(v<0)throw new NumberFormatException();return v;}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid bstart byte offset");}}
    @Override public void destroy(){workers.shutdownNow();super.destroy();}
}
