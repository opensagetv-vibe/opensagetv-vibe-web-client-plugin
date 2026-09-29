# SageTV Web Player 3.1.0

Real SageTV MiniClient GFX/SageMC in a browser, hosted by the existing SageTV Jetty plugin. This update starts from the uploaded **3.0.3** project and ports missing behavior from the Python HTML5 client's **v150 browser source**.

**No Python server, replacement Sage.jar, or new browser-facing port is required.** The plugin uses the existing MiniClient and media services internally. The browser receives H.264/AAC HLS from a separately installed FFmpeg-compatible executable.

This is a tested development/field-validation build, not a claim of complete Python or Android-client parity. Read `docs/PYTHON_PORT_AUDIT.md` for the implemented features and remaining gaps.

## Install/update

The ZIP includes the compiled WAR, local-plugin installer, complete source, regression tests, and handoff notes. No local compilation is necessary to install it.

For an existing installation, stop SageTV/Jetty, back up the current WAR, and replace:

```text
jetty/webapps/SageTVWebPlayer.war
```

with `dist/SageTVWebPlayer.war` from this package. Restart SageTV/Jetty. Preserve the old WAR for rollback. If Jetty keeps using its old expanded deployment, stop it again and remove **only** the generated `jetty/webapps/SageTVWebPlayer/` directory before restarting. Do not delete recordings, SageTV properties, or the vendor cache.

The local-plugin-manager alternative is to copy both files below to the server's `SageTVPluginsDev.d` folder, install/update SageTV Web Player, and restart when requested:

```text
dist/local-install/SageTVPluginsDev.d/
  sagetv-webplayer.xml
  sagetv-webplayer-3.1.0.zip
```

Open the real client at:

```text
http://<server>:<existing-jetty-port>/SageTVWebPlayer/miniclient.html
```

Hard-refresh the page (Ctrl+F5 in desktop Chrome/Edge) and confirm **3.1.0** in its title/toolbar. Close older client tabs during the update. The new service worker uses a versioned, network-first app shell; API, media and vendor responses are not shell-cached.

`index.html` remains the simple recording player; `client.html` remains the optional API-based Web Admin. They are not the real SageMC MiniClient page.

## What changed

### Buffering and playback

Startup no longer waits for a fixed ten seconds of generated video. Automatic mode starts after **1–4 published segments**, selected by FFmpeg's observed output rate; the first HLS segment is configured for one second of video. This is not a promise of one-second wall-clock startup. Encoder initialization, input probing, disk/network speed, browser autoplay policy and the source format still matter.

The player explicitly starts each HLS session at position zero, fetches the local hls.js asset in parallel with producer startup, and caches encoder capability probes for five minutes (invalidated by executable/config changes). Settings includes a one-segment override and browser-buffer targets.

Rebuffering uses playback intent, rather than treating its temporary `video.pause()` as a user pause. Resume no longer references the undefined `paused` variable. Status polling is serialized; asynchronous startup, status, seek and reconnect work is guarded against stale sessions. A normally completed playlist is not restarted while useful content remains. Recovery sends the absolute position and expected HLS session in one request, waits for the replacement session, and stops after six attempts.

Growing recordings now use a byte follower even when their files are directly readable. It waits at temporary EOF, rechecks segment count and recording state, follows new files, drains final appended bytes, and closes the input when recording truly finishes. The final recorded duration updates the browser timeline when available.

### Hold-menu and input

By default, **hold the left mouse button or a touch on the SageTV canvas for 575 ms** to open the local remote. Releasing a hold does not click/select the SageMC item underneath. A movement greater than 12 CSS pixels becomes a drag instead. The toolbar's Remote button opens the same panel.

The local remote is distinct from SageTV's Options command. Press **Options** inside it to request the STV's own menu. Direction, volume, channel, page, skip and fast-forward/rewind controls repeat while held. The panel also provides numeric keys, literal keyboard text, a full-duration timeline and diagnostics download.

Settings supports right hold, left/right double-click, middle-click activation, native/press-release/double-select click modes, Guide-click assistance, mouse side-button Back, literal keyboard mode, video aspect and render-only view. Settings are saved in this browser. Native clicks travel as one ordered move/press/release/click request.

