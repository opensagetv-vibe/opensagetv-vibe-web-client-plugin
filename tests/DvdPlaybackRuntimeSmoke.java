package org.opensagetv.webplayer;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Real FFmpeg P08 smoke: PS/PES, physical audio mapping, HLS output, drain and still/audio-only cells. */
public final class DvdPlaybackRuntimeSmoke {
    private static int pass;
    public static void main(String[] args) throws Exception {
        String ffmpeg=args.length>0?args[0]:"/usr/bin/ffmpeg";
        if(!new File(ffmpeg).isFile()) throw new AssertionError("FFmpeg missing: "+ffmpeg);
        System.setProperty("sagetv.webplayer.ffmpeg",ffmpeg);
        System.setProperty("sagetv.webplayer.videoEncoder","software");
        Path root=Files.createTempDirectory("p08-dvd-");
        System.setProperty("sagetv.webplayer.hlsDir",root.resolve("hls").toString());
        try{
            Path multi=root.resolve("multi.vob");
            run(Arrays.asList(ffmpeg,"-hide_banner","-loglevel","error","-y",
                "-f","lavfi","-i","testsrc2=size=320x240:rate=30000/1001:duration=2.5",
                "-f","lavfi","-i","sine=frequency=440:sample_rate=48000:duration=2.5",
                "-f","lavfi","-i","sine=frequency=880:sample_rate=48000:duration=2.5",
                "-map","0:v","-map","1:a","-map","2:a","-c:v","mpeg2video","-b:v","1800k","-g","15",
                "-c:a","ac3","-b:a","192k","-f","dvd",multi.toString()),30);
            testPhysicalAudioAndDrain(ffmpeg,multi);

            Path still=root.resolve("still.vob");
            run(Arrays.asList(ffmpeg,"-hide_banner","-loglevel","error","-y","-f","lavfi","-i","color=blue:s=720x480:r=30000/1001",
                "-frames:v","1","-c:v","mpeg2video","-f","dvd",still.toString()),20);
            testShortCell(still,true);

            Path audio=root.resolve("audio.mpg");
            run(Arrays.asList(ffmpeg,"-hide_banner","-loglevel","error","-y","-f","lavfi","-i","sine=frequency=660:sample_rate=48000:duration=0.8",
                "-c:a","mp2","-f","mpeg",audio.toString()),20);
            testShortCell(audio,false);

            Path pal=root.resolve("pal.vob");
            run(Arrays.asList(ffmpeg,"-hide_banner","-loglevel","error","-y","-f","lavfi","-i","testsrc2=size=352x288:rate=25:duration=1.6",
                "-c:v","mpeg2video","-b:v","1200k","-g","12","-f","dvd",pal.toString()),20);
            testCadence(ffmpeg,pal);

            testClockAndSpuIsolation();
            System.out.println("DvdPlaybackRuntimeSmoke PASS "+pass+"/5");
        } finally { HlsSessionManager.stopAll(); delete(root.toFile()); }
    }

    private static void testPhysicalAudioAndDrain(String ffmpeg,Path source) throws Exception {
        DvdNativeSession dvd=new DvdNativeSession();dvd.init(0);
        byte[] sel=new byte[8];writeInt(sel,0,0);writeInt(sel,4,0xbd81);
        eq(0,dvd.setStream(sel),"physical second AC3 selector");
        byte[] bytes=Files.readAllBytes(source);int pos=0;int first=Math.min(32768,bytes.length);
        dvd.push(push(bytes,0,first,0));pos=first;
        HlsSessionManager.Session hls=HlsSessionManager.startDvd(dvd);
        while(pos<bytes.length){int n=Math.min(32768,bytes.length-pos);int flags=pos+n==bytes.length?0x80:0;dvd.push(push(bytes,pos,n,flags));pos+=n;}
        waitEnded(hls,30000);
        truth(hls.playlistReady&&hls.segments>0,"moving title produced HLS");
        truth(hls.exitCode==0,"FFmpeg exit 0: "+hls.stderrTail);
        truth(hls.dvdAudioWireCode==0xbd81,"wire audio selector retained");
        String stats=runCapture(Arrays.asList(ffmpeg,"-hide_banner","-loglevel","info","-i",hls.playlist.getAbsolutePath(),"-t","0.8","-vn",
            "-af","astats=metadata=1:reset=0","-f","null","-"),20);
        double z=parseLast(stats,"Zero crossings rate:");
        truth(z>0.03,"second authored 880Hz track selected, zero crossing rate="+z);
        truth(!HlsSessionManager.updateDvdBrowserProgress(hls,Math.max(0L,(long)(hls.hlsSeconds*1000.0)-900L)),"sub-threshold browser clock alone does not guess cell completion");
        truth(HlsSessionManager.updateDvdBrowserProgress(hls,Math.max(0L,(long)(hls.hlsSeconds*1000.0)-900L),true),"matching browser ended state marks decoder drained despite HLS duration rounding");
        eq(-2,dvd.push(emptyPoll(0x100)),"native drain sentinel after browser consumption");
        HlsSessionManager.stop(hls.id);dvd.close();pass++;
    }

