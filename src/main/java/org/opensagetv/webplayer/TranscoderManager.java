package org.opensagetv.webplayer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Optional external compatibility fallback. The original/Wasm path never requires it. */
final class TranscoderManager {
    static final class Probe {
        final boolean available;
        final String executable;
        final String source;
        final String videoEncoder;
        final boolean hardware;
        final boolean hardwareDecode;
        final boolean hardwareFilters;
        final String decodeAccelerator;
        final String accelerator;
        final String detail;

        Probe(boolean available, String executable, String source, String videoEncoder,
              boolean hardware, boolean hardwareDecode, String accelerator, String detail) {
            this(available, executable, source, videoEncoder, hardware, hardwareDecode, false,
                    hardwareDecode ? accelerator : "", accelerator, detail);
        }

        Probe(boolean available, String executable, String source, String videoEncoder,
              boolean hardware, boolean hardwareDecode, boolean hardwareFilters,
              String decodeAccelerator, String accelerator, String detail) {
            this.available = available;
            this.executable = executable;
            this.source = source;
            this.videoEncoder = videoEncoder;
            this.hardware = hardware;
            this.hardwareDecode = hardwareDecode;
            this.hardwareFilters = hardwareFilters;
            this.decodeAccelerator = decodeAccelerator == null ? "" : decodeAccelerator;
            this.accelerator = accelerator;
            this.detail = detail;
        }
    }

    private TranscoderManager() {}

    private static Probe cachedProbe;
    private static String cachedProbeKey = "";
    private static long cachedProbeAt;

    /** Hardware initialization probes are expensive. Reuse them across seeks and
     * recordings, invalidating on executable/config changes or after five minutes. */
    static synchronized Probe probe() {
        File executable = findExecutable();
        String key = (executable == null ? "missing" : executable.getAbsolutePath() + ":" + executable.length() + ":" + executable.lastModified())
                + ":" + firstNonEmpty(System.getProperty("sagetv.webplayer.videoEncoder"), System.getenv("SAGETV_WEBPLAYER_VIDEO_ENCODER"))
                + ":" + firstNonEmpty(System.getProperty("sagetv.webplayer.fullHardware"), System.getenv("SAGETV_WEBPLAYER_FULL_HW"));
        long now = System.currentTimeMillis();
        if (cachedProbe != null && cachedProbe.available && key.equals(cachedProbeKey) && now - cachedProbeAt < 300000L) return cachedProbe;
        Probe result = probeUncached();
        cachedProbe = result; cachedProbeKey = key; cachedProbeAt = System.currentTimeMillis();
        return result;
    }

    static synchronized void clearProbeCache() { cachedProbe = null; cachedProbeKey = ""; perEncoder.clear();perEncoderAt.clear(); }

    private static final java.util.Map<String,Probe> perEncoder = new java.util.HashMap<String,Probe>();
    private static final java.util.Map<String,Long> perEncoderAt = new java.util.HashMap<String,Long>();
    static synchronized Probe forStream(StreamPlan plan) throws IOException {
        File exe=findExecutable();if(exe==null)throw new IOException("OpenSageTV Vibe FFmpeg Plugin transcoder is unavailable");
        if(plan.copyVideo)return new Probe(true,exe.getAbsolutePath(),"stream copy","copy",false,false,"none","Video copied without re-encoding");
        String requested=plan.options.encoderName();
        String key=exe.getAbsolutePath()+":"+exe.length()+":"+exe.lastModified()+":"+requested;
        Probe p=perEncoder.get(key);Long at=perEncoderAt.get(key);
        if(p==null||at==null||System.currentTimeMillis()-at>300000L){
            p=probeRequested(requested);if(perEncoder.size()>16){perEncoder.clear();perEncoderAt.clear();}perEncoder.put(key,p);perEncoderAt.put(key,System.currentTimeMillis());
        }
        if(!p.available)throw new IOException(p.detail);
        if(!plan.options.allowFallback&&!"auto".equals(requested)&&!requested.equals(p.videoEncoder))throw new IOException(p.detail);
        if(!plan.options.allowFallback&&"auto".equals(requested)&&!p.hardware)throw new IOException("Hardware required but no hardware encoder initialized; select Software or allow fallback");
        return new Probe(p.available,p.executable,p.source,p.videoEncoder,p.hardware,p.hardwareDecode,p.hardwareFilters,
            p.decodeAccelerator,p.accelerator,p.detail);
    }

