# OpenSageTV Vibe Web Client Plugin

Current development version: **3.2.30**. This stock-SageTV-compatible
Jetty WAR provides a browser MiniClient, recording player, captions/subtitles,
DVD playback, diagnostics, and HTML5 compatibility transcoding.

Current build and `.232` commissioning evidence is in
[`docs/VALIDATION-3.2.30.md`](docs/VALIDATION-3.2.30.md). Generated/local and
physical results are reported separately; the full authored/physical DVD
menu-SPU matrix remains open.

The platform-matching **OpenSageTV Vibe FFmpeg Plugin is required**. Production
discovery uses its `SageTVTranscoder` bridge and never selects SageTV's stock
`ffmpeg` directly. The explicit transcoder/legacy FFmpeg override exists only
for isolated regression tests and commissioning diagnostics.

This project is not yet approved for public plugin publication.

## 3.2.29 imported baseline


## 3.2.29 DVD startup recovery

DVD HLS startup now retries full hardware -> hardware encode/software decode+filters -> full software when fallback is enabled. The plugin retains a bounded 16 MiB replay prefix of the current MiniDVDPlayer MPEG-PS generation so a failed hardware startup cannot consume the authored DVD cell before the retry. This is especially important for short DVD menu/still cells. Diagnostics report `fallbackStage`, `dvdFallbackReason`, `dvdReplayBytes`, and `dvdReplayTruncated`.

DVD menu video/still backgrounds are now retained independently from the short-lived HLS cell. The browser caches decoded DVD frames per native DVD generation, promotes the cached authored frame when SageTV FLUSHes a menu cell, keeps that image through SPU-only/no-video generations, and releases it only after a real decoded frame arrives from the replacement DVD generation. This fixes the field symptom where only the colored DVD SPU button highlight remained over a black screen.

The 3.2.27 DVD hardware transcoding fix remains unchanged: DVD-Video is converted from MPEG-2/PS to H.264/AAC HLS with full hardware decode/process/encode when supported. The 3.2.26 default-on MiniDVDPlayer role and 3.2.25 STV caption control also remain in place. Stock SageTV server/JAR behavior is unchanged.

## Real-file parameter codec A/B test (3.2.23)

`/codec-test.html` now retains the original 360p30 A/B/C controls and adds D/E using the known parameters of the tested SageTV recording: MPEG-2 Main, 1280×720 progressive at 60000/1001 fps, ~6.3 Mb/s video, English AC-3 5.1 384 kb/s plus Spanish AC-3 stereo 192 kb/s. D performs video Copy while converting the selected English AC-3 track to AAC stereo. E uses the exact same synthetic source but converts video to H.264 while preserving 1280×720p59.94. This isolates native MPEG-2 decoding from resolution, cadence, scan type, HLS segmentation and source-audio format.

For normal playback, MPEG-2 → H.264 fallback continues to use Source resolution/FPS. Deinterlace Automatic now skips `yadif` when probing identifies the source as progressive, avoiding unnecessary filtering of 720p59.94 broadcasts.

## Source metadata probe fallback (3.2.22)

Source codec discovery now tries `ffprobe` first (configured path, FFmpeg sibling, then PATH). If `ffprobe` is missing, cannot start, exits unsuccessfully, or returns no tracks, the plugin falls back to the configured FFmpeg executable and parses its bounded input probe output. Diagnostics expose `probeMethod` as `ffprobe` or `ffmpeg`, so Copy/Auto decisions no longer lose codec metadata solely because `ffprobe` is absent.

## Explicit Copy passthrough (3.2.21)

`Video processing = Copy` now attempts FFmpeg video stream copy without a codec allow-list. Auto remains conservative. If copied HLS advances without decoded video dimensions, Allow fallback can restart the same position as H.264. This aligns normal MiniClient playback with the codec-test page: explicit Copy tests actual FFmpeg/browser behavior instead of rejecting the source in policy first.

## Copy video mode (3.2.21)

Settings → **Streaming and transcoding → Video processing** now has a single explicit **Copy** mode. Selecting **Copy** now persists immediately: with active media it uses the same-position Apply/restart path; with no active media it is saved for the next recording. Closing and reopening Settings no longer restores the prior mode. **Auto / Transcode when needed** remains conservative and continues to convert MPEG-2 video to H.264. **Copy** keeps the source video codec with FFmpeg `-c:v copy` and remuxes/resegments it for delivery without a server video decode/encode. This applies to MPEG-2, H.264, and browser-supported HEVC.

