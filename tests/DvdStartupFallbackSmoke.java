package org.opensagetv.webplayer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Real-process DVD startup fallback smoke: failed full-QSV startup must replay bytes and recover at encode-only stage. */
public final class DvdStartupFallbackSmoke {
    public static void main(String[] args) throws Exception {
        String real=args.length>0?args[0]:"/usr/bin/ffmpeg";
        if(!new File(real).isFile()) throw new AssertionError("FFmpeg missing: "+real);
        Path root=Files.createTempDirectory("dvd-fallback-");
        try {
            Path wrapper=root.resolve("ffmpeg-qsv-fail-first.sh");
            String script="#!/bin/bash\n"+
                "set -e\n"+
                "REAL='"+real.replace("'","'\\''")+"'\n"+
                "ALL=\" $* \"\n"+
                "if [[ \"$ALL\" == *\" -encoders \"* ]]; then echo ' V..... h264_qsv Intel Quick Sync'; exit 0; fi\n"+
                "if [[ \"$ALL\" == *\" -filters \"* ]]; then echo ' ... vpp_qsv V->V'; exit 0; fi\n"+
                "if [[ \"$ALL\" == *\" -hwaccels \"* ]]; then echo 'Hardware acceleration methods:'; echo 'qsv'; exit 0; fi\n"+
                "if [[ \"$ALL\" == *\" -hwaccel qsv \"* && \"$ALL\" != *\" pipe:0 \"* ]]; then exit 0; fi\n"+
                "if [[ \"$ALL\" == *\" -c:v h264_qsv \"* && \"$ALL\" != *\" pipe:0 \"* ]]; then exit 0; fi\n"+
                "if [[ \"$ALL\" == *\" pipe:0 \"* && \"$ALL\" == *\" -hwaccel qsv \"* ]]; then cat >/dev/null & pid=$!; sleep 0.15; kill $pid 2>/dev/null || true; exit 23; fi\n"+
                "args=(); prev=''; for a in \"$@\"; do if [[ \"$prev\" == '-c:v' && \"$a\" == 'h264_qsv' ]]; then args+=('libx264'); else args+=(\"$a\"); fi; prev=\"$a\"; done\n"+
                "exec \"$REAL\" \"${args[@]}\"\n";
            Files.write(wrapper,script.getBytes("UTF-8"));wrapper.toFile().setExecutable(true);
            System.setProperty("sagetv.webplayer.ffmpeg",wrapper.toString());
            System.setProperty("sagetv.webplayer.videoEncoder","qsv");
            System.setProperty("sagetv.webplayer.dvdStartupTimeoutMs","3500");
            System.setProperty("sagetv.webplayer.hlsDir",root.resolve("hls").toString());
            TranscoderManager.clearProbeCache();

            Path vob=root.resolve("sample.vob");
            run(Arrays.asList(real,"-hide_banner","-loglevel","error","-y",
                "-f","lavfi","-i","testsrc2=size=352x240:rate=30000/1001:duration=2.2",
                "-f","lavfi","-i","sine=frequency=440:sample_rate=48000:duration=2.2",
                "-map","0:v","-map","1:a","-c:v","mpeg2video","-b:v","1600k","-g","15",
                "-c:a","ac3","-b:a","192k","-f","dvd",vob.toString()),30);

            Map<String,String> m=new HashMap<String,String>();m.put("encoder","qsv");m.put("allowFallback","true");
            StreamOptions options=StreamOptions.fromMap(m).forDvdTranscode();
            DvdNativeSession dvd=new DvdNativeSession();dvd.init(0);byte[] bytes=Files.readAllBytes(vob);
            int first=Math.min(32768,bytes.length);dvd.push(push(bytes,0,first,0));
            HlsSessionManager.Session hls=HlsSessionManager.startDvd(dvd,options);
            int pos=first;while(pos<bytes.length){int n=Math.min(32768,bytes.length-pos);dvd.push(push(bytes,pos,n,pos+n==bytes.length?0x80:0));pos+=n;}
            long end=System.currentTimeMillis()+30000L;while(System.currentTimeMillis()<end&&!hls.playlistReady&&!hls.dvdNoPlayableMedia){HlsSessionManager.refreshMetrics(hls);Thread.sleep(50);}
            if(!hls.playlistReady)throw new AssertionError("fallback produced no HLS: stage="+hls.fallbackStage+" error="+hls.error+" stderr="+hls.stderrTail);
            if(hls.fallbackStage!=1)throw new AssertionError("expected encode-only fallback stage 1, got "+hls.fallbackStage);
            if(!hls.hardwareDecodeFallback)throw new AssertionError("hardwareDecodeFallback not set");
            if(hls.dvdReplayBytes<=0)throw new AssertionError("DVD startup replay captured no bytes");
            if(hls.dvdFallbackReason==null||hls.dvdFallbackReason.isEmpty())throw new AssertionError("missing fallback reason");
            System.out.println("DvdStartupFallbackSmoke PASS stage="+hls.fallbackStage+" replay="+hls.dvdReplayBytes+" reason="+hls.dvdFallbackReason);
            HlsSessionManager.stop(hls.id);dvd.close();

            // Force both QSV stages to fail, then hold a deliberately short
            // software generation open beyond dvdStartupTimeoutMs.  The final
            // software stage must wait for DVD EOS rather than falsely
            // declaring that an open still/menu cell has no playable media.
            Path wrapper2=root.resolve("ffmpeg-qsv-fail-both.sh");
            String script2="#!/bin/bash\n"+
                "set -e\n"+
                "REAL='"+real.replace("'","'\\''")+"'\n"+
                "ALL=\" $* \"\n"+
                "if [[ \"$ALL\" == *\" -encoders \"* ]]; then echo ' V..... h264_qsv Intel Quick Sync'; exit 0; fi\n"+
                "if [[ \"$ALL\" == *\" -filters \"* ]]; then echo ' ... vpp_qsv V->V'; exit 0; fi\n"+
                "if [[ \"$ALL\" == *\" -hwaccels \"* ]]; then echo 'Hardware acceleration methods:'; echo 'qsv'; exit 0; fi\n"+
                "if [[ \"$ALL\" == *\" -hwaccel qsv \"* && \"$ALL\" != *\" pipe:0 \"* ]]; then exit 0; fi\n"+
                "if [[ \"$ALL\" == *\" -c:v h264_qsv \"* && \"$ALL\" != *\" pipe:0 \"* ]]; then exit 0; fi\n"+
                "if [[ \"$ALL\" == *\" pipe:0 \"* && \"$ALL\" == *\"h264_qsv\"* ]]; then sleep 0.15; exit 24; fi\n"+
                "exec \"$REAL\" \"$@\"\n";
            Files.write(wrapper2,script2.getBytes("UTF-8"));wrapper2.toFile().setExecutable(true);
            System.setProperty("sagetv.webplayer.ffmpeg",wrapper2.toString());
            TranscoderManager.clearProbeCache();
            DvdNativeSession held=new DvdNativeSession();held.init(0);
            int heldFirst=Math.min(4096,bytes.length);held.push(push(bytes,0,heldFirst,0));
            HlsSessionManager.Session heldHls=HlsSessionManager.startDvd(held,options);
            long softwareDeadline=System.currentTimeMillis()+10000L;
            while(System.currentTimeMillis()<softwareDeadline&&heldHls.fallbackStage<2&&!heldHls.dvdNoPlayableMedia)Thread.sleep(25L);
            if(heldHls.fallbackStage!=2)throw new AssertionError("expected final software stage, got "+heldHls.fallbackStage+" error="+heldHls.error);
            Thread.sleep(4200L);
            if(heldHls.dvdNoPlayableMedia)throw new AssertionError("open software DVD generation timed out before EOS: "+heldHls.error);
            int heldPos=heldFirst;while(heldPos<bytes.length){int n=Math.min(32768,bytes.length-heldPos);held.push(push(bytes,heldPos,n,heldPos+n==bytes.length?0x100:0));heldPos+=n;}
            long heldEnd=System.currentTimeMillis()+30000L;
            while(System.currentTimeMillis()<heldEnd&&!heldHls.playlistReady&&!heldHls.dvdNoPlayableMedia){HlsSessionManager.refreshMetrics(heldHls);Thread.sleep(50L);}
            if(!heldHls.playlistReady)throw new AssertionError("software stage produced no HLS after EOS: "+heldHls.error+" stderr="+heldHls.stderrTail);
            if(!heldHls.error.isEmpty())throw new AssertionError("successful software fallback retained a stale startup error: "+heldHls.error+
                " eos="+heldHls.dvdInputEos+" converterEnded="+heldHls.dvdConverterEnded+" exit="+heldHls.exitCode+
                " bytesInput="+heldHls.bytesInput+" replay="+heldHls.dvdReplayBytes);
            System.out.println("DvdStartupFallbackSmoke PASS software-waits-for-eos stage="+heldHls.fallbackStage);
            HlsSessionManager.stop(heldHls.id);held.close();
        } finally {HlsSessionManager.stopAll();delete(root.toFile());System.clearProperty("sagetv.webplayer.ffmpeg");System.clearProperty("sagetv.webplayer.videoEncoder");}
    }
    private static byte[] push(byte[] source,int off,int len,int flags){byte[] b=new byte[len+8];writeInt(b,0,len);writeInt(b,4,flags);System.arraycopy(source,off,b,8,len);return b;}
    private static void writeInt(byte[] b,int o,int v){b[o]=(byte)(v>>>24);b[o+1]=(byte)(v>>>16);b[o+2]=(byte)(v>>>8);b[o+3]=(byte)v;}
    private static void run(List<String> cmd,int sec)throws Exception{Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();ByteArrayOutputStream out=new ByteArrayOutputStream();copy(p.getInputStream(),out);if(!p.waitFor(sec,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("timeout: "+cmd);}if(p.exitValue()!=0)throw new AssertionError("command failed "+p.exitValue()+": "+out.toString("UTF-8"));}
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);}
    private static void delete(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] c=f.listFiles();if(c!=null)for(File x:c)delete(x);}f.delete();}
}
