package org.opensagetv.webplayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Bounded probe subprocess. Drains before waiting; terminates hung GPU/ffprobe processes. */
final class ProcessCapture {
    static final class Result { final int exit; final String text; Result(int e,String t){exit=e;text=t;} }
    static Result run(List<String> command,int timeoutSeconds,int maxBytes) throws IOException {
        final Process p=new ProcessBuilder(command).redirectErrorStream(true).start();
        final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        Thread drain=new Thread(() -> {
            try(InputStream in=p.getInputStream()) {
                byte[] chunk=new byte[8192];int n;
                while((n=in.read(chunk))!=-1) {
                    synchronized(bytes){if(bytes.size()<maxBytes) bytes.write(chunk,0,Math.min(n,maxBytes-bytes.size()));}
                }
            }catch(IOException ignored){}
        },"webplayer-probe-output");drain.setDaemon(true);drain.start();
        try {
            try{p.getOutputStream().close();}catch(IOException ignored){}
            if(!p.waitFor(timeoutSeconds,TimeUnit.SECONDS))throw new IOException("Media capability probe timed out after "+timeoutSeconds+" seconds");
            drain.join(1500L);
            synchronized(bytes){return new Result(p.exitValue(),new String(bytes.toByteArray(),StandardCharsets.UTF_8));}
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Media probe interrupted",e);}
        finally{if(p.isAlive()){p.destroyForcibly();}try{p.getInputStream().close();}catch(IOException ignored){}}
    }
}