    private static void testShortCell(Path source,boolean expectVideo) throws Exception {
        DvdNativeSession dvd=new DvdNativeSession();dvd.init(0);byte[] bytes=Files.readAllBytes(source);
        if(expectVideo){byte[] sel=new byte[8];writeInt(sel,0,0);writeInt(sel,4,0xbd80);eq(0,dvd.setStream(sel),"silent menu retains selected title-audio preference");}
        int first=Math.min(16384,bytes.length);dvd.push(push(bytes,0,first,0));HlsSessionManager.Session hls=HlsSessionManager.startDvd(dvd);
        int pos=first;while(pos<bytes.length){int n=Math.min(16384,bytes.length-pos);dvd.push(push(bytes,pos,n,pos+n==bytes.length?0x80:0));pos+=n;}
        if(pos==first)dvd.push(emptyPoll(0x80));
        waitEnded(hls,20000);
        truth(hls.playlistReady&&hls.segments>=1,"short "+(expectVideo?"still":"audio")+" cell produced playable HLS");
        if(expectVideo)truth(dvd.epochHasVideo()&&dvd.epochStillCandidate(),"one-picture menu classified as still candidate");
        HlsSessionManager.updateDvdBrowserProgress(hls,(long)(hls.hlsSeconds*1000.0));
        eq(-2,dvd.push(emptyPoll(0x100)),"short cell drains without reserve deadlock");
        HlsSessionManager.stop(hls.id);dvd.close();pass++;
    }


    private static void testCadence(String ffmpeg,Path source) throws Exception {
        DvdNativeSession dvd=new DvdNativeSession();dvd.init(0);byte[] bytes=Files.readAllBytes(source);int first=Math.min(16384,bytes.length);dvd.push(push(bytes,0,first,0));
        HlsSessionManager.Session hls=HlsSessionManager.startDvd(dvd);int pos=first;while(pos<bytes.length){int n=Math.min(16384,bytes.length-pos);dvd.push(push(bytes,pos,n,pos+n==bytes.length?0x80:0));pos+=n;}if(pos==first)dvd.push(emptyPoll(0x80));
        waitEnded(hls,20000);truth(hls.playlistReady,"PAL cadence output playlist");
        String ffprobe=new File(new File(ffmpeg).getParentFile(),"ffprobe").getAbsolutePath();
        String text=runCapture(Arrays.asList(ffprobe,"-v","error","-select_streams","v:0","-show_entries","stream=avg_frame_rate","-of","default=nw=1:nk=1",hls.playlist.getAbsolutePath()),15).trim();
        String rate=text.split("\r?\n")[0].trim();String[] q=rate.split("/");double fps=q.length==2?Double.parseDouble(q[0])/Double.parseDouble(q[1]):Double.parseDouble(rate);
        truth(fps>=24.8&&fps<=25.2,"PAL authored cadence preserved, fps="+fps);
        HlsSessionManager.updateDvdBrowserProgress(hls,(long)(hls.hlsSeconds*1000));eq(-2,dvd.push(emptyPoll(0x100)),"PAL cell drain");
        HlsSessionManager.stop(hls.id);dvd.close();pass++;
    }

