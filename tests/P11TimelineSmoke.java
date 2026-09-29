package org.opensagetv.webplayer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** P11 timestamp seek, EOF settle, overwrite protection and command placement. */
public final class P11TimelineSmoke {
    static int tests;
    static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
    static void pass(String n){tests++;System.out.println("PASS "+n);}
    static void run(List<String> c,File log)throws Exception{Process p=new ProcessBuilder(c).redirectErrorStream(true).redirectOutput(log).start();if(!p.waitFor(45,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("ffmpeg timeout");}check(p.exitValue()==0,"ffmpeg failed: "+new String(Files.readAllBytes(log.toPath()),"UTF-8"));}
    public static void main(String[] args)throws Exception{
        String ff=args.length>0?args[0]:"/usr/bin/ffmpeg";check(new File(ff).isFile(),"ffmpeg missing");
        Path root=Files.createTempDirectory("p11-timeline-");File ts=root.resolve("vbr.ts").toFile();
        // Two very different complexity halves make byte ratio intentionally a
        // poor time estimate while PTS remains authoritative.
        run(Arrays.asList(ff,"-hide_banner","-loglevel","error","-y",
                "-f","lavfi","-i","color=c=black:size=320x180:rate=25:d=30",
                "-f","lavfi","-i","testsrc2=size=320x180:rate=25:d=30",
                "-f","lavfi","-i","sine=frequency=700:sample_rate=48000:d=60",
                "-filter_complex","[0:v][1:v]concat=n=2:v=1:a=0[v]","-map","[v]","-map","2:a",
                "-c:v","libx264","-preset","veryfast","-crf","21","-g","25","-bf","0","-c:a","aac","-f","mpegts",ts.getAbsolutePath()),root.resolve("make.log").toFile());
        MpegTsSeekIndex.Sources one=new MpegTsSeekIndex.Sources(){public int segmentCount(){return 1;}public SeekableMediaSource open(int i)throws IOException{return new DirectFileSource(ts);}};
        MpegTsSeekIndex.Result r=MpegTsSeekIndex.resolve(one,45_000);
        check(r.validated,"PTS index unavailable: "+r.reason);check(r.absoluteByte>0&&r.packetSize==188,"bad PTS byte anchor");
        check(r.anchorMs<=45_000&&r.errorMs<=1500,"seek anchor tolerance: "+r.anchorMs+" error="+r.errorMs);
        long proportional=(long)(ts.length()*.75);check(Math.abs(r.absoluteByte-proportional)>188*10,"fixture did not distinguish PTS from proportional byte guess");
        pass("VBR MPEG-TS seek uses observed PTS rather than duration/file-size ratio");
        List<String> cmd=new ArrayList<String>(Arrays.asList(ff,"-i","pipe:0","-map","0:v:0","out.ts"));
        HlsSessionManager.addPostInputSeek(cmd,1.375);int i=cmd.indexOf("-i");check("-ss".equals(cmd.get(i+2))&&"1.375".equals(cmd.get(i+3)),"post-input residual seek placement");
        pass("pipe residual timestamp seek is placed after input and before mapping");
        File bad=root.resolve("not-ts.bin").toFile();Files.write(bad.toPath(),new byte[64*1024]);MpegTsSeekIndex.Sources bogus=new MpegTsSeekIndex.Sources(){public int segmentCount(){return 1;}public SeekableMediaSource open(int i)throws IOException{return new DirectFileSource(bad);}};
        MpegTsSeekIndex.Result no=MpegTsSeekIndex.resolve(bogus,30_000);check(!no.validated&&no.absoluteByte==0,"non-TS source must fail closed to byte zero");
        pass("unindexed source never invents a time-to-byte offset");

        // SageTV can clear the recording flag before its final size update. The
        // follower waits a bounded settle interval and captures late bytes.
        File grow=root.resolve("grow.ts").toFile();Files.write(grow.toPath(),"ABC".getBytes("UTF-8"));AtomicBoolean rec=new AtomicBoolean(false),active=new AtomicBoolean(true);ByteArrayOutputStream out=new ByteArrayOutputStream();
        RecordingFollower.Sources gs=new RecordingFollower.Sources(){public int segmentCount(){return 1;}public boolean isRecording(){return rec.get();}public SeekableMediaSource open(int x)throws IOException{return new DirectFileSource(grow);}};
        ExecutorService pool=Executors.newSingleThreadExecutor();Future<?> f=pool.submit(()->{try{RecordingFollower.copy(gs,0,0,out,active::get,n->{},v->{});}catch(Exception e){throw new RuntimeException(e);}});
        Thread.sleep(350);Files.write(grow.toPath(),"DEF".getBytes("UTF-8"),StandardOpenOption.APPEND);f.get(3,TimeUnit.SECONDS);check("ABCDEF".equals(out.toString("UTF-8")),"final settle lost bytes: "+out);pool.shutdownNow();
        pass("completed EOF uses bounded final-tail settle and captures late visible bytes");

        // Replacing/truncating an active source must not reinterpret an old
        // resume offset against new content.
        File shrink=root.resolve("shrink.ts").toFile();byte[] big=new byte[512*1024];Files.write(shrink.toPath(),big);AtomicBoolean live=new AtomicBoolean(true);ByteArrayOutputStream sink=new ByteArrayOutputStream();ExecutorService p2=Executors.newSingleThreadExecutor();
        RecordingFollower.Sources ss=new RecordingFollower.Sources(){public int segmentCount(){return 1;}public boolean isRecording(){return live.get();}public SeekableMediaSource open(int x)throws IOException{return new SlowSource(shrink);}};
        Future<?> sf=p2.submit(()->{try{RecordingFollower.copy(ss,0,0,sink,()->true,n->{},v->{});return null;}catch(Exception e){throw new RuntimeException(e);}});
        Thread.sleep(80);try(RandomAccessFile raf=new RandomAccessFile(shrink,"rw")){raf.setLength(1024);}boolean rejected=false;try{sf.get(3,TimeUnit.SECONDS);}catch(ExecutionException e){rejected=String.valueOf(e.getCause()).contains("shrank")||String.valueOf(e.getCause().getCause()).contains("shrank");}live.set(false);p2.shutdownNow();check(rejected,"source replacement was not rejected");
        pass("source shrink/replacement invalidates old byte identity");
        System.out.println("P11 timeline smoke PASS: "+tests);
    }
    static final class SlowSource implements SeekableMediaSource{
        final RandomAccessFile r;SlowSource(File f)throws IOException{r=new RandomAccessFile(f,"r");}
        public long length()throws IOException{return r.length();}public void seek(long p)throws IOException{r.seek(p);}public int read(byte[] b,int o,int l)throws IOException{int n=r.read(b,o,Math.min(l,4096));try{Thread.sleep(2);}catch(InterruptedException e){Thread.currentThread().interrupt();}return n;}public String mode(){return "slow";}public String detail(){return "test";}public void close()throws IOException{r.close();}
    }
}