For **Copy**, Source resolution/FPS must remain selected and **Deinterlace On** must not be forced, because copied video cannot be resized, frame-rate converted, or deinterlaced. **Deinterlace Auto** is pass-through in Copy mode so interlaced ATSC MPEG-2 can remain unchanged. Incompatible processing choices are rejected instead of silently changing the meaning of Copy.

When **Allow fallback** is enabled and Copy is carrying MPEG-2, the browser watches for the field-test failure signature: HLS playback time advances beyond 0.5 seconds for at least 8 seconds while the video element still reports `0×0`. If that happens, the MiniClient requests one guarded compatibility restart at the same absolute playback position with H.264 video. If the browser actually decodes MPEG-2 and reports video dimensions, playback remains on Copy. Automatic fallback can be disabled for strict testing. Streaming profile schema remains 8; the temporary `mpeg2copy` value from the codec-test checkpoint migrates automatically to `copy`.

## Native HLS codec field test (3.2.18)

Open `codec-test.html` from the deployed WebPlayer context (normally `/SageTVWebPlayer/codec-test.html`). Click **Generate / refresh streams**, then **Run all native playback tests** on the iPhone/iPad/Safari device being evaluated. The page intentionally does not load hls.js. FFmpeg creates three fixed synthetic HLS VOD cases: MPEG-2 video copied unchanged, the same MPEG-2 source transcoded to H.264, and H.264 copied unchanged. AAC audio is used in all cases so audio support does not confound the video-codec result. Use **Copy result JSON** to capture the browser/device evidence. No SageTV recording is read or modified.

**Test Current Video** is now a real explicit test: normal completed recordings check cadence/buffering, pause/resume and a reversible seek, then restore position, pause intent, mute, volume and rate. Growing/live, short/unknown and DVD/menu sessions skip unsafe mutation with a reason. Interrupted restoration is media-ID guarded so a different recording never receives the old seek.

Schema-v1 profile export/import is previewed and limited to browser-saved defaults; identity, per-session track/PID/page choices and administrator settings are excluded. Server report writes are available only to an administrator-configured mounted filesystem path with writes explicitly enabled. The browser never receives SMB credentials or opens `smb://`; failed writes use a bounded 10-file/10-MiB plugin spool with explicit retry and cooperative cancellation between file steps. See `docs/parity/p14/` for report, validation, security design and field acceptance. Next phase is optional P15 extended-Core/provider integration, while earlier field gates remain unchanged.

## Vibe Android overlay

Long-press the SageTV picture or select Remote. The centered text-button card
has been replaced by the transparent full-screen edge layout from
`opensagetv-vibe/opensagetv-vibe-android-client/source/dev`:

- Home at the upper left; Help and Close at the upper right.
- Options / Info / Back above the left-side D-pad, with the original circular
  Select icon. Hide and Page Up/Down on the right.
- Keyboard at the lower left; seven playback icons along the bottom; video
  info, export, playback check, stats, aspect, video, audio and caption tools
  at the lower right.

The 28 vector drawables retain their source paths/colors as SVG. The Home and
Close PNGs are the original bytes. All 30 icons are bundled in the WAR and
listed in the offline shell: no icon CDN or font is required. Original button
background is #A0000000; the overlay does not dim the entire video.

The `?` Help drawer keeps browser-only keypad, guide/recordings, volume/channel,
full-duration timeline and Render-only controls. The aspect icon cycles Fill,
Fit and Stretch. Video/audio/caption icons open the existing working browser
settings. The test icon performs the bounded P14 current-video check. Normal completed media can exercise cadence, pause/resume and a reversible seek; unsafe live/DVD/short cases are skipped and original browser playback state is restored. Close and the green
Hide button dismiss the browser overlay without closing the browser tab.

Hold-to-repeat, text entry and suppression of the opening click are retained.
Narrow screens reflow the bottom rows; short stages reduce icon size. These
responsive adaptations are not a claim of pixel identity to every Android DPI.

## Video display

Normal full-playback video owns the entire stage below the toolbar, regardless
of native overscan/near-fullscreen destination insets. In render-only/fullscreen
mode it owns the full content area. Automatic UI resolution also uses every
stage CSS pixel; fixed 720p/1080p UI modes still preserve their logical aspect.

Black CLEAR_RECT/FILL_RECT operations are now masks, not position commands.
They no longer move or shrink fullscreen video when SageTV's OSD disappears.
Recognized opaque full-playback edge mattes are cleared from the graphics plane;
translucent OSD panels are retained. Explicit small native/color-key previews
still use the same coordinate mapping as the UI. Native-placement override is
available for STVs that need exact server rectangles.