    /** Resolve the browser-selected encoder for DVD-Video. DVD is always an
     * H.264/AAC transcode, but it should honor the same Auto/QSV/NVENC/AMF/
     * VAAPI/VideoToolbox/Software choice as ordinary recordings. */
    static synchronized Probe forDvd(StreamOptions options) throws IOException {
        File exe=findExecutable();if(exe==null)throw new IOException("OpenSageTV Vibe FFmpeg Plugin transcoder is unavailable");
        StreamOptions o=options==null?StreamOptions.defaults():options;
        String requested=o.encoderName();
        String key=exe.getAbsolutePath()+":"+exe.length()+":"+exe.lastModified()+":dvd:"+requested;
        Probe p=perEncoder.get(key);Long at=perEncoderAt.get(key);
        if(p==null||at==null||System.currentTimeMillis()-at>300000L){
            p=probeRequested(requested);if(perEncoder.size()>16){perEncoder.clear();perEncoderAt.clear();}perEncoder.put(key,p);perEncoderAt.put(key,System.currentTimeMillis());
        }
        if(!p.available)throw new IOException(p.detail);
        if(!o.allowFallback&&!"auto".equals(requested)&&!requested.equals(p.videoEncoder))throw new IOException(p.detail);
        if(!o.allowFallback&&"auto".equals(requested)&&!p.hardware)throw new IOException("Hardware required but no hardware encoder initialized; select Software or allow fallback");
        return new Probe(p.available,p.executable,p.source,p.videoEncoder,p.hardware,p.hardwareDecode,p.hardwareFilters,
            p.decodeAccelerator,p.accelerator,p.detail);
    }

    private static Probe probeUncached() {
        String requested=firstNonEmpty(System.getProperty("sagetv.webplayer.videoEncoder"),System.getenv("SAGETV_WEBPLAYER_VIDEO_ENCODER"));
        return probeRequested(requested==null?"auto":requested);
    }

    private static Probe probeRequested(String requested) {
        File executable = findExecutable();
        if (executable == null) {
            return new Probe(false, "", "not configured", "", false, false, "",
                    "Install OpenSageTV Vibe FFmpeg Plugin or set SAGETV_WEBPLAYER_TRANSCODER");
        }

        requested = requested.trim().toLowerCase(Locale.ROOT);

        if ("software".equals(requested) || "libx264".equals(requested) || "off".equals(requested)) {
            return new Probe(true, executable.getAbsolutePath(), "configured/discovered", "libx264",
                    false, false, "software", "Software H.264 fallback explicitly selected");
        }

        String encoders = runText(executable, "-hide_banner", "-encoders");
        String filters = runText(executable, "-hide_banner", "-filters");
        String hwaccels = runText(executable, "-hide_banner", "-hwaccels");
        if (!"auto".equals(requested)) {
            if (containsToken(encoders, requested) && encoderWorks(executable, requested)) {
                return probeForEncoder(executable, requested, filters, hwaccels, true);
            }
            return new Probe(true, executable.getAbsolutePath(), "configured/discovered", "libx264",
                    false, false, "software", "Requested encoder " + requested + " is unavailable or cannot initialize; using libx264");
        }

        // First pass: prefer a GPU path that can initialize BOTH hardware decode
        // and hardware encode. This prevents an encode-only backend from winning
        // merely because it appears first in FFmpeg's encoder list.
        String[] preferred = new String[] {"h264_nvenc", "h264_qsv", "h264_amf", "h264_vaapi", "h264_videotoolbox"};
        Probe encodeOnly = null;
        for (String encoder : preferred) {
            if (!containsToken(encoders, encoder)) continue;
            if ("h264_vaapi".equals(encoder) && !vaapiDevice().exists()) continue;
            if (!encoderWorks(executable, encoder)) continue;
            Probe p = probeForEncoder(executable, encoder, filters, hwaccels, false);
            if (p.hardwareDecode) return p;
            if (encodeOnly == null) encodeOnly = p;
        }
        if (encodeOnly != null) return encodeOnly;
        return new Probe(true, executable.getAbsolutePath(), "configured/discovered", "libx264",
                false, false, "software", "No supported H.264 hardware encoder was detected; using libx264");
    }

    private static final class HardwarePath {
        final boolean decode;
        final boolean filters;
        final String decodeAccelerator;
        final String note;
        HardwarePath(boolean decode, boolean filters, String decodeAccelerator, String note) {
            this.decode=decode;this.filters=filters;this.decodeAccelerator=decodeAccelerator==null?"":decodeAccelerator;this.note=note==null?"":note;
        }
    }

    private static Probe probeForEncoder(File executable, String encoder, String filters, String hwaccels, boolean requested) {
        String accelerator = encoder;
        if ("h264_nvenc".equals(encoder)) accelerator = "NVIDIA NVENC";
        else if ("h264_qsv".equals(encoder)) accelerator = "Intel Quick Sync";
        else if ("h264_amf".equals(encoder)) accelerator = "AMD AMF";
        else if ("h264_vaapi".equals(encoder)) accelerator = "VAAPI";
        else if ("h264_videotoolbox".equals(encoder)) accelerator = "Apple VideoToolbox";

        HardwarePath path = probeHardwarePath(executable, encoder, filters, hwaccels);
        String detail = accelerator + " hardware H.264 encode";
        if (path.decode) {
            detail += " + hardware decode via " + path.decodeAccelerator;
            detail += path.filters ? " + hardware video processing" : " + software video processing when filters are required";
        } else {
            detail += "; hardware decode could not initialize, so encode-only fallback is available";
        }
        if (!path.note.isEmpty()) detail += " (" + path.note + ")";
        if (requested) detail += " [explicit selection]";
        return new Probe(true, executable.getAbsolutePath(), "configured/discovered", encoder,
                true, path.decode, path.filters, path.decodeAccelerator, accelerator, detail);
    }

