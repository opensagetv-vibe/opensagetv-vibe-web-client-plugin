package org.opensagetv.webplayer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Isolated synthetic native-HLS codec probe used by codec-test.html.
 *
 * No SageTV recording is read and no browser-supplied path or FFmpeg option is
 * accepted.  The original small A/B/C fixtures are retained and a second D/E
 * pair mirrors the known parameters of the supplied real-world recording:
 * MPEG-2 Main, 1280x720 progressive, 60000/1001 fps, ~6.3 Mb/s video with the
 * reported 19.4 Mb/s VBV ceiling, English AC-3 5.1 384 kb/s plus Spanish AC-3
 * stereo 192 kb/s.  The HLS path selects English audio and converts it to AAC
 * stereo, matching the WebPlayer compatibility path while D copies video and E
 * transcodes the exact same synthetic source to H.264.
 */
final class CodecHlsTestManager {
    static final String[] VARIANTS = {
        "mpeg2-copy", "h264-transcode", "h264-copy",
        "mpeg2-720p5994-copy", "h264-720p5994-transcode"
    };
    private static final Object LOCK = new Object();
    private static final long CACHE_MS = 30L * 60L * 1000L;
    private static volatile long generatedAt;
    private static volatile String lastError = "";
    private static volatile String ffmpegVersion = "";

    private CodecHlsTestManager() {}

    static final class Result {
        final boolean ready;
        final long generatedAt;
        final String error;
        final String ffmpegVersion;
        Result(boolean ready,long generatedAt,String error,String ffmpegVersion){
            this.ready=ready;this.generatedAt=generatedAt;this.error=error;this.ffmpegVersion=ffmpegVersion;
        }
        String json(){
            StringBuilder b=new StringBuilder("{");
            b.append("\"pluginVersion\":\"").append(HttpUtil.json(PluginVersion.VERSION)).append("\",");
            b.append("\"ready\":").append(ready).append(',');
            b.append("\"generatedAt\":").append(generatedAt).append(',');
            b.append("\"ageSeconds\":").append(generatedAt<=0?0:Math.max(0,(System.currentTimeMillis()-generatedAt)/1000L)).append(',');
            b.append("\"error\":\"").append(HttpUtil.json(error)).append("\",");
            b.append("\"ffmpegVersion\":\"").append(HttpUtil.json(ffmpegVersion)).append("\",");
            b.append("\"nativeTestOnly\":true,");
            b.append("\"variants\":[");
            for(int i=0;i<VARIANTS.length;i++){
                if(i>0)b.append(',');
                appendVariant(b,VARIANTS[i]);
            }
            return b.append("]}").toString();
        }
    }

    private static void appendVariant(StringBuilder b,String v){
        b.append("{\"id\":\"").append(v).append("\",\"playlistUrl\":\"codec-test-media/").append(v).append("/stream.m3u8\"");
        if("mpeg2-copy".equals(v)){
            b.append(",\"testProfile\":\"baseline\",\"videoCodec\":\"mpeg2video\",\"operation\":\"copy\",\"audioCodec\":\"aac\",\"source\":\"640x360p30 MPEG-2 + AAC stereo\"");
        }else if("h264-transcode".equals(v)){
            b.append(",\"testProfile\":\"baseline\",\"videoCodec\":\"h264\",\"operation\":\"transcode from mpeg2video\",\"audioCodec\":\"aac\",\"source\":\"640x360p30 MPEG-2 + AAC stereo\"");
        }else if("h264-copy".equals(v)){
            b.append(",\"testProfile\":\"baseline\",\"videoCodec\":\"h264\",\"operation\":\"copy\",\"audioCodec\":\"aac\",\"source\":\"640x360p30 H.264 + AAC stereo\"");
        }else if("mpeg2-720p5994-copy".equals(v)){
            b.append(",\"testProfile\":\"real-file-parameters\",\"videoCodec\":\"mpeg2video\",\"operation\":\"copy video / AC-3 to AAC audio\",\"audioCodec\":\"aac\",\"sourceVideoCodec\":\"mpeg2video\",\"sourceVideoProfile\":\"Main\",\"sourceWidth\":1280,\"sourceHeight\":720,\"sourceFps\":\"60000/1001\",\"sourceScan\":\"progressive\",\"sourceVideoKbps\":6300,\"sourceAudioCodec\":\"ac3\",\"sourceAudioChannels\":6,\"sourceAudioKbps\":384");
        }else{
            b.append(",\"testProfile\":\"real-file-parameters\",\"videoCodec\":\"h264\",\"operation\":\"transcode video to H.264 / AC-3 to AAC audio\",\"audioCodec\":\"aac\",\"sourceVideoCodec\":\"mpeg2video\",\"sourceVideoProfile\":\"Main\",\"sourceWidth\":1280,\"sourceHeight\":720,\"sourceFps\":\"60000/1001\",\"sourceScan\":\"progressive\",\"sourceVideoKbps\":6300,\"sourceAudioCodec\":\"ac3\",\"sourceAudioChannels\":6,\"sourceAudioKbps\":384");
        }
        b.append('}');
    }

