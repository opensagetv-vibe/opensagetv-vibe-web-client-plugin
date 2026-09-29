# SageTV Web Player handoff — 3.1.0

## User request and constraints

The Python HTML5 client buffered faster; click-and-hold remote did not work; port missing Python behavior. Start from the user's 3.0.3 ZIP. Preserve real MiniClient GFX/SageMC, stock Sage.jar, Java 8 bytecode, existing Jetty/browser port, and separate external FFmpeg/MIM. Do not substitute an API-only imitation of the STV or require a Python server.

## Current result

See README and `docs/PYTHON_PORT_AUDIT.md`. Implemented automatic published-segment startup, explicit HLS position zero, guarded asynchronous playback, user-pause-aware rebuffering, bounded atomic recovery, local hold/double/middle remote, repeat and literal keyboard input, Guide assist, complete-duration timeline, video-mask/preview behavior, diagnostic export, cached encoder probes and a testable growing-recording follower.

The full v150 browser `app.js` reference was CRC-verified; not every Python server module was retrieved. Do not call this a complete Python/backend or Android port. Outstanding features and live acceptance steps are enumerated in the audit.

## Main files

- `js/miniclient-core.js`: pure settings/startup/buffering/gesture/mask rules; CommonJS export for Node tests, browser global for deployment.
- `js/miniclient-playback.js`: HLS lifecycle. Dependencies are injected; generation guards reject stale starts/status/library loads. Intent pause is independent of temporary browser rebuffer pause. Recovery budget survives recovery-created sessions, not unrelated media/manual seeks.
- `js/miniclient.js`: existing raw MiniClient canvas renderer plus DOM controls, ordered input queue, event stream, telemetry and mask/preview integration. Preserve negative texture abs/copy handling and canvas-owned scaling. Do not unconditionally punch a stale fullscreen video hole through menu frames.
- `MiniClientSession`/`MiniClientServlet`: atomic native click, literal text, UTF-8 POST control actions, expected-session seek/recovery/state. Sage commands are native event 136, not assumed raw IR.
- `MiniClientMediaBridge`: media lifecycle, duration, expected-HLS guard, browser position and video bounds. Playback rate handling is limited to ordinary positive browser-supported rates, not reverse playback/frame stepping.
- `HlsSessionManager`/`RecordingFollower`: FFmpeg session lifecycle, dynamic segment/file growth, final EOF, retired-process identity guards, diagnostics and completed-duration updates.
- `TranscoderManager`: hardware-first configuration retained; keyed probe cache added; stdin container autodetection retained for both TS and PS.
- `sw.js`: versioned network-first shell; media/API/vendor excluded.

## Build/test

```sh
bash scripts/build-local.sh
python scripts/package-local-plugin.py
RUN_BROWSER_TESTS=1 bash scripts/validate.sh
```

See the saved validation transcript. The browser suite is offline with mocked transport/location/storage/HLS; it exercises the actual DOM/CSS/renderer/pointer code. An actual synthetic FFmpeg H.264/AAC EVENT HLS transcode and file-growth/rollover tests run separately. No actual SageTV server, SageMC install, Windows service, GPU encoder or native Safari playback was tested here. Do not describe these as live end-to-end tests.

Regression traps: bind native browser timers through wrappers (method-style calls with the wrong `this` caused Chromium “Illegal invocation”); never use a bare undefined `paused`; do not reset recovery count on recovery videoStart; do not recover merely because ENDLIST exists; do not close FFmpeg stdin before terminating a blocked producer on cancellation; stale input/browser state must not retarget newly opened media.

## Next work

Prioritize field diagnostics from the user's server before more speculative refactoring. Reproduce the original short-playback stop, verify held remote and SageMC preview/menu transitions, and measure warm/cold startup against Python. Then implement timestamp-aware pipe seeks and explicitly requested advanced Python controls. Keep the audit updated instead of calling missing features complete.

Install the compiled WAR from `dist/`, or use the included local-plugin XML/ZIP. Existing Jetty authentication remains necessary. No GitHub commit, push or new repository was made as part of this package.

Historical handoff is `docs/handoff-v3.0.3-history.md`; its old “current version” sections are superseded by this document.