    /**
     * Every GPU backend first attempts a real hardware-decode + hardware-encode
     * path. NVIDIA/Intel/VAAPI additionally test a zero-copy hardware-filter
     * chain; AMD AMF and VideoToolbox still use hardware decode and encode even
     * when scaling/deinterlace must pass through system memory.
     */
    private static HardwarePath probeHardwarePath(File executable, String encoder, String filters, String hwaccels) {
        if ("h264_nvenc".equals(encoder)) {
            boolean decode = containsToken(hwaccels,"cuda") && hardwareDecodeWorks(executable,"cuda");
            boolean gpuFilters = decode && containsToken(filters,"scale_cuda") && containsToken(filters,"yadif_cuda")
                    && zeroCopyPipelineWorks(executable, encoder);
            return new HardwarePath(decode,gpuFilters,decode?"CUDA/NVDEC":"",gpuFilters?"CUDA zero-copy VPP tested":"NVDEC/NVENC attempted before encode-only fallback");
        }
        if ("h264_qsv".equals(encoder)) {
            boolean decode = containsToken(hwaccels,"qsv") && hardwareDecodeWorks(executable,"qsv");
            boolean gpuFilters = decode && containsToken(filters,"vpp_qsv") && zeroCopyPipelineWorks(executable, encoder);
            return new HardwarePath(decode,gpuFilters,decode?"QSV":"",gpuFilters?"QSV decode/VPP/encode tested":"QSV decode/encode attempted before encode-only fallback");
        }
        if ("h264_amf".equals(encoder)) {
            String decodeAccel="";
            if (containsToken(hwaccels,"d3d11va") && hardwareDecodeWorks(executable,"d3d11va")) decodeAccel="D3D11VA";
            else if (containsToken(hwaccels,"dxva2") && hardwareDecodeWorks(executable,"dxva2")) decodeAccel="DXVA2";
            else if (containsToken(hwaccels,"vaapi") && vaapiDevice().exists() && hardwareDecodeWorks(executable,"vaapi")) decodeAccel="VAAPI";
            return new HardwarePath(!decodeAccel.isEmpty(),false,decodeAccel,"AMD decode and AMF encode are both attempted; software filters bridge surfaces when required");
        }
        if ("h264_vaapi".equals(encoder)) {
            boolean decode = vaapiDevice().exists() && containsToken(hwaccels,"vaapi") && hardwareDecodeWorks(executable,"vaapi");
            boolean gpuFilters = decode && containsToken(filters,"scale_vaapi") && containsToken(filters,"deinterlace_vaapi")
                    && zeroCopyPipelineWorks(executable, encoder);
            return new HardwarePath(decode,gpuFilters,decode?"VAAPI":"",gpuFilters?"VAAPI decode/VPP/encode tested":"VAAPI decode/encode attempted before encode-only fallback");
        }
        if ("h264_videotoolbox".equals(encoder)) {
            boolean decode = containsToken(hwaccels,"videotoolbox") && hardwareDecodeWorks(executable,"videotoolbox");
            return new HardwarePath(decode,false,decode?"VideoToolbox":"","VideoToolbox hardware decode and encode are both attempted; software filters are used when required");
        }
        return new HardwarePath(false,false,"","");
    }

    private static File vaapiDevice() {
        return new File(System.getProperty("sagetv.webplayer.vaapiDevice","/dev/dri/renderD128"));
    }

    /** Test that FFmpeg can actually hardware-decode MPEG-2 on this backend. */
    private static boolean hardwareDecodeWorks(File executable, String accel) {
        File dir=null;
        try {
            dir=java.nio.file.Files.createTempDirectory("webplayer-hwdecode-").toFile();
            File source=createHardwareProbeSource(executable,dir);
            if(source==null)return false;
            List<String> cmd=new ArrayList<String>();cmd.add(executable.getAbsolutePath());
            java.util.Collections.addAll(cmd,"-hide_banner","-loglevel","error","-nostdin","-hwaccel",accel);
            if("vaapi".equals(accel))java.util.Collections.addAll(cmd,"-hwaccel_device",vaapiDevice().getAbsolutePath());
            java.util.Collections.addAll(cmd,"-i",source.getAbsolutePath(),"-frames:v","8","-an","-f","null","-");
            return ProcessCapture.run(cmd,12,256*1024).exit==0;
        } catch(Exception e){return false;} finally {if(dir!=null)deleteTree(dir);}
    }