    static Result status(){return new Result(allOutputsPresent(),generatedAt,lastError,ffmpegVersion);}

    static Result generate(boolean force) throws IOException {
        synchronized(LOCK){
            if(!force && allOutputsPresent() && generatedAt>0 && System.currentTimeMillis()-generatedAt<CACHE_MS) return status();
            File ffmpeg=TranscoderManager.findExecutable();
            if(ffmpeg==null){lastError="OpenSageTV Vibe FFmpeg Plugin transcoder is unavailable";throw new IOException(lastError);}
            generatedAt=0;lastError="";ffmpegVersion=probeVersion(ffmpeg);
            File root=root();resetRoot(root);
            File sourceDir=new File(root,"source");mkdir(sourceDir);
            for(String v:VARIANTS)mkdir(new File(root,v));
            File mpeg2Source=new File(sourceDir,"mpeg2-aac.ts");
            File h264Source=new File(sourceDir,"h264-aac.ts");
            File realSource=new File(sourceDir,"mpeg2-720p5994-ac3.ts");
            try{
                run(ffmpeg, Arrays.asList(
                    "-hide_banner","-loglevel","error","-y","-nostdin",
                    "-f","lavfi","-i","testsrc2=size=640x360:rate=30",
                    "-f","lavfi","-i","sine=frequency=1000:sample_rate=48000",
                    "-t","8","-map","0:v:0","-map","1:a:0",
                    "-c:v","mpeg2video","-g","30","-bf","0","-b:v","2M","-pix_fmt","yuv420p",
                    "-c:a","aac","-b:a","128k","-ar","48000","-ac","2","-f","mpegts",
                    mpeg2Source.getAbsolutePath()));

                runBaselineHls(ffmpeg,mpeg2Source,new File(root,"mpeg2-copy"),true);
                runBaselineHls(ffmpeg,mpeg2Source,new File(root,"h264-transcode"),false);

                run(ffmpeg, Arrays.asList(
                    "-hide_banner","-loglevel","error","-y","-nostdin","-i",mpeg2Source.getAbsolutePath(),
                    "-map","0:v:0","-map","0:a:0","-c:v","libx264","-preset","ultrafast","-profile:v","main",
                    "-pix_fmt","yuv420p","-g","30","-keyint_min","30","-sc_threshold","0","-c:a","copy",
                    "-f","mpegts",h264Source.getAbsolutePath()));
                runBaselineHls(ffmpeg,h264Source,new File(root,"h264-copy"),true);

                // Real-file parameter fixture.  Known input characteristics from the
                // supplied ffprobe output are reproduced; actual programme content,
                // captions and exact broadcast GOP decisions are intentionally synthetic.
                run(ffmpeg, Arrays.asList(
                    "-hide_banner","-loglevel","error","-y","-nostdin",
                    "-f","lavfi","-i","testsrc2=size=1280x720:rate=60000/1001",
                    "-f","lavfi","-i","anullsrc=channel_layout=5.1:sample_rate=48000",
                    "-f","lavfi","-i","anullsrc=channel_layout=stereo:sample_rate=48000",
                    "-t","8","-map","0:v:0","-map","1:a:0","-map","2:a:0",
                    "-c:v","mpeg2video","-profile:v","main","-pix_fmt","yuv420p","-g","30","-bf","2",
                    "-b:v","6300k","-maxrate","19400k","-bufsize","7995392",
                    "-c:a","ac3","-b:a:0","384k","-ac:a:0","6","-b:a:1","192k","-ac:a:1","2",
                    "-metadata:s:a:0","language=eng","-metadata:s:a:1","language=spa","-f","mpegts",
                    realSource.getAbsolutePath()));
                runRealProfileHls(ffmpeg,realSource,new File(root,"mpeg2-720p5994-copy"),true);
                runRealProfileHls(ffmpeg,realSource,new File(root,"h264-720p5994-transcode"),false);

                if(!allOutputsPresent())throw new IOException("FFmpeg completed but one or more HLS test outputs are missing");
                generatedAt=System.currentTimeMillis();return status();
            }catch(IOException e){lastError=compact(e.getMessage());throw e;}
        }
    }

    static File mediaFile(String pathInfo) throws IOException {
        if(pathInfo==null)throw new IOException("Missing codec-test media path");
        String p=pathInfo.replace('\\','/').replaceFirst("^/+","");String[] parts=p.split("/");
        if(parts.length!=2 || !isVariant(parts[0]) || !isAllowedFile(parts[1]))throw new IOException("Invalid codec-test media path");
        File root=root().getCanonicalFile();File f=new File(new File(root,parts[0]),parts[1]).getCanonicalFile();
        if(!f.getPath().startsWith(root.getPath()+File.separator))throw new IOException("Invalid codec-test media path");return f;
    }

