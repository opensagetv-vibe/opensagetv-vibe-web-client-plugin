package org.opensagetv.webplayer;

import java.io.File;
import java.util.*;

/** One input, browser-compatible output, and optional original-video CC tap. No shell or raw user arguments. */
final class StreamCommand {
    static List<String> build(TranscoderManager.Probe p,File input,boolean concat,boolean pipe,double start,File dir,StreamPlan plan) {
        StreamOptions o=plan.options;List<String> c=new ArrayList<String>();
        add(c,p.executable,"-hide_banner","-loglevel","warning","-y","-nostats","-progress",new File(dir,"progress.log").getAbsolutePath());
        if(!pipe)add(c,"-nostdin");
        if(!pipe&&start>0)add(c,"-ss",String.format(Locale.US,"%.3f",start));
        add(c,"-thread_queue_size","1024","-fflags","+genpts+discardcorrupt","-err_detect","ignore_err","-analyzeduration","2000000","-probesize","4000000");

        boolean transcoding=!plan.copyVideo;
        boolean deinterlace=transcoding&&("on".equals(o.deinterlace)||("auto".equals(o.deinterlace)&&plan.video!=null&&plan.video.interlaced()));
        boolean scale=transcoding&&!"source".equals(o.resolution);
        boolean frameRate=transcoding&&!"source".equals(o.fps);
        boolean gpuFilterPath=transcoding&&p.hardwareDecode&&p.hardwareFilters&&gpuFilterEncoder(p.videoEncoder);
        // If the source dimensions are unknown, keep the decode hardware path but
        // use software scaling rather than risk an unintended GPU upscale.
        if(gpuFilterPath&&scale&&(plan.video==null||plan.video.height<=0))gpuFilterPath=false;
        addHardwareDecode(c,p,gpuFilterPath);
        if(transcoding&&"h264_vaapi".equals(p.videoEncoder)&&!p.hardwareDecode)
            add(c,"-vaapi_device",System.getProperty("sagetv.webplayer.vaapiDevice","/dev/dri/renderD128"));

        if(concat)add(c,"-f","concat","-safe","0");
        add(c,"-i",pipe?"pipe:0":input.getAbsolutePath(),"-map","0:v:0","-map",plan.audioMap,"-sn","-dn");

        if(plan.copyVideo)add(c,"-c:v","copy");
        else {
            String vf=gpuFilterPath?gpuVideoFilter(p.videoEncoder,o,plan,deinterlace,scale,frameRate):softwareVideoFilter(p.videoEncoder,o,plan,deinterlace,scale,frameRate);
            if(!vf.isEmpty())add(c,"-vf",vf);
            add(c,"-c:v",p.videoEncoder);
            if("libx264".equals(p.videoEncoder))add(c,"-preset","veryfast","-tune","zerolatency");
            else if("h264_nvenc".equals(p.videoEncoder))add(c,"-preset","p4","-tune","ll");
            else if("h264_qsv".equals(p.videoEncoder))add(c,"-preset","veryfast");
            else if("h264_amf".equals(p.videoEncoder))add(c,"-quality","balanced","-usage","transcoding");
            // CUDA and VAAPI do not have a general hardware FPS filter. Apply
            // output CFR after GPU decode/VPP so frames stay hardware-backed.
            if(gpuFilterPath&&frameRate&&("h264_nvenc".equals(p.videoEncoder)||"h264_vaapi".equals(p.videoEncoder)))
                add(c,"-r",fps(o.fps),"-fps_mode","cfr");
            add(c,"-b:v",o.videoKbps+"k","-maxrate",Math.round(o.videoKbps*1.25)+"k","-bufsize",(o.videoKbps*2)+"k","-bf",String.valueOf(o.bFrames));
            int rate=(int)Math.ceil("source".equals(o.fps)?sourceFps(plan.video):Double.parseDouble(o.fps));
            int gop=Math.max(1,Math.min(600,rate*o.keyFrameSeconds));
            add(c,"-g",String.valueOf(gop),"-force_key_frames","expr:gte(t,n_forced*"+o.keyFrameSeconds+")");
        }
        add(c,"-c:a",plan.audioEncoder);
        if(!plan.copyAudio){
            add(c,"-b:a",o.audioKbps+"k","-ar","48000");
            if(!"source".equals(o.audioChannels))add(c,"-ac",o.audioChannels);
            String af="aresample=async=1000:first_pts=0";
            if(o.audioOffsetMs<0)af+=",atrim=start="+String.format(Locale.US,"%.3f",-o.audioOffsetMs/1000.0)+",asetpts=PTS-STARTPTS";
            if(o.audioOffsetMs>0)af+=",adelay="+o.audioOffsetMs+":all=1";
            add(c,"-af",af);
        }
        add(c,"-max_muxing_queue_size","2048","-muxdelay","0","-muxpreload","0","-flush_packets","1","-avoid_negative_ts","make_zero");
        if(o.ts())add(c,"-f","mpegts","-mpegts_flags","+resend_headers+initial_discontinuity",new File(dir,"stream.ts").getAbsolutePath());
        else {
            add(c,"-f","hls","-hls_time","3","-hls_init_time","1","-hls_segment_type","mpegts","-hls_list_size","0","-hls_playlist_type","event");
            add(c,"-hls_flags",plan.copyVideo?"temp_file":"independent_segments+temp_file","-hls_segment_filename",new File(dir,"seg_%05d.ts").getAbsolutePath(),new File(dir,"stream.m3u8").getAbsolutePath());
        }
        if(o.captionsEnabled()) {
            // Stream-copy, not a second video encode. Original A/53 bytes bypass
            // encoder-specific caption preservation. A Java drain extracts only
            // timed CC triples; original video is never sent twice to the browser.
            add(c,"-map","0:v:0","-c:v","copy","-an","-sn","-dn","-muxdelay","0","-muxpreload","0","-flush_packets","1","-avoid_negative_ts","make_zero","-f","mpegts","pipe:1");
        }
        return c;
    }

