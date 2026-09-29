package org.opensagetv.webplayer;

import java.io.File;
import java.util.*;

/** Command-level coverage for full hardware decode+encode attempts on every GPU backend. */
public final class HardwarePipelineSmoke {
    static int n;
    static void ok(boolean v,String m){if(!v)throw new AssertionError(m);n++;System.out.println("PASS "+m);}
    static boolean hasPair(List<String> c,String a,String b){for(int i=0;i+1<c.size();i++)if(a.equals(c.get(i))&&b.equals(c.get(i+1)))return true;return false;}
    static StreamPlan plan(){
        MediaProbe.Info i=new MediaProbe.Info();
        MediaProbe.Track v=new MediaProbe.Track();v.index=0;v.type="video";v.codec="mpeg2video";v.width=1920;v.height=1080;v.field="tt";v.rate="30000/1001";i.tracks.add(v);
        MediaProbe.Track a=new MediaProbe.Track();a.index=1;a.type="audio";a.codec="ac3";a.channels=6;a.language="eng";i.tracks.add(a);
        Map<String,String> m=new HashMap<String,String>();m.put("player","hls");m.put("videoMode","transcode");m.put("resolution","720");m.put("fps","59.94");m.put("deinterlace","auto");m.put("audioCodec","aac");m.put("allowFallback","true");
        return new StreamPlan(StreamOptions.fromMap(m),i);
    }
    static TranscoderManager.Probe p(String enc,String dec,boolean filters,String name){return new TranscoderManager.Probe(true,"/usr/bin/ffmpeg","test",enc,true,true,filters,dec,name,"test full hardware");}
    static List<String> cmd(TranscoderManager.Probe p){return StreamCommand.build(p,new File("/tmp/source.ts"),false,false,0,new File("/tmp/out"),plan());}
    public static void main(String[] args){
        Map<String,String> dvdPrefs=new HashMap<String,String>();dvdPrefs.put("player","mpegts");dvdPrefs.put("videoMode","copy");dvdPrefs.put("encoder","qsv");dvdPrefs.put("audioCodec","copy");
        StreamOptions dvdOptions=StreamOptions.fromMap(dvdPrefs).forDvdTranscode();
        ok("hls".equals(dvdOptions.player)&&"transcode".equals(dvdOptions.videoMode)&&"qsv".equals(dvdOptions.encoder)&&"aac".equals(dvdOptions.audioCodec),"DVD forces H.264/AAC HLS while preserving selected hardware encoder");

        List<String> q=cmd(p("h264_qsv","QSV",true,"Intel Quick Sync"));
        ok(hasPair(q,"-hwaccel","qsv")&&hasPair(q,"-hwaccel_output_format","qsv"),"QSV attempts hardware decode surfaces");
        List<String> dvdQsv=TranscoderManager.buildDvdHlsCommand(p("h264_qsv","QSV",true,"Intel Quick Sync"),0xbd80,new File("/tmp/dvd-hls"));
        int dvdInput=dvdQsv.indexOf("-i"),dvdHw=dvdQsv.indexOf("-hwaccel");
        ok(dvdHw>=0&&dvdInput>=0&&dvdHw<dvdInput,"DVD QSV hardware decode options precede the MPEG-PS input");
        ok(hasPair(dvdQsv,"-c:v","h264_qsv")&&hasPair(dvdQsv,"-c:a","aac"),"DVD path always transcodes MPEG-2 video to browser H.264/AAC");
        ok(hasPair(dvdQsv,"-map","0:i:0x80?"),"DVD physical audio mapping is optional for silent menu/still cells");
        ok(q.get(q.indexOf("-vf")+1).contains("vpp_qsv")&&q.get(q.indexOf("-vf")+1).contains("deinterlace=advanced")&&q.get(q.indexOf("-vf")+1).contains("framerate=60000/1001"),"QSV uses hardware VPP deinterlace/scale/FPS");
        ok(hasPair(q,"-c:v","h264_qsv"),"QSV hardware encode remains selected");

        List<String> nv=cmd(p("h264_nvenc","CUDA/NVDEC",true,"NVIDIA NVENC"));
        ok(hasPair(nv,"-hwaccel","cuda")&&hasPair(nv,"-hwaccel_output_format","cuda"),"NVIDIA attempts NVDEC/CUDA decode surfaces");
        ok(nv.get(nv.indexOf("-vf")+1).contains("yadif_cuda")&&nv.get(nv.indexOf("-vf")+1).contains("scale_cuda"),"NVIDIA uses CUDA deinterlace/scale");
        ok(hasPair(nv,"-c:v","h264_nvenc"),"NVIDIA hardware encode remains selected");

        List<String> va=cmd(p("h264_vaapi","VAAPI",true,"VAAPI"));
        ok(hasPair(va,"-hwaccel","vaapi")&&hasPair(va,"-hwaccel_output_format","vaapi"),"VAAPI attempts hardware decode surfaces");
        ok(va.get(va.indexOf("-vf")+1).contains("deinterlace_vaapi")&&va.get(va.indexOf("-vf")+1).contains("scale_vaapi"),"VAAPI uses hardware deinterlace/scale");
        ok(hasPair(va,"-c:v","h264_vaapi"),"VAAPI hardware encode remains selected");

        List<String> amf=cmd(p("h264_amf","D3D11VA",false,"AMD AMF"));
        ok(hasPair(amf,"-hwaccel","d3d11va"),"AMD AMF path attempts D3D11VA hardware decode");
        ok(amf.get(amf.indexOf("-vf")+1).contains("yadif")&&amf.get(amf.indexOf("-vf")+1).contains("format=nv12"),"AMD keeps software filters only when GPU VPP is unavailable");
        ok(hasPair(amf,"-c:v","h264_amf"),"AMD hardware encode remains selected");

        List<String> vt=cmd(p("h264_videotoolbox","VideoToolbox",false,"Apple VideoToolbox"));
        ok(hasPair(vt,"-hwaccel","videotoolbox"),"VideoToolbox path attempts hardware decode");
        ok(hasPair(vt,"-c:v","h264_videotoolbox"),"VideoToolbox hardware encode remains selected");

        TranscoderManager.Probe mixed=TranscoderManager.encodeOnlyFallback(p("h264_qsv","QSV",true,"Intel Quick Sync"),"decode startup failed");
        ok(mixed.hardware&&!mixed.hardwareDecode&&"h264_qsv".equals(mixed.videoEncoder),"decode failure falls back to hardware encode before software encode");
        List<String> mixedCmd=cmd(mixed);ok(!mixedCmd.contains("-hwaccel")&&hasPair(mixedCmd,"-c:v","h264_qsv"),"encode-only retry removes hardware decode but keeps QSV encode");
        System.out.println("Hardware pipeline: "+n+" tests PASS");
    }
}