    /** Test the zero-copy GPU filtering chain where FFmpeg exposes one. */
    private static boolean zeroCopyPipelineWorks(File executable, String encoder) {
        File dir=null;
        try {
            dir=java.nio.file.Files.createTempDirectory("webplayer-hwpipeline-").toFile();
            File source=createHardwareProbeSource(executable,dir);if(source==null)return false;
            List<String> cmd=new ArrayList<String>();cmd.add(executable.getAbsolutePath());
            java.util.Collections.addAll(cmd,"-hide_banner","-loglevel","error","-nostdin");
            if("h264_nvenc".equals(encoder)) {
                java.util.Collections.addAll(cmd,"-hwaccel","cuda","-hwaccel_output_format","cuda","-i",source.getAbsolutePath(),
                        "-vf","scale_cuda=w=320:h=180:format=nv12","-frames:v","8","-an","-c:v","h264_nvenc","-f","null","-");
            } else if("h264_qsv".equals(encoder)) {
                java.util.Collections.addAll(cmd,"-hwaccel","qsv","-hwaccel_output_format","qsv","-i",source.getAbsolutePath(),
                        "-vf","vpp_qsv=w=320:h=180:format=nv12","-frames:v","8","-an","-c:v","h264_qsv","-f","null","-");
            } else if("h264_vaapi".equals(encoder)) {
                if(!vaapiDevice().exists())return false;
                java.util.Collections.addAll(cmd,"-hwaccel","vaapi","-hwaccel_device",vaapiDevice().getAbsolutePath(),"-hwaccel_output_format","vaapi","-i",source.getAbsolutePath(),
                        "-vf","scale_vaapi=w=320:h=180:format=nv12","-frames:v","8","-an","-c:v","h264_vaapi","-f","null","-");
            } else return false;
            return ProcessCapture.run(cmd,12,256*1024).exit==0;
        }catch(Exception e){return false;}finally{if(dir!=null)deleteTree(dir);}
    }

    private static File createHardwareProbeSource(File executable, File dir) throws IOException {
        File source=new File(dir,"source.ts");
        List<String> generate=new ArrayList<String>();generate.add(executable.getAbsolutePath());
        java.util.Collections.addAll(generate,"-hide_banner","-loglevel","error","-y","-f","lavfi","-i","testsrc2=size=640x360:rate=30000/1001",
                "-t","0.6","-c:v","mpeg2video","-q:v","5","-f","mpegts",source.getAbsolutePath());
        try {if(ProcessCapture.run(generate,10,128*1024).exit!=0||!source.isFile()||source.length()==0)return null;}
        catch(IOException e){return null;}
        return source;
    }

    private static void deleteTree(File f) {
        if (f == null || !f.exists()) return;
        File[] children=f.listFiles();if(children!=null)for(File c:children)deleteTree(c);
        try { f.delete(); } catch (Exception ignored) {}
    }

    static Probe encodeOnlyFallback(Probe base, String reason) {
        if(base==null||!base.hardware||base.videoEncoder==null||base.videoEncoder.isEmpty()||"libx264".equals(base.videoEncoder))return softwareFallback(base,reason);
        return new Probe(true,base.executable,base.source,base.videoEncoder,true,false,false,"",base.accelerator,
                base.accelerator+" hardware encode with software decode/video processing"+(reason==null||reason.isEmpty()?"":" after "+reason));
    }

    private static void addHardwareDecodeArgs(List<String> cmd, Probe probe, boolean gpuFrames) {
        if(probe==null||!probe.hardwareDecode)return;
        String d=probe.decodeAccelerator==null?"":probe.decodeAccelerator.toLowerCase(Locale.ROOT);
        if(d.contains("cuda")||d.contains("nvdec")){cmd.add("-hwaccel");cmd.add("cuda");if(gpuFrames){cmd.add("-hwaccel_output_format");cmd.add("cuda");}}
        else if(d.contains("qsv")){cmd.add("-hwaccel");cmd.add("qsv");if(gpuFrames){cmd.add("-hwaccel_output_format");cmd.add("qsv");}}
        else if(d.contains("vaapi")){cmd.add("-hwaccel");cmd.add("vaapi");cmd.add("-hwaccel_device");cmd.add(vaapiDevice().getAbsolutePath());if(gpuFrames){cmd.add("-hwaccel_output_format");cmd.add("vaapi");}}
        else if(d.contains("d3d11")){cmd.add("-hwaccel");cmd.add("d3d11va");}
        else if(d.contains("dxva2")){cmd.add("-hwaccel");cmd.add("dxva2");}
        else if(d.contains("videotoolbox")){cmd.add("-hwaccel");cmd.add("videotoolbox");}
    }

    private static String genericHardwareFilter(Probe probe, String encoder) {
        if(probe.hardwareDecode&&probe.hardwareFilters&&"h264_nvenc".equals(encoder))return "yadif_cuda=mode=send_frame:parity=auto:deint=interlaced";
        if(probe.hardwareDecode&&probe.hardwareFilters&&"h264_qsv".equals(encoder))return "vpp_qsv=deinterlace=advanced:rate=frame:format=nv12";
        if(probe.hardwareDecode&&probe.hardwareFilters&&"h264_vaapi".equals(encoder))return "deinterlace_vaapi=mode=motion_adaptive:rate=frame:auto=1";
        if("h264_vaapi".equals(encoder))return "yadif=0:-1:0,format=nv12,hwupload";
        if("h264_qsv".equals(encoder)||"h264_nvenc".equals(encoder)||"h264_amf".equals(encoder))return "yadif=0:-1:0,format=nv12";
        return "yadif=0:-1:0,format=yuv420p";
    }

