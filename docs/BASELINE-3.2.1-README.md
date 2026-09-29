# SageTV Web Player 3.2.1 — Native GFX recovery update

This is a focused update to the supplied v3.2.0 package, not an Android APK or a
replacement Sage.jar. Read `docs/RELEASE-v3.2.1.md` and `handoff.md` first.

**The screenshot identifies a native GFX reset, not its cause.** This update fixes
confirmed missing recovery and browser terminal-state handling. A reset before
the first frame, a server that does not negotiate reconnect, or three failed
recovery attempts remains a visible failure requiring an explicit Reconnect.
The update does not silently create endless new native sessions.

Install `dist/SageTVWebPlayer.war` as `jetty/webapps/SageTVWebPlayer.war` while
SageTV/Jetty is stopped, restart, then Ctrl+F5. Confirm **3.2.1** in the toolbar.
Keep the previous WAR for rollback. Close duplicate browser-client tabs for the
first test. Do not delete Sage.properties, client profiles, or the vendor cache.
If Jetty retains an old exploded application, remove only its generated
`jetty/webapps/SageTVWebPlayer/` directory while stopped.

Successful negotiated type-5 recovery retains the native media connection and
image handles. The server must first send RECONNECT_SUPPORTED=TRUE and at least
one STARTFRAME. Event output is suppressed during replacement; a command sent
during that interval is rejected rather than silently reported as successful.
Three handshake attempts use delays of 0, 150 and 350 ms and 5-second per-socket
connect/read timeouts (both can contribute to elapsed time). Encryption and GFX
compression remain unsupported and are not falsely acknowledged.

A terminal failure reveals **Reconnect** and **Download diagnostics**, including
in fullscreen/render-only view. Download before reconnecting so it includes the
failed session. It contains the native phase, last GFX/media command, reconnect
attempts, exception class/message, and a bounded 64-entry timestamped trace.
The added trace records property names, not values or media payloads. Existing
browser diagnostics may contain server addresses, session IDs and media metadata;
review a report before sharing it publicly.

Only one session within this plugin may own a given server/client ID. A new
connection retires the old session with a visible explanation; a delayed stop
from the old page cannot stop its replacement. This is not a cross-process lock
against another application, plugin deployment, or a second server hostname.

The underlying v3.2.0 video/audio/caption settings, continuous TS/HLS paths,
FFmpeg/MIM separation, Jetty context/port, and stock-server boundary are retained.
Vendor assets still use the existing cache/Prepare player assets workflow.
No new streaming dependency or server configuration is needed for this update.

## Existing streaming reference

# SageTV Web Player 3.2.1

Java 8 / existing Jetty WAR with real SageTV MiniClient GFX/media for SageMC/STVs, continuous MPEG-TS via mpegts.js, and configurable video/audio/browser-local captions.

Settings reference: **opensagetv-vibe/opensagetv-vibe-android-client/source/dev**, not the older OpenSageTV MiniClient. No Android repository changes were made. See [source mapping](docs/VIBE_STREAMING_PORT.md).

## Install and first use

Stop SageTV/Jetty and retain the old WAR for rollback. Replace `jetty/webapps/SageTVWebPlayer.war` with `dist/SageTVWebPlayer.war`, restart, open the existing `/SageTVWebPlayer/miniclient.html`, then Ctrl+F5. Confirm **3.2.0** in the toolbar. No changed port, replacement Sage.jar, Python service, or FFmpeg/MIM builder is required.

The full ZIP also includes a local-plugin installer under `dist/local-install/SageTVPluginsDev.d`. Use either direct WAR replacement or the local plugin installer. If Jetty keeps an old expanded app, stop it and remove only its generated `jetty/webapps/SageTVWebPlayer/` directory before restarting. Do not remove recordings, properties or the vendor cache.

Open **Settings → Streaming and transcoding**. Use **Prepare player assets** before testing to put pinned mpegts.js and hls.js into the server cache. This build does not embed those third-party bundles: the server requires outbound HTTPS to version-pinned jsDelivr/npm URLs on a cache miss, or an administrator can pre-seed the cache. Browsers then load assets from the plugin's same-origin vendor endpoint. Failed downloads are reported, not disguised as a successful mpegts.js installation.

## Controls wired to playback

| Group | Implemented controls |
| --- | --- |
| Player | Automatic (MPEG-TS preferred), continuous MPEG-TS, compatibility HLS; optional fallback |
| Video | When-needed / always / strict copy; encoder; 250–50,000 kbps target; source or maximum height 480/720/1080/2160; source or 23.976–60 fps choices; deinterlace Auto/On/Off |
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

DVB bitmap, Teletext, PGS/DVD bitmap and SRT/file subtitles are not implemented in this MiniClient update and are not shown as working options. Complete Android virtual CC1/CC2 slots, broadcast language discovery and native STV authority remain separate work. Existing CEA rendering is not a certification of every broadcast styling/control-code variant.

## Test evidence and remaining limits

See `VALIDATION-v3.2.0.txt` and `VIBE_STREAMING_PORT.md`. Tests include actual FFmpeg video/audio conversion, two-language source probing, continuous file growth/byte resume, MPEG-2/H.264/HEVC A/53 parser fixtures, original-video side-copy during encoding, actual JS CEA decoding, mock-servlet async delivery, and production Chromium settings/GFX DOM tests.

**The mpegts.js player is mocked in browser tests. The real vendor bundle was not available to execute in this offline build environment. Actual vendor/MSE playback, live SageTV/SageMC, real user recordings, Windows/macOS, GPU encoders and native Safari remain untested.** Build/test success is not live playback proof or a measured speedup over Python.

Direct-file FFmpeg seeks use time-based input seeking; copied video remains keyframe-limited. The inherited pipe/MediaServer fallback uses byte-proportional seeks, which are not frame-accurate, especially for variable-bitrate/growing media. Automatic transport fallback is bounded to one transition per recording; strict copy and disabled-fallback policies remain strict.

Field acceptance: test the same recording with MPEG-TS and HLS; inspect Buffer info; pause until the reserve fills; resume/seek; change bitrate/audio with Apply now; test real CC1 and a known 708 service; then test a growing recording through completion. Capture Remote → Download diagnostics immediately after a failure. Review reports before sharing: addresses, client IDs and media paths may be included.

## Retained UI and deployment boundaries

The 3.1.1 full-window native resize/repaint, negative-texture geometry, preview/mask placement, full-duration timeline and long-press remote remain. Hold the picture about 0.6 seconds to open the local remote. Its Options button sends SageTV's own menu command. Remove any old `?ui=1280x720` / `?ui=1920x1080` override for Match browser window. Fixed-size modes preserve aspect and may leave bars; video aspect is separate.

The new settings are in **miniclient.html**. `index.html` retains the simple player and `client.html` retains the API Web Admin; those older pages have not been converted to the new profile system. Existing Jetty authentication/access controls remain necessary. Do not expose an unprotected server to the Internet. No independent authentication system is added.

## Server configuration

Keep the known-working FFmpeg/MIM executable; it must accept standard FFmpeg arguments. FFmpeg/ffprobe are external. Install matching ffprobe beside FFmpeg, on PATH or explicitly configure it. Missing probe metadata produces a warning and compatibility defaults; strict copy cannot guess codec safety.

| JVM property | Environment equivalent / default |
| --- | --- |
| `sagetv.webplayer.ffmpeg` | `SAGETV_WEBPLAYER_FFMPEG` — configured/discovered executable |
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
