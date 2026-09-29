package org.opensagetv.webplayer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Read-only sanity report for imported/library metadata. Never mutates SageTV. */
final class DiscMetadataHealth {
    final long playbackDurationMs;final long fileDurationMs;final long bytes;final String metadataDuration;final String videoCodec;final String audioStreams;final List<String> issues=new ArrayList<String>();
    DiscMetadataHealth(SageApiBridge sage,Object mf){
        playbackDurationMs=sage.getPlaybackDuration(mf);fileDurationMs=sage.getFileDurationRaw(mf);metadataDuration=sage.getMediaFileMetadata(mf,"Duration");videoCodec=sage.getMediaFileMetadata(mf,"Format.Video.Codec");audioStreams=sage.getMediaFileMetadata(mf,"Format.Audio.NumStreams");
        long total=0;int count=sage.getNumberOfSegments(mf);for(int i=0;i<count;i++){File f=sage.getFileForSegment(mf,i);if(f!=null&&f.isFile())total+=Math.max(0L,f.length());}bytes=total;
        issues.addAll(detectIssues(playbackDurationMs,fileDurationMs,bytes,videoCodec,audioStreams));
    }
    static List<String> detectIssues(long playbackDurationMs,long fileDurationMs,long bytes,String videoCodec,String audioStreams){
        List<String> found=new ArrayList<String>();String video=videoCodec==null?"":videoCodec.trim();String audio=audioStreams==null?"":audioStreams.trim();
        if(playbackDurationMs<0||fileDurationMs<0)found.add("negative-duration");
        if(bytes>1024L*1024L&&fileDurationMs>0&&fileDurationMs<1000L)found.add("implausibly-short-file-duration");
        if(bytes>1024L*1024L&&playbackDurationMs>0&&playbackDurationMs<1000L)found.add("implausibly-short-playback-duration");
        if(bytes>1024L*1024L&&video.isEmpty()&&audio.isEmpty())found.add("missing-stream-metadata");
        return found;
    }
    boolean healthy(){return issues.isEmpty();}
    String json(){StringBuilder b=new StringBuilder("{\"healthy\":").append(healthy()).append(",\"playbackDurationMs\":").append(playbackDurationMs).append(",\"fileDurationMs\":").append(fileDurationMs).append(",\"sourceBytes\":").append(bytes).append(",\"metadataDuration\":\"").append(HttpUtil.json(metadataDuration)).append("\",\"videoCodec\":\"").append(HttpUtil.json(videoCodec)).append("\",\"audioStreams\":\"").append(HttpUtil.json(audioStreams)).append("\",\"issues\":[");for(int i=0;i<issues.size();i++){if(i>0)b.append(',');b.append('"').append(HttpUtil.json(issues.get(i))).append('"');}return b.append("],\"automaticRepair\":false,\"repairRequiresUserApproval\":true,\"backupRequired\":true,\"repairGuidance\":\"Back up wiz.bin/database state first, verify the physical media, then use a narrow SageTV metadata re-detect/reimport workflow for this MediaFile only. Playback never rewrites library metadata.\"}").toString();}
}
