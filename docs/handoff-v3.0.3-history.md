# SageTV Web Player handoff — v3.0.3

## v3.0.3 Python-layout parity

Field testing showed the MiniClient connection/GFX renderer working but SageMC content clipped. The older Python HTML client did not have this problem. v3.0.3 therefore ports its layout model directly: `#stageWrap`/stage is a relative wrapper with `max-width:1280px`, the GFX canvas is `width:100%; height:auto`, and the browser does not perform a second viewport-fit/letterbox transform. Video bounds are mapped through the actual canvas DOM rectangle and pointer mapping continues to use the canvas bounding box.

## Primary direction: real MiniClient, not HTML recreation

## v3.0.3 field-test fixes

The second real SageMC field test showed the STV/GFX path running but video stuck at `Building playback buffer...`. Root cause: `miniclient.js` referenced HLS helper functions (`waitServerBuffer`, `pollHlsStatus`, `hlsHealth`, `recoverMedia`) that were not present in the v3.0.1 bundle. v3.0.3 restores those functions and keeps HLS startup asynchronous so GFX/media events continue while FFmpeg builds the startup buffer.

The browser window also had a non-16:9 stage and the Canvas/video planes were stretched to it. v3.0.3 reports a 1280x720 logical MiniClient UI by default and aspect-fits/letterboxes it in the browser. Pointer coordinates map through the fitted Canvas. `?ui=1920x1080` is available for field testing.

Do not remove the HLS health/recovery loop: it polls `/api/hls`, pauses when playback catches FFmpeg, resumes after buffer recovery, and asks the MiniClient server bridge to restart media from the current SageTV clock if the producer stalls or ends early.


### SageMC browser texture rule fixed in v3.0.3

The first real SageMC field test proved the MiniClient protocol/GFX path works but exposed a browser-only orientation bug: rasterized text was mirrored and some composed surfaces were vertically flipped. The cause was treating negative `DRAWTEXTURED` destination width/height as Canvas flip instructions. The Vibe Android GDX renderer uses `abs(width)`/`abs(height)` and never flips on those signs; negative height only selects the unblended copy path. Keep that rule. Browser Canvas surfaces are already top-left logical surfaces, so do not add the OpenGL framebuffer Y inversion used by GDX.


The user explicitly wants a **full SageTV MiniClient like the Vibe Android client so SageMC/STV runs unchanged**. Use `opensagetv-vibe/opensagetv-vibe-android-client` `source/dev` as the behavioral source of truth. Do not replace SageMC with an HTML approximation.

### v3.0.3 bridge

- `MiniClientSession` / `MiniClientSessionManager`: real SageTV MiniClient sockets on port 31099 (media type 1 first, GFX type 0 second), Vibe-style framing/property replies/event replies.
- `MiniClientMediaBridge`: Vibe MediaCmd subset backed by existing `HlsSessionManager`; PULL is forced to avoid a browser PUSH pipe.
- `MiniClientServlet`: start/status/stop/input/state plus streaming NDJSON GFX/event transport.
- `miniclient.html` + `js/miniclient.js`: Canvas GFX renderer, surfaces/images, compressed image decoding, texture drawing, video-plane bounds, input events, native HLS/hls.js playback.
- `SageApiBridge.getMediaFileForFilePath()` resolves MiniClient media URLs back to SageTV MediaFiles for the server-side HLS bridge.

Field-test SageMC menus, image loading, navigation, video start/stop/seek and repeated media replacement before adding more API-web UI features.

### Known v3.0 browser-port boundaries

