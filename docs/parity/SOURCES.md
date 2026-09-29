# Reference sources and scope

Read-only source checkpoint for planning revision 1, 2026-09-24.

**Commit:** `f1ba340e05fe18eaaba939253a933c3dfe6d6caf`. **Git tree:** `06a4c99958b8e07f6ad84b0c0d721a7b92e71918`. Earlier project notes sometimes labeled the commit value a “tree”; this lock records the verified distinction without rewriting historical build metadata.

No Android source file was added to runtime in this update. Full-file runtime review of every upstream change is intentionally the first implementation-preparation gate P01. The list distinguishes reviewed materials from follow-up targets.

## U01 — TASKS.md

Durable checklist revision 71, completed foundation/caption fixes and explicitly open DVD/audio/seek/device gates.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/TASKS.md#L1-L740
**Observed blob SHA:** `c1b0c3d9a838d5dff167731e5a03187503b09109`.

## U02 — CHANGELOG.md

v0.5.95/94/93 current behavior and earlier reviewed caption/DVD/seek fixes. P01 revision 3 completed the literal 1,066-record changelog census and 102-file relevant regression-source census; the 248-record normalized functional ledger remains the browser disposition map.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/CHANGELOG.md#L1-L355
**Observed blob SHA:** `650e8a65d1407fa5a6529240078b41ada2f91afe`.

## U03 — docs/PLAYER_SERVER_COMPATIBILITY.md

Authoritative distinction between CC, DVB, ordinary subtitles, native DVD, optional Core and physical evidence.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/docs/PLAYER_SERVER_COMPATIBILITY.md#L1-L210
**Observed blob SHA:** `91f936eac51d345bc0f4dac653674f7ea32b6065`.

## U04 — source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java

Native DVD INIT/no-OPENURL, drain/FLUSH, exact reply lengths, commands 32–37 and Push timestamp calibration.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/source/dev/core/src/main/java/opensagetv/vibe/miniclient/MediaCmd.java#L365-L1110
**Observed blob SHA:** `c7373bd41b68842aca91b1e4eab38222fec828e1`.

## U05 — source/dev/core/src/main/java/opensagetv/vibe/miniclient/media/TeletextSubtitleEngine.java

Level-1 Teletext parser, service discovery, page decoder and Android singleton ownership requiring per-session adaptation.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/source/dev/core/src/main/java/opensagetv/vibe/miniclient/media/TeletextSubtitleEngine.java
**Observed blob SHA:** `006ae6c511a205bf3adb002347f74b0c747c44e0`.

## U06 — source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/TeletextCea608Bridge.java

Timed text-to-stock-CC bridge, serialized drains, explicit reset/flush and virtual-slot mapping.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/source/dev/core/src/main/java/opensagetv/vibe/miniclient/video/TeletextCea608Bridge.java
**Observed blob SHA:** `d00f9e78afe9512b9340a19f64f9e66ebbc27469`.

## U07 — source/dev/core/src/main/java/opensagetv/vibe/miniclient/media/CaptionSlotPolicy.java

Source-aware virtual slots; includeDvb must be false for current CC1/CC2 policy; latest TASKS supersedes older broad modes.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/source/dev/core/src/main/java/opensagetv/vibe/miniclient/media/CaptionSlotPolicy.java
**Observed blob SHA:** `c9b31c68191061aeb7ff2c2c09dae9563ec0862d`.

## U08 — source/dev/core/src/main/java/opensagetv/vibe/miniclient/dvd/DvdSpuDecoder.java

Bounds-checked SPU/RLE, commands, CHG_COLCON. Related core assembler/compositor/highlight/palette classes were also reviewed.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/source/dev/core/src/main/java/opensagetv/vibe/miniclient/dvd/DvdSpuDecoder.java
**Observed blob SHA:** `7f336ae3ff2c70b4cf95402abb534802b3bdfd33`.

## U09 — source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/DvdSubpictureDecoder.java

Android adapter shows palette/selection/generation/highlight behavior; Bitmap output must be replaced for the web backend.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/DvdSubpictureDecoder.java
**Observed blob SHA:** `b77dcd080bb8f8e1e47af85b9af4c3949407cdd6`.

