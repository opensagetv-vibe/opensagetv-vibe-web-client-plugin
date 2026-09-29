# ADR P02-005 — decoder placement

Decision: keep subtitle decoding session-scoped on the Java/Jetty side and send versioned text/straight-alpha bitmap cues to the browser. Do not embed Android Media3/Bitmap classes and do not link libavcodec into the WAR.

* Teletext: adapt the requested Vibe `TeletextSubtitleEngine` Level-1 core to per-playback ownership in P03. The current Vibe implementation is player-independent but process-global, so its singleton state is not copied into the multi-client servlet JVM.
* DVB bitmap: use a project-owned pure-Java raw-RGBA adapter based on the Apache-2.0 AndroidX Media3 DVB parsing model, replacing Android `Bitmap`, `Canvas` and `SparseArray` dependencies. P04 must retain attribution/license material when parser code is actually imported.
* PGS/VobSub: implement bounded Java adapters behind the same cue contract in P06. External FFmpeg may be used for source stream-copy/probing, but an FFmpeg decoder name alone is not accepted as renderer support.
* DVD SPU: reuse/adapt the platform-neutral Vibe DVD core in P09, separate from ordinary file subtitles.

Deployment remains the current WAR plus the already-supported external FFmpeg executable. No Python service, JNI library or Android runtime is added. Decoder work must obey `SubtitleLimits` and `SubtitleWorkBudget`; cancellation follows `PlaybackSessionContext` epochs. If a decoder is absent or fails its capability probe, its UI option remains unavailable and A/V continues.