Remote keyboard shortcuts include arrows, Enter, Escape/Backspace, Page Up/Down, Home, Space, digits, G (Guide), I (Info), O (Options), R (Record), M (Mute) and S (Stop). Text mode and the Keyboard panel send literal text instead of these shortcuts.

### Video presentation and diagnostics

The logical 1280×720 canvas keeps its aspect ratio and remains the sole scaling reference for pointer/video coordinates. The previous negative-texture-dimension fix is preserved and browser-tested. Video-mask assistance recognizes trusted color-key/dark-clear rectangles, reveals the underlying video plane and relocates live preview windows without stopping playback. Menu navigation suspends stale automatic holes and requests repaint. The heuristic can be disabled in Settings for an STV that uses these colors differently.

Buffer info reports browser/server seconds ahead, segment count, output rate, encoder, hardware/software mode, input path type, first-segment/first-progress timing, recovery count and errors. Remote → Download diagnostics saves a JSON report. Review it before sharing because it may contain server addresses and media paths.

## Configuration retained from 3.0.3

Keep the FFmpeg/MIM installation that already worked. The deployed plugin does not bundle FFmpeg. Common overrides are:

```text
SAGETV_WEBPLAYER_FFMPEG=/path/to/ffmpeg
SAGETV_WEBPLAYER_VIDEO_ENCODER=auto
SAGETV_WEBPLAYER_HLS_DIR=/writable/hls/session/storage
SAGETV_WEBPLAYER_CACHE_DIR=/writable/vendor/cache
```

The equivalent JVM properties include `sagetv.webplayer.ffmpeg`, `sagetv.webplayer.videoEncoder`, `sagetv.webplayer.hlsDir` and `sagetv.webplayer.cacheDir`. Hardware encoding remains automatic with software fallback. Full hardware decoding/deinterlacing remains opt-in via `SAGETV_WEBPLAYER_FULL_HW=1` after validating the installed driver/filter chain; enabling this is not necessary for the new startup fix.

The existing pinned hls.js/vendor-cache mechanism is unchanged. A first uncached vendor load requires outbound access from the SageTV server. Native Safari controls its own browser-side buffer length.

SageTV sees a stable browser MiniClient ID, saved in localStorage. Configure SageMC for that client/UI context on the server. Optional URL overrides remain `?clientId=020000000123`, `?ui=1920x1080`, and `?server=host:31099`.

Existing Jetty authentication and network access controls remain important. This release does not add an independent authentication layer. Do not expose an unprotected deployment to the Internet.

## Validation and known limits

See `docs/VALIDATION.md` and `docs/VALIDATION-v3.1.0.txt`. Validation includes 23 core/gesture tests, 19 playback lifecycle tests, the existing seven Node test suites, the native MiniClient wire smoke, eight growing-file/FFmpeg runtime tests, and 15 offline Chromium DOM/renderer/gesture checks.

The Chromium tests mock location/storage/fetch/HLS rather than contacting SageTV. The FFmpeg test uses generated MPEG-2/AC-3 media and software encoding. **The user's SageTV/SageMC server, hardware encoders, Windows deployment, native Safari/iPhone playback and long-running real recordings were not available for live testing.** No measured speedup versus the Python server is claimed.

Advanced Python server controls, Video.js alternatives, custom path-map administration, audio offset/track settings and full renderer/protocol parity remain outside this update; the audit lists them explicitly. The inherited byte-proportional seek approximation for pipe-fed media is still approximate, particularly for variable-bitrate or growing recordings. Direct completed-file FFmpeg seeks remain time-based.

## Build and reproduce tests

Developer tools only: a JDK capable of `--release 8`, a javax.servlet 3.1+ API JAR, Node.js, and Python 3 for packaging/validation. These are not new runtime dependencies for the server.

```sh
bash scripts/build-local.sh
python scripts/package-local-plugin.py
bash scripts/validate.sh
# Optional browser test; needs Python playwright and Chromium:
RUN_BROWSER_TESTS=1 bash scripts/validate.sh
```

`SERVLET_API_JAR` overrides the servlet JAR path. `TEST_FFMPEG` overrides the FFmpeg integration-test executable. `CHROMIUM_PATH` selects a Chromium executable for the offline browser test. Built Java classes target version 52 (Java 8).

Project history is preserved under `docs/*-v3.0.3-history.md`; those files describe older releases and do not override this README. See `handoff.md` for continuation work and `CHANGELOG.md` for this release.
