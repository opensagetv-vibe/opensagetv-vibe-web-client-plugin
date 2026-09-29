# SageTV Web Player 3.0.3


## v3.0.3 MiniClient playback + aspect-ratio fix

- Fix a MiniClient browser regression where `videoStart` reached the page but playback stopped at **Building playback buffer...** because the HLS helper functions were referenced but missing from `miniclient.js`.
- Restore HLS server-status polling, startup-buffer waiting, buffer-ahead monitoring, starvation detection, and MiniClient media recovery.
- Do not block the ordered GFX/event stream while a 10-second video startup buffer is being built; video startup now runs asynchronously so SageMC can continue drawing and responding to input.
- Default the SageTV MiniClient UI to **1280x720 (16:9)** and scale/letterbox that logical surface inside the browser instead of stretching SageMC to the browser window aspect ratio. Use `?ui=1920x1080` to test a higher 16:9 logical UI size.
- Keep the v3.0.1 `DRAWTEXTURED` orientation fix: negative destination width/height are rendering flags, not mirror/flip instructions.
- Bump the PWA service-worker cache to force browsers off the broken v3.0.1 MiniClient JavaScript.

## Real Vibe-derived MiniClient / SageMC mode

v3.0.x adds a **real SageTV MiniClient protocol client in the browser**, using `opensagetv-vibe/opensagetv-vibe-android-client` as the protocol/reference implementation. This is not the API-based Web Admin UI. SageTV itself runs the configured STV (including SageMC), sends MiniClient GFX commands, and receives MiniClient remote/keyboard/mouse events.

Architecture:

`Browser Canvas/video -> Jetty /api/miniclient -> real MiniClient GFX+media sockets -> SageTV :31099`

- Connection type 1 (media) is established before connection type 0 (GFX), matching the Vibe startup contract.
- GFX commands are rendered into browser Canvas surfaces/images; the video plane sits behind the transparent SageTV GFX layer.
- Sage command, keyboard, pointer, resize and repaint events are sent back over the MiniClient event channel.
- Media commands use the Vibe MiniPlayer contract. The browser advertises PULL and the plugin maps SageTV `OPENURL`/seek/play/pause/volume/video-rectangle commands onto the existing FFmpeg HLS compatibility backend.
- GFX/event delivery uses a streaming NDJSON Jetty response to avoid one HTTP request per draw command, with long-poll fallback.
- Open `/SageTVWebPlayer/miniclient.html` to run the MiniClient/SageMC view. The recordings-first page remains at `/SageTVWebPlayer/`; `client.html` remains an optional API/Web Admin page.
- The browser generates and persists a stable MiniClient ID (MAC-style ID) in `localStorage`, so SageTV gets a stable client/UI context across reconnects. You can override it for testing with `?clientId=020000000123`; use `?server=host:31099` only when the plugin cannot reach SageTV on the automatically detected server address.
- To use SageMC, configure that MiniClient/UI context on the SageTV server the same way you would configure the Android MiniClient or an extender. The browser is rendering the STV sent by SageTV; it is not recreating SageMC in HTML.

This is the first browser port of the Vibe MiniClient core and must be field-tested against the user's actual SageMC/STV before claiming renderer parity with Android.


A SageTV **server-side Jetty web application plugin** for watching SageTV recordings and active recordings from a modern browser without modifying `Sage.jar`.

## v2.4 full-client + HLS starvation recovery

- Add a separate **Full Client** page at `client.html` while keeping the simple recordings-first `index.html`. The full client is tabbed so the page does not become vertically overloaded: Home, Recordings, Guide/Live TV, DVR, Favorites, Player, and More/System.
- Compatibility playback now builds a real server-side startup buffer before attaching the browser. This prevents the browser from immediately catching a software encoder that is running close to real time.
- When the browser reaches the generated HLS edge, playback pauses/rebuffers instead of being treated as the end of the SageTV recording.
- Add an HLS starvation watchdog. If FFmpeg stops advancing, the MediaServer feed ends early, or the HLS playlist ends before the known SageTV duration, compatibility playback automatically creates a new HLS session at the current **absolute SageTV time**.
- Compatibility time/buffer reporting now follows the session's changing absolute offset across automatic recovery sessions.
- If the browser receives an early `ended` event before the known recording duration, the outer player also restarts compatibility playback from the current absolute time instead of marking the show finished.
- Software fallback is tuned for throughput (`libx264` ultrafast) so it is less likely to be consumed faster than it can encode.
- Hardware H.264 encoding remains the default. NVENC/VAAPI now default to the more reliable **software MPEG-2 decode/deinterlace + hardware H.264 encode** path; full GPU decode/deinterlace is opt-in with `SAGETV_WEBPLAYER_FULL_HW=1`.