    private static void addHardwareDecode(List<String> c,TranscoderManager.Probe p,boolean gpuFrames){
        if(!p.hardwareDecode)return;
        String d=(p.decodeAccelerator==null?"":p.decodeAccelerator).toLowerCase(Locale.ROOT);
        if(d.contains("cuda")||d.contains("nvdec")){
            add(c,"-hwaccel","cuda");if(gpuFrames)add(c,"-hwaccel_output_format","cuda");
        }else if(d.contains("qsv")){
            add(c,"-hwaccel","qsv");if(gpuFrames)add(c,"-hwaccel_output_format","qsv");
        }else if(d.contains("vaapi")){
            add(c,"-hwaccel","vaapi","-hwaccel_device",System.getProperty("sagetv.webplayer.vaapiDevice","/dev/dri/renderD128"));if(gpuFrames)add(c,"-hwaccel_output_format","vaapi");
        }else if(d.contains("d3d11"))add(c,"-hwaccel","d3d11va");
        else if(d.contains("dxva2"))add(c,"-hwaccel","dxva2");
        else if(d.contains("videotoolbox"))add(c,"-hwaccel","videotoolbox");
    }

    private static boolean gpuFilterEncoder(String encoder){return "h264_nvenc".equals(encoder)||"h264_qsv".equals(encoder)||"h264_vaapi".equals(encoder);}

    private static String gpuVideoFilter(String encoder,StreamOptions o,StreamPlan plan,boolean deinterlace,boolean scale,boolean frameRate){
        int target=targetHeight(o,plan);
        if("h264_qsv".equals(encoder)){
            List<String> opts=new ArrayList<String>();
            if(deinterlace){opts.add("deinterlace=advanced");opts.add("rate=frame");}
            if(scale&&target>0){opts.add("w=-1");opts.add("h="+target);}
            if(frameRate)opts.add("framerate="+fps(o.fps));
            opts.add("format=nv12");
            return "vpp_qsv="+join(opts,":");
        }
        List<String> chain=new ArrayList<String>();
        if("h264_nvenc".equals(encoder)){
            if(deinterlace)chain.add("yadif_cuda=mode=send_frame:parity=auto:deint="+("on".equals(o.deinterlace)?"all":"interlaced"));
            if(scale&&target>0)chain.add("scale_cuda=w=-2:h="+target+":force_original_aspect_ratio=decrease:force_divisible_by=2:format=nv12");
        }else if("h264_vaapi".equals(encoder)){
            if(deinterlace)chain.add("deinterlace_vaapi=mode=motion_adaptive:rate=frame:auto="+("on".equals(o.deinterlace)?"0":"1"));
            if(scale&&target>0)chain.add("scale_vaapi=w=-2:h="+target+":force_original_aspect_ratio=decrease:force_divisible_by=2:format=nv12");
        }
        return join(chain,",");
    }

    private static String softwareVideoFilter(String encoder,StreamOptions o,StreamPlan plan,boolean deinterlace,boolean scale,boolean frameRate){
        StringBuilder vf=new StringBuilder();
        if(deinterlace)vf.append("yadif=mode=send_frame:parity=auto:deint=").append("on".equals(o.deinterlace)?"all":"interlaced").append(',');
        if(scale)vf.append("scale=w=-2:h='min(ih,").append(o.resolution).append(")':force_divisible_by=2,");
        if(frameRate)vf.append("fps=").append(fps(o.fps)).append(',');
        boolean vaapi="h264_vaapi".equals(encoder);
        vf.append(vaapi?"format=nv12,hwupload":"libx264".equals(encoder)||"h264_videotoolbox".equals(encoder)?"format=yuv420p":"format=nv12");
        return vf.toString();
    }

    private static int targetHeight(StreamOptions o,StreamPlan plan){
        if("source".equals(o.resolution))return 0;
        int requested=Integer.parseInt(o.resolution);
        if(plan.video!=null&&plan.video.height>0&&plan.video.height<=requested)return 0;
        return requested;
    }

    static String fps(String s){if("23.976".equals(s))return "24000/1001";if("29.97".equals(s))return "30000/1001";if("59.94".equals(s))return "60000/1001";return s;}
    private static double sourceFps(MediaProbe.Track t){try{String[] r=t.rate.split("/");double v=Double.parseDouble(r[0])/(r.length==2?Double.parseDouble(r[1]):1);return Double.isFinite(v)&&v>0?v:30;}catch(Exception e){return 30;}}
    private static String join(List<String> values,String sep){StringBuilder b=new StringBuilder();for(String v:values){if(b.length()>0)b.append(sep);b.append(v);}return b.toString();}
    private static void add(List<String> c,String... values){Collections.addAll(c,values);}
}
