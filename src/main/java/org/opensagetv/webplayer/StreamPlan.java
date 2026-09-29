package org.opensagetv.webplayer;

import java.util.List;

/** Resolved choices are observable; a saved bitrate is not misreported as the bitrate of copied video. */
final class StreamPlan {
    final StreamOptions options;
    final MediaProbe.Info source;
    final MediaProbe.Track video,audio,subtitle;
    final boolean copyVideo,copyAudio;
    final String audioEncoder,audioMap,reason;
    StreamPlan(StreamOptions o,MediaProbe.Info source) {
        this.options=o;this.source=source;video=source.video();
        List<MediaProbe.Track> tracks=source.audio();MediaProbe.Track chosen=AudioTrackPolicy.choose(tracks,o);audio=chosen;
        MediaProbe.Track sub=null;List<MediaProbe.Track> subs=source.ordinarySubtitles();
        if("track".equals(o.subtitleMode)){if(o.subtitleTrack<0)throw new IllegalArgumentException("Choose an ordinary subtitle track or select Auto/Off.");for(MediaProbe.Track t:subs)if(t.index==o.subtitleTrack)sub=t;if(sub==null)throw new IllegalArgumentException("Selected ordinary subtitle track is not in this recording. Refresh tracks or select Automatic/Off.");}
        else if("auto".equals(o.subtitleMode)){String want=MediaProbe.languageKey(o.subtitleLanguage);for(MediaProbe.Track t:subs){if(o.subtitleForcedOnly&&!t.forced)continue;if(!want.isEmpty()&&want.equals(MediaProbe.languageKey(t.language))){sub=t;break;}}if(sub==null)for(MediaProbe.Track t:subs){if(o.subtitleForcedOnly&&!t.forced)continue;if(t.defaultTrack){sub=t;break;}}if(sub==null)for(MediaProbe.Track t:subs){if(!o.subtitleForcedOnly||t.forced){sub=t;break;}}}
        subtitle=sub;
        audioMap=audio==null?"0:a:0?":"0:"+audio.index;
        boolean compatible=video!=null&&("h264".equals(video.codec)||("hevc".equals(video.codec)&&o.hevcSupported));
        boolean mpeg2=video!=null&&"mpeg2video".equals(video.codec);
        boolean filters=!"source".equals(o.resolution)||!"source".equals(o.fps)||"on".equals(o.deinterlace)||
                ("auto".equals(o.deinterlace)&&video!=null&&video.interlaced());
        boolean explicitCopyFilters=!"source".equals(o.resolution)||!"source".equals(o.fps)||"on".equals(o.deinterlace);
        boolean copySelected="copy".equals(o.videoMode);
        // Explicit Copy means exactly that: ask FFmpeg to stream-copy the first
        // video stream and let the HLS muxer/browser prove whether that codec is
        // usable.  Do not reject an unknown or uncommon codec in policy before
        // FFmpeg gets a chance to try it.  Auto remains conservative below.
        if(copySelected&&explicitCopyFilters)
            throw new IllegalArgumentException("Video Copy requires Source resolution/FPS and Deinterlace Auto/Off because copied video cannot be resized, frame-rate converted, or deinterlaced.");
        copyVideo=copySelected||(!"transcode".equals(o.videoMode)&&compatible&&!filters);
        reason=copySelected&&mpeg2?"Copy selected; MPEG-2 video is passed through unchanged and remuxed/resegmented for HLS":
            copySelected&&video!=null?"Copy selected; "+video.codec+" video is passed through without video transcoding":
            copySelected?"Copy selected; source codec was not identified by the probe, so FFmpeg will attempt video stream copy":
            copyVideo?"Compatible video is copied; video bitrate/encoder settings apply only when transcoding":
            !compatible?"Video codec unknown or not supported by this browser":"Video processing/transcoding requested";
        String ac=audio==null?"":audio.codec;
        boolean audioCompatible="aac".equals(ac)||("ac3".equals(ac)&&o.ac3Supported);
        boolean channelChange=!"source".equals(o.audioChannels)&&(audio==null||audio.channels!=Integer.parseInt(o.audioChannels));
        if("ac3".equals(o.audioCodec)&&!o.ac3Supported)throw new IllegalArgumentException("This browser did not report AC-3 support. Select AAC or Automatic.");
        if("copy".equals(o.audioCodec)&&(!audioCompatible||channelChange||o.audioOffsetMs!=0))
            throw new IllegalArgumentException("Audio copy requires a supported source codec, matching/Source channels, and zero audio offset.");
        copyAudio=("copy".equals(o.audioCodec)||"auto".equals(o.audioCodec))&&audioCompatible&&!channelChange&&o.audioOffsetMs==0;
        audioEncoder=copyAudio?"copy":"ac3".equals(o.audioCodec)?"ac3":"aac";
    }
    String json(String videoEncoder) { return json(videoEncoder,false,false,false,"","",""); }
    String json(String videoEncoder, boolean hardwareEncode, boolean hardwareDecode, String accelerator, String pipelineDetail) {
        return json(videoEncoder,hardwareEncode,hardwareDecode,false,hardwareDecode?accelerator:"",accelerator,pipelineDetail);
    }
    String json(String videoEncoder, boolean hardwareEncode, boolean hardwareDecode, boolean hardwareFilters, String decodeAccelerator, String accelerator, String pipelineDetail) {
        String decoder=copyVideo?"copy":hardwareDecode?"hardware":"software";
        String fallbackReason="";String requested=options.encoder;
        if(!copyVideo){String effective=videoEncoder==null?"":videoEncoder;boolean explicitMismatch=!"auto".equals(requested)&&!"software".equals(requested)&&!effective.endsWith(requested);if(("auto".equals(requested)&&!hardwareEncode)||explicitMismatch)fallbackReason=pipelineDetail==null?"":pipelineDetail;}
        boolean processingNeeded=!"source".equals(options.resolution)||!"source".equals(options.fps)||"on".equals(options.deinterlace)||(
                "auto".equals(options.deinterlace)&&video!=null&&video.interlaced());
        String filters=copyVideo?"none":processingNeeded?(hardwareFilters?"gpu":"software"):"none";
        boolean endToEndGpu=!copyVideo&&hardwareEncode&&hardwareDecode&&(!processingNeeded||hardwareFilters);
        return "{\"video\":\""+(copyVideo?"copy":HttpUtil.json(videoEncoder))+"\",\"mpeg2Copy\":"+("copy".equals(options.videoMode)&&video!=null&&"mpeg2video".equals(video.codec)&&copyVideo)+",\"audio\":\""+audioEncoder+"\",\"audioStreamIndex\":"+(audio==null?"null":audio.index)+
            ",\"audioLanguage\":\""+HttpUtil.json(audio==null?"":audio.language)+"\",\"audioTitle\":\""+HttpUtil.json(audio==null?"":audio.title)+"\",\"audioDelayMs\":"+options.audioOffsetMs+
            ",\"videoTargetKbps\":"+(copyVideo?"null":options.videoKbps)+",\"audioTargetKbps\":"+(copyAudio?"null":options.audioKbps)+
            ",\"requestedEncoder\":\""+HttpUtil.json(options.encoder)+"\",\"effectiveEncoder\":\""+HttpUtil.json(copyVideo?"copy":videoEncoder)+"\",\"decode\":\""+decoder+"\",\"decodeAccelerator\":\""+HttpUtil.json(decodeAccelerator)+"\",\"filters\":\""+filters+"\",\"hardwareEncode\":"+hardwareEncode+",\"hardwareDecode\":"+hardwareDecode+",\"hardwareFilters\":"+hardwareFilters+",\"endToEndGpu\":"+endToEndGpu+
            ",\"accelerator\":\""+HttpUtil.json(accelerator)+"\",\"pipelineDetail\":\""+HttpUtil.json(pipelineDetail)+"\",\"fallbackReason\":\""+HttpUtil.json(fallbackReason)+"\",\"reason\":\""+HttpUtil.json(reason)+"\",\"captionTransport\":\""+(options.teletextEnabled()?"original-source DVB Teletext decoder":options.captionsEnabled()?"original-video A/53 side channel":"off")+"\"}";
    }

}
