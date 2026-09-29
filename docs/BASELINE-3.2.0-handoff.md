# Handoff — SageTV Web Player 3.2.0

## Request and baseline

User approved mpegts.js plus Android-like streaming bitrate/audio/CC settings, explicitly selecting opensagetv-vibe/opensagetv-vibe-android-client. Used its source/dev files. Baseline is supplied v3.1.1 ZIP. See docs/VIBE_STREAMING_PORT.md for source/blob identities and scope. Keep stock Sage.jar, existing Jetty port/context, separate FFmpeg/MIM repository and builder, native GFX/long-press remote and HLS fallback. No GitHub changes were pushed.

## Architecture

StreamOptions: immutable allowlisted profile, request prefix stream_. MediaProbe: bounded ffprobe, tracks/language/index. StreamPlan: requested/effective copy/encode decisions. StreamCommand: argument arrays for TS/HLS and optional original-video stream-copy stdout. TranscoderManager.forStream caches bounded encoder probes. The portable path is CPU video decode/filter plus optional GPU encode, not a full GPU pipeline.

HlsSessionManager retains its legacy name/signatures and owns both transports. Continuous sessions write stream.ts/progress.log. ContinuousStreamServlet has a bounded async reader pool; ContinuousFilePump follows growth/final EOF/exact byte resume. Use bstart with 200 chunked, never invent a total Content-Range for growing output. Lazy-reader disconnect must retain FFmpeg/output. Time seek/Apply now builds a replacement before stopping old playback. Periodic output/free-space/idle guards require adequate storage, not a new reservation system.

A53CaptionTap drains original-video copy from the same FFmpeg process, extracts MPEG-2 GA94 or H.264/HEVC SEI triples with PTS and writes captions.bin. It does not keep duplicate video or create a second encode. CaptionsServlet returns bounded look-ahead records; miniclient-captions.js drives existing atsc-captions.js pushCcData using the independent browser video clock. CC is browser-local, not Android STV/event225. No Teletext/bitmap/file subtitle parity is claimed.

miniclient-streaming.js mirrors validation/configuration. miniclient-playback.js owns TS/HLS, session guards, pause intent and one transport fallback per recording. isLive:false is intentional DVR mode: upstream bypasses lazy buffering with isLive:true. Do not re-enable live chasing. Lazy byte resume is not time seeking.

miniclient.js binds Settings, track discovery, profile persistence, requested/effective diagnostics and local CC output. Save-next and Apply-now are distinct. Audio track index resets for future recordings. The new settings apply to miniclient.html; old simple/admin player behavior remains.

## Dependencies and evidence

mpegts.js 1.8.0 is version-pinned in the strict shared AssetCache, NOT embedded. Prepare player assets fetches only mpegts.js+hls.js. Server internet on cache miss or valid cache pre-seeding is required. Do not claim the actual bundle was executed locally; this environment could not fetch it.

Build → package → RUN_BROWSER_TESTS=1 bash scripts/validate.sh. See docs/VALIDATION-v3.2.0.txt. Real FFmpeg tests generate two-language MPEG-2/AC-3/A53, transcode chosen audio/video, copy original CC separately and feed records to the real JS decoder. Mock Servlet tests exercise actual async workers/file IO. Chromium runs production DOM/settings/GFX/CC with mock mpegts.js and SageTV transport.

Priority next acceptance: actual vendor/MSE plus user stock SageTV, then physical QSV/NVENC/VAAPI/AMF/VideoToolbox. Test start→reserve→lazy-resume, pause, seeks, quality/audio/CC Apply, growing completion, stop/reconnect, and collect diagnostics. Never turn mocks into a physical PASS.

Known limits: pipe byte-proportional seek; copied-video keyframe seek; MIME capability hints not decoder/passthrough proof; no HEVC/AV1 encoding; browser CC only (no event225/Teletext/bitmap/SRT); GFX remains main-thread cooperative Canvas; representative CEA decoder tests are not complete broadcast conformance. User recordings are read-only. README documents install, server properties and vendor offline cache paths.
