package org.opensagetv.webplayer;

import java.io.File;

public final class CodecHlsTestSmoke {
    private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    public static void main(String[] args)throws Exception{
        if(args.length>0)System.setProperty("sagetv.webplayer.ffmpeg",args[0]);
        CodecHlsTestManager.Result r=CodecHlsTestManager.generate(true);
        check(r.ready,"codec HLS fixtures were not ready");
        String json=r.json();
        check(json.contains("\"mpeg2-copy\"")&&json.contains("\"h264-transcode\"")&&json.contains("\"h264-copy\"")&&
              json.contains("\"mpeg2-720p5994-copy\"")&&json.contains("\"h264-720p5994-transcode\""),"variant JSON missing");
        check(json.contains("\"videoCodec\":\"mpeg2video\"")&&json.contains("\"videoCodec\":\"h264\"")&&
              json.contains("\"sourceFps\":\"60000/1001\"")&&json.contains("\"sourceAudioCodec\":\"ac3\""),"real-file parameter evidence missing");
        for(String v:CodecHlsTestManager.VARIANTS){
            File p=CodecHlsTestManager.mediaFile("/"+v+"/stream.m3u8");
            File s=CodecHlsTestManager.mediaFile("/"+v+"/seg_000.ts");
            check(p.isFile()&&p.length()>32,"playlist missing for "+v);
            check(s.isFile()&&s.length()>=188,"segment missing for "+v);
        }
        MediaProbe.Info realCopy=MediaProbe.read(CodecHlsTestManager.mediaFile("/mpeg2-720p5994-copy/seg_000.ts"),System.getProperty("sagetv.webplayer.ffmpeg","/usr/bin/ffmpeg"));
        check(realCopy.video()!=null&&"mpeg2video".equals(realCopy.video().codec)&&realCopy.video().width==1280&&realCopy.video().height==720&&"60000/1001".equals(realCopy.video().rate),"real-profile MPEG-2 copy parameters changed: "+realCopy.json());
        check(!realCopy.audio().isEmpty()&&"aac".equals(realCopy.audio().get(0).codec)&&realCopy.audio().get(0).channels==2,"real-profile Copy audio path should be AAC stereo: "+realCopy.json());
        MediaProbe.Info realH264=MediaProbe.read(CodecHlsTestManager.mediaFile("/h264-720p5994-transcode/seg_000.ts"),System.getProperty("sagetv.webplayer.ffmpeg","/usr/bin/ffmpeg"));
        check(realH264.video()!=null&&"h264".equals(realH264.video().codec)&&realH264.video().width==1280&&realH264.video().height==720&&"60000/1001".equals(realH264.video().rate),"real-profile H.264 transcode did not preserve 720p59.94: "+realH264.json());
        boolean rejected=false;try{CodecHlsTestManager.mediaFile("/mpeg2-copy/../source/mpeg2-aac.ts");}catch(Exception expected){rejected=true;}
        check(rejected,"path traversal should be rejected");
        rejected=false;try{CodecHlsTestManager.mediaFile("/not-a-variant/stream.m3u8");}catch(Exception expected){rejected=true;}
        check(rejected,"unknown variant should be rejected");
        System.out.println("Codec HLS synthetic/native test smoke PASS");
    }
}
