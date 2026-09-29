package org.opensagetv.webplayer;

/** Truthful capability declaration through P06. */
final class SubtitleCapabilities {
    private SubtitleCapabilities(){}
    static String json(){return "{\"contractVersion\":1,\"limits\":"+SubtitleLimits.json()+",\"families\":{"+
            "\"cea608\":{\"state\":\"implemented-baseline\",\"renderer\":\"browser-local\"},"+
            "\"cea708\":{\"state\":\"implemented-baseline\",\"renderer\":\"browser-local\"},"+
            "\"teletext\":{\"state\":\"implemented-level1-subtitle\",\"advertised\":true,\"renderer\":\"browser-local-text\"},"+
            "\"dvbBitmap\":{\"state\":\"implemented-generated-vectors\",\"advertised\":true,\"renderer\":\"browser-local-rgba\",\"fieldAcceptance\":\"pending\"},"+
            "\"pgs\":{\"state\":\"implemented-ffmpeg-bitmap-adapter\",\"advertised\":true,\"renderer\":\"browser-local-rgba\"},"+
            "\"vobsub\":{\"state\":\"implemented-ffmpeg-bitmap-adapter\",\"advertised\":true,\"renderer\":\"browser-local-rgba\",\"palette\":\"container-or-idx-required\"},"+
            "\"textFile\":{\"state\":\"implemented-webvtt-adapter\",\"advertised\":true,\"formats\":[\"subrip\",\"webvtt\",\"ass\",\"ssa\"],\"styleLoss\":\"ASS/SSA advanced styling is flattened\"}}}";}
}
