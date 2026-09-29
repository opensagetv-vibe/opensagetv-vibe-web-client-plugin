# Scope note for 3.2.0

Video/audio settings, continuous MPEG-TS and local CEA controls have since been added using the Vibe Android reference. The historical table below is not the current feature matrix. See VIBE_STREAMING_PORT.md and RELEASE-v3.2.0.md. Full Python/Android backend parity is still not claimed.

# 3.1.1 update to the Python-port audit

The historical 3.1.0 table below is retained for provenance. **Its fixed 1280×720 canvas and automatic 1–4-segment descriptions are superseded.** In 3.1.1 the UI follows the window by default, native resize/repaint is actually sent to SageTV, and startup is one published segment followed by a separately configured larger reserve. Browser media events no longer wait for image decode. See `RELEASE-v3.1.1.md` and the current README.

These changes do not fill the previously listed advanced Python backend, alternate Video.js or full Android/extender parity gaps. The full Python backend was not newly audited in this release.

---

# Python v150 → Java/Jetty port audit — 3.1.0

## Compared inputs and scope

Target baseline: the supplied `SageTV-WebPlayer-Local-Plugin-v3.0.3(1).zip`.

Reference repository: `https://github.com/jzhvymetal/SageTV_HTML5_Client-Python_Server`.
Reference archive: `SageTV_HTML5_Client-Python_Server_v150.zip` on the repository's main branch; Git blob SHA `515db2ec53ae41a08b8737ed8ab3260be52565fa`.

The complete v150 `static/app.js` browser source was recovered and verified against its ZIP member: 115,583 uncompressed bytes; CRC32 `6b94fff1`. The archived Python server modules were **not all retrieved/reviewed**. This is therefore a browser-behavior audit plus targeted Java streaming fixes, not proof that every Python backend feature was ported. No upstream archive or Python runtime is bundled.

## Implemented or retained

| Python behavior / missing target behavior | 3.1.0 implementation | Qualification |
|---|---|---|
| Automatic startup 1/2/3/4 segments by fill rate | `SageMiniCore.requiredStartSegments` | Exact v150 rate thresholds; manual/profile overrides added |
| HLS starts at zero | `SageMiniPlayback.start` | `startPosition:0` and `startLoad(0)`; Safari native path retained |
| Custom startup/browser buffer | Settings; finite hls.js targets | Settings apply on the next recording/seek; Safari owns its internal buffer |
| Resume after starvation | Playback intent and bounded watchdog | Fixes undefined variable and accidental user-pause override |
| No restart just because FFmpeg finishes early | Produced/buffered tail checks | ENDLIST alone is not an error |
| Reliable seek/recovery position | Atomic time + expected-session request | Full-duration timeline; inherited pipe byte-seek limitation remains |
| Left/touch hold remote | 575-ms timer, 12-CSS-pixel movement tolerance | No native click/press emitted before a hold resolves |
| Alternate remote triggers | Right hold, left/right double, middle click | Deferred single-click behavior prevents activation behind a double-click remote |
| Transparent local remote | Browser overlay, separate from SageTV Options | Reimplemented layout rather than copying the entire Python page |
| Repeat controls | Initial repeat delay 420 ms; subsequent 150 ms | Cancellation on release/cancel/lost capture/blur |
| Click modes | Native, press/release, double-select | Atomic backend event sequence |
| Guide-click assist | Guide menu hint + delayed SELECT | Default on; switch off if an STV already selects correctly |
| Mouse side-button Back | Command 75, duplicate suppression | Native Sage command, not raw IR |
| Literal keyboard entry | 512-UTF-16-unit request limit; UTF-8 HTTP body | Native keyboard event wire encoding tested |
| Render-only and fullscreen | Toolbarless view and browser fullscreen | “Start render-only” matches Python's no-toolbar intent |
| Video source/fill/zoom/4:3/16:9 | DOM video sizing inside SageTV destination | Source-rectangle crop/complete native renderer equivalence not claimed |
| Color-key/dark-clear video masks and previews | Trusted mask/preview assist + menu repaint | Heuristic can be disabled; full STV behavior needs live testing |
| Canvas-owned aspect ratio | Existing correct 1280×720 geometry retained | No second viewport scaling transform |
| Negative texture dimensions | Existing abs/copy semantics preserved | Native Chromium pixel test verifies orientation and clipping |
| Buffer/producer status | Overlay, compact status, diagnostics JSON | Producing seconds are not wall-clock startup promises |
| Growing media and segment rollover | Java `RecordingFollower` | Actual file append, rollover, final drain and cancellation tests |
| Avoid repeated encoder initialization probes | Five-minute keyed cache | Executable/config changes invalidate the cache |
| Existing simple player/Web Admin | Retained | New MiniClient-only UI does not replace STV with API screens |

## Explicit gaps / not claimed as complete

1. The Python page exposes additional FFmpeg profile controls: live encoder selection, latency modes, output resolution/FPS/bitrate, detailed QSV decode/filter selection, PTS/probe/queue/VBV tuning, audio bitrate/offset and related status. This package retains the Java plugin's existing hardware-first configuration plus new browser buffering controls; those server controls are not all ported.
2. Python's alternate Video.js MPEG-TS/fMP4 HLS player choices, segment-mode knobs, source-fill options and RAM-storage-management UI are not added. The existing Java EVENT/MPEG-TS HLS path remains the supported MiniClient pipeline.
3. Python path-map CRUD/auto-discovery and server-settings administration are not recreated. Existing SageTV MediaFile lookup, direct access and MediaServer fallback remain. The reference Python backend was not fully audited.
4. This is not full Android MiniClient/extender parity. DVD navigation, remote filesystem, transforms/diffuse textures, advanced/offline image caching, unified YUV image/subtitle paths, event crypto and push-media remain unadvertised/unimplemented as in the baseline. No claim is made that every caption/audio-track command in every STV works.
5. Pipe-fed seeks remain byte-proportional, not guaranteed frame/time accurate. Growing files and VBR sources especially need a timestamp-aware seek implementation. The timeline shows the full known media duration; seeking beyond recorded data is not a supported live-edge guarantee.
6. Six recovery attempts are bounded per media/manual-seek lifecycle. They are a fallback, not proof that the underlying recording/input/decoder fault is cured.

## Live acceptance required

Test the same recording with the Python client and this build on the same server/browser. Record first-segment and first-progress timing, encoder, fill rate and browser/server buffer. Repeat after caches are warm. Test a completed recording through EOF, a growing recording through rollover and completion, pause/resume, several seeks, menu↔preview↔fullscreen transitions, long holds, and reconnect while starting playback. Native Safari and Windows/GPU validation remain outstanding.
