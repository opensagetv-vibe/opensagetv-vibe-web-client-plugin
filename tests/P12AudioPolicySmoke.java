package org.opensagetv.webplayer;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class P12AudioPolicySmoke {
    private static int pass;
    public static void main(String[] args) throws Exception {
        primaryLanguageBeatsCommentary();
        strictFallbackRejectsMissingLanguage();
        signedDelayAndQualityControlsReachCommand();
        copyIgnoresEncoderQualityControls();
        runtimeTruthSeparatesDecodeFilterEncode();
        mediaRateContractIsTruthful();
        System.out.println("P12 audio/quality policy: "+pass+" PASS");
    }
    private static MediaProbe.Info fixture() {
        return MediaProbe.parse(
            "streams.stream.0.index=0\nstreams.stream.0.codec_type=video\nstreams.stream.0.codec_name=mpeg2video\nstreams.stream.0.width=720\nstreams.stream.0.height=480\nstreams.stream.0.field_order=tt\nstreams.stream.0.r_frame_rate=30000/1001\n"+
            "streams.stream.1.index=1\nstreams.stream.1.codec_type=audio\nstreams.stream.1.codec_name=ac3\nstreams.stream.1.channels=2\nstreams.stream.1.tags.language=eng\nstreams.stream.1.tags.title=Narrative Audio / Commentary\nstreams.stream.1.disposition.default=0\nstreams.stream.1.disposition.visual_impaired=1\n"+
            "streams.stream.2.index=2\nstreams.stream.2.codec_type=audio\nstreams.stream.2.codec_name=ac3\nstreams.stream.2.channels=6\nstreams.stream.2.tags.language=eng\nstreams.stream.2.tags.title=English Main\nstreams.stream.2.disposition.default=1\n"+
            "streams.stream.3.index=3\nstreams.stream.3.codec_type=audio\nstreams.stream.3.codec_name=aac\nstreams.stream.3.channels=2\nstreams.stream.3.tags.language=spa\nstreams.stream.3.tags.title=Spanish\n");
    }
    private static Map<String,String> base() {
        Map<String,String> m=new HashMap<String,String>();
        m.put("profileSchemaVersion","7");m.put("videoMode","transcode");m.put("encoder","software");m.put("audioCodec","aac");m.put("audioChannels","2");
        return m;
    }
    private static void primaryLanguageBeatsCommentary() {
        Map<String,String> m=base();m.put("audioLanguage","eng");m.put("audioFallback","default");
        StreamPlan p=new StreamPlan(StreamOptions.fromMap(m),fixture());
        eq(2,p.audio.index,"primary English selected instead of leading NAR/commentary");
        pass++;
    }
    private static void strictFallbackRejectsMissingLanguage() {
        Map<String,String> m=base();m.put("audioLanguage","fra");m.put("audioFallback","strict");
        boolean threw=false;try{new StreamPlan(StreamOptions.fromMap(m),fixture());}catch(IllegalArgumentException e){threw=e.getMessage().contains("not available");}
        yes(threw,"strict missing-language policy");pass++;
    }
    private static void signedDelayAndQualityControlsReachCommand() throws Exception {
        Map<String,String> m=base();m.put("audioLanguage","eng");m.put("audioOffsetMs","500");m.put("videoKbps","6000");m.put("keyFrameSeconds","2");m.put("bFrames","2");m.put("deinterlace","on");
        StreamPlan p=new StreamPlan(StreamOptions.fromMap(m),fixture());
        TranscoderManager.Probe probe=new TranscoderManager.Probe(true,"/usr/bin/ffmpeg","test","libx264",false,false,"software","test");
        List<String> c=StreamCommand.build(probe,new File("in.ts"),false,false,0,new File("."),p);
        contains(c,"adelay=500:all=1","positive delay"); containsPair(c,"-bf","2","B-frames"); containsPair(c,"-g","60","2-second GOP at 29.97"); containsPair(c,"-b:v","6000k","bitrate");
        m.put("audioOffsetMs","-500");p=new StreamPlan(StreamOptions.fromMap(m),fixture());c=StreamCommand.build(probe,new File("in.ts"),false,false,0,new File("."),p);
        contains(c,"atrim=start=0.500","negative delay trim");pass++;
    }
    private static void copyIgnoresEncoderQualityControls() throws Exception {
        MediaProbe.Info i=MediaProbe.parse("streams.stream.0.index=0\nstreams.stream.0.codec_type=video\nstreams.stream.0.codec_name=h264\nstreams.stream.0.width=1280\nstreams.stream.0.height=720\nstreams.stream.0.field_order=progressive\nstreams.stream.0.r_frame_rate=30/1\nstreams.stream.1.index=1\nstreams.stream.1.codec_type=audio\nstreams.stream.1.codec_name=aac\nstreams.stream.1.channels=2\n");
        Map<String,String> m=base();m.put("videoMode","copy");m.put("deinterlace","off");m.put("videoKbps","12345");m.put("keyFrameSeconds","7");m.put("bFrames","3");m.put("audioCodec","copy");m.put("audioChannels","source");
        StreamPlan p=new StreamPlan(StreamOptions.fromMap(m),i);yes(p.copyVideo,"video copied");
        TranscoderManager.Probe probe=new TranscoderManager.Probe(true,"/usr/bin/ffmpeg","test","copy",false,false,"none","copy");
        List<String> c=StreamCommand.build(probe,new File("in.ts"),false,false,0,new File("."),p);
        containsPair(c,"-c:v","copy","video copy command"); no(c,"-b:v","copy ignores video bitrate"); no(c,"-g","copy ignores GOP"); no(c,"-bf","copy ignores B-frames");pass++;
    }
    private static void runtimeTruthSeparatesDecodeFilterEncode() {
        Map<String,String> m=base();m.put("audioLanguage","eng");m.put("encoder","nvenc");m.put("deinterlace","on");
        StreamPlan p=new StreamPlan(StreamOptions.fromMap(m),fixture());String j=p.json("h264_nvenc",true,false,"NVIDIA NVENC","driver fallback detail");
        yes(j.contains("\"hardwareEncode\":true")&&j.contains("\"hardwareDecode\":false")&&j.contains("\"endToEndGpu\":false"),"truthful GPU stages");
        yes(j.contains("\"pipelineDetail\":\"driver fallback detail\""),"pipeline detail reported");
        Map<String,String> q=base();q.put("audioLanguage","eng");q.put("encoder","qsv");q.put("deinterlace","on");
        StreamPlan qp=new StreamPlan(StreamOptions.fromMap(q),fixture());
        j=qp.json("libx264",false,false,"software","Requested encoder h264_qsv unavailable; using libx264");
        yes(j.contains("\"fallbackReason\":\"Requested encoder h264_qsv unavailable; using libx264\""),"fallback reason reported");pass++;
    }
    private static void mediaRateContractIsTruthful() throws Exception {
        MiniClientSession s=new MiniClientSession("p12","127.0.0.1:9","02:00:00:00:12:12",640,360);
        byte[] d=new byte[4];putInt(d,Float.floatToIntBits(1.5f));byte[] r=s.media().handle(30,d);
        float accepted=Float.intBitsToFloat(MiniClientSession.readInt(r,0));yes(Math.abs(accepted-1.5f)<0.001f,"1.5x accepted");
        putInt(d,Float.floatToIntBits(-2.0f));r=s.media().handle(30,d);accepted=Float.intBitsToFloat(MiniClientSession.readInt(r,0));yes(Math.abs(accepted-1.0f)<0.001f,"reverse rejected as unsupported");
        java.lang.reflect.Method m=MiniClientSession.class.getDeclaredMethod("property",String.class);m.setAccessible(true);
        eqs("NATIVE_FORWARD_0.5_TO_2",String.valueOf(m.invoke(s,"VIDEO_PLAYBACK_RATE")),"rate capability");
        eqs("",String.valueOf(m.invoke(s,"FRAME_STEP")),"frame step remains unadvertised");pass++;
    }
    private static void putInt(byte[] b,int v){b[0]=(byte)(v>>>24);b[1]=(byte)(v>>>16);b[2]=(byte)(v>>>8);b[3]=(byte)v;}
    private static void contains(List<String> c,String s,String n){for(String v:c)if(v.contains(s))return;throw new AssertionError(n+": "+c);}
    private static void containsPair(List<String> c,String a,String b,String n){for(int i=0;i+1<c.size();i++)if(a.equals(c.get(i))&&b.equals(c.get(i+1)))return;throw new AssertionError(n+": "+c);}
    private static void no(List<String> c,String a,String n){if(c.contains(a))throw new AssertionError(n+": "+c);}
    private static void eqs(String a,String b,String n){if(!a.equals(b))throw new AssertionError(n+" expected="+a+" actual="+b);}
    private static void eq(int a,int b,String n){if(a!=b)throw new AssertionError(n+" expected="+a+" actual="+b);}
    private static void yes(boolean v,String n){if(!v)throw new AssertionError(n);}
}
