# SageTV Web Player 3.1.1

Real SageTV MiniClient GFX/SageMC in a browser, hosted by the existing SageTV Jetty plugin. This update starts from **SageTV-WebPlayer-Local-Plugin-v3.1.0.zip** and addresses unused browser space, native GFX clipping and small startup buffers that do not grow into an ongoing playback reserve.

**No Python server, replacement Sage.jar or new browser-facing port is required.** FFmpeg/MIM remains separately installed. This is a field-validation build, not a claim of complete Python/Android-client parity or live testing on the user's SageMC server.

## Install/update

Stop SageTV/Jetty, back up the current WAR and replace `jetty/webapps/SageTVWebPlayer.war` with **`dist/SageTVWebPlayer.war`** from this project. Restart and open the existing `/SageTVWebPlayer/miniclient.html`. Hard-refresh with Ctrl+F5 and confirm **3.1.1** in the toolbar. Close older client tabs during the update.

Keep the deployed filename `SageTVWebPlayer.war`: a versioned name creates a different Jetty context. If Jetty reuses an old expanded deployment, stop it again and remove **only** its generated `jetty/webapps/SageTVWebPlayer/` directory, then restart. Preserve recordings, properties and the vendor cache. Retain the old WAR for rollback.

The alternative local-plugin installation uses both files in `dist/local-install/SageTVPluginsDev.d/`: `sagetv-webplayer.xml` and `sagetv-webplayer-3.1.1.zip`. Copy them to the server's `SageTVPluginsDev.d` and install/update through SageTV's plugin manager.

## Window sizing and GFX

The stage fills the usable browser area below the toolbar. **Settings → UI resolution → Match browser window** is the new default, including when existing 3.1.0 preferences are loaded. The logical render size follows that area, up to 1920×1080 pixels. Larger displays scale that surface while retaining its aspect.

SageTV receives native resize and repaint events after GFX initialization and when the window, toolbar or fullscreen size changes. The renderer retains downloaded image handles during a resize instead of deleting cached menu text/textures. UI rendering, video bounds and pointer input use the same coordinate mapping.

Fixed 1280×720 and 1920×1080 modes are available for an STV that expects a fixed widescreen layout. Those modes fit the whole image without cropping and can therefore leave bars when the window has a different aspect ratio. An existing `?ui=1280x720` or `?ui=1920x1080` URL overrides the setting; remove `?ui=...` to use automatic window matching.

Video aspect is independent of UI resolution. Source aspect preserves the video's proportions; Fill stretches the video and Zoom may crop it. The negative-texture-size and video-mask/preview behavior from 3.1.0 is retained.

## Fast start, then fill a larger reserve

Default automatic startup waits for **one published playable segment**, not the full reserve. FFmpeg requests a one-second first segment; that is media duration, not a guarantee of one-second wall-clock startup. Initialization, input probing, keyframes, I/O and autoplay restrictions still affect startup.

The HLS loader begins with a **six-second forward target**. The first appended fragment or playback progress promotes that target to **180 seconds by default**. Settings allows 30–600 seconds. This is a goal, not a promise that every browser/device has enough memory for the selected duration. The loader continues fetching until the reserve target, then replenishes it as playback consumes data. Low-delay mode no longer caps the ongoing reserve at 60 seconds. A setting change applies without reopening the recording.

The player disables finite live-edge catch-up for the recording EVENT playlist: a fast FFmpeg producer must not drag playback forward toward newly produced segments. A refill watchdog wakes a loader that stops advancing **before the browser buffer is empty**, including while paused or blocked by autoplay. It avoids repeatedly interrupting healthy in-flight segments and avoids competing recovery restarts. If the server already has playable data, the browser loader is handled before restarting FFmpeg.

FFmpeg is not paced at playback speed and can continue building its disk-backed buffer while the browser plays or pauses. It can finish transcoding a recording before playback finishes; ENDLIST is normal in that case. Growing recordings follow appended data and new source segments. At the live edge, the producer cannot buffer content that has not been recorded yet. Browser forward reserve is bounded; server HLS disk files remain until session cleanup. **Use adequate writable HLS storage; this is not an unlimited in-memory cache or a new disk-quota manager.**

Backward browser buffer is 30 seconds. Quota errors reduce the effective forward target rather than repeatedly restoring an allocation the browser rejected. Native Safari owns its internal buffer; its target is shown as browser-managed.

## Threading and scheduling

