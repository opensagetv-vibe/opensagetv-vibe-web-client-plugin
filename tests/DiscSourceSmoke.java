package org.opensagetv.webplayer;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

public final class DiscSourceSmoke {
    public static void main(String[] args) throws Exception {
        int passed=0;
        testUrlDecoding(); passed++;
        testDuplicateNameSafety(); passed++;
        testStaleRootSuffix(); passed++;
        testDiscTaxonomy(); passed++;
        testTitleParser(); passed++;
        testInvalidMetadataDetection(); passed++;
        testCapabilityProbe(args.length>0?args[0]:"ffmpeg"); passed++;
        System.out.println("DiscSourceSmoke PASS "+passed+"/7");
    }

    private static void testUrlDecoding() throws Exception {
        File a=MediaUrlPath.decode("/media/My+Movie%20%C3%9Cnicode.mkv");
        eq("My+Movie Ünicode.mkv",a.getName(),"literal plus and UTF-8 percent decode");
        File b=MediaUrlPath.decode("stv://server/media/Folder%2BOne/VIDEO_TS");
        eq("VIDEO_TS",b.getName(),"stv path basename");
        check(b.getParentFile().getName().equals("Folder+One"),"encoded plus preserved");
    }

    private static void testDuplicateNameSafety(){
        List<File> files=Arrays.asList(new File("/a/one/movie.mkv"),new File("/b/two/movie.mkv"));
        check(AuthorizedPathMatcher.uniqueBestMatch(new File("/stale/movie.mkv"),files)==-1,"basename-only fallback must fail");
        check(AuthorizedPathMatcher.uniqueBestMatch(new File("/stale/two/movie.mkv"),files)==1,"parent+basename unique match");
    }

    private static void testStaleRootSuffix(){
        List<File> files=Arrays.asList(new File("/srv/media/tv/Show/episode.ts"));
        check(AuthorizedPathMatcher.uniqueBestMatch(new File("Z:\\mapped\\tv\\Show\\episode.ts"),files)==0,"mapped root suffix match");
        check(AuthorizedPathMatcher.bestSuffixScore(new File("/x/Show/episode.ts"),files.get(0))>=2,"suffix score");
    }

    private static void testDiscTaxonomy() throws Exception {
        File root=Files.createTempDirectory("p10-disc").toFile();root.deleteOnExit();
        File videoTs=new File(root,"VIDEO_TS");check(videoTs.mkdir(),"create VIDEO_TS");
        touch(new File(videoTs,"VIDEO_TS.IFO"));
        DiscSourceInfo parent=DiscSourceInfo.classify(root,true,false,false);
        check(parent.kind==DiscSourceInfo.Kind.DVD_MOUNTED,"dvd parent/mounted classification");
        check(parent.videoTs!=null&&parent.videoTs.getName().equals("VIDEO_TS"),"video ts root");
        DiscSourceInfo direct=DiscSourceInfo.classify(videoTs,true,false,false);
        check(direct.kind==DiscSourceInfo.Kind.DVD_VIDEO_TS,"direct VIDEO_TS classification");
        DiscSourceInfo iso=DiscSourceInfo.classify(new File(root,"Movie.ISO"),true,false,false);
        check(iso.kind==DiscSourceInfo.Kind.DVD_ISO&&"stock-server-iso-mount".equals(iso.stage),"ISO mount boundary");
        DiscSourceInfo vob=DiscSourceInfo.classify(new File(videoTs,"VTS_01_1.VOB"),false,false,false);
        check(vob.kind==DiscSourceInfo.Kind.VOB_FILE&&!vob.interactiveNative,"VOB is ordinary/non-navigable");
        DiscSourceInfo mkv=DiscSourceInfo.classify(new File(root,"disc-rip.mkv"),false,false,false);
        check(mkv.kind==DiscSourceInfo.Kind.IMPORTED_MKV,"MKV not DVD");
        File bdmv=new File(root,"BDMV");check(bdmv.mkdir(),"create BDMV");
        DiscSourceInfo br=DiscSourceInfo.classify(root,false,true,false);
        check(br.kind==DiscSourceInfo.Kind.BLURAY_BDMV&&!br.interactiveNative,"Blu-ray scope separated");
    }

    private static void testTitleParser(){
        String flat="format.duration=\"3723.500000\"\n"+
                "streams.stream.0.codec_type=\"video\"\n"+
                "streams.stream.1.codec_type=\"audio\"\n"+
                "streams.stream.2.codec_type=\"subtitle\"\n"+
                "chapters.chapter.0.id=1\nchapters.chapter.0.start_time=\"0.000000\"\nchapters.chapter.0.end_time=\"120.000000\"\n"+
                "chapters.chapter.1.id=2\nchapters.chapter.1.start_time=\"120.000000\"\nchapters.chapter.1.end_time=\"245.000000\"\n";
        DiscTitleProbe.Title t=DiscTitleProbe.parseFlat(3,flat);
        check(t.available&&t.title==3,"explicit title identity");
        check(Math.abs(t.duration-3723.5)<0.001,"title duration");
        check(t.chapters.size()==2&&t.chapters.get(1).id==2,"chapter metadata");
        check(t.videoStreams==1&&t.audioStreams==1&&t.subtitleStreams==1,"stream metadata");
        String json=t.json();check(json.contains("\"timelineBase\":\"dvd-title\"")&&json.contains("\"chapterTimesRelativeToTitle\":true"),"title/chapter timeline semantics");
    }

    private static void testInvalidMetadataDetection(){
        List<String> issues=DiscMetadataHealth.detectIssues(1,1,16L*1024L*1024L,"","");
        check(issues.contains("implausibly-short-file-duration"),"1 ms file duration reported as invalid metadata");
        check(issues.contains("implausibly-short-playback-duration"),"1 ms playback duration reported as invalid metadata");
        check(issues.contains("missing-stream-metadata"),"missing stream metadata detected");
    }

    private static void testCapabilityProbe(String ffmpeg){
        DiscCapabilityProbe p=DiscCapabilityProbe.probe(ffmpeg);
        check(p.executable,"FFmpeg executable");
        check(p.dvdvideoDemuxer,"dvdvideo demuxer");
        check(p.libdvdread&&p.libdvdnav,"libdvdread/libdvdnav enabled");
        check(p.movieOnlyAvailable(),"movie-only capability gate");
    }

    private static void touch(File f)throws Exception{try(FileOutputStream o=new FileOutputStream(f)){o.write("fixture".getBytes(StandardCharsets.US_ASCII));}f.deleteOnExit();}
    private static void eq(String a,String b,String m){if(!a.equals(b))throw new AssertionError(m+": expected='"+a+"' actual='"+b+"'");}
    private static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
}