A SageTV **server-side Jetty web application plugin** for watching SageTV recordings and active recordings from a modern browser without modifying `Sage.jar`.

The preferred path keeps the original SageTV/ATSC transport stream unchanged on the server. The browser demuxes MPEG-TS and, when native HTML5 playback cannot decode ATSC MPEG-2/AC-3, uses WebAssembly software decoders. An optional external FFmpeg/MIM-compatible executable is available only as a compatibility fallback.

## v2.3 compatibility streaming architecture

Compatibility playback no longer exposes one long FFmpeg fragmented-MP4 response.  It now ports the stable HLS model from `jzhvymetal/SageTV_HTML5_Client-Python_Server` directly into this Java plugin:

`SageTV MediaFile -> direct file or SageTV MediaServer:7818 -> FFmpeg -> stream.m3u8 + atomic .ts segments -> Jetty /hls/* -> hls.js/native HLS`

There is **no Python/FastAPI server** in this path.  Jetty starts/stops FFmpeg sessions through `/api/hls`, serves snapshot copies of the generated playlist/segments through `/hls/*`, and uses the existing SageTV MediaServer reader when the database file path is not mounted inside the SageTV/Jetty runtime. Completed recordings are filled as fast as FFmpeg can transcode. The visible seek bar continues to use the full SageTV recording duration; seeking starts a new HLS session at the selected position.

Desktop Chrome/Edge/Firefox use pinned **hls.js 1.7.3** from the plugin's strict vendor cache. Safari/iPhone/iPad use native HLS where available. The old `/transcode.mp4` endpoint remains only as a legacy/debug endpoint and is not the default Compatibility player.


## 2.2.0 compatibility streaming/timeline fix

- The **Watch** button now defaults to **Compatibility (FFmpeg)** for completed recordings when FFmpeg is available. Active recordings continue to use the original/live path.
- Compatibility playback now uses **Media Source Extensions (MSE)** when the browser supports it. FFmpeg fragmented MP4 is fetched continuously and appended as new fragments arrive instead of treating the first produced fragment as a complete video.
- The visible SageTV seek bar always represents the **full recording duration**. Seeking to an unbuffered point restarts FFmpeg at that absolute recording time.
- The misleading native fMP4 controls are hidden in Compatibility mode; the SageTV player controls/timeline are authoritative.
- A new buffer display shows buffered range, seconds buffered ahead, received MiB, stream state, and MSE append state.
- FFmpeg now emits ~1 second fMP4 fragments and flushes them promptly for smoother continuous playback.
- Multi-segment SageTV recordings are concatenated server-side for Compatibility playback so playback can continue across SageTV file segments.


## 2.1.0 simplified recordings-first UI

The visible page is intentionally small now. It shows a **Recordings** list, one **Watch** button for each recording, and the player. The previous Home, Server Watch History, Tuners / Encoder Health, SageTV Favorites / Recording Rules, and DVR Schedule management panels are no longer shown. Their server-side APIs are left in the WAR so they can be reused later without destabilizing playback.

Compatibility playback is now **hardware-first**. FFmpeg probes for `h264_nvenc`, `h264_qsv`, `h264_amf`, `h264_vaapi`, and `h264_videotoolbox` and uses the first suitable hardware encoder. CUDA and VAAPI also use hardware decode/deinterlace when the required FFmpeg acceleration/filter chain is detected. Other hardware encoders use GPU H.264 encoding with software MPEG-2 decode/deinterlace. `libx264` is only the fallback. Set `SAGETV_WEBPLAYER_VIDEO_ENCODER` (or `-Dsagetv.webplayer.videoEncoder=`) to `auto`, `software`, or an exact FFmpeg encoder name to override selection.


## What 2.0.0 implements