    static Probe softwareFallback(Probe base, String reason) {
        String executable = base == null ? "" : base.executable;
        String source = base == null ? "runtime fallback" : base.source;
        return new Probe(executable != null && executable.length() > 0, executable, source, "libx264",
                false, false, "software", "Software fallback" + (reason == null || reason.length() == 0 ? "" : ": " + reason));
    }
    static List<String> buildFmp4Command(Probe probe, File input, boolean concatInput, double startSeconds, int audioIndex) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(probe.executable);
        cmd.add("-hide_banner");
        cmd.add("-loglevel"); cmd.add("error");
        cmd.add("-nostdin");
        if (startSeconds > 0) { cmd.add("-ss"); cmd.add(String.format(Locale.US, "%.3f", startSeconds)); }

        String encoder = probe.videoEncoder == null || probe.videoEncoder.length() == 0 ? "libx264" : probe.videoEncoder;
        boolean gpuFrames=probe.hardwareDecode&&probe.hardwareFilters&&("h264_nvenc".equals(encoder)||"h264_qsv".equals(encoder)||"h264_vaapi".equals(encoder));
        addHardwareDecodeArgs(cmd,probe,gpuFrames);
        if("h264_vaapi".equals(encoder)&&!probe.hardwareDecode){cmd.add("-vaapi_device");cmd.add(vaapiDevice().getAbsolutePath());}
        String vf = genericHardwareFilter(probe,encoder);

        if (concatInput) {
            cmd.add("-f"); cmd.add("concat");
            cmd.add("-safe"); cmd.add("0");
        }
        cmd.add("-i"); cmd.add(input.getAbsolutePath());
        cmd.add("-map"); cmd.add("0:v:0");
        cmd.add("-map"); cmd.add("0:a:" + Math.max(0, audioIndex) + "?");
        cmd.add("-vf"); cmd.add(vf);
        cmd.add("-c:v"); cmd.add(encoder);

        if ("libx264".equals(encoder)) {
            cmd.add("-preset"); cmd.add("veryfast");
            cmd.add("-profile:v"); cmd.add("main");
            cmd.add("-level:v"); cmd.add("4.0");
            cmd.add("-pix_fmt"); cmd.add("yuv420p");
        } else if ("h264_nvenc".equals(encoder)) {
            cmd.add("-preset"); cmd.add("fast");
            cmd.add("-profile:v"); cmd.add("main");
            cmd.add("-b:v"); cmd.add("8M");
            cmd.add("-maxrate"); cmd.add("12M");
            cmd.add("-bufsize"); cmd.add("16M");
        } else if ("h264_qsv".equals(encoder)) {
            cmd.add("-preset"); cmd.add("veryfast");
            cmd.add("-profile:v"); cmd.add("main");
            cmd.add("-b:v"); cmd.add("8M");
            cmd.add("-maxrate"); cmd.add("12M");
            cmd.add("-bufsize"); cmd.add("16M");
        } else if ("h264_amf".equals(encoder)) {
            cmd.add("-quality"); cmd.add("balanced");
            cmd.add("-usage"); cmd.add("transcoding");
            cmd.add("-profile"); cmd.add("main");
            cmd.add("-b:v"); cmd.add("8M");
        } else if ("h264_vaapi".equals(encoder)) {
            cmd.add("-profile:v"); cmd.add("main");
            cmd.add("-b:v"); cmd.add("8M");
            cmd.add("-maxrate"); cmd.add("12M");
            cmd.add("-bufsize"); cmd.add("16M");
        } else if ("h264_videotoolbox".equals(encoder)) {
            cmd.add("-profile:v"); cmd.add("main");
            cmd.add("-b:v"); cmd.add("8M");
        }