- This release intentionally disables MiniClient ZLIB transfer, event crypto, offline/advanced image caching, unified YUV image cache, transforms/diffuse textures, remote FS, and DVD protocol advertisement until they are needed and can be field-tested. The server therefore stays on the ordinary uncompressed GFX/image/surface path used by SageTV/Android MiniClient fallback logic.
- `PUSH_AV_CONTAINERS=NONE` forces ordinary video playback onto PULL. `MiniClientMediaBridge` resolves the SageTV media URL back to a MediaFile and starts the existing FFmpeg HLS session. This keeps SageTV in charge of UI/media commands while the browser receives H.264/AAC.
- The browser GFX renderer implements lifecycle, primitive drawing, surfaces, uncompressed image lines, compressed bitmap images, texture draws, XFM image, targeted image allocation, and video bounds. Text mode is disabled exactly so SageTV rasterizes text instead of requiring browser font parity.
- The browser transport is streaming NDJSON (one Jetty response carrying ordered GFX/media events); long-poll remains the fallback. Protocol replies to SageTV are generated immediately in Java so SageTV is not blocked by browser image decode/render latency.
- The stable browser MiniClient ID is stored in localStorage. If SageMC is configured per-client on the server, configure the new browser client ID the same way as an Android MiniClient/extender.
- Field test before claiming full Vibe/Android parity: SageMC menu navigation, compressed image loading, surface composition, Live TV/video rectangle transitions, repeated file changes/DEINIT media-socket recycling, long HLS playback, seek, and browser reconnect.


## v2.4.0 current architecture

The simple recordings page remains `index.html`. A separate `client.html` exposes the full API-based SageTV web client in tabs rather than stacking every feature on one page. This preserves the user's earlier request for a simple recording list while making the broader client functionality available when wanted.

The most important playback change is in `js/fmp4-fallback.js`: compatibility HLS now waits for a server-side startup cushion, pauses/rebuffers when it catches the FFmpeg edge, and can restart the HLS session from the absolute SageTV playback position when the producer stops advancing or ends early. `app.js` no longer assumes a browser HLS `ended` event is the true end unless it is near the known SageTV duration.

`TranscoderManager` still prefers NVENC/QSV/AMF/VAAPI/VideoToolbox. NVENC and VAAPI now default to software MPEG-2 decode/deinterlace feeding the hardware H.264 encoder because this is substantially more portable across FFmpeg/driver builds than assuming GPU deinterlace is valid. Set `SAGETV_WEBPLAYER_FULL_HW=1` only when the complete decode/deinterlace chain has been verified on the real server.

Do not remove the startup/rebuffer watchdog just because a fast desktop appears fine; the user's real server showed the browser consuming 27 seconds of generated HLS and then reaching `0.0s ahead`.

## v2.3.0 HLS compatibility merge

The short-playback problem in v2.2 was addressed by replacing the continuous fMP4 response/MSE box pump with the HLS architecture proven in `jzhvymetal/SageTV_HTML5_Client-Python_Server`. The implementation is a Java/Jetty port, not a dependency on Python.

Key classes: `HlsSessionManager`, `HlsControlServlet`, and `HlsFileServlet`. `HlsSessionManager` starts FFmpeg, writes an EVENT HLS playlist and atomic MPEG-TS segments under a per-session temp directory, monitors startup, and retries once with `libx264` if a hardware encoder does not create a playlist. If SageTV's file paths are not locally readable, the manager streams each MediaFile segment through `MediaSourceFactory`/`SageMediaServerSource` (port 7818) into FFmpeg stdin.

The browser compatibility adapter retains the historical `SageFmp4Fallback` API but now uses native HLS on Safari and pinned hls.js 1.7.3 elsewhere. It polls `/api/hls` for server buffer/encoder status. The old `/transcode.mp4` endpoint is retained for diagnostics/backward compatibility only.

Do not regress Compatibility back to one never-ending `fetch()` of fragmented MP4; that was the mechanism that repeatedly stopped after a short buffered period on the user's server.


## v2.2.0 playback fix

The default Watch action for completed recordings is Compatibility (FFmpeg). Compatibility now uses browser MSE to consume the FFmpeg fMP4 response continuously, while the app-owned timeline remains the complete SageTV recording duration. Seeking restarts the FFmpeg request at the requested absolute time. The UI exposes current buffered range/ahead/bytes/state. Server FFmpeg output uses frequent fragments and concatenates multiple SageTV MediaFile segments.


## Current status

v2.3.0 is the current field-test build. The active work is now compatibility-stream field validation rather than adding management features. Do not add more speculative features before running the real SageTV/iPhone tests unless a concrete new requirement appears. The remaining unknowns require real server/media/device evidence.

## v2.1.0 requirement change

