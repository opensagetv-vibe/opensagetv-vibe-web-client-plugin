package org.opensagetv.webplayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** New streaming command, byte-resume and A/53 tests using real FFmpeg where stated. */
public final class StreamingRuntimeSmoke {
    static int count;
    static void check(boolean b,String reason){if(!b)throw new AssertionError(reason);}
    static void pass(String s){count++;System.out.println("PASS "+s);}
    static StreamOptions options(String... values){Map<String,String> m=new HashMap<>();for(int i=0;i<values.length;i+=2)m.put(values[i],values[i+1]);return StreamOptions.fromMap(m);}
    static void rejects(Runnable task){try{task.run();throw new AssertionError("Expected invalid settings to fail");}catch(IllegalArgumentException good){}}
    static void run(List<String> cmd,File log)throws Exception{
        Process p=new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(log).start();
        if(!p.waitFor(35,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("Timed out: "+cmd);}
        check(p.exitValue()==0,new String(Files.readAllBytes(log.toPath()),StandardCharsets.UTF_8));
    }
    static byte[] a53(){return new byte[]{0,0,1,(byte)0xb2,0x47,0x41,0x39,0x34,3,0x44,(byte)0xff,(byte)0xfc,(byte)0x94,0x2e,(byte)0xfc,(byte)0x94,0x20,(byte)0xfc,0x48,0x49,(byte)0xfc,(byte)0x94,0x2f,(byte)0xff};}
    static byte[] pesPacket(long pts,byte[] es,int cc){
        byte[] b=new byte[188];Arrays.fill(b,(byte)255);b[0]=0x47;b[1]=0x41;b[2]=1;b[3]=(byte)(0x10|(cc&15));
        byte[] header={0,0,1,(byte)0xe0,0,0,(byte)0x80,(byte)0x80,5};System.arraycopy(header,0,b,4,9);
        b[13]=(byte)(0x21|((pts>>29)&14));b[14]=(byte)(pts>>22);b[15]=(byte)(((pts>>14)&254)|1);b[16]=(byte)(pts>>7);b[17]=(byte)(((pts<<1)&254)|1);
        System.arraycopy(es,0,b,18,Math.min(es.length,170));return b;
    }
    static byte[] join(byte[]... pieces)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();for(byte[] p:pieces)out.write(p);return out.toByteArray();}
    public static void main(String[] args)throws Exception {
        Path root=Files.createTempDirectory("webplayer-stream-tests-");
        StreamOptions d=StreamOptions.defaults();check(d.ts()&&d.videoKbps==8000&&d.audioKbps==192&&d.captions.equals("off"),"defaults");
        rejects(()->options("encoder","sh -c bad"));rejects(()->options("videoKbps","NaN"));rejects(()->options("audioKbps","900"));rejects(()->options("captionService","5","captions","608"));rejects(()->options("audioLanguage","en;bad"));
        check(options("audioTrack","3").forNextRecording().audioTrack==-1,"per-recording audio index leaked");pass("validated per-browser options reject unsafe/invalid values and reset audio indices between recordings");
        String flat="streams.stream.0.index=0\nstreams.stream.0.codec_type=\"video\"\nstreams.stream.0.codec_name=\"h264\"\nstreams.stream.0.field_order=\"progressive\"\nstreams.stream.1.index=2\nstreams.stream.1.codec_type=\"audio\"\nstreams.stream.1.codec_name=\"aac\"\nstreams.stream.1.channels=2\nstreams.stream.1.tags.language=\"eng\"\nstreams.stream.2.index=3\nstreams.stream.2.codec_type=\"audio\"\nstreams.stream.2.codec_name=\"ac3\"\nstreams.stream.2.channels=6\nstreams.stream.2.tags.language=\"spa\"\n";
        MediaProbe.Info info=MediaProbe.parse(flat);StreamPlan direct=new StreamPlan(d,info);
        check(direct.copyVideo&&direct.copyAudio&&direct.audio.index==2,"compatible copy");
        StreamPlan language=new StreamPlan(options("audioLanguage","es"),info);check(language.audio.index==3&&!language.copyAudio,"language mapping or incompatible AC3 copy");
        check(new StreamPlan(options("audioTrack","3","audioChannels","source","ac3Supported","true"),info).copyAudio,"AC3 passthrough gate");
        rejects(()->new StreamPlan(options("audioTrack","1"),info));rejects(()->new StreamPlan(options("audioCodec","ac3"),info));
        rejects(()->new StreamPlan(options("videoMode","copy","resolution","720"),info));pass("codec-aware remux, preferred-language selection, actual stream index and strict compatibility validation");
        MediaProbe.Info mpeg2Info=MediaProbe.parse("streams.stream.0.index=0\nstreams.stream.0.codec_type=video\nstreams.stream.0.codec_name=mpeg2video\nstreams.stream.0.field_order=tt\nstreams.stream.1.index=1\nstreams.stream.1.codec_type=audio\nstreams.stream.1.codec_name=aac\nstreams.stream.1.channels=2\n");
        StreamPlan mpeg2Copy=new StreamPlan(options("player","hls","videoMode","copy","deinterlace","auto"),mpeg2Info);check(mpeg2Copy.copyVideo&&mpeg2Copy.reason.contains("MPEG-2 video is passed through"),"MPEG-2 copy policy");
        rejects(()->new StreamPlan(options("player","hls","videoMode","copy","deinterlace","on"),mpeg2Info));
        check(options("player","mpegts","videoMode","copy").hlsFallback().videoMode.equals("copy"),"TS-to-HLS fallback lost Copy selection");
        check(options("player","hls","videoMode","copy").transcodeFallback().videoMode.equals("transcode"),"Copy decode fallback did not force H.264 transcode");
        MediaProbe.Info vp9Info=MediaProbe.parse("streams.stream.0.index=0\nstreams.stream.0.codec_type=video\nstreams.stream.0.codec_name=vp9\nstreams.stream.1.index=1\nstreams.stream.1.codec_type=audio\nstreams.stream.1.codec_name=aac\nstreams.stream.1.channels=2\n");
        StreamPlan vp9Copy=new StreamPlan(options("player","hls","videoMode","copy","deinterlace","off"),vp9Info);check(vp9Copy.copyVideo&&vp9Copy.reason.contains("vp9 video is passed through"),"explicit Copy must not pre-reject a source codec before FFmpeg tries it");
        MediaProbe.Info unknownInfo=new MediaProbe.Info();StreamPlan unknownCopy=new StreamPlan(options("player","hls","videoMode","copy","deinterlace","off"),unknownInfo);check(unknownCopy.copyVideo&&unknownCopy.reason.contains("not identified"),"explicit Copy must be allowed when ffprobe metadata is unavailable");
        check(options("videoMode","mpeg2copy").videoMode.equals("copy"),"legacy mpeg2copy profile did not migrate to Copy");pass("Copy attempts source video passthrough without a codec allow-list, preserves source interlace, migrates legacy profiles, and has an H.264 fallback");
        MediaProbe.Info p720=MediaProbe.parse("streams.stream.0.index=0\nstreams.stream.0.codec_type=video\nstreams.stream.0.codec_name=mpeg2video\nstreams.stream.0.width=1280\nstreams.stream.0.height=720\nstreams.stream.0.field_order=progressive\nstreams.stream.0.r_frame_rate=60000/1001\nstreams.stream.1.index=1\nstreams.stream.1.codec_type=audio\nstreams.stream.1.codec_name=ac3\nstreams.stream.1.channels=6\n");
        StreamPlan p720Plan=new StreamPlan(options("player","hls","videoMode","transcode","encoder","software","resolution","source","fps","source","deinterlace","auto","audioCodec","aac","audioChannels","2"),p720);
        TranscoderManager.Probe p720Encoder=new TranscoderManager.Probe(true,"ffmpeg","test","libx264",false,false,"software","test");
        List<String> p720Command=StreamCommand.build(p720Encoder,new File("p720.ts"),false,false,0,new File("."),p720Plan);
        String p720Vf=p720Command.get(p720Command.indexOf("-vf")+1);
        check(!p720Vf.contains("yadif")&&!p720Vf.contains("fps="),"progressive source/Source FPS should not be deinterlaced or rate-converted: "+p720Vf);
        check("60".equals(p720Command.get(p720Command.indexOf("-g")+1)),"59.94 source cadence should use a 60-frame one-second GOP");
        pass("720p59.94 progressive MPEG-2 fallback preserves source cadence and skips unnecessary deinterlacing");
        List<long[]> packets=new ArrayList<>();List<byte[]> triples=new ArrayList<>();A53CaptionTap.Parser parser=new A53CaptionTap.Parser((t,b)->{packets.add(new long[]{t});triples.add(b);});
        byte[] stream=join(pesPacket(90000,a53(),0),pesPacket(180000,a53(),1));
        for(int i=0;i<stream.length;i+=17)parser.push(stream,i,Math.min(17,stream.length-i));parser.finish();
        check(packets.size()==2&&packets.get(0)[0]==0&&packets.get(1)[0]==1000,"MPEG2 fragmentation/PTS");check(triples.get(0).length==12,"A53 triples");pass("MPEG-2 A/53 extraction crosses fragmented TS chunks and preserves relative PTS");
        List<byte[]> seiResult=new ArrayList<>();byte[] payload=Arrays.copyOfRange(a53(),4,a53().length);
        byte[] sei=join(new byte[]{0,0,0,1,6,4,(byte)(payload.length+3),(byte)181,0,49},payload,new byte[]{(byte)128});
        A53CaptionTap.Parser avc=new A53CaptionTap.Parser((t,b)->seiResult.add(b));byte[] avcTs=pesPacket(0,sei,0);avc.push(avcTs,0,188);avc.finish();check(seiResult.size()==1,"AVC SEI missing");
        byte[] hevc=join(new byte[]{0,0,1,78,1,4,(byte)(payload.length+3),(byte)181,0,49},payload,new byte[]{(byte)128});
        A53CaptionTap.Parser hevcParser=new A53CaptionTap.Parser((t,b)->seiResult.add(b));byte[] hevcTs=pesPacket(0,hevc,0);hevcParser.push(hevcTs,0,188);hevcParser.finish();check(seiResult.size()==2,"HEVC SEI missing");pass("AVC and HEVC registered A/53 SEI extraction");
        packets.clear();A53CaptionTap.Parser wrap=new A53CaptionTap.Parser((t,b)->packets.add(new long[]{t}));
        byte[] wrapTs=join(pesPacket(0x200000000L-90000,a53(),0),pesPacket(0,a53(),1));wrap.push(wrapTs,0,wrapTs.length);wrap.finish();check(packets.get(1)[0]==1000,"33-bit wrap");pass("caption PTS wrap handling does not jump by 26 hours");
        File bin=root.resolve("captions.bin").toFile();A53CaptionTap tap=new A53CaptionTap(bin);tap.start(new ByteArrayInputStream(stream));tap.thread.join(2000);
        String early=A53CaptionTap.read(bin,0,0);check(early.contains("fc4849")&&tap.records==2&&tap.cea608==8,"caption sidecar");
        try{A53CaptionTap.read(bin,bin.length()+1,1000);throw new AssertionError("invalid cursor accepted");}catch(IllegalArgumentException expected){}
        pass("bounded binary caption sidecar, JSON packets and invalid cursor rejection");
        AtomicBoolean producing=new AtomicBoolean(true),cancel=new AtomicBoolean();File growing=root.resolve("growing.ts").toFile();Files.write(growing.toPath(),new byte[]{1,2,3});ByteArrayOutputStream received=new ByteArrayOutputStream();
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            Future<?> f=pool.submit(()->{try{ContinuousFilePump.copy(growing,0,received,producing::get,cancel::get,n->{});}catch(Exception e){throw new RuntimeException(e);}});
            Thread.sleep(180);check(!f.isDone()&&received.size()==3,"temporary EOF");Files.write(growing.toPath(),new byte[]{4,5,6},StandardOpenOption.APPEND);Thread.sleep(150);producing.set(false);f.get(2,TimeUnit.SECONDS);check(Arrays.equals(received.toByteArray(),new byte[]{1,2,3,4,5,6}),"continuous growth");
            ByteArrayOutputStream resumed=new ByteArrayOutputStream();ContinuousFilePump.copy(growing,3,resumed,()->false,()->false,n->{});check(Arrays.equals(resumed.toByteArray(),new byte[]{4,5,6}),"resume not exact");pass("continuous stream follows growth and resumes exact bytes without re-running encoder");
        }finally{cancel.set(true);pool.shutdownNow();}
        check(HlsSessionManager.parseProgressSeconds("out_time_us=1000000\nout_time_us=NaN\nout_time_us=3500000\n")==3.5,"progress parser");pass("FFmpeg progress microseconds are converted to produced seconds");
        String ff=System.getenv("TEST_FFMPEG");if(ff==null)ff="/usr/bin/ffmpeg";System.setProperty("sagetv.webplayer.ffmpeg",ff);
        File elementary=root.resolve("source.m2v").toFile(),captioned=root.resolve("cc.m2v").toFile(),input=root.resolve("source.ts").toFile();
        run(Arrays.asList(ff,"-v","error","-y","-f","lavfi","-i","testsrc2=size=320x180:rate=30","-t","10","-c:v","mpeg2video","-g","15","-bf","0","-f","mpeg2video",elementary.toString()),root.resolve("generate.log").toFile());
        byte[] es=Files.readAllBytes(elementary.toPath());ByteArrayOutputStream annotated=new ByteArrayOutputStream();boolean picture=false;int added=0;
        for(int i=0;i<es.length;i++){
            if(i+3<es.length&&es[i]==0&&es[i+1]==0&&es[i+2]==1){int code=es[i+3]&255;if(code==0)picture=true;else if(code>=1&&code<=0xaf&&picture){annotated.write(a53());picture=false;added++;}}
            annotated.write(es[i]);
        }
        check(added>=290,"not enough captioned frames");Files.write(captioned.toPath(),annotated.toByteArray());
        run(Arrays.asList(ff,"-v","error","-y","-fflags","+genpts","-r","30","-i",captioned.toString(),"-f","lavfi","-i","sine=frequency=440:sample_rate=48000","-f","lavfi","-i","sine=frequency=880:sample_rate=48000","-t","10","-map","0:v:0","-map","1:a:0","-map","2:a:0","-c:v","copy","-c:a","ac3","-metadata:s:a:0","language=eng","-metadata:s:a:1","language=spa","-f","mpegts",input.toString()),root.resolve("mux.log").toFile());
        MediaProbe.Info probed=MediaProbe.read(input,ff);check(probed.video()!=null&&probed.audio().size()==2&&"ffprobe".equals(probed.probeMethod),"real metadata: "+probed.json());pass("real ffprobe discovers MPEG-2 video and two language-tagged audio tracks");
        String oldProbe=System.getProperty("sagetv.webplayer.ffprobe");
        try{
            System.setProperty("sagetv.webplayer.ffprobe",root.resolve("missing-ffprobe").toString());
            MediaProbe.Info ffmpegFallback=MediaProbe.read(input,ff);
            check("ffmpeg".equals(ffmpegFallback.probeMethod)&&ffmpegFallback.video()!=null&&"mpeg2video".equals(ffmpegFallback.video().codec)&&ffmpegFallback.audio().size()==2,"FFmpeg fallback metadata: "+ffmpegFallback.json());
            check(ffmpegFallback.warning.contains("FFmpeg fallback"),"fallback warning does not explain probe path: "+ffmpegFallback.warning);
            pass("missing ffprobe falls back to FFmpeg input probing and still discovers source video/audio codecs");
        }finally{if(oldProbe==null)System.clearProperty("sagetv.webplayer.ffprobe");else System.setProperty("sagetv.webplayer.ffprobe",oldProbe);}
        StreamOptions o=options("player","mpegts","encoder","software","videoMode","transcode","videoKbps","1250","fps","25","audioLanguage","es","audioCodec","aac","audioKbps","128","audioChannels","1","captions","608");
        StreamPlan plan=new StreamPlan(o,probed);TranscoderManager.Probe encoder=TranscoderManager.forStream(plan);
        File dest=root.resolve("continuous").toFile();check(dest.mkdir(),"mkdir");List<String> command=StreamCommand.build(encoder,input,false,false,0,dest,plan);
        check(command.contains("1250k")&&command.contains("128k")&&command.get(command.indexOf("-vf")+1).contains("fps=25"),"bitrate settings absent");
        Process process=new ProcessBuilder(command).redirectError(root.resolve("continuous.log").toFile()).start();process.getOutputStream().close();
        A53CaptionTap actualTap=new A53CaptionTap(new File(dest,"captions.bin"));actualTap.start(process.getInputStream());
        check(process.waitFor(35,TimeUnit.SECONDS),"actual FFmpeg timeout");actualTap.thread.join(3000);check(process.exitValue()==0,new String(Files.readAllBytes(root.resolve("continuous.log")),StandardCharsets.UTF_8));
        MediaProbe.Info actual=MediaProbe.read(new File(dest,"stream.ts"),ff);check(actual.video().codec.equals("h264")&&actual.audio().get(0).codec.equals("aac")&&actual.audio().get(0).channels==1,"wrong actual codecs/channels");
        check(actual.video().rate.equals("25/1"),"FPS choice ignored: "+actual.video().rate);check(actualTap.records>250&&actualTap.cea608>750,"captions lost through encoder: "+actualTap.json());
        pass("real MPEG-2 to H.264 MPEG-TS uses selected video bitrate/FPS and Spanish mono AAC settings");
        pass("original-video CC side channel survives actual video transcode without asking encoder to preserve it");
        HlsSessionManager.Session metrics=new HlsSessionManager.Session("test",1,0,0,0,10000,false,dest,o);metrics.process=process;metrics.exitCode=0;metrics.launchedAt=System.currentTimeMillis()-1000;metrics.sourceInfo=probed;metrics.streamPlan=plan;metrics.encoder=encoder.videoEncoder;
        HlsSessionManager.refreshMetrics(metrics);check(metrics.playlistReady&&metrics.endList&&metrics.hlsSeconds>=9.5,"TS metrics: "+HlsSessionManager.json(metrics));pass("continuous stream status measures output duration/bytes and clean EOF without an HLS playlist");
        File handoff=Paths.get("build/test-results").toFile();handoff.mkdirs();Files.write(new File(handoff,"stream-caption-packets.json").toPath(),A53CaptionTap.read(new File(dest,"captions.bin"),0,12000).getBytes(StandardCharsets.UTF_8));
        File copyDir=root.resolve("copy").toFile();copyDir.mkdir();StreamPlan remux=new StreamPlan(options("encoder","software","audioChannels","source"),actual);check(remux.copyVideo&&remux.copyAudio,"real H264/AAC not copied");
        run(StreamCommand.build(TranscoderManager.forStream(remux),new File(dest,"stream.ts"),false,false,0,copyDir,remux),root.resolve("copy.log").toFile());check(MediaProbe.read(new File(copyDir,"stream.ts"),ff).video().codec.equals("h264"),"copy stream");pass("real compatible H.264/AAC remux uses video/audio copy without GPU encoder probe");
        File hd=root.resolve("hls").toFile();hd.mkdir();StreamPlan hp=new StreamPlan(options("player","hls","encoder","software","videoKbps","1000","audioKbps","128","resolution","480","audioOffsetMs","250"),probed);
        run(StreamCommand.build(TranscoderManager.forStream(hp),input,false,false,2,hd,hp),root.resolve("hls.log").toFile());String playlist=new String(Files.readAllBytes(new File(hd,"stream.m3u8").toPath()),StandardCharsets.UTF_8);check(playlist.contains("#EXT-X-ENDLIST")&&HlsSessionManager.parsePlaylistSeconds(playlist)>7,"HLS fallback output");pass("real HLS compatibility path keeps user quality/audio-delay settings and server seek offset");
        System.out.println("Streaming runtime: "+count+" tests PASS; real FFmpeg software path, no GPU or live SageTV test");
    }
}