The server is multithreaded: separate native GFX and media-reader threads, Jetty request workers, a feeder thread for pipe input, an FFmpeg stderr reader and a producer monitor. FFmpeg itself is a separate process. The feeder is not used for ordinary direct-file input, and finished/unused threads correctly show idle.

The browser enables **hls.js worker mode** for HLS transmuxing when supported. The player/DOM/GFX Canvas code remains on the main JavaScript thread. GFX commands execute in order but yield after 128 commands or approximately 8 ms, and media lifecycle events do not wait behind PNG decoding. This reduces contention; it is **not** an OffscreenCanvas worker renderer, and `async` alone is not multithreading. Browser decoder/compositor implementation and hardware acceleration are browser/device-dependent. The diagnostic label distinguishes worker configuration from proof of a running worker.

## Remote and diagnostics

Hold left click or touch on the SageTV picture for about 0.6 seconds to open the local remote. Release does not activate the underlying item; a drag cancels the hold. **Options** inside the remote sends SageTV's own menu command. Keyboard, repeat controls, Guide-click assist and the full-duration recording timeline are retained.

**Buffer info** shows native/display dimensions, startup/reserve phase, current/effective browser target, server seconds ahead, recent/average production rate, refill wakeups, encoder, segment age and producer-thread activity. **Remote → Download diagnostics** includes those details plus a MiniClient connection/thread snapshot. Review reports before sharing: they can contain server addresses, client IDs and media paths.

## Configuration and dependencies

Keep the FFmpeg/MIM setup that already worked. Common retained settings:

```text
SAGETV_WEBPLAYER_FFMPEG=/path/to/ffmpeg
SAGETV_WEBPLAYER_VIDEO_ENCODER=auto
SAGETV_WEBPLAYER_HLS_DIR=/writable/hls/session/storage
SAGETV_WEBPLAYER_CACHE_DIR=/writable/vendor/cache
```

Equivalent JVM properties are `sagetv.webplayer.ffmpeg`, `sagetv.webplayer.videoEncoder`, `sagetv.webplayer.hlsDir` and `sagetv.webplayer.cacheDir`. Hardware-first encoding with software fallback is unchanged. Full hardware decode/deinterlace remains opt-in after driver/filter validation; it is not required for this update. The pinned vendor-cache mechanism remains; an uncached first load requires server Internet access.

SageTV sees a browser MiniClient ID saved in localStorage. Configure SageMC for that client context. The optional `?clientId=...` and `?server=host:31099` overrides remain. Existing Jetty authentication/access controls remain necessary; this update adds no independent authentication layer. Do not expose an unprotected server to the Internet.

`index.html` remains the simple media player and `client.html` the API-based Web Admin. Use **miniclient.html** for the real STV/SageMC.

## Validation and limitations

See `docs/VALIDATION.md`, `docs/VALIDATION-v3.1.1.txt` and `docs/RELEASE-v3.1.1.md`. Tests cover 27 core/gesture scenarios, 31 playback scenarios, native resize/repaint wire messages, 10 file/FFmpeg checks, 27 offline Chromium UI/layout/scheduling checks and the legacy suites. A 240-second synthetic MPEG-2/AC-3 recording produces H.264/AAC HLS beyond a 180-second reserve and reaches clean EOF.

**Browser HLS tests mock the loader/video buffer; they are not live network/MSE playback tests.** The native peer is mocked. The user's SageTV/SageMC server, real recordings, Windows deployment, GPU paths and native Safari were not available for live testing. No measured speedup relative to Python is claimed. Passing tests do not establish complete SageMC compatibility.

Advanced Python server controls, alternate Video.js players, custom path maps, audio offset/track settings, timestamp-accurate pipe seeking, full transforms/diffuse textures and unified YUV/subtitle rendering remain outside this update. `docs/PYTHON_PORT_AUDIT.md` preserves the scope and qualifications; the old fixed-1280/1–4-segment descriptions are explicitly superseded.

## Build/test

Developer-only dependencies: JDK supporting `--release 8`, a javax.servlet 3.1 API JAR, Node.js, Python for packaging, FFmpeg for runtime tests, and optional Playwright/Chromium for UI tests. No Python service is deployed.

```sh
bash scripts/build-local.sh
python scripts/package-local-plugin.py
RUN_BROWSER_TESTS=1 bash scripts/validate.sh
```

`SERVLET_API_JAR`, `TEST_FFMPEG` and `CHROMIUM_PATH` override test/build paths. Java classes target major version 52 (Java 8). Source, compiled WAR, local-plugin installer and `handoff.md` are included. No GitHub changes were pushed.