- SageTV/Jetty WAR at `/SageTVWebPlayer/`.
- SageTV MediaFile lookup through the installed `sagex-api` plugin.
- Recordings browser with search, episode/channel metadata and active-recording indicator.
- Original media delivery with HTTP `Range` support.
- Direct-file streaming plus SageTV MediaServer (`7818`) fallback for recordings not directly visible to the WAR.
- Active-recording/live follower that streams the growing original file and follows SageTV segment rollovers.
- Browser MPEG-TS demux and MPEG-2, AC-3 and E-AC-3 WebAssembly decode using pinned libmedia `1.3.1`.
- Audio stream discovery and switching.
- CEA-608 CC1–CC4 extraction and timed overlay, including North-American special and extended character sets.
- CEA-708 DTVCC packet/service assembly with multi-window visibility, positioning, pen location, basic color/italic/underline handling and service selection.
- Auto playback policy: original native when appropriate, otherwise original+Wasm, then optional compatibility mode.
- Optional compatibility path using an external FFmpeg/MIM-compatible executable to generate continuous H.264/AAC HLS segments. It never alters the recording.
- VOD seek, speed, volume, fullscreen, wake-lock, live-edge control and decoder statistics.
- Pinned browser decoder asset cache. The browser can be fully served from the SageTV host after the assets have been cached.
- Strict vendor-asset allow-list and no arbitrary filesystem-path streaming endpoint.





## 2.0.0 feature-complete pre-field-test phase

This release completes the broad set of features that can be implemented safely without direct access to a real SageTV tuner/server/browser session:

- **Home dashboard** with Continue Watching, Recording Now, upcoming DVR, server watch history, and playback queue.
- **Browser profiles** with isolated resume positions, preferences, local favorites, favorite channels, recent history, and playback queues. Profiles can be exported/imported as JSON.
- **Playback queue/binge mode** with card/detail queue controls and optional queue-first auto-play at end of a recording.
- **Favorite channels** plus channel-logo display and favorite-channel-only guide filtering.
- **SageTV Favorite recording-rule management**: create/remove, enable/disable, first-run/re-run policy, padding, recording quality, retention/auto-delete, and priority ordering.
- **Tuner/encoder health dashboard**: functioning/active/network-encoder/live-client state, active MediaFile, inputs, lineups, signal, standards, encoder merit, per-device quality, and global recording quality.
- **Manual recording options** for start/stop padding and recording quality on existing manual recordings.
- **Expanded DVR details** including actual schedule start/end, recording quality, and scheduled capture-device assignment.
- **SageTV server watch history** via `GetRecentlyWatched`.
- **Bulk library actions** for watched/unwatched and playback-queue assignment. Permanent deletion remains intentionally individual and confirmed.
- **Picture-in-Picture / browser remote playback hooks** for native and compatibility video paths.
- **Sleep timer** for fixed durations or end-of-recording.
- **Settings/profile export-import** for backup/migration between browsers.
- Diagnostics schema v6 now includes active profile, queue, and favorite-channel state.

At this point remaining work is field-test driven: real tuner behavior, broadcaster-specific caption streams, iPhone thermal/performance behavior, actual Comskip files, real conflict cases, and browser-specific media quirks.

## 1.5.0 DVR/library-management phase

- Add an upcoming SageTV DVR schedule and unresolved-conflict dashboard.
- Add explicit cancellation for manual scheduled recordings.
- Add SageTV watched/unwatched controls from the recording details dialog.
- Add optional periodic SageTV watched-position synchronization while browser-local resume remains independent.
- Read SageTV's existing resume position and use it as a playback fallback when no browser-local bookmark exists.
- Add guarded permanent recording deletion through SageTV, including a without-prejudice option for bad recordings.
- Add Channel −/+ live surfing based on the currently-airing SageTV guide entries.
- Expand diagnostics to schema v5 with DVR state.

## 1.4.0 guide / PWA / commercial-skip phase

- **Live TV Guide** reads SageTV EPG airings through `sagex-api`, grouped by channel and time window.
- **Record / Cancel** actions call SageTV's manual-recording API explicitly. **Watch live** is deliberately implemented as “record then play”: it starts a SageTV manual recording for the currently airing program, waits for its MediaFile, and then plays the growing original transport stream. It does not depend on a SageTV UI context.
- **Comskip EDL support** reads `.edl` files adjacent to SageTV-owned recording files only. The browser can display break state, skip the current break, or auto-skip marked breaks for completed VOD. No arbitrary filename API is exposed.
- **Recording details dialog** adds a larger metadata/artwork view plus Play, Favorite, and raw-stream actions.
- **PWA / Add-to-Home-Screen** adds a manifest, local icons, service worker app-shell cache, Apple mobile-web-app metadata, and an install button where the browser supports the install prompt. API/media responses are intentionally excluded from service-worker caching.
- **10-foot / remote navigation** provides geometry-based D-pad focus movement, focus styling, Escape/Back behavior, and basic standard-gamepad D-pad/A/B handling.
- Diagnostics schema v4 adds guide state and commercial markers.