The user requested a much simpler web page. The visible UI is now recordings-first: a scrollable recording list with a **Watch** button on every row, followed by the player. Server Watch History, Tuners / Encoder Health, SageTV Favorites / Recording Rules, DVR Schedule, Home dashboard and other management surfaces are intentionally not loaded into the visible page. Do not re-add them unless specifically requested.

Compatibility mode previously hard-coded `libx264`. v2.1.0 made it hardware-first using FFmpeg encoder probing (NVENC/QSV/AMF/VAAPI/VideoToolbox) with `libx264` only as fallback. CUDA/VAAPI may use a full hardware decode/deinterlace/encode chain when the required FFmpeg capabilities are positively detected. Status is exposed through `/api/transcoder` and `X-SageTV-Video-Encoder`, `X-SageTV-Hardware-Transcode`, and `X-SageTV-Hardware-Decode` response headers.


### New in v2.0.0

- SageTV Favorite-rule CRUD/core options and priority.
- Tuner/encoder health, device/input/signal/quality/merit information.
- Manual recording padding/quality editor.
- Server recently-watched history.
- Home dashboard, multi-profile browser state, queue/binge playback, favorite channels/logos, bulk watched/queue actions, sleep timer, PiP, and profile export/import.
- DVR rows now expose scheduled start/end, quality and device assignment.
- Diagnostics schema v6.


## Goal

Maintain a stock-SageTV server web player with **no `Sage.jar` modification**. Prefer the original SageTV MPEG-TS recording and browser-side decoding. Server transcoding is a compatibility fallback, not the primary design.

## Known real-world baseline from the project owner

MediaFile `246520` was verified through v0.1.1 as readable from the SageTV Docker container:

- title: `NOVA`
- path: `/var/media/tv/NOVA-TheBattletoBreathe-65464538-0.ts`
- size: `3427999520`
- transport: MPEG2-TS
- video: MPEG2-Video 1080i @ 29.97 fps
- audio: multiple Dolby Digital / AC-3 tracks
- raw HTTP stream: works
- native browser playback: rejected because the browser does not support that source/codec path

Use this or another known-good ATSC recording for field validation. Do not hard-code this ID/path into production code.

## v1.1.0 architecture

### Server

Jetty WAR context: `/SageTVWebPlayer/`

Key Java classes:

- `SageApiBridge` — reflection-only Sage/sagex access, avoiding duplicate Sage API JARs.
- `MediaSourceFactory`, `DirectFileSource`, `SageMediaServerSource` — original recording byte sources.
- `StreamServlet` — finite original media + single HTTP byte ranges.
- `LiveStreamServlet` — follows an active recording's growth and segment rollover.
- `RecordingsServlet`, `MediaInfoServlet` — library/metadata JSON.
- `AssetCache`, `VendorAssetServlet`, `AssetStatusServlet` — strict pinned libmedia 1.3.1 browser-asset cache/proxy.
- `TranscoderManager`, `TranscodeServlet`, `TranscodeStatusServlet` — optional external FFmpeg/MIM-compatible fallback.
- `SecurityHeadersFilter` — safe response headers without adding a CSP that would accidentally block Wasm/runtime code.

### Browser

- `wasm-decoder-adapter.js` — wraps libmedia 1.3.1, forces non-MSE software decoding for the original MPEG-TS path, detects Wasm SIMD, exposes seek/audio-track/volume/rate/stat APIs, supports active-recording mode.
- `atsc-captions.js` — MPEG-TS PAT/PMT/PES scanner, ATSC GA94 A/53 caption extraction, CEA-608 and CEA-708 service/window state.
- `fmp4-fallback.js` — historical filename for the Compatibility browser adapter; v2.3 uses HLS.js/native HLS, not continuous fMP4.
- `app.js` — Auto policy, library UI, controls, diagnostics and caption overlay.


## 1.1.0 device/performance phase

