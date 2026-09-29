package org.opensagetv.webplayer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.*;

/** Explicit generated A/V calibration. No SageTV media, mute state or persisted settings are changed. */
public final class P12AvCalibrationSmoke {
    public static void main(String[] args) throws Exception {
        String ffmpeg=args.length>0?args[0]:"/usr/bin/ffmpeg";
        File exe=new File(ffmpeg);if(!exe.isFile()) {System.out.println("SKIP P12 A/V calibration: FFmpeg missing");return;}
        File root=Files.createTempDirectory("p12-av-cal-").toFile();
        try {
            File src=new File(root,"fixture.ts");
            run(Arrays.asList(ffmpeg,"-hide_banner","-loglevel","error","-y",
                "-f","lavfi","-i","testsrc=size=320x240:rate=30:duration=3",
                "-f","lavfi","-i","aevalsrc=if(between(t\\,1\\,1.20)\\,0.8*sin(2*PI*1000*t)\\,0):s=48000:d=3",
                "-c:v","mpeg2video","-g","15","-bf","0","-c:a","ac3","-b:a","192k","-f","mpegts",src.getAbsolutePath()),30);
            double neg=transcodeAndOnset(exe,src,new File(root,"neg"),-500);
            double zero=transcodeAndOnset(exe,src,new File(root,"zero"),0);
            double pos=transcodeAndOnset(exe,src,new File(root,"pos"),500);
            require(zero-neg>0.30 && zero-neg<0.70,"negative delay did not advance audible pulse by ~500 ms: "+neg+" / "+zero);
            require(pos-zero>0.30 && pos-zero<0.70,"positive delay did not retard audible pulse by ~500 ms: "+zero+" / "+pos);
            System.out.printf(Locale.US,"P12 generated A/V calibration PASS (onset -500=%.3f, 0=%.3f, +500=%.3f)%n",neg,zero,pos);
        } finally { delete(root); }
    }
    static double transcodeAndOnset(File ffmpeg,File src,File dir,int offset) throws Exception {
        if(!dir.mkdirs())throw new IOException("mkdir "+dir);
        MediaProbe.Info info=MediaProbe.read(src,ffmpeg.getAbsolutePath());
        Map<String,String> m=new HashMap<String,String>();m.put("profileSchemaVersion","7");m.put("player","mpegts");m.put("videoMode","transcode");m.put("encoder","software");m.put("audioCodec","aac");m.put("audioChannels","2");m.put("audioOffsetMs",String.valueOf(offset));m.put("deinterlace","off");
        StreamPlan plan=new StreamPlan(StreamOptions.fromMap(m),info);
        TranscoderManager.Probe probe=new TranscoderManager.Probe(true,ffmpeg.getAbsolutePath(),"test","libx264",false,false,"software","calibration");
        List<String> cmd=StreamCommand.build(probe,src,false,false,0,dir,plan);run(cmd,30);
        File out=new File(dir,"stream.ts");require(out.length()>1000,"calibration output missing");
        List<String> analyze=Arrays.asList(ffmpeg.getAbsolutePath(),"-hide_banner","-i",out.getAbsolutePath(),"-map","0:a:0","-af","silencedetect=noise=-30dB:d=0.05","-f","null","-");
        String text=runCapture(analyze,30);Matcher mm=Pattern.compile("silence_end: ([0-9.]+)").matcher(text);if(!mm.find())throw new AssertionError("no audio onset in "+text);return Double.parseDouble(mm.group(1));
    }
    static void run(List<String> c,int sec)throws Exception{Process p=new ProcessBuilder(c).redirectErrorStream(true).start();byte[] b=readAll(p.getInputStream(),512*1024);if(!p.waitFor(sec,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("timeout "+c);}if(p.exitValue()!=0)throw new IOException("exit "+p.exitValue()+" "+new String(b,StandardCharsets.UTF_8)+" cmd="+c);}
    static String runCapture(List<String> c,int sec)throws Exception{Process p=new ProcessBuilder(c).redirectErrorStream(true).start();byte[] b=readAll(p.getInputStream(),512*1024);if(!p.waitFor(sec,java.util.concurrent.TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("timeout "+c);}if(p.exitValue()!=0)throw new IOException("exit "+p.exitValue()+" "+new String(b,StandardCharsets.UTF_8));return new String(b,StandardCharsets.UTF_8);}
    static byte[] readAll(InputStream in,int max)throws IOException{ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] x=new byte[8192];int r;while((r=in.read(x))>=0){if(b.size()+r<=max)b.write(x,0,r);}return b.toByteArray();}
    static void require(boolean x,String m){if(!x)throw new AssertionError(m);} static void delete(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File c:a)delete(c);}f.delete();}
}
