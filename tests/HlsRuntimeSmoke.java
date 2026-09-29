package org.opensagetv.webplayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Production feeder, FFmpeg command and playlist tests; no SageTV JVM needed. */
public final class HlsRuntimeSmoke {
    static int tests;
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void pass(String name){tests++;System.out.println("PASS "+name);}
    static byte[] bytes(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static void waitSize(ByteArrayOutputStream out,int size) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
        while(out.size()<size&&System.nanoTime()<end)Thread.sleep(20);
        require(out.size()>=size,"Follower did not produce "+size+" bytes: "+out.size());
    }
    static void run(List<String> command,File log) throws Exception {
        Process p=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log).start();
        if(!p.waitFor(25,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("FFmpeg timed out");}
        require(p.exitValue()==0,"FFmpeg failed: "+new String(Files.readAllBytes(log.toPath()),StandardCharsets.UTF_8));
    }
    public static void main(String[] args)throws Exception {
        Path temp=Files.createTempDirectory("webplayer-tests-");
        File first=temp.resolve("first.ts").toFile(),second=temp.resolve("second.ts").toFile();
        Files.write(first.toPath(),bytes("ABC"));
        AtomicInteger segments=new AtomicInteger(1);AtomicBoolean recording=new AtomicBoolean(true),active=new AtomicBoolean(true);
        AtomicLong consumed=new AtomicLong();AtomicBoolean lastRecording=new AtomicBoolean(true);
        RecordingFollower.Sources sources=new RecordingFollower.Sources(){
            public int segmentCount(){return segments.get();}
            public boolean isRecording(){return recording.get();}
            public SeekableMediaSource open(int index)throws IOException{return new DirectFileSource(index==0?first:second);}
        };
        ByteArrayOutputStream out=new ByteArrayOutputStream();ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<?> copied=pool.submit(()->{try{RecordingFollower.copy(sources,0,0,out,active::get,consumed::addAndGet,lastRecording::set);}catch(Exception e){throw new RuntimeException(e);}});
            waitSize(out,3);Thread.sleep(300);require(!copied.isDone(),"temporary EOF ended growing recording");
            Files.write(first.toPath(),bytes("DEF"),StandardOpenOption.APPEND);waitSize(out,6);
            pass("growing file EOF waits and follows appended bytes");
            Files.write(second.toPath(),bytes("GHI"));segments.set(2);waitSize(out,9);
            Files.write(second.toPath(),bytes("JK"),StandardOpenOption.APPEND);recording.set(false);
            copied.get(4,TimeUnit.SECONDS);require(out.toString("UTF-8").equals("ABCDEFGHIJK"),out.toString("UTF-8"));
            require(consumed.get()==11&&!lastRecording.get(),"completion state/count not updated");
            pass("segment rollover follows new file and drains final bytes before EOF");
            ByteArrayOutputStream seekOut=new ByteArrayOutputStream();RecordingFollower.copy(sources,0,7,seekOut,()->true,n->{},v->{});
            require(seekOut.toString("UTF-8").equals("HIJK"),"multi-segment byte skip");pass("byte-offset feeder spans completed segments");
            recording.set(true);ByteArrayOutputStream stopped=new ByteArrayOutputStream();
            Future<?> stopFuture=pool.submit(()->{try{RecordingFollower.copy(sources,0,0,stopped,active::get,n->{},v->{});}catch(Exception e){throw new RuntimeException(e);}});
            waitSize(stopped,11);active.set(false);stopFuture.get(2,TimeUnit.SECONDS);pass("cancellation terminates EOF wait promptly");
        }finally{active.set(false);pool.shutdownNow();}
        require(Math.abs(HlsSessionManager.parsePlaylistSeconds("#EXTM3U\n#EXTINF:1.25,\n#EXTINF:NaN,\n#EXTINF:Infinity,\n#EXTINF:-4,\n#EXTINF:nope,\n#EXTINF:2.75,\n")-4)<.0001,"playlist parser");
        pass("playlist duration ignores invalid/nonfinite/negative entries");
        String ffmpeg=System.getenv("TEST_FFMPEG");if(ffmpeg==null)ffmpeg="/usr/bin/ffmpeg";
        require(new File(ffmpeg).isFile(),"Set TEST_FFMPEG to run FFmpeg integration");
        System.setProperty("sagetv.webplayer.ffmpeg",ffmpeg);System.setProperty("sagetv.webplayer.videoEncoder","software");
        TranscoderManager.clearProbeCache();TranscoderManager.Probe probe=TranscoderManager.probe();require(probe==TranscoderManager.probe(),"probe not cached");
        TranscoderManager.clearProbeCache();require(probe!=TranscoderManager.probe(),"cache clear failed");pass("FFmpeg probe cache reuse and explicit invalidation");
        File input=temp.resolve("fixture.ts").toFile(),output=temp.resolve("hls").toFile();require(output.mkdir(),"mkdir");
        run(Arrays.asList(ffmpeg,"-hide_banner","-loglevel","error","-y","-f","lavfi","-i","testsrc2=size=320x180:rate=30","-f","lavfi","-i","sine=frequency=440:sample_rate=48000","-t","8","-c:v","mpeg2video","-c:a","ac3","-f","mpegts",input.toString()),temp.resolve("fixture.log").toFile());
        List<String> command=TranscoderManager.buildHlsCommand(probe,input,false,false,0.0,0,output);
        require(command.contains("independent_segments+temp_file")&&command.contains("event"),"atomic EVENT HLS contract");
        run(command,temp.resolve("transcode.log").toFile());
        String playlist=new String(Files.readAllBytes(new File(output,"stream.m3u8").toPath()),StandardCharsets.UTF_8);
        require(playlist.contains("#EXT-X-ENDLIST"),"normal EOF missing ENDLIST");
        double seconds=HlsSessionManager.parsePlaylistSeconds(playlist);require(seconds>=7.8&&seconds<8.5,"wrong duration "+seconds);
        require(output.listFiles((dir,name)->name.endsWith(".ts")).length>=3,"too few segments");
        HlsSessionManager.Session session=new HlsSessionManager.Session("test",1,0,0,0,8000,false,output);
        String json=HlsSessionManager.json(session);require(json.contains("\"playlistReady\":true")&&json.contains("\"endList\":true"),"playlist status mismatch");
        pass("real MPEG-2/AC-3 to H.264/AAC EVENT HLS transcode, duration and ENDLIST");
        List<String> pipe=TranscoderManager.buildHlsCommand(probe,null,false,true,0,0,output);
        int i=pipe.indexOf("-i");require(pipe.get(i+1).equals("pipe:0"),"pipe input");require(!pipe.subList(0,i).contains("mpegts"),"pipe must autodetect TS/PS");
        pass("pipe input does not force MPEG-TS on MPEG-PS recordings");
        // Observe the actual production monitor between first segment and EOF,
        // without any browser JSON polls doing the refresh for it. A test-only
        // readrate makes that interval deterministic; production remains unpaced.
        File longer=temp.resolve("reserve-fixture.ts").toFile(),reserveDir=temp.resolve("reserve").toFile();require(reserveDir.mkdir(),"reserve mkdir");
        run(Arrays.asList(ffmpeg,"-hide_banner","-loglevel","error","-y","-f","lavfi","-i","testsrc2=size=160x90:rate=30","-f","lavfi","-i","sine=frequency=660:sample_rate=48000","-t","240","-c:v","mpeg2video","-threads","2","-c:a","ac3","-f","mpegts",longer.toString()),temp.resolve("long-fixture.log").toFile());
        List<String> continuous=TranscoderManager.buildHlsCommand(probe,longer,false,false,0,0,reserveDir);
        require(!continuous.contains("-re")&&!continuous.contains("-readrate"),"production encoder must not be paced at 1x");
        // Exercise the actual unpaced production command. FFmpeg 8's TS
        // read-rate governor can throttle far below the requested multiplier
        // when input timestamps begin above zero, which made the former
        // test-only -readrate command both slow and version-dependent.
        HlsSessionManager.Session reserve=new HlsSessionManager.Session("reserve-test",1,0,0,0,240000,false,reserveDir);
        Process process=new ProcessBuilder(continuous).redirectOutput(temp.resolve("reserve.stdout").toFile()).start();
        reserve.process=process;reserve.running=true;reserve.launchedAt=System.currentTimeMillis();
        java.lang.reflect.Method monitor=HlsSessionManager.class.getDeclaredMethod("startMonitor",HlsSessionManager.Session.class,SageApiBridge.class,Object.class,boolean.class);
        java.lang.reflect.Method stderr=HlsSessionManager.class.getDeclaredMethod("startStderrReader",HlsSessionManager.Session.class);
        monitor.setAccessible(true);stderr.setAccessible(true);stderr.invoke(null,reserve);monitor.invoke(null,reserve,null,null,false);
        try {
            long until=System.currentTimeMillis()+10000;while(reserve.segments==0&&System.currentTimeMillis()<until)Thread.sleep(30);
            require(reserve.segments>0&&process.isAlive(),"first published segment not observed during production");
            require(reserve.monitorThread.isAlive()&&reserve.stderrThread.isAlive(),"independent producer threads not active");
            double firstSeconds=reserve.hlsSeconds;
            until=System.currentTimeMillis()+7000;while(reserve.hlsSeconds<firstSeconds+30&&System.currentTimeMillis()<until)Thread.sleep(30);
            require(reserve.hlsSeconds>=firstSeconds+30,"monitor stopped updating after startup: first="+firstSeconds+" current="+reserve.hlsSeconds+" alive="+process.isAlive()+" stderr="+reserve.stderrTail);
            pass("separate monitor/stderr threads track producer growth beyond startup without browser polling");
            require(process.waitFor(25,TimeUnit.SECONDS),"reserve transcode timeout");reserve.monitorThread.join(3000);reserve.stderrThread.join(3000);
            require(process.exitValue()==0&&reserve.endList&&!reserve.running,"final producer status/ENDLIST mismatch: "+reserve.stderrTail);
            require(reserve.hlsSeconds>=239&&reserve.segments>60,"producer failed to build reserve beyond 180 seconds");
            require(reserve.recentFillRate>1,"recent fill rate not recorded");
            String metrics=HlsSessionManager.json(reserve);require(metrics.contains("\"producerMonitor\":false")&&metrics.contains("\"lastSegmentAgeMs\":"),"thread/progress diagnostics missing");
            pass("240-second synthetic recording continues producing beyond the 180-second browser target and ends cleanly");
        } finally {reserve.stopRequested=true;if(process.isAlive())process.destroyForcibly();}
        System.out.println("HLS runtime: "+tests+" tests PASS (synthetic media; no hardware/SageTV live test)");
        try(java.util.stream.Stream<Path> paths=Files.walk(temp)){paths.sorted(Comparator.reverseOrder()).forEach(p->{try{Files.delete(p);}catch(Exception ignored){}});}
    }
}