    private static void testClockAndSpuIsolation() throws Exception {
        DvdNativeSession dvd=new DvdNativeSession();dvd.init(0);
        byte[] stc=new byte[4];writeInt(stc,0,45000);eq(0,dvd.setStc(stc),"stc");
        dvd.updatePlayerClock(0);long before=dvd.mediaTimeMs();truth(before>=999&&before<=1001,"45k STC -> 1 second");
        // A private_stream_1 SPU PES with PTS must not consume the A/V rebase.
        byte[] spu=pes(0xbd,90000,0x20);dvd.push(push(spu,0,spu.length,0));
        long afterSpu=dvd.clock().currentClock90k();truth(afterSpu==90000,"SPU PTS did not alter DVD clock");
        dvd.flush();dvd.updatePlayerClock(0);truth(dvd.mediaTimeMs()>=999,"post-FLUSH browser epoch preserves logical clock anchor");
        dvd.close();pass++;
    }

    private static void writeInt(byte[] b,int o,int v){b[o]=(byte)(v>>>24);b[o+1]=(byte)(v>>>16);b[o+2]=(byte)(v>>>8);b[o+3]=(byte)v;}
    private static byte[] pes(int streamId,long pts,int sub){byte[] p=new byte[32];p[0]=0;p[1]=0;p[2]=1;p[3]=(byte)streamId;p[4]=0;p[5]=26;p[6]=(byte)0x80;p[7]=(byte)0x80;p[8]=5;
        p[9]=(byte)(0x20|(((pts>>30)&7)<<1)|1);p[10]=(byte)(pts>>22);p[11]=(byte)((((pts>>15)&0x7f)<<1)|1);p[12]=(byte)(pts>>7);p[13]=(byte)(((pts&0x7f)<<1)|1);p[14]=(byte)sub;return p;}
    private static byte[] push(byte[] source,int off,int len,int flags){byte[] b=new byte[len+8];writeInt(b,0,len);writeInt(b,4,flags);System.arraycopy(source,off,b,8,len);return b;}
    private static byte[] emptyPoll(int flags){byte[] b=new byte[8];writeInt(b,0,0);writeInt(b,4,flags);return b;}
    private static void waitEnded(HlsSessionManager.Session s,long ms)throws Exception{long end=System.currentTimeMillis()+ms;while(System.currentTimeMillis()<end&&!s.dvdConverterEnded){HlsSessionManager.refreshMetrics(s);Thread.sleep(50);}if(!s.dvdConverterEnded)throw new AssertionError("DVD converter timeout: "+s.stderrTail);HlsSessionManager.refreshMetrics(s);}
    private static void run(List<String> cmd,int sec)throws Exception{Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();ByteArrayOutputStream out=new ByteArrayOutputStream();copy(p.getInputStream(),out);if(!p.waitFor(sec,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("timeout: "+cmd);}if(p.exitValue()!=0)throw new AssertionError("command failed "+p.exitValue()+": "+out.toString("UTF-8"));}
    private static String runCapture(List<String> cmd,int sec)throws Exception{Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();ByteArrayOutputStream out=new ByteArrayOutputStream();copy(p.getInputStream(),out);if(!p.waitFor(sec,TimeUnit.SECONDS)){p.destroyForcibly();throw new AssertionError("timeout: "+cmd);}return out.toString("UTF-8");}
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[8192];int n;while((n=in.read(b))>=0)out.write(b,0,n);}
    private static double parseLast(String text,String key){double v=Double.NaN;for(String line:text.split("\\r?\\n")){int i=line.indexOf(key);if(i>=0)try{v=Double.parseDouble(line.substring(i+key.length()).trim());}catch(Exception ignored){}}if(Double.isNaN(v))throw new AssertionError("missing "+key+" in "+text);return v;}
    private static void truth(boolean v,String m){if(!v)throw new AssertionError(m);}private static void eq(long e,long a,String m){if(e!=a)throw new AssertionError(m+" expected="+e+" actual="+a);}
    private static void delete(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] c=f.listFiles();if(c!=null)for(File x:c)delete(x);}f.delete();}
}
