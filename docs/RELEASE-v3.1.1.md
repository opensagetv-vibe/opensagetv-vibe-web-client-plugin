# SageTV Web Player 3.1.1 — window, reserve and threading update

## Diagnosis and implemented changes

The 3.1.0 source imposed a 1280-pixel maximum display width and did not notify SageTV of native size changes from its browser layout callback. The 3.1.1 stage occupies the full usable viewport by default, sends resize/repaint at initialization and later size changes, and preserves native image handles during resize. Fixed-aspect rendering remains an option; video aspect is separate.

Startup and steady reserve now have independent policies: wait for one published segment, begin loading with a six-second forward target, then promote to the configured larger reserve after the first fragment/progress. Default reserve180seconds; configurable30–600. The goal is continuous top-up, not waiting for that full reserve before starting. Actual memory limits, available media and production/download rates determine achievable reserve.

The recording HLS playlist is EVENT and can still be treated as live by hls.js. A finite live-max-latency could move playback toward the advancing producer edge; that limit is disabled. Refill wakeup runs before starvation, including while paused, without repeated restarts of active fragment requests. Useful server reserve is handled as a browser-load problem before discarding/restarting FFmpeg.

The FFmpeg monitor continues refreshing after startup and records a final EOF snapshot. It reports recent production rate and segment age. Ordinary file input remains unpaced, while growing recordings follow newly recorded data. Completed production is not the same as completed playback. The browser reserve is bounded; server disk storage still accumulates segments until cleanup.

## Is it multithreaded on both sides?

**Server: yes.** Native GFX/media readers, Jetty workers, optional input feeder, stderr reader and producer monitor are separate threads; FFmpeg is a separate process. Not every thread is active for every input mode. More threads do not guarantee sufficient encoder or I/O throughput.

**Browser: partly.** HLS worker mode is enabled, subject to support/fallback. DOM, control and Canvas GFX remain main-thread JavaScript. This release separates media dispatch from PNG decode and yields between GFX batches to reduce contention. It does not implement a dedicated GFX-renderer worker. Worker-enabled configuration is not proof that a particular device successfully started its worker. Native decoding/composition are managed by the browser.

## Tests and installation

See VALIDATION.md and the saved transcript. 27 core tests,31 playback tests,27 offline Chromium checks,10 runtime checks and native protocol/legacy validation. A real synthetic240-second transcode confirms producer growth beyond the180-second browser target. Browser HLS buffering is mocked, not live MSE/network playback. The user's SageMC/GPU/Safari environment remains untested.

Install dist/SageTVWebPlayer.war as jetty/webapps/SageTVWebPlayer.war, restart and hard-refresh. Verify3.1.1. Use Match browser window and remove an old ?ui= override. Test the same recording with Buffer info on. Capture Remote→Download diagnostics immediately after any remaining stall. Keep the3.1.0 WAR for rollback.

## Primary-source references used in this change

- SageTV native renderer resize behavior: https://github.com/google/sagetv/blob/master/java/sage/miniclient/GFXCMD2.java (Canvas componentResized posts native resize).
- HLS live-edge synchronization logic: https://github.com/video-dev/hls.js/blob/master/src/controller/stream-controller.ts (synchronizeToLiveEdge).
- HLS buffer-configuration surface: https://hlsjs.video-dev.org/api-docs/hls.js.streamcontrollerconfig
- Worker vs main-thread execution: https://developer.mozilla.org/en-US/docs/Web/API/Web_Workers_API/Using_web_workers

These references support API/architecture choices, not a claim of live validation on the user's server. The inherited Python browser-port scope and its remaining backend/protocol gaps are recorded in PYTHON_PORT_AUDIT.md.