- `SecurityHeadersFilter` sends COOP/COEP so secure origins can expose SharedArrayBuffer/Wasm threads. Plain LAN HTTP may remain non-secure; do not assume threading is active.
- `wasm-decoder-adapter.js` probes SIMD, Wasm atomics, Web Workers, secure-context status, cross-origin isolation and shared memory. It prefers a worker pipeline and retries without workers before failing.
- Atomic Wasm asset variants are now allow-listed alongside baseline/SIMD.
- `performance-watchdog.js` evaluates sustained decode/render performance. Auto mode may switch a completed recording to compatibility playback at the current timestamp if the watchdog enters `bad` state and a transcoder is available. Manual ORIGINAL + WASM never silently changes modes.
- Diagnostics JSON is generated entirely in the browser and includes server status, MediaFile JSON, browser fingerprint/capability data, decoder/caption stats, watchdog history and event log.
- On iPhone/iPad, first compare behavior over existing HTTP and Jetty HTTPS. HTTPS is required before treating `wasmThreads=false` as a browser limitation.

## Auto policy

1. For MPEG-2/Dolby Digital sources, try ORIGINAL + WASM first.
2. For sources not obviously requiring software decode, try ORIGINAL NATIVE first and then Wasm.
3. If those fail and an external transcoder is configured, use COMPATIBILITY.
4. Always label the active mode in the UI. Never silently transcode.

## Browser asset cache

Pinned version: libmedia `1.3.1`.

The browser normally asks SageTV for `/vendor/libmedia/...`. The server downloads only an allow-listed set from pinned jsDelivr/npm paths. Numeric `*.avplayer.js` chunks are allowed only within the pinned AVPlayer UMD directory, which permits dynamic Webpack chunks while preventing arbitrary URL proxying.

`POST /api/assets` prefetches the known runtime/chunks/codecs. Cache location can be overridden with `SAGETV_WEBPLAYER_CACHE_DIR` or `-Dsagetv.webplayer.cacheDir=`.

Important: a completely fresh server needs outbound Internet once to populate the cache. Do not claim the distributable ZIP contains the third-party `.wasm` binaries; it intentionally does not.

## Captions

CEA-608 implemented:

- CC1–CC4 field/channel routing
- pop-on, roll-up, paint-on controls
- backspace, clear, carriage return, EOC
- basic North-American, special and two extended character sets

CEA-708 implemented:

- `cc_type` 3 packet start and type 2 continuation assembly
- standard and extended service blocks
- service selection
- CW0–CW7, CLW/DSW/HDW/TGW/DLW, DLY/DLC/RST
- SPA/SPC/SPL, DF0–DF7
- multi-window visibility and priority
- anchor/relative positioning metadata
- pen row/column
- basic foreground/background/italic/underline styling
- G0/G1, useful G2, P16 characters and C2/C3 skipping

Still treat the 708 renderer as an interoperability implementation, not a formal conformance claim. Effects, every predefined style semantic, edge styles and every broadcaster corner case need real stream samples.

## Optional FFmpeg/MIM fallback

No FFmpeg binary is bundled. The executable is discovered/configured and invoked with a fixed argument list. The endpoint only accepts SageTV MediaFile IDs/segment/audio/start values; it does not accept arbitrary input paths or command fragments.

Compatibility is hardware-first (NVENC/QSV/AMF/VAAPI/VideoToolbox when usable), with software decode/deinterlace where needed and `libx264` startup fallback. Output is H.264/AAC HLS TS. Field-test the actual server GPU path before changing encoder order.

## Highest-priority field tests

1. Known-good 1080i MPEG-2 + AC-3 recording on Chrome/Edge desktop.
2. Same recording on current iPhone Safari; verify first video/audio render and 10+ minutes A/V sync.
3. Audio switch among English/Spanish AC-3 tracks.
4. CEA-608 CC1 sample.
5. CEA-708 service sample with multiple windows.
6. VOD seek forward/back repeatedly.
7. Active recording from start and `Go Live`; verify a SageTV segment rollover.
8. Decoder asset cache: prefetch, remove Internet, reload page and replay.
9. Optional FFmpeg compatibility mode, including seek-by-restart.
10. Long-duration 1080i CPU/thermal behavior on iPhone/iPad and low-power clients.

Collect browser console errors, `sagetv_0.txt` Jetty entries, `/api/media` JSON and decoder/TS diagnostics for failures.

## Do not regress

- stock `Sage.jar`
- original bytes as preferred path
- single-port Jetty integration
- HTTP Range behavior for completed recordings
- SageTV MediaServer fallback
- Java 8 bytecode
- no arbitrary filesystem-path endpoint
- no mandatory FFmpeg dependency