## U10 — source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java

Reviewed capability negotiation, functional DVD_DISC_* names, native rate and caption callback properties.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java#L1000-L1380

## U11 — source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/DvdPsExtractor.java

Reviewed private-stream audio routing, sparse/still video behavior and SPU versus A/V rebasing; Media3-specific code is not a browser drop-in.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/android/video/media3/DvdPsExtractor.java
**Observed blob SHA:** `a94933d5d1da40b81019219b22f62e61156bed1c`.

## U12 — source/dev/core/src/main/java/opensagetv/vibe/miniclient/dvd/DvdSpuAssembler.java

Bounded per-substream fragmentation and PTS markers.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/source/dev/core/src/main/java/opensagetv/vibe/miniclient/dvd/DvdSpuAssembler.java
**Observed blob SHA:** `2d6aaa3ead31536aaf2922f250eb2234d6bc5c69`.

## U13 — java/sage/MiniDVDPlayer.java

Reviewed stock DVD VM, ISO mount prerequisite, four-byte DVD command replies and STC wire units. Re-pin the exact target Core in P01.

**Review classification:** `reviewed_server_reference_not_installed_core_identity`.
**Source:** https://github.com/google/sagetv/blob/master/java/sage/MiniDVDPlayer.java
**Observed blob SHA:** `abee89b421e095ea3c09fbf19d204cc9effa2078`.

## U14 — source/dev/android-shared/src/main/res/layout/navigation.xml

Actual Vibe overlay baseline already ported in 3.2.4; retain icons/grouping while adding functional panels.

**Review classification:** `reviewed_upstream`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/source/dev/android-shared/src/main/res/layout/navigation.xml
**Observed blob SHA:** `b55d384b6dcb9c86bf2cfa771f1a9d615710ed77`.

## U15 — tests/test_dvd_protocol.py

Known upstream test target to inspect/reuse in P01/P07; included in the frozen relevant-regression source census; upstream test execution is not browser acceptance.

**Review classification:** `identified_for_followup`.
**Source:** https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/tests/test_dvd_protocol.py
**Observed blob SHA:** `18e96f89e799f6ee54749864b67de3cd1f21dd91`.

## E01 — FFmpeg decoder documentation

dvbsub/dvdsub palette behavior and build-dependent libzvbi support; not proof the installed FFmpeg includes a decoder.

**Review classification:** `additional_primary_feasibility_reference`.
**Source:** https://ffmpeg.org/ffmpeg-codecs.html

## E02 — FFmpeg DVD demuxer documentation

dvdvideo accepts titles, directory/ISO/device inputs with libdvdnav/libdvdread; extracting menu assets is not interactive menu navigation.

**Review classification:** `additional_primary_feasibility_reference`.
**Source:** https://github.com/FFmpeg/FFmpeg/blob/master/doc/demuxers.texi

## B01 — README.md, handoff.md, BUILD_INFO.json, docs/RELEASE-v3.2.4.md, docs/ARCHITECTURE.md

The exact 3.2.4 runtime, accepted overlay/display behavior, inherited tests and stated limitations.

**Review classification:** `reviewed_exact_uploaded_baseline`.
**Baseline SHA-256:** See `REFERENCE_LOCK.json` for each exact local file.

## B02 — src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java

Lines43–71: INIT-only reply and discarded PUSHBUFFER data; lines90–103: frame-step unsupported/DVD32–37 rejected; lines114–156: source resolution, basename fallback and stream restart seeks.

**Review classification:** `reviewed_exact_uploaded_baseline`.
**Baseline SHA-256:** See `REFERENCE_LOCK.json` for each exact local file.

## B03 — src/main/java/org/opensagetv/webplayer/StreamOptions.java, src/main/java/org/opensagetv/webplayer/MediaProbe.java

Only off/608/708 caption modes; audio-oriented simple metadata without subtitle PID/page/forced fields.

**Review classification:** `reviewed_exact_uploaded_baseline`.
**Baseline SHA-256:** See `REFERENCE_LOCK.json` for each exact local file.