        cmd.add("-c:a"); cmd.add("aac");
        cmd.add("-ac"); cmd.add("2");
        cmd.add("-b:a"); cmd.add("192k");
        cmd.add("-movflags"); cmd.add("frag_keyframe+empty_moov+default_base_moof");
        cmd.add("-frag_duration"); cmd.add("1000000");
        cmd.add("-flush_packets"); cmd.add("1");
        cmd.add("-f"); cmd.add("mp4");
        cmd.add("pipe:1");
        return cmd;
    }


    /** Build a stable HLS EVENT stream for browser compatibility playback. */
    static List<String> buildHlsCommand(Probe probe, File input, boolean concatInput, boolean pipeInput,
                                        double startSeconds, int audioIndex, File outputDir) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(probe.executable);
        cmd.add("-hide_banner");
        cmd.add("-loglevel"); cmd.add(firstNonEmpty(System.getProperty("sagetv.webplayer.ffmpegLogLevel"), System.getenv("SAGETV_WEBPLAYER_FFMPEG_LOGLEVEL")) == null ? "warning" : firstNonEmpty(System.getProperty("sagetv.webplayer.ffmpegLogLevel"), System.getenv("SAGETV_WEBPLAYER_FFMPEG_LOGLEVEL")));
        cmd.add("-y");
        if (!pipeInput) cmd.add("-nostdin");
        if (!pipeInput && startSeconds > 0) { cmd.add("-ss"); cmd.add(String.format(Locale.US, "%.3f", startSeconds)); }

        String encoder = probe.videoEncoder == null || probe.videoEncoder.length() == 0 ? "libx264" : probe.videoEncoder;
        boolean gpuFrames=probe.hardwareDecode&&probe.hardwareFilters&&("h264_nvenc".equals(encoder)||"h264_qsv".equals(encoder)||"h264_vaapi".equals(encoder));
        addHardwareDecodeArgs(cmd,probe,gpuFrames);
        if("h264_vaapi".equals(encoder)&&!probe.hardwareDecode){cmd.add("-vaapi_device");cmd.add(vaapiDevice().getAbsolutePath());}
        String vf = genericHardwareFilter(probe,encoder);

        cmd.add("-thread_queue_size"); cmd.add("4096");
        cmd.add("-fflags"); cmd.add("+genpts+discardcorrupt");
        cmd.add("-err_detect"); cmd.add("ignore_err");
        cmd.add("-analyzeduration"); cmd.add("2000000");
        cmd.add("-probesize"); cmd.add("2000000");

        if (pipeInput) {
            // Let FFmpeg identify TS versus MPEG-PS. SageTV can record either.
            cmd.add("-i"); cmd.add("pipe:0");
        } else {
            if (concatInput) {
                cmd.add("-f"); cmd.add("concat");
                cmd.add("-safe"); cmd.add("0");
            }
            cmd.add("-i"); cmd.add(input.getAbsolutePath());
        }
        cmd.add("-map"); cmd.add("0:v:0");
        cmd.add("-map"); cmd.add("0:a:" + Math.max(0, audioIndex) + "?");
        cmd.add("-sn"); cmd.add("-dn");
        cmd.add("-vf"); cmd.add(vf);
        cmd.add("-c:v"); cmd.add(encoder);

        if ("libx264".equals(encoder)) {
            cmd.add("-preset"); cmd.add("ultrafast");
            cmd.add("-tune"); cmd.add("zerolatency");
            cmd.add("-profile:v"); cmd.add("main");
            cmd.add("-level:v"); cmd.add("4.0");
            cmd.add("-pix_fmt"); cmd.add("yuv420p");
            cmd.add("-b:v"); cmd.add("5M");
            cmd.add("-maxrate"); cmd.add("7M");
            cmd.add("-bufsize"); cmd.add("10M");
        } else if ("h264_nvenc".equals(encoder)) {
            cmd.add("-preset"); cmd.add("p4");
            cmd.add("-tune"); cmd.add("ll");
            cmd.add("-profile:v"); cmd.add("main");
            cmd.add("-bf"); cmd.add("0");
            cmd.add("-b:v"); cmd.add("8M");
            cmd.add("-maxrate"); cmd.add("12M");
            cmd.add("-bufsize"); cmd.add("16M");
        } else if ("h264_qsv".equals(encoder)) {
            cmd.add("-preset"); cmd.add("veryfast");
            cmd.add("-profile:v"); cmd.add("main");
            cmd.add("-bf"); cmd.add("0");
            cmd.add("-b:v"); cmd.add("8M");
            cmd.add("-maxrate"); cmd.add("12M");
            cmd.add("-bufsize"); cmd.add("16M");
        } else if ("h264_amf".equals(encoder)) {
            cmd.add("-quality"); cmd.add("balanced");
            cmd.add("-usage"); cmd.add("transcoding");
            cmd.add("-profile"); cmd.add("main");
            cmd.add("-b:v"); cmd.add("8M");
        } else if ("h264_vaapi".equals(encoder)) {
            cmd.add("-profile:v"); cmd.add("main");
            cmd.add("-b:v"); cmd.add("8M");
            cmd.add("-maxrate"); cmd.add("12M");
            cmd.add("-bufsize"); cmd.add("16M");
        } else if ("h264_videotoolbox".equals(encoder)) {
            cmd.add("-profile:v"); cmd.add("main");
            cmd.add("-b:v"); cmd.add("8M");
        }

        // One-second GOPs give HLS reliable segment boundaries and fast seeking/startup.
        cmd.add("-g"); cmd.add("30");
        cmd.add("-keyint_min"); cmd.add("1");
        cmd.add("-sc_threshold"); cmd.add("0");
        cmd.add("-force_key_frames"); cmd.add("expr:gte(t,n_forced*1)");
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add("-ar"); cmd.add("48000");
        cmd.add("-ac"); cmd.add("2");
        cmd.add("-b:a"); cmd.add("192k");
        cmd.add("-af"); cmd.add("aresample=async=1000:first_pts=0,asetpts=PTS-STARTPTS");
        cmd.add("-max_muxing_queue_size"); cmd.add("2048");
        cmd.add("-muxdelay"); cmd.add("0");
        cmd.add("-muxpreload"); cmd.add("0");
        cmd.add("-flush_packets"); cmd.add("1");
        cmd.add("-avoid_negative_ts"); cmd.add("make_zero");
        cmd.add("-f"); cmd.add("hls");
        cmd.add("-hls_time"); cmd.add("3");
        cmd.add("-hls_init_time"); cmd.add("1");
        cmd.add("-hls_segment_type"); cmd.add("mpegts");
        cmd.add("-hls_list_size"); cmd.add("0");
        cmd.add("-hls_playlist_type"); cmd.add("event");
        cmd.add("-hls_flags"); cmd.add("independent_segments+temp_file");
        cmd.add("-hls_segment_filename"); cmd.add(new File(outputDir, "seg_%05d.ts").getAbsolutePath());
        cmd.add(new File(outputDir, "stream.m3u8").getAbsolutePath());
        return cmd;
    }


    /** Build a short-latency HLS converter for one stock MiniDVDPlayer MPEG-PS generation. */
    static List<String> buildDvdHlsCommand(Probe probe, int audioWireCode, File outputDir) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(probe.executable);
        cmd.add("-hide_banner");
        cmd.add("-loglevel"); cmd.add(firstNonEmpty(System.getProperty("sagetv.webplayer.ffmpegLogLevel"), System.getenv("SAGETV_WEBPLAYER_FFMPEG_LOGLEVEL")) == null ? "warning" : firstNonEmpty(System.getProperty("sagetv.webplayer.ffmpegLogLevel"), System.getenv("SAGETV_WEBPLAYER_FFMPEG_LOGLEVEL")));
        cmd.add("-y");

        // Hardware decode options are INPUT options in FFmpeg and MUST appear
        // before the DVD pipe's -i.  The 3.2.24/3.2.26 DVD command placed them
        // after -i, so FFmpeg rejected the full-hardware path before producing
        // any HLS media and the native DVD push ring eventually filled.
        String encoder = probe.videoEncoder == null || probe.videoEncoder.length() == 0 ? "libx264" : probe.videoEncoder;
        boolean gpuFrames=probe.hardwareDecode&&probe.hardwareFilters&&("h264_nvenc".equals(encoder)||"h264_qsv".equals(encoder)||"h264_vaapi".equals(encoder));
        addHardwareDecodeArgs(cmd,probe,gpuFrames);
        if("h264_vaapi".equals(encoder)&&!probe.hardwareDecode){cmd.add("-vaapi_device");cmd.add(vaapiDevice().getAbsolutePath());}

        cmd.add("-thread_queue_size"); cmd.add("4096");
        cmd.add("-fflags"); cmd.add("+genpts+discardcorrupt");
        cmd.add("-err_detect"); cmd.add("ignore_err");
        cmd.add("-analyzeduration"); cmd.add("1500000");
        cmd.add("-probesize"); cmd.add("1500000");
        // The stock DVD VM owns MPEG-PS packet boundaries. Never concatenate VOB files here.
        cmd.add("-f"); cmd.add("mpeg");
        cmd.add("-i"); cmd.add("pipe:0");
        cmd.add("-map"); cmd.add("0:v:0?");
        if (audioWireCode >= 0) {
            DvdAudioStreamCode code = DvdAudioStreamCode.decode(audioWireCode);
            if (!code.supportedByFfmpegPolicy()) throw new IllegalArgumentException("Unsupported DVD audio selector: " + code.description());
            // DVD menu/still cells commonly omit the title's selected audio
            // stream.  Keep that physical stream when it is present, but do
            // not reject an otherwise valid video-only cell.  The next FLUSH
            // starts a fresh FFmpeg generation and resolves the title audio
            // again, so optional mapping cannot silently select another track.
            cmd.add("-map"); cmd.add("0:i:" + code.ffmpegStreamId() + "?");
        } else {
            cmd.add("-map"); cmd.add("0:a:0?");
        }
        cmd.add("-sn"); cmd.add("-dn");

        String vf = genericHardwareFilter(probe,encoder)+",setpts=PTS-STARTPTS";
        cmd.add("-vf"); cmd.add(vf);
        cmd.add("-c:v"); cmd.add(encoder);
        if ("libx264".equals(encoder)) {
            cmd.add("-preset"); cmd.add("ultrafast");
            cmd.add("-tune"); cmd.add("zerolatency");
            cmd.add("-profile:v"); cmd.add("main");
            cmd.add("-level:v"); cmd.add("4.0");
            cmd.add("-pix_fmt"); cmd.add("yuv420p");
            cmd.add("-b:v"); cmd.add("5M"); cmd.add("-maxrate"); cmd.add("7M"); cmd.add("-bufsize"); cmd.add("10M");
        } else if ("h264_nvenc".equals(encoder)) {
            cmd.add("-preset"); cmd.add("p4"); cmd.add("-tune"); cmd.add("ll"); cmd.add("-profile:v"); cmd.add("main"); cmd.add("-bf"); cmd.add("0");
            cmd.add("-b:v"); cmd.add("8M"); cmd.add("-maxrate"); cmd.add("12M"); cmd.add("-bufsize"); cmd.add("16M");
        } else if ("h264_qsv".equals(encoder)) {
            cmd.add("-preset"); cmd.add("veryfast"); cmd.add("-profile:v"); cmd.add("main"); cmd.add("-bf"); cmd.add("0");
            cmd.add("-b:v"); cmd.add("8M"); cmd.add("-maxrate"); cmd.add("12M"); cmd.add("-bufsize"); cmd.add("16M");
        } else if ("h264_amf".equals(encoder)) {
            cmd.add("-quality"); cmd.add("balanced"); cmd.add("-usage"); cmd.add("transcoding"); cmd.add("-profile"); cmd.add("main"); cmd.add("-b:v"); cmd.add("8M");
        } else if ("h264_vaapi".equals(encoder)) {
            cmd.add("-profile:v"); cmd.add("main"); cmd.add("-b:v"); cmd.add("8M"); cmd.add("-maxrate"); cmd.add("12M"); cmd.add("-bufsize"); cmd.add("16M");
        } else if ("h264_videotoolbox".equals(encoder)) {
            cmd.add("-profile:v"); cmd.add("main"); cmd.add("-b:v"); cmd.add("8M");
        }
        // Preserve source PAL/NTSC cadence; only force periodic keyframes for short HLS startup.
        cmd.add("-g"); cmd.add("15");
        cmd.add("-keyint_min"); cmd.add("1");
        cmd.add("-sc_threshold"); cmd.add("0");
        cmd.add("-force_key_frames"); cmd.add("expr:gte(t,n_forced*0.5)");
        cmd.add("-c:a"); cmd.add("aac");
        cmd.add("-ar"); cmd.add("48000");
        cmd.add("-ac"); cmd.add("2");
        cmd.add("-b:a"); cmd.add("192k");
        cmd.add("-af"); cmd.add("aresample=async=1000:first_pts=0,asetpts=PTS-STARTPTS");
        cmd.add("-max_muxing_queue_size"); cmd.add("2048");
        cmd.add("-muxdelay"); cmd.add("0"); cmd.add("-muxpreload"); cmd.add("0"); cmd.add("-flush_packets"); cmd.add("1");
        cmd.add("-avoid_negative_ts"); cmd.add("make_zero");
        cmd.add("-f"); cmd.add("hls");
        cmd.add("-hls_time"); cmd.add("0.5");
        cmd.add("-hls_init_time"); cmd.add("0.25");
        cmd.add("-hls_segment_type"); cmd.add("mpegts");
        cmd.add("-hls_list_size"); cmd.add("0");
        cmd.add("-hls_playlist_type"); cmd.add("event");
        cmd.add("-hls_flags"); cmd.add("independent_segments+temp_file");
        cmd.add("-hls_segment_filename"); cmd.add(new File(outputDir,"seg_%05d.ts").getAbsolutePath());
        cmd.add(new File(outputDir,"stream.m3u8").getAbsolutePath());
        return cmd;
    }

    static File findExecutable() {
        String configured = firstNonEmpty(
                firstNonEmpty(System.getProperty("sagetv.webplayer.transcoder"), System.getenv("SAGETV_WEBPLAYER_TRANSCODER")),
                firstNonEmpty(System.getProperty("sagetv.webplayer.ffmpeg"), System.getenv("SAGETV_WEBPLAYER_FFMPEG")));
        if (configured != null) {
            File f = new File(configured);
            if (f.isFile() && f.canExecute()) return f;
        }
        List<File> candidates = new ArrayList<File>();
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String launcher = windows ? "SageTVTranscoder.exe" : "SageTVTranscoder";
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            candidates.add(new File(userDir, launcher));
            candidates.add(new File(userDir, "plugins/SageTVFFmpegPlugin/launcher/" + launcher));
        }
        String sageHome = System.getenv("SAGETV_HOME");
        if (sageHome != null) {
            candidates.add(new File(sageHome, launcher));
            candidates.add(new File(sageHome, "plugins/SageTVFFmpegPlugin/launcher/" + launcher));
        }
        for (File f : candidates) if (f.isFile() && f.canExecute()) return f;
        return null;
    }


    private static boolean encoderWorks(File executable, String encoder) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(executable.getAbsolutePath());
        cmd.add("-hide_banner"); cmd.add("-loglevel"); cmd.add("error"); cmd.add("-nostdin");
        if ("h264_vaapi".equals(encoder)) {
            if (!new File("/dev/dri/renderD128").exists()) return false;
            cmd.add("-vaapi_device"); cmd.add("/dev/dri/renderD128");
        }
        cmd.add("-f"); cmd.add("lavfi");
        cmd.add("-i"); cmd.add("color=c=black:s=64x64:d=0.04");
        cmd.add("-frames:v"); cmd.add("1");
        cmd.add("-an");
        if ("h264_vaapi".equals(encoder)) {
            cmd.add("-vf"); cmd.add("format=nv12,hwupload");
        }
        cmd.add("-c:v"); cmd.add(encoder);
        cmd.add("-f"); cmd.add("null"); cmd.add("-");
        try { return ProcessCapture.run(cmd,8,64*1024).exit==0; }
        catch(IOException e) { return false; }
    }
    private static String runText(File executable, String... args) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(executable.getAbsolutePath());
        for (String arg : args) cmd.add(arg);
        try { return ProcessCapture.run(cmd,8,1024*1024).text; }
        catch(IOException e) { return ""; }
    }

    private static boolean containsToken(String text, String token) {
        if (text == null || token == null) return false;
        return text.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT));
    }

    private static String firstNonEmpty(String a, String b) {
        if (a != null && !a.trim().isEmpty()) return a.trim();
        if (b != null && !b.trim().isEmpty()) return b.trim();
        return null;
    }
}