## 1.5.0 DVR/library-management phase

- `DvrServlet` exposes upcoming SageTV scheduled recordings and unresolved `GetAiringsThatWontBeRecorded(true)` conflicts.
- `LibraryActionServlet` exposes explicit MediaFile-ID-scoped watched, unwatched, progress-sync and guarded delete operations.
- Progress sync is opt-in and uses `SetWatchedTimes`; browser-local bookmarks continue to work with it disabled.
- `RecordingsServlet` and `MediaInfoServlet` now expose `serverResumeSeconds` and `watchedCompletely`.
- Live Channel −/+ uses the current guide's currently-airing rows and the existing explicit Watch-live flow.
- Deletion requires both a MediaFile ID and `confirm=DELETE`; active recordings are rejected.

## 1.4.0 guide / PWA / commercial-skip phase

- `GuideServlet` uses `Database.GetAiringsOnViewableChannelsAtTime` and Airing APIs through the reflection bridge. It exposes read-only EPG data plus explicit `record`, `cancel`, and `watch` POST actions. `watch` means **manual-record current airing and then play the resulting MediaFile**; it does not call UI-context-dependent SageTV playback APIs.
- `CommercialsServlet` accepts only a SageTV MediaFile ID/segment and checks adjacent basename `.edl` or `<mediafile>.edl` files. It never accepts an arbitrary filesystem path.
- `commercial-skip.js` performs browser-side VOD skip decisions and calls the existing seek abstraction. Live follower playback is not seekable, so commercial auto-skip is intentionally VOD-only.
- `manifest.webmanifest`, `sw.js`, and local PNG icons make the app installable/standalone. The service worker caches only static app-shell assets and bypasses API, media, live, transcode, and vendor decoder requests.
- `remote-navigation.js` adds optional geometric D-pad navigation and basic standard-gamepad support. The feature is opt-in and persisted per browser so normal desktop/iPhone arrow-key seek behavior is not changed unless 10-foot mode is enabled.
- The recordings library now has a details dialog in addition to direct Play/Favorite actions.
- Diagnostics schema is v4.

Field-test the guide with at least one current and one future airing. Confirm that Watch Live visibly creates a manual recording in SageTV, returns a MediaFile ID, and then follows the growing TS. Confirm Cancel Recording behaves as expected before relying on it as a live-TV workflow.

## 1.3.0 library / lean-back phase

- `RecordingsServlet` and `MediaInfoServlet` now expose category, year, SageTV watched state and a same-origin sagex thumbnail URL.
- `SageApiBridge` adds `IsWatched`, `GetShowCategory`, and `GetShowYear` reflection calls.
- `session-state.js` now stores browser-local favorites and `autoPlayNext`; it still does not mutate SageTV metadata.
- `library-utils.js` provides deterministic filtering/sorting, series-neighbor selection and resume-progress calculation with Node tests.
- `app.js` renders thumbnail cards, category/filter/sort views, favorites, continue-watching progress, series previous/next controls and optional next-recording autoplay.
- Media Session API integration exposes title/episode/artwork and play/pause/seek/next/previous where the browser supports it.
- Touch-visible -15s/+30s/mute/CC/favorite controls were added so mobile use does not depend on keyboard shortcuts.
- Diagnostics schema is now v3 and includes favorites plus library filter state.

Do not treat browser-local Favorites as SageTV Favorites; they intentionally remain independent until a deliberate SageTV metadata write feature is designed.

## 1.2.0 changes

- Added `js/session-state.js` for browser-local settings, resume bookmarks, and recent-playback state.
- Added automatic VOD resume with near-end cleanup.
- Added persisted preferred audio language and automatic Wasm audio-track selection.
- Added active-recording live-edge default plus decoder/stall-triggered reconnect with exponential backoff.
- Added local-cache-only decoder policy to prove offline/self-hosted operation after prefetch.
- Added keyboard controls and diagnostics schema v2.
- Added `tests/test-session-state.js`.

Field testing should now validate resume accuracy, language selection, iOS background/foreground recovery, live reconnect, and offline-only playback in addition to the existing 1080i/A-V/caption checks.