**Default: Fill browser.** This preserves source proportions and removes added
letterbox/pillarbox bars, but crops edges when the source/window aspects differ.
Fit shows every source edge with bars as needed. Stretch shows every edge
without added bars but changes proportions. Bars encoded into the recording
are not automatically detected or removed. No mode can preserve aspect, show
every edge AND fill a differently shaped window simultaneously.

Settings -> Video display mode / Video placement control this policy. The old
implicit Source default migrates to Fill once; an explicit Fit selection after
this update is saved and respected. Legacy Stretch/Zoom selections are kept.
Native Source notifications no longer override this browser preference.

## Install

The authoritative installation procedure is under **Install and first use**
below. Historical checkpoint instructions in this section are retained only as
version history; a current installation must report `3.2.30` from
`/SageTVWebPlayer/api/health`.

## Retained startup safety and verification

The 3.2.3 first-FLIPBUFFER native-event gate is unchanged. There are no new
native events sent from INIT and no authentication-policy changes. This checkpoint changes only streaming policy/fallback selection: the FFmpeg command builder already supports stream copy, and explicit **Copy** now permits MPEG-2 HLS as well as the existing H.264/HEVC cases.

14 new display-policy checks and 22 new Chromium scenarios pass. All inherited
validation groups also pass across split executions, including the 13 native
startup-ordering tests. See `docs/VALIDATION.md` for counts and limitations.
A controlled test against the original 3.2.3 WAR reproduces the black-clear
video shrink. Real local H.264 decoding and the shipped icon bytes were tested;
SageTV/vendor transport was simulated, not live SageMC or GPU playback.

Diagnostics now include `videoLayout`: native/source rectangles, effective
presentation, stage/CSS dimensions, server aspect and selected browser mode.
Native startup, encryption, recovery, stream and buffer diagnostics are retained.

## Retained streaming reference


Java 8 / existing Jetty WAR with real SageTV MiniClient GFX/media for SageMC/STVs, continuous MPEG-TS via mpegts.js, and configurable video/audio/browser-local captions.

Settings reference: **opensagetv-vibe/opensagetv-vibe-android-client/source/dev**, not the older OpenSageTV MiniClient. No Android repository changes were made. See [source mapping](docs/VIBE_STREAMING_PORT.md).

## Install and first use

Install the platform-matching OpenSageTV Vibe FFmpeg Plugin first. Stop
SageTV/Jetty and retain the old WAR for rollback. Replace
`jetty/webapps/SageTVWebPlayer.war` with `dist/SageTVWebPlayer.war`, restart,
open the existing `/SageTVWebPlayer/miniclient.html`, then Ctrl+F5. Confirm
**3.2.30** in the toolbar and `/SageTVWebPlayer/api/health`. No changed
port, replacement Sage.jar, or Python service is required. Do not replace
SageTV's stock `ffmpeg`; this plugin resolves the FFmpeg Plugin's
`SageTVTranscoder` bridge.

The full ZIP also includes a local-plugin installer under `dist/local-install/SageTVPluginsDev.d`. Use either direct WAR replacement or the local plugin installer. If Jetty keeps an old expanded app, stop it and remove only its generated `jetty/webapps/SageTVWebPlayer/` directory before restarting. Do not remove recordings, properties or the vendor cache.

Open **Settings → Streaming and transcoding**. Use **Prepare player assets** before testing to put pinned mpegts.js and hls.js into the server cache. This build does not embed those third-party bundles: the server requires outbound HTTPS to version-pinned jsDelivr/npm URLs on a cache miss, or an administrator can pre-seed the cache. Browsers then load assets from the plugin's same-origin vendor endpoint. Failed downloads are reported, not disguised as a successful mpegts.js installation.

## Controls wired to playback

| Group | Implemented controls |
| --- | --- |
| Player | Automatic (MPEG-TS preferred), continuous MPEG-TS, compatibility HLS; optional fallback |
| Video | When-needed / always transcode / Copy; encoder; 250–50,000 kbps target; source or maximum height 480/720/1080/2160; source or 23.976–60 fps choices; deinterlace Auto/On/Off |
| Encoder | Auto, Intel QSV, NVIDIA NVENC, Linux VAAPI, AMD AMF, Apple VideoToolbox, software libx264; optional software fallback |
| Audio | Actual source track index; preferred language; Auto/AAC/AC-3/strict copy; 64–640 kbps choices; source/mono/stereo/5.1; delay −3000…+3000 ms |
| Broadcast CC | Off, CEA-608 CC1–CC4, CEA-708 service 1–63; independent delay −5000…+5000 ms |
| Buffer | Existing 30–600-second forward reserve, 180 seconds default; minimal TS startup stash; automatic refill |

