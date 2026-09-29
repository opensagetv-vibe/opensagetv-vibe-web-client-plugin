# SageTV Web Player handoff — 3.1.1

## Request / base

The user reported that 3.1.0 leaves browser space unused, clips SageMC GFX and does not continuously fill a large reserve after fast startup. They also asked whether both sides are multithreaded. Input is the conversation's `SageTV-WebPlayer-Local-Plugin-v3.1.0.zip`; its SHA-256 is in BUILD_INFO.json.

Keep stock Sage.jar, existing Jetty context/port, Java 8 bytecode, real native MiniClient GFX/STV behavior, external FFmpeg/MIM and a complete source+WAR+local-installer ZIP. Do not replace SageMC with an API-only UI or reintroduce a Python server. No GitHub push/repository mutation was performed.

## Changed production files

- `miniclient.css/html/js`, `miniclient-core.js`: remove 1280 display cap; viewport flex layout; automatic bounded native resolution plus fixed modes; one contain rectangle for UI/video/input. INIT and resize handshake now reach native SageTV. Retain image/surface caches on resize. URL `?ui=...` deliberately overrides Settings.
- `miniclient-playback.js` + core: automatic first-segment startup, 6-second initial loader target, promotion to 180-second default reserve, immediate buffer-setting updates, no finite EVENT live-edge jump, 30-second back buffer, quota reduction retention. Proactive refill before starvation, including user-paused/autoplay-blocked states. Give healthy inflight fragments time to finish. Do not let proactive/reactive watchdogs duel; handle available server data before producer restarts. Recovery still bounded to six per media/manual seek.
- `miniclient.js` event loop: media lifecycle bypasses image decode; ordered GFX processing yields every 128 commands or ~8 ms. Read-ahead has queue/byte backpressure. Renderer remains main-thread Canvas, NOT an OffscreenCanvas worker. `enableWorker` is configured for hls.js but browser fallback can occur.
- `MiniClientSession`: after GFX_INIT reply send native resize192 + repaint193. Browser resize does the same. Queue local resize ahead of native notification; keep synchronized output packets. Validate size bounds. Report queue/thread state; explicit overflow instead of arbitrary GFX command loss.
- `MiniClientServlet`: drain final queued error/status even after session closes.
- `HlsSessionManager`: monitor stays active after startup and forces final EOF metrics. Parse published manifest rather than sorting the directory. Throttle snapshots, report recent rate/last-segment age/thread activity. Segment filenames allow 5 or more digits. No new -re pacing, FFmpeg command changes, disk quota or buffer deletion policy.
- Version 3.1.1 across WAR UI, cache key, PluginVersion, build and installer. Existing dependency versions are unchanged; in particular javax.servlet API stays 3.1.0.

## Tests and scope

Run build, package and `RUN_BROWSER_TESTS=1 bash scripts/validate.sh`. See `docs/VALIDATION-v3.1.1.txt` and `docs/VALIDATION.md` for precise results and limitations. 27 core/gesture, 31 playback, 15 existing Chromium and 12 new geometry/scheduling tests; native loopback resize/repaint test; 10 runtime checks including autonomous monitor updates during a 240-second synthetic FFmpeg transcode.

Browser loader/TimeRanges are mocked, including a virtual-time top-up sequence. New Chromium geometry test runs shipped code with a synthetic native event peer and checks corners, resize requests, pointer scaling, cached handles, fullscreen and media dispatch during blocked PNG decode. It is NOT a live SageMC/video test. The FFmpeg test really encodes synthetic MPEG-2/AC-3 into H.264/AAC HLS; a test-only readrate allows observation during production. Production FFmpeg command is asserted to be unpaced. No live user server, real recording, Windows service, GPU, Safari or performance comparison to Python was tested.

## Field acceptance

Replace the existing WAR, restart and hard-refresh. Confirm toolbar3.1.1. Remove any ?ui= URL override and select Match browser window. Check all menu corners and preview placement through resize/fullscreen. Start the same recording, verify buffer phase moves startup→reserve and browser ahead grows toward180 while server ahead/segments continue increasing. Pause and verify reserve filling without auto-resume; resume and seek; play a growing recording through rollover/end.

If another stall occurs, get Remote→Download diagnostics immediately: requested/native/display size, actual/effective buffer target, server-ahead, segment age, recent rate, loader wakeups, FFmpeg stderr and thread snapshot. Diagnose which stage is stuck before changing unrelated encoder settings. Field success is not yet established.

## Remaining work / traps

The existing Python-port audit remains deliberately incomplete: alternate Video.js paths, advanced FFmpeg/QSV/audio controls, path-map CRUD, timestamp-accurate pipe seeks, full native transforms/diffuse/YUV/subtitle parity and a true worker GFX renderer remain out of scope. Do not claim that this update completes them.

Do not reset image handles on ordinary resize, drop random GFX packets, call startLoad on every health poll, restore maxMaxBufferLength after library quota reduction, wait for180seconds before initial play, cap low-delay reserve implicitly, or use a finite EVENT liveMaxLatencyDurationCount. Keep stage size out of intrinsic canvas flow to avoid feedback loops. `async` is not a background thread. HLS files can grow to the size of the transcoded recording; reserve settings bound browser memory targets, not server disk use.

Current README/architecture supersede historical 3.1.0 and API-player docs. Keep the previous ZIP/WAR untouched for rollback.
