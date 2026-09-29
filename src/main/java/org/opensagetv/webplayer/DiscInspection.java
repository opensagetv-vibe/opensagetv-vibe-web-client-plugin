package org.opensagetv.webplayer;

/** Complete read-only P10 report for one authorized SageTV MediaFile. */
final class DiscInspection {
    final DiscSourceInfo source;final DiscCapabilityProbe capability;final DiscMetadataHealth metadata;
    DiscInspection(DiscSourceInfo source,DiscCapabilityProbe capability,DiscMetadataHealth metadata){this.source=source;this.capability=capability;this.metadata=metadata;}
    static DiscInspection inspect(SageApiBridge sage,Object mediaFile){return new DiscInspection(DiscSourceInfo.inspect(sage,mediaFile),DiscCapabilityProbe.probe(null),new DiscMetadataHealth(sage,mediaFile));}
    String json(){return "{\"source\":"+source.json()+",\"capability\":"+capability.json()+",\"metadata\":"+metadata.json()+",\"timeline\":{\"libraryPlaybackDurationMs\":"+metadata.playbackDurationMs+",\"libraryFileDurationMs\":"+metadata.fileDurationMs+",\"dvdTitleTimeline\":\"separate-explicit-title-probe\",\"chapterTimes\":\"relative-to-selected-dvd-title\",\"automaticMainFeatureSelection\":false},\"scope\":{\"dvdNative\":true,\"movieOnly\":"+capability.movieOnlyAvailable()+",\"isoPluginMount\":false,\"blurayBdmv\":false,\"bdj\":false,\"encryptedDisc\":false,\"drm\":false}}";}
}