**Save for next recording** stores the profile in this browser and updates the current MiniClient's next-recording defaults, without changing existing playback. **Apply now / restart stream** creates a new stream at the current reported timestamp while retaining pause intent and the native GFX connection. The selected audio index is per recording; future recordings return to automatic/preferred-language selection. **Refresh tracks** lists ffprobe-discovered audio streams. Invalid combinations fail visibly before replacing an active stream.

Choose **Always transcode** to enforce a video bitrate/encoder. When compatible video is copied, those settings do not apply. Requested settings and effective output are reported separately. Bitrate is an encoder target, not a measured constant transmission rate.

The new pipeline encodes video to H.264. Compatible H.264, or browser-advertised HEVC, can instead be copied into MPEG-TS. New HEVC/AV1 encoding is not implemented. AAC is the compatibility audio target; AC-3 requires browser support. Browser capability checks are preliminary, not proof of physical decoding or surround-output support. This is not Android HDMI bitstream-passthrough control.

## Continuous delivery and buffering

FFmpeg produces a growing `stream.ts`, served as one continuous HTTP response without waiting for an HLS segment to close. mpegts.js starts without its large IO stash, then fills toward the reserve. At the target it may close the HTTP reader normally; its next request resumes at the exact `bstart` byte offset in the same file without restarting FFmpeg. Time seeks are different: the plugin creates a new media session through its existing timeline/seek controls.

The mpegts.js source is deliberately configured `isLive:false` even for a growing DVR recording. Its v1.8.0 loading controller bypasses lazy-load limiting for `isLive:true`; DVR mode enables a bounded forward reserve and avoids live-latency chasing that could skip recording content. The Java input follower still follows a recording source. Filling can continue while paused without resuming playback. At the live edge there is no future content to buffer. Changing the TS reserve applies on the next stream start/seek or Apply now; HLS can change its target in place.

FFmpeg can run faster than playback and finish a completed recording early; that is normal. Browser memory limits can reduce the achievable reserve. The server spool has periodic safeguards: 8 GiB per-session output default, 128 MiB free-space threshold, and 120-second idle expiry. These are not pre-reserved disk quotas. Multiple clients need adequate disk and compute resources. Limit failures are reported and do not alter the original recording.

## Hardware, threads and captions

Encoder selection uses bounded FFmpeg capability/runtime probes, cached per encoder/executable. **The new portable settings path uses software video decoding/filtering followed by hardware encoding** when available. Audio encoding and caption parsing use CPU. It does not claim a fully GPU-resident decode/filter/encode chain. Video copy uses neither a server video decoder nor encoder.

mpegts.js worker and MSE-worker modes are requested, subject to browser support/fallback. SageMC/GFX remains the main-thread Canvas renderer with cooperative yielding. Server native media/GFX, input following, stderr/producer monitoring, caption extraction and continuous HTTP delivery use separate threads where applicable. FFmpeg is a separate process. Worker-enabled configuration is not proof that a physical worker/GPU actually ran.

The selected CEA channel/service is **browser-local**, not Android's STV-controlled event-225 caption path. An independent stream-copy output from the same FFmpeg process carries original compressed video to the server's A/53 extractor; only small timed caption packets reach the browser. This does not create a second encode and does not depend on the GPU encoder retaining caption metadata. Select a service present in the source. Unsupported source-caption formats or absent packets cannot create captions.

Teletext and DVB bitmap subtitles are now implemented as separate browser-local paths. PGS/DVD bitmap and SRT/WebVTT file subtitles are implemented in P06; standalone VobSub IDX/SUB field acceptance remains pending. Virtual CC1/CC2 mapping and stock STV caption authority are implemented in P05, with their real stock-server field rows still pending. Existing CEA/Teletext/DVB rendering is not a certification of every broadcast styling/control-code variant or real-source field acceptance.

## Test evidence and remaining limits

See `docs/VALIDATION.md` and `docs/VIBE_STREAMING_PORT.md` for current and retained evidence. Tests include actual FFmpeg video/audio conversion, two-language source probing, continuous file growth/byte resume, MPEG-2/H.264/HEVC A/53 parser fixtures, original-video side-copy during encoding, actual JS CEA decoding, mock-servlet async delivery, and production Chromium settings/GFX DOM tests.