    private static void runBaselineHls(File ffmpeg,File input,File outputDir,boolean copyVideo)throws IOException{
        List<String> a=new ArrayList<String>();
        a.addAll(Arrays.asList("-hide_banner","-loglevel","error","-y","-nostdin","-i",input.getAbsolutePath(),"-map","0:v:0","-map","0:a:0"));
        if(copyVideo)a.addAll(Arrays.asList("-c:v","copy"));
        else a.addAll(Arrays.asList("-c:v","libx264","-preset","ultrafast","-profile:v","main","-pix_fmt","yuv420p","-g","30","-keyint_min","30","-sc_threshold","0"));
        a.addAll(Arrays.asList("-c:a","copy"));addHlsOutput(a,outputDir);run(ffmpeg,a);
    }

    private static void runRealProfileHls(File ffmpeg,File input,File outputDir,boolean copyVideo)throws IOException{
        List<String> a=new ArrayList<String>();
        a.addAll(Arrays.asList("-hide_banner","-loglevel","error","-y","-nostdin","-i",input.getAbsolutePath(),"-map","0:v:0","-map","0:a:0"));
        if(copyVideo)a.addAll(Arrays.asList("-c:v","copy"));
        else a.addAll(Arrays.asList(
            "-vf","format=yuv420p","-c:v","libx264","-preset","veryfast","-tune","zerolatency","-profile:v","main",
            "-b:v","8000k","-maxrate","10000k","-bufsize","16000k","-bf","0","-g","60",
            "-force_key_frames","expr:gte(t,n_forced*1)"));
        // This mirrors the normal browser-compatibility audio path: choose the
        // first (English) AC-3 track and convert to 48 kHz stereo AAC.
        a.addAll(Arrays.asList("-c:a","aac","-b:a","192k","-ar","48000","-ac","2","-af","aresample=async=1000:first_pts=0"));
        addHlsOutput(a,outputDir);run(ffmpeg,a);
    }

    private static void addHlsOutput(List<String> a,File outputDir){
        a.addAll(Arrays.asList("-f","hls","-hls_time","1","-hls_list_size","0","-hls_playlist_type","vod","-hls_segment_type","mpegts","-hls_flags","independent_segments",
                "-hls_segment_filename",new File(outputDir,"seg_%03d.ts").getAbsolutePath(),new File(outputDir,"stream.m3u8").getAbsolutePath()));
    }

    private static void run(File ffmpeg,List<String> args)throws IOException{
        List<String> cmd=new ArrayList<String>();cmd.add(ffmpeg.getAbsolutePath());cmd.addAll(args);
        ProcessCapture.Result r=ProcessCapture.run(cmd,60,512*1024);
        if(r.exit!=0)throw new IOException("FFmpeg codec test failed: "+compact(r.text));
    }

    private static String probeVersion(File ffmpeg){
        try{ProcessCapture.Result r=ProcessCapture.run(Arrays.asList(ffmpeg.getAbsolutePath(),"-hide_banner","-version"),8,16*1024);if(r.exit!=0)return "FFmpeg available";String t=r.text==null?"":r.text.trim();int n=t.indexOf('\n');return compact(n>=0?t.substring(0,n):t);}catch(IOException e){return "FFmpeg available";}
    }

    private static boolean allOutputsPresent(){try{File root=root();for(String v:VARIANTS){File p=new File(new File(root,v),"stream.m3u8"),s=new File(new File(root,v),"seg_000.ts");if(!p.isFile()||p.length()<32||!s.isFile()||s.length()<188)return false;}return true;}catch(RuntimeException e){return false;}}
    private static File root(){return new File(System.getProperty("java.io.tmpdir"),"sagetv-webplayer-codec-test");}
    private static void mkdir(File f)throws IOException{if(!f.isDirectory()&&!f.mkdirs())throw new IOException("Could not create codec-test directory");}
    private static void resetRoot(File root)throws IOException{File canonical=root.getCanonicalFile();String tmp=new File(System.getProperty("java.io.tmpdir")).getCanonicalPath();if(!canonical.getPath().startsWith(tmp+File.separator)||!"sagetv-webplayer-codec-test".equals(canonical.getName()))throw new IOException("Unsafe codec-test temp directory");deleteTree(canonical);mkdir(canonical);}
    private static void deleteTree(File f)throws IOException{if(!f.exists())return;if(Files.isSymbolicLink(f.toPath())){Files.delete(f.toPath());return;}if(f.isDirectory()){File[] kids=f.listFiles();if(kids!=null)for(File k:kids)deleteTree(k);}if(!f.delete())throw new IOException("Could not reset codec-test temp data");}
    private static boolean isVariant(String v){for(String x:VARIANTS)if(x.equals(v))return true;return false;}
    private static boolean isAllowedFile(String f){return "stream.m3u8".equals(f)||f.matches("seg_[0-9]{3}\\.ts");}
    private static String compact(String s){if(s==null)return "";s=s.replace('\r',' ').replace('\n',' ').trim();return s.length()>500?s.substring(0,500):s;}
}