## 1.3.0 library / lean-back phase

This phase expands the web player from a diagnostics-first prototype into a more usable TV library experience:

- **Thumbnail cards** use SageTV/sagex generated thumbnails when available, with graceful text fallback.
- **Richer metadata** includes category, year, channel, SageTV watched state and description.
- **Library views** include All, Continue Watching, Favorites, Recently Played, SageTV Unwatched and Recording Now.
- **Browser-local favorites** are stored per browser and never alter SageTV favorites or recording metadata.
- **Progress bars** show locally saved resume progress on recording cards.
- **Category and sort controls** support category filtering plus newest, oldest, title and recent-progress ordering.
- **Series navigation** treats recordings with the same SageTV title as a series and enables Previous/Next recording controls. Optional auto-play-next can continue to the next recording after playback ends.
- **Visible transport controls** add -15 seconds, +30 seconds, mute, caption toggle and Favorite buttons for touch/remote-style use.
- **Media Session API** exposes metadata, artwork, play/pause, seek and previous/next actions to compatible browser/OS lock-screen or headset controls.
- Diagnostics schema v3 now includes favorites and the active library view/filter state.

## 1.2.0 pre-test playback hardening

This phase adds behavior that can be implemented and validated without the physical iPhone/ATSC test device:

- **Resume bookmarks** are stored locally in each browser for unfinished recordings. Playback automatically resumes from the saved position when enabled; bookmarks are cleared near the end of a program. No SageTV database metadata is changed.
- **Persistent device preferences** remember volume, playback speed, captions, caption service, preferred audio language, auto-fallback, live-edge startup, live reconnect, and local-cache-only decoder mode.
- **Preferred audio language** can automatically select English, Spanish, French, or German when the transport stream exposes the language metadata. A manual track change also remembers that track's language when available.
- **Live startup/recovery** can start active recordings near the live edge and reconnect with exponential backoff when decoder/network errors occur or the player stops making progress for 12 seconds.
- **Offline-only decoder mode** disables pinned CDN fallback. This is useful after **Cache all decoder assets** reports Offline-ready and proves the web player no longer depends on Internet access.
- **Keyboard controls**: Space pause/resume, Left -15 seconds, Right +30 seconds, F fullscreen, C captions, M mute.
- Diagnostics now include the active settings, saved resume position, recent playback list, and live reconnect state.

## 1.1.0 real-device hardening

This phase adds runtime behavior specifically for iPhone/iPad and low-power browser clients:

- The web app now sends COOP/COEP isolation headers. When the page is served from a **secure origin** (normally Jetty HTTPS or localhost), compatible browsers can expose `SharedArrayBuffer` and use Wasm threading.
- On plain LAN HTTP such as `http://192.168.x.x:8080`, browsers may still refuse shared memory. The player detects that and uses Web Workers instead, with an automatic single-thread retry if a worker pipeline fails.
- The decoder loader now supports baseline, atomic and SIMD libmedia Wasm variants.
- A performance watchdog monitors decode/render FPS, new video stutters and A/V drift. When **Play Auto** selected Wasm and the device cannot sustain real-time decode for a sustained period, the player can switch to the optional compatibility path at the same playback timestamp. This can be disabled with the UI checkbox.
- **Download diagnostic report** exports media metadata, browser capabilities, Wasm/worker/thread state, decoder statistics, caption statistics, performance history and session events as JSON. **Copy diagnostics** provides the same content for forum/GitHub reports.

For maximum iPhone performance, enable HTTPS in the existing SageTV Jetty plugin and access the player through that HTTPS URL. The diagnostics panel will explicitly show whether the browser is a secure context, cross-origin isolated, using Web Workers, and able to use Wasm threads.

## Install as a local SageTV plugin

The ready-to-install files are in:

```text
dist/local-install/SageTVPluginsDev.d/
  sagetv-webplayer.xml
  sagetv-webplayer-2.3.0.zip
```

Copy both files into the SageTV server's `SageTVPluginsDev.d` directory, then install/update **SageTV Web Player** from the SageTV plugin manager and restart SageTV/Jetty if requested.

If Jetty continues serving a previously expanded WAR after an update, stop SageTV and remove the old `jetty/webapps/SageTVWebPlayer/` expanded directory and old WAR, then restart and reinstall/update the plugin.