The complete local browser suite includes mocked edge cases, and `.232`
commissioning additionally exercised the real cached mpegts.js and hls.js
bundles, real SageTV/SageMC media, and QSV capability/runtime paths. Native
Safari, Windows/macOS browsers, unindexed H.265/file-subtitle fixtures, a live
growing recording, and the full physical/authored DVD menu matrix remain
untested or open as listed in `docs/VALIDATION-3.2.30.md`.

Direct-file FFmpeg seeks use time-based input seeking; copied video remains keyframe-limited. The inherited pipe/MediaServer fallback uses byte-proportional seeks, which are not frame-accurate, especially for variable-bitrate/growing media. Automatic transport fallback is bounded to one transition per recording; strict copy and disabled-fallback policies remain strict.

Field acceptance: test the same recording with MPEG-TS and HLS; inspect Buffer info; pause until the reserve fills; resume/seek; change bitrate/audio with Apply now; test real CC1 and a known 708 service; then test a growing recording through completion. Capture Remote → Download diagnostics immediately after a failure. Review reports before sharing: addresses, client IDs and media paths may be included.

## Retained UI and deployment boundaries

The 3.1.1 full-window native resize/repaint, negative-texture geometry, preview/mask placement, full-duration timeline and long-press remote remain. Hold the picture about 0.6 seconds to open the local remote. Its Options button sends SageTV's own menu command. Remove any old `?ui=1280x720` / `?ui=1920x1080` override for Match browser window. Fixed-size modes preserve aspect and may leave bars; video aspect is separate.

The new settings are in **miniclient.html**. `index.html` retains the simple player and `client.html` retains the API Web Admin; those older pages have not been converted to the new profile system. Existing Jetty authentication/access controls remain necessary. Do not expose an unprotected server to the Internet. No independent authentication system is added.

## Server configuration

Keep the known-working FFmpeg/MIM executable; it must accept standard FFmpeg arguments. FFmpeg/ffprobe are external. Install matching ffprobe beside FFmpeg, on PATH or explicitly configure it. Missing probe metadata produces a warning and compatibility defaults; strict copy cannot guess codec safety.

| JVM property | Environment equivalent / default |
| --- | --- |
| `sagetv.webplayer.transcoder` | `SAGETV_WEBPLAYER_TRANSCODER` — optional explicit Vibe plugin bridge override; production auto-discovers `SageTVTranscoder` |
| `sagetv.webplayer.ffmpeg` | `SAGETV_WEBPLAYER_FFMPEG` — legacy isolated-test override only |
| `sagetv.webplayer.ffprobe` | `SAGETV_WEBPLAYER_FFPROBE` — matching ffprobe |
| `sagetv.webplayer.hlsDir` | `SAGETV_WEBPLAYER_HLS_DIR` — writable session output, including TS |
| `sagetv.webplayer.cacheDir` | `SAGETV_WEBPLAYER_CACHE_DIR` — writable vendor cache |
| `sagetv.webplayer.vaapiDevice` | `/dev/dri/renderD128` |
| `sagetv.webplayer.streamMaxBytes` | `8589934592` (8 GiB periodic output guard) |
| `sagetv.webplayer.streamIdleMs` | `120000` (120 seconds; minimum 30 seconds) |
| `sagetv.webplayer.maxStreams` | `8` producer admission limit |

The continuous HTTP pool allows 16 concurrent readers. Closing a full-buffer reader does not terminate its producer; Stop/disconnect/idle expiry does. Deletion is delayed for in-flight reads. The admission/output checks do not reserve GPU resources or disk space.

For offline installation, pre-seed valid upstream bundles under the cache base:

```text
<cache-base>/libmedia-1.3.1/mpegts-1.8.0/mpegts.min.js
<cache-base>/libmedia-1.3.1/hlsjs/hls.min.js
```

`GET api/assets` reports the versioned root (already including `libmedia-1.3.1`). The inherited directory name does not mean mpegts.js runs through libmedia. Exact pinned URLs are in AssetCache.remoteUrl and THIRD_PARTY.md. These are version pins, not embedded checksum-verified vendor binaries. Do not save a GitHub HTML page as JavaScript.

## Build/test

Developer dependencies: JDK supporting --release 8; javax.servlet 3.1+ API JAR; Node; Python for packaging/tests; FFmpeg/ffprobe; optional Playwright and Chromium. No Python service is deployed.

```sh
bash scripts/build-local.sh
python scripts/package-local-plugin.py
RUN_BROWSER_TESTS=1 bash scripts/validate.sh
```

Overrides: SERVLET_API_JAR, TEST_FFMPEG, CHROMIUM_PATH. Java class target is 52. Full source, compiled WAR, local installer, tests and handoff are included. No GitHub changes were pushed.
