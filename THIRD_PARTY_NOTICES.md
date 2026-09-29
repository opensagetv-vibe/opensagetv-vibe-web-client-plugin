# Third-party components

## libmedia 1.3.1

The browser software-decoding path is designed around the pinned `zhaohappy/libmedia` 1.3.1 runtime and codec modules. libmedia is LGPL-3.0-or-later and its decoder modules are derived in part from FFmpeg/libavcodec; individual upstream components retain their own licenses.

This repository/package does **not** embed the libmedia Wasm binaries. The SageTV plugin provides a strict pinned cache endpoint that can fetch approved assets from jsDelivr/npm on first use or explicit prefetch, then serves them locally to browsers.

Upstream source and license notices should remain available to users when this plugin is distributed publicly.

## OpenSageTV Vibe FFmpeg Plugin and FFmpeg

FFmpeg and MIM are **not bundled**. The platform-matching OpenSageTV Vibe
FFmpeg Plugin is a required SageTV plugin dependency and supplies the separate
runtime plus `SageTVTranscoder` bridge. Its runtime packages carry the
applicable FFmpeg/component licenses. This repository contains no FFmpeg/MIM
binaries and never replaces SageTV's stock `ffmpeg`.

## SageTV / sagex / Jetty

The WAR calls SageTV through the installed `sagex-api` and runs inside the existing SageTV Jetty plugin. It intentionally does not bundle duplicate SageTV, sagex or Jetty libraries.


## hls.js

Compatibility playback uses hls.js 1.7.3 (MIT License) on browsers without native HLS. The Java plugin retrieves the pinned browser asset through a strict allow-list cache and serves it from the same SageTV Jetty origin. Source: video-dev/hls.js.

## OpenSageTV Vibe Android MiniClient

The browser MiniClient protocol bridge and renderer behavior in v3.0.x are derived from the active MiniClient implementation in:

- Project: `opensagetv-vibe/opensagetv-vibe-android-client`
- Source areas: `source/dev/core/.../MiniClientConnection.java`, `GFXCMD2.java`, `MediaCmd.java`, GFX command-family classes, and `UIRenderer.java`
- License: Apache License 2.0

The web plugin does not bundle the Android application or Android/Media3 runtime. It ports the SageTV MiniClient wire protocol, GFX command semantics, event ordering, and media-command behavior into the Java/Jetty + browser environment.

## Python HTML5 client behavior reference (3.1.0)

The owner-supplied reference is `jzhvymetal/SageTV_HTML5_Client-Python_Server`, v150 archive, browser `static/app.js`. Startup thresholds, gesture timing, Guide assistance and video-mask behavior were compared and adapted to the existing Java/Jetty architecture. The Python archive/runtime is not bundled. See `docs/PYTHON_PORT_AUDIT.md` for provenance and scope. Preserve applicable upstream notices and confirm redistribution terms before a public third-party release; this entry does not assign a new license to the reference project.

## mpegts.js 1.8.0

Source: https://github.com/xqq/mpegts.js/tree/v1.8.0 . Apache License 2.0; copyright magicxqq/zheng qian and contributors. Unchanged npm build URL: https://cdn.jsdelivr.net/npm/mpegts.js@1.8.0/dist/mpegts.min.js . This ZIP/WAR does NOT embed that bundle. Preserve upstream license/attribution with offline copies. No unavailable vendor source or binary was fabricated.

Retained hls.js build: https://cdn.jsdelivr.net/npm/hls.js@1.7.3/dist/hls.min.js . Check PluginVersion/AssetCache when changing pins. These are version pins, not an included checksum-verified vendor manifest.

## Vibe Android settings reference

https://github.com/opensagetv-vibe/opensagetv-vibe-android-client (Apache-2.0). Settings grouping and caption-authority separation are the reference; this package does not include Android runtimes/APK or claim identical subtitle engines. Reviewed source identities are in docs/VIBE_STREAMING_PORT.md.


## Native authentication behavior (3.2.2)

RSA/Blowfish protocol behavior was reimplemented from the Apache-2.0 SageTV and
Vibe Android client references, without bundling either implementation or adding
a crypto library. JCA/JCE providers in the host JVM perform crypto.
Vibe `MiniClientConnection.java` blob: `ce2a558a4f4960461f6b2436d9508c46e579b34c`.
SageTV `MiniClientSageRenderer.java` blob: `0d93dfbc812e2bb90a6202dc2d517a1d3dd7b3d7`.
See `docs/RELEASE-v3.2.2.md` for paths, transition rules and security limits.


## Vibe Android navigation overlay and icons — added in 3.2.4

Source: opensagetv-vibe/opensagetv-vibe-android-client, source/dev,
reference tree f1ba340e05fe18eaaba939253a933c3dfe6d6caf.
Layout navigation.xml blob b55d384b6dcb9c86bf2cfa771f1a9d615710ed77;
NavIconButton styles, dimensions and colors retain the original organization.
28 vector drawables are converted to browser SVG with unchanged path geometry
and colors; two PNGs are copied unchanged. The per-file attribution, upstream
blob SHA and distributed SHA-256 are in third_party/vibe-icons.json.
Apache License 2.0 applies; see third_party/LICENSE-vibe-android.txt. The same
license, attribution notice and asset inventory are included in WEB-INF/licenses
inside the WAR. Android runtime code, compiled APK and font files are not
included. Modified browser layout/handlers include responsive reflow and
browser-specific utility actions described in RELEASE-v3.2.4.md.

## Vibe Android Teletext Level-1 engine — adapted in 3.2.6

P03 adapts the player-independent Level-1 DVB Teletext subtitle algorithm from
`source/dev/core/src/main/java/opensagetv/vibe/miniclient/media/TeletextSubtitleEngine.java`
in `opensagetv-vibe/opensagetv-vibe-android-client`, pinned at commit
`f1ba340e05fe18eaaba939253a933c3dfe6d6caf`. The upstream project is Apache-2.0.

The WebPlayer adaptation removes the Android process-global active session and binds
parser/service/page/timing state to one server playback session, then emits the
WebPlayer's versioned text-cue contract. It reuses the documented PAT/PMT descriptor
0x56, PES/data-unit, Hamming, page-row and erase-page behavior; no Android runtime,
Media3 class or APK code is bundled. See the included Apache-2.0 license and P03
report for scope and limitations.


## AndroidX Media3 DVB subtitle parser model — adapted in 3.2.7

P04's pure-Java DVB bitmap decoder is an independent server-side adaptation of the Apache-2.0 AndroidX Media3 `DvbParser` algorithm and ETSI EN 300 743 data model. The reviewed upstream source is `androidx/media` `libraries/extractor/src/main/java/androidx/media3/extractor/text/dvb/DvbParser.java`. The WebPlayer implementation replaces Android `Bitmap`, `Canvas`, `SparseArray` and Media3 cue types with Java arrays and the existing WebPlayer straight-alpha RGBA cue contract; no Android or Media3 runtime is bundled. See `third_party/LICENSE-androidx-media3.txt` and `docs/parity/p04/REPORT.md`.
