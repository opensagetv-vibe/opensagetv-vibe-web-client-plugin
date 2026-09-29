package org.opensagetv.webplayer;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/** Bounded runtime probe for FFmpeg's DVD-Video demuxer and linked DVD libraries. */
final class DiscCapabilityProbe {
    final String ffmpeg;
    final boolean executable;
    final boolean dvdvideoDemuxer;
    final boolean libdvdread;
    final boolean libdvdnav;
    final String detail;

    private DiscCapabilityProbe(String ffmpeg,boolean executable,boolean dvdvideoDemuxer,boolean libdvdread,boolean libdvdnav,String detail){
        this.ffmpeg=ffmpeg;this.executable=executable;this.dvdvideoDemuxer=dvdvideoDemuxer;this.libdvdread=libdvdread;this.libdvdnav=libdvdnav;this.detail=detail;
    }

    boolean movieOnlyAvailable(){return executable&&dvdvideoDemuxer&&libdvdread&&libdvdnav;}

    static DiscCapabilityProbe probe(String configuredFfmpeg){
        String ffmpeg=configuredFfmpeg;
        if(ffmpeg==null||ffmpeg.trim().isEmpty()){
            File resolved=TranscoderManager.findExecutable();
            if(resolved==null)return new DiscCapabilityProbe("",false,false,false,false,"OpenSageTV Vibe FFmpeg Plugin transcoder is unavailable");
            ffmpeg=resolved.getAbsolutePath();
        }
        boolean explicit=ffmpeg.indexOf(File.separatorChar)>=0||ffmpeg.indexOf('/')>=0||ffmpeg.indexOf('\\')>=0;
        if(explicit){File f=new File(ffmpeg);if(!f.isFile()||!f.canExecute())return new DiscCapabilityProbe(ffmpeg,false,false,false,false,"Configured Vibe transcoder is not executable");}
        try{
            ProcessCapture.Result version=ProcessCapture.run(Arrays.asList(ffmpeg,"-hide_banner","-version"),4,64*1024);
            if(version.exit!=0)return new DiscCapabilityProbe(ffmpeg,false,false,false,false,"FFmpeg version probe failed");
            String lower=version.text.toLowerCase(Locale.ROOT);
            boolean read=lower.contains("--enable-libdvdread"),nav=lower.contains("--enable-libdvdnav");
            ProcessCapture.Result demux=ProcessCapture.run(Arrays.asList(ffmpeg,"-hide_banner","-demuxers"),4,256*1024);
            boolean dvd=demux.exit==0&&demux.text.toLowerCase(Locale.ROOT).contains("dvdvideo");
            return new DiscCapabilityProbe(ffmpeg,true,dvd,read,nav,dvd?"FFmpeg dvdvideo demuxer detected":"FFmpeg is present but dvdvideo demuxer is unavailable");
        }catch(IOException e){return new DiscCapabilityProbe(ffmpeg,false,false,false,false,compact(e.getMessage()));}
    }

    String json(){return "{\"ffmpeg\":\""+HttpUtil.json(ffmpeg)+"\",\"executable\":"+executable+",\"dvdvideoDemuxer\":"+dvdvideoDemuxer+",\"libdvdread\":"+libdvdread+",\"libdvdnav\":"+libdvdnav+",\"movieOnlyAvailable\":"+movieOnlyAvailable()+",\"detail\":\""+HttpUtil.json(detail)+"\"}";}
    private static String compact(String s){return s==null?"unknown error":s.replace('\r',' ').replace('\n',' ').trim();}
}
