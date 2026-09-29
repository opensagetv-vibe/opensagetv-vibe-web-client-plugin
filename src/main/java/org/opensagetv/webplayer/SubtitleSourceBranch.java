package org.opensagetv.webplayer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * Original-source subtitle preservation independent of the main video encoder.
 * MPEG-TS services are copied by PID; container subtitle streams use a bounded
 * ffmpeg stream-copy helper. Failure is diagnostic-only and never stops A/V.
 */
final class SubtitleSourceBranch {
    private static final AtomicInteger ACTIVE=new AtomicInteger();
    final PlaybackSessionContext.Token token; final MediaProbe.Track track; final File output;
    volatile long bytes; volatile boolean running,complete,stopRequested; volatile String error="",mode="";
    private volatile Thread thread; private volatile Process process; private final long startedAt=System.currentTimeMillis();
    private SubtitleSourceBranch(PlaybackSessionContext.Token token,MediaProbe.Track track,File output){this.token=token;this.track=track;this.output=output;}

    static SubtitleSourceBranch start(File source,MediaProbe.Track track,File directory,String ffmpeg,PlaybackSessionContext.Token token,BooleanSupplier sourceActive){
        if(source==null||track==null||directory==null||token==null)return null;
        File out=new File(directory,track.sourcePid>=0?"subtitle-source.ts":"subtitle-source.mkv");SubtitleSourceBranch b=new SubtitleSourceBranch(token,track,out);
        if(ACTIVE.incrementAndGet()>SubtitleLimits.MAX_ACTIVE_TAPS){ACTIVE.decrementAndGet();b.error="subtitle tap limit reached";return b;}
        b.running=true;
        if(track.sourcePid>=0)b.startPidCopy(source,track.sourcePid,sourceActive);else b.startFfmpegCopy(source,ffmpeg);
        return b;
    }
    private void startPidCopy(final File source,final int pid,final BooleanSupplier sourceActive){mode="ts-pid-copy";thread=new Thread(()->{
        long idleAt=System.currentTimeMillis();try(BufferedInputStream in=new BufferedInputStream(new FileInputStream(source));BufferedOutputStream out=new BufferedOutputStream(new FileOutputStream(output))){
            byte[] p=new byte[188];while(!stopRequested){if(System.currentTimeMillis()-startedAt>SubtitleLimits.MAX_TAP_WALL_MS){error="subtitle tap wall-time limit reached";break;}int n=readPacket(in,p);if(n<0){if(sourceActive!=null&&sourceActive.getAsBoolean()&&System.currentTimeMillis()-idleAt<SubtitleLimits.TAP_IDLE_MS){sleep(80);continue;}break;}idleAt=System.currentTimeMillis();if(n!=188)break;if((p[0]&255)!=0x47)continue;int packetPid=((p[1]&31)<<8)|(p[2]&255);if(packetPid!=pid)continue;if(bytes+188>SubtitleLimits.MAX_SUBTITLE_TAP_BYTES){error="subtitle tap byte limit reached";break;}out.write(p);bytes+=188;}
            out.flush();
        }catch(Exception e){if(!stopRequested)error=compact(e);}finally{finish();}
    },"webplayer-subtitle-pid-"+pid);thread.setDaemon(true);thread.start();}
    private void startFfmpegCopy(File source,String ffmpeg){mode="ffmpeg-stream-copy";thread=new Thread(()->{
        try{
            if(ffmpeg==null||ffmpeg.trim().isEmpty()||!new File(ffmpeg).isFile())throw new IOException("FFmpeg unavailable for subtitle stream copy");
            ProcessBuilder pb=new ProcessBuilder(Arrays.asList(ffmpeg,"-hide_banner","-loglevel","warning","-nostdin","-y","-i",source.getAbsolutePath(),"-map","0:"+track.index,"-c","copy","-f","matroska",output.getAbsolutePath()));
            pb.redirectError(new File(output.getParentFile(),"subtitle-tap.log"));process=pb.start();
            while(process.isAlive()&&!stopRequested){if(System.currentTimeMillis()-startedAt>SubtitleLimits.MAX_TAP_WALL_MS){error="subtitle tap wall-time limit reached";process.destroyForcibly();break;}bytes=output.isFile()?output.length():0;if(bytes>SubtitleLimits.MAX_SUBTITLE_TAP_BYTES){error="subtitle tap byte limit reached";process.destroyForcibly();break;}sleep(100);}
            if(stopRequested&&process.isAlive())process.destroyForcibly();if(process.isAlive())process.waitFor();bytes=output.isFile()?output.length():0;
            if(!stopRequested&&process.exitValue()!=0&&error.isEmpty())error="subtitle stream-copy helper exited "+process.exitValue();
        }catch(Exception e){if(!stopRequested)error=compact(e);}finally{finish();}
    },"webplayer-subtitle-copy-"+track.index);thread.setDaemon(true);thread.start();}
    void stop(){stopRequested=true;Process p=process;if(p!=null&&p.isAlive())p.destroyForcibly();Thread t=thread;if(t!=null)t.interrupt();}
    private void finish(){running=false;complete=error.isEmpty()&&!stopRequested;ACTIVE.decrementAndGet();}
    String json(){return "{\"mode\":\""+HttpUtil.json(mode)+"\",\"trackIndex\":"+track.index+",\"sourcePid\":"+track.sourcePid+",\"running\":"+running+",\"complete\":"+complete+",\"bytes\":"+bytes+",\"error\":\""+HttpUtil.json(error)+"\"}";}
    private static int readPacket(BufferedInputStream in,byte[] p)throws IOException{int first=in.read();if(first<0)return -1;while(first!=0x47){first=in.read();if(first<0)return -1;}p[0]=(byte)first;int off=1;while(off<188){int n=in.read(p,off,188-off);if(n<0)return off;off+=n;}return off;}
    private static void sleep(long ms){try{Thread.sleep(ms);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
    private static String compact(Exception e){String s=e.getMessage();return s==null?e.getClass().getSimpleName():s.replace('\n',' ').replace('\r',' ');}
}
