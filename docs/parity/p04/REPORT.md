# P04 completion report — SageTV Web Player 3.2.7

**Date:** 2026-09-25  
**Phase:** P04 — DVB bitmap subtitles  
**Base:** `SageTV-WebPlayer-Local-Plugin-v3.2.6-P03.zip`  
**Reference:** `opensagetv-vibe/opensagetv-vibe-android-client` commit `f1ba340e05fe18eaaba939253a933c3dfe6d6caf`; AndroidX Media3 DVB parser model under Apache-2.0.

## Result

P04 is implemented at the local/generated acceptance boundary. WP04-001 through WP04-006 are complete. WP04-007 is `IMPLEMENTED_AWAITING_FIELD`: this workspace contains deterministic 2/4/8-bit DVB transport fixtures but no unchanged real UK DVB-bitmap recording. Generated pixels/counters are therefore not relabeled as a visible/timing field pass.

## Implementation

`DvbSubtitleSession` is per playback and retains source PID plus descriptor `0x59` language, subtitling type, composition page, ancillary page and hearing-impaired identity. Selection never uses an ffprobe subtitle ordinal or a CEA/Teletext service number. Explicit physical PID/page requests fail closed rather than falling back to a different DVB service.

`DvbBitmapDecoder` is a pure-Java server adaptation of the ETSI EN 300 743 / AndroidX Media3 parser model. It maintains display-definition, page, region, CLUT and object state; handles page versions/state resets and ancillary objects/CLUTs; decodes 2/4/8-bit pixel-code strings and mapping tables; converts Y/Cr/Cb/transparency to straight-alpha RGBA; and emits positioned region rectangles on the source canvas. Android `Bitmap`, `Canvas`, Media3 runtime classes, JNI and Python are not used.

`DvbSourcePump` reads the original authorized SageTV source independently of FFmpeg video output. DVB pixels are therefore not burned into the video by default and do not depend on QSV/NVENC/VAAPI/software video encoding preserving subtitle packets. Seek/FLUSH/source generations retire stale cues; EOS/stop publishes a clear.

The browser owns a dedicated `#dvbCaptions` canvas inside the existing video bounds. RGBA cues are drawn separately from text captions and the canvas uses the current video source/display transform for Fit/Fill/Stretch/fullscreen behavior. The SageTV MiniClient GFX canvas and established video-bound behavior are unchanged.

Streaming-profile schema 4 adds explicit `dvb`, source PID and composition-page preferences plus discovered service selection. DVB remains separate from Teletext, CEA CC slots and ordinary file/DVD subtitles.

Ordinary-video media command 36/type 1 now selects DVB by source PID. PID 8192 disables it. The adapter can start the DVB decoder/source reader on demand even if the stream profile did not pre-enable DVB, preserves the request across stream fallback/relaunch, re-resolves after asynchronous PMT discovery and rejects an unknown explicit PID once an inventory is known. DVD private-stream commands remain untouched for their later phase.

## Validation boundary

Generated fixtures prove exact RGBA/alpha pixels, source placement, 188/192/204-byte TS framing, late discovery, physical PID selection/disable, unknown-PID fail-closed behavior, seek/FLUSH/EOS cleanup and payload-free diagnostics. Browser tests prove the independent bitmap cue lifecycle and timeout clear. Existing retained regressions are rerun by the release validation script.

Not proved here:

- unchanged real UK DVB-bitmap playback and human-visible timing/placement;
- physical hardware-encoder/GPU/browser combinations not present in this environment;
- every malformed/rare ETSI EN 300 743 broadcaster edge case;
- PGS, VobSub, ordinary text/file subtitles or native DVD SPU;
- P01 real pinned vendor MSE acceptance.

## Phase status

- WP04-001 — DONE
- WP04-002 — DONE at generated-vector boundary
- WP04-003 — DONE at production DOM/controller boundary
- WP04-004 — DONE
- WP04-005 — DONE
- WP04-006 — DONE at architecture/generated/software boundary; physical encoder matrix remains part of WP04-007 field work
- WP04-007 — IMPLEMENTED_AWAITING_FIELD

Next runtime implementation phase after field closure: **P05 — stock SageTV CC authority and virtual CC1/CC2**.

## Final build identity

- WAR SHA-256: `3b8f3ff513b9b99dcc1fab4964a2ec774b2f5ca5c8194a4212fa304aa4d78409`
- Local installer SHA-256: `51c2e1d70ce7cf6b5d57e5ed22fb6ea36e6945c341efd9b74987c0a2be0207f6`
- Installer MD5 recorded by plugin manifest: `8e9f30c4f60c1f5f7c5c3ccd92acd536`