## B04 — src/main/java/org/opensagetv/webplayer/StreamCommand.java, src/main/java/org/opensagetv/webplayer/StreamPlan.java, src/main/java/org/opensagetv/webplayer/A53CaptionTap.java, src/main/java/org/opensagetv/webplayer/CaptionsServlet.java, src/main/webapp/js/miniclient-captions.js

Original-video-only A/53 side branch and local timed CEA captions; no Teletext/DVB/PGS/SPU cue path.

**Review classification:** `reviewed_exact_uploaded_baseline`.
**Baseline SHA-256:** See `REFERENCE_LOCK.json` for each exact local file.

## B05 — src/main/java/org/opensagetv/webplayer/MiniClientSession.java

Lines397–440: role/capability replies, PULL-only and no subtitle callbacks/unified; lines476–505: callback rejection and forwarded VIDEO_CC_STATE.

**Review classification:** `reviewed_exact_uploaded_baseline`.
**Baseline SHA-256:** See `REFERENCE_LOCK.json` for each exact local file.

## B06 — src/main/java/org/opensagetv/webplayer/MediaSourceFactory.java, src/main/java/org/opensagetv/webplayer/SageApiBridge.java

Current MediaFile/source/public API bridge; disc path/mount and context-specific control need separate validation.

**Review classification:** `reviewed_exact_uploaded_baseline`.
**Baseline SHA-256:** See `REFERENCE_LOCK.json` for each exact local file.

## B07 — src/main/java/org/opensagetv/webplayer/RecordingFollower.java, src/main/java/org/opensagetv/webplayer/HlsSessionManager.java, src/main/java/org/opensagetv/webplayer/SageMediaServerSource.java, src/main/webapp/js/miniclient-playback.js, README.md

Existing source following, buffer recovery and documented proportional remote/pipe seeking limitation; retain or adapt rather than replace blindly.

**Review classification:** `reviewed_exact_uploaded_baseline`.
**Baseline SHA-256:** See `REFERENCE_LOCK.json` for each exact local file.

## Private incident evidence not copied into this package

The supplied earlier SageTV log reports a Linux/Java11 server and a failed ISO mount because sudo was unavailable. It also contains sensitive request headers. Only the requirement to distinguish mount/environment failures from player failures is carried into P10. Raw server logs, diagnostic JSON, media payloads and credentials are not added to this planning ZIP. The user’s current statement that the update works supersedes treating those old startup incidents as a new reported failure.

FFmpeg documentation is an additional primary feasibility reference, not evidence of installed capabilities. Test the actual deployed executable and its libraries before exposing options. DVD title/menu-asset demuxing does not by itself supply a complete interactive DVD VM.


## Revision2 review/evidence additions

Current main was read again and still equals the frozen commit; no source lock
was advanced. p01/SOURCE_FAMILIES_AND_TESTS.md distinguishes inspected code from
known but not fully reviewed regression targets. The248-record ledger is a
normalized functional review, not a raw commit/paragraph count. The exact
TASKS revision 71 crosswalk preserves the 68 reviewed named parents and their open states.

The pinned TeletextSubtitleEngineTest.java (Git blob
249055db9d252f13ae4e8073c6b3a7aed8c3e615) provided test-vector bit-order conventions.
FFmpeg n7.1 libavcodec/libzvbi-teletextdec.c (blob
68ffe1f76ce1db6095541c8f378e5d00a3abb61a) was read as a decoder-input-format
reference (45-byte header/PES sizing); no FFmpeg decoder implementation was
copied into production. The independently installed FFmpeg 7.1.5 decoder ran the
fixture checks. No native library, executable or font is bundled.

Reference sources:
https://github.com/opensagetv-vibe/opensagetv-vibe-android-client/blob/f1ba340e05fe18eaaba939253a933c3dfe6d6caf/source/dev/core/src/test/java/opensagetv/vibe/miniclient/media/TeletextSubtitleEngineTest.java
https://github.com/FFmpeg/FFmpeg/blob/n7.1/libavcodec/libzvbi-teletextdec.c