The web UI is normally available at:

```text
http://<sagetv-server>:<jetty-port>/SageTVWebPlayer/
```

Jetty authentication/security settings remain authoritative for this application.

## First-run decoder cache

The browser runtime is pinned to libmedia `1.3.1`. The plugin exposes a strict allow-listed cache under `/vendor/libmedia/...`.

Use **Cache all decoder assets** in the UI. On the first cache fill the SageTV server needs outbound Internet access to the pinned jsDelivr/npm assets. Once all expected files are cached, normal browser playback can use only the SageTV server.

You can override the cache directory with either:

```text
-Dsagetv.webplayer.cacheDir=/path/to/cache
```

or:

```text
SAGETV_WEBPLAYER_CACHE_DIR=/path/to/cache
```

The default tries a writable `webplayer-cache` directory under SageTV's working directory, then the user's home, then the Java temp directory.

## Playback modes

### ORIGINAL + WASM (preferred for ATSC 1.0)

```text
SageTV .ts
  -> Jetty raw bytes
  -> browser MPEG-TS demux
  -> MPEG-2 Wasm -> browser canvas renderer
  -> AC-3/E-AC-3 Wasm -> WebAudio
  -> A/53 captions -> HTML overlay
```

No server video/audio re-encode occurs.

### ORIGINAL NATIVE

The raw recording is assigned directly to HTML5 `<video>`. This is useful for media whose codecs are already supported by that browser. Typical ATSC 1.0 MPEG-2 + AC-3 is expected to require the Wasm path on many browsers.

### COMPATIBILITY

If configured, an external FFmpeg-compatible program can deinterlace/transcode to H.264/AAC HLS. The Jetty plugin owns the HLS session/playlist/segment endpoints. Configure FFmpeg using:

```text
SAGETV_WEBPLAYER_FFMPEG=/path/to/ffmpeg
```

or:

```text
-Dsagetv.webplayer.ffmpeg=/path/to/ffmpeg
```

The plugin also checks common SageTV/Linux locations. This path is optional and is never required for ORIGINAL + WASM.

## Active recordings / live edge

For a MediaFile that SageTV reports as currently recording, `/live.ts` follows the current physical file as it grows and switches to the next SageTV segment when a rollover occurs. **Go Live** starts approximately 8 MiB behind the current end and aligns the start to a 188-byte MPEG-TS packet boundary so the browser demuxer can resynchronize.

## Main endpoints

```text
GET  /api/health
GET  /api/recordings?limit=75&q=...
GET  /api/guide?start=<epoch-ms>&hours=4
POST /api/guide?airingId=<id>&action=record|cancel|watch
GET  /api/commercials?id=<MediaFileID>&segment=0
GET  /api/media?id=<MediaFileID>
GET  /api/assets
POST /api/assets                       # prefetch pinned decoder assets
GET  /api/transcoder
GET  /stream.ts?id=<id>&segment=<n>    # finite original stream + Range
GET  /live.ts?id=<id>&start=begin|live
GET  /transcode.mp4?id=<id>&segment=0&audio=0&start=0
GET  /vendor/libmedia/...              # strict pinned asset cache
```

There is intentionally no API that accepts an arbitrary local filename.

## Build

Requirements for the manual build included here:

- JDK capable of `javac --release 8`
- `javax.servlet` API 3.1+ JAR (defaults to `/usr/share/java/servlet-api.jar`)
- Node.js for JavaScript syntax/caption tests
- Python 3 for local-plugin packaging

Build and validate:

```bash
./scripts/build-local.sh
./scripts/package-local-plugin.py
./scripts/validate.sh
```

The Java classes are compiled to Java class version **52 (Java 8)**.

## Current validation boundary

The package is build-tested, XML/MD5/WAR validated, and synthetic CEA-608/CEA-708 transport-stream tests are included. This environment cannot run your SageTV database, your real ATSC tuner recordings, or iPhone Safari, so **real 1080i performance, long-duration A/V sync, live segment rollover and device-specific decoding still require field testing on the actual server and clients**. See `docs/TEST_PLAN.md` and `handoff.md`.

## License / third party

Project code is intended for the OpenSageTV ecosystem. Browser decoding uses libmedia/FFmpeg-derived decoder modules under their respective licenses. See `THIRD_PARTY.md`. The plugin does not redistribute FFmpeg itself.
