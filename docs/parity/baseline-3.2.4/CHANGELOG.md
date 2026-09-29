# 3.2.4 — Vibe overlay and full-browser video

- Port actual Vibe Android edge layout and 30 source icon assets; package source
  provenance and license notices. Browser tools are in Help, not a giant grid.
- Connect video/audio/CC/diagnostic/statistics/aspect icons to working actions.
- Fix clear/fill mask operations changing video destination when OSD hides.
- Browser-owned full video with native small-preview and exact-native options.
- Fill default and one-time Source migration; Fit/Stretch remain selectable.
- Preserve translucent OSD, pointer mapping, cached images and native startup gate.
- 14 new display and 22 new browser scenarios, including real local H.264 decode.
- All required groups passed in split runs; live SageTV/GPU/MSE still untested.

# 3.2.3 — First-frame UI-event ordering (2026-09-24)

- Diagnose the server log's pre-frame MiniUIClientReceiver NPE; crypto query was a later symptom.
- Gate unsolicited UI events until the first FLIPBUFFER reply; keep initialization replies live.
- Coalesce startup resize/repaint/media updates and drop early user input, never replay at login.
- Preserve latest window dimensions, runtime resize, image handles, native recovery and encryption.
- Add first-frame/event-gate diagnostics and 13 real-TCP synthetic-server startup cases.
- Reproduce original 3.2.2 premature resize/repaint against its untouched WAR.
- No auth policy, Sage.jar, media configuration, FFmpeg/MIM, or installed plugin metadata changes.
- Live SageTV/SageMC verification remains required; see current validation evidence.

# 3.2.2 — Native authentication handshake

- Implement native RSA/Blowfish authentication handshake instead of returning empty CRYPTO_ALGORITHMS.
- Preserve binary public/session keys; encrypt nonempty reply/event bodies with original plaintext header lengths.
- Serialize old-mode enable/disable acknowledgements with input and property replies.
- Respect server authentication settings; do not change target host, credentials, or login policy.
- Suppress type-5 recovery while event encryption is active; retain diagnostics without exporting keys.
- Add 14 real-JCA/loopback authentication scenarios and reproduce the 3.2.1 failure with a synthetic auth-required peer.
- Live server reason/policy and physical SageMC login/playback remain unverified.


# 3.2.1 — 2026-09-24

Fixed native GFX recovery negotiation and type-5 reconnect, partial-frame handling,
worker startup ordering, socket cleanup/cancellation, and same-client-ID in-plugin
replacement. Terminal browser failures no longer endlessly reopen a dead native
session's HTTP stream. Recovery/diagnostics controls remain visible in render-only
and fullscreen views. Added bounded connection metadata and failure snapshot.
Added 11 real-loopback/fake-server and 10 production-browser/mock-HTTP regression
scenarios. This does not identify the source of the original server reset and is
not a live SageTV/GPU validation claim. Existing streaming settings are unchanged.

# Changelog

## 3.2.0 — 2026-09-24

Continuous MPEG-TS/mpegts.js, validated quality/encoder controls, source audio track/language/codec/channels/delay and original-video A/53 extraction with browser-local CEA-608/708. Reference: opensagetv-vibe/opensagetv-vibe-android-client/source/dev. Retains stock Sage.jar/Jetty/GFX/remote/HLS. Adds byte-resume/idle/output guards and regression tests. Vendor assets are server-cached, not bundled. Actual vendor/MSE and physical GPU/SageTV remain untested; full Android subtitle/CC parity is not claimed. See docs/RELEASE-v3.2.0.md.

## 3.1.1 — 2026-09-24

### Browser window and native graphics

Remove the 1280-pixel display cap. Default UI resolution follows the usable window (bounded at 1920×1080 render pixels); fixed 720p/1080p modes remain available and are contained without cropping. Notify SageTV with native resize/repaint at INIT and subsequent size changes. Keep cached image/surface handles when resizing. Canvas, video bounds and pointer mapping use one display rectangle.

### Fast start and continuous reserve

Automatic startup now waits for the first published segment regardless of producer rate. The HLS loader starts with a six-second forward target, then promotes to the configured reserve after the first append/progress (180 seconds default, 30–600 configurable). Low-delay mode no longer forces a small reserve. EVENT playlists retain recording position instead of finite live-edge catch-up. A no-progress loader wakeup works before starvation and while paused; available server data is not mistaken for a producer failure. Respect memory-pressure reductions and keep only 30 seconds of backward buffer.

### Scheduling and diagnostics

Media lifecycle events bypass the GFX image-decode queue. Ordered GFX work yields every 128 commands or approximately 8 ms; this is cooperative main-thread scheduling, not a GFX worker. Queue overflow is explicit, not silent command loss. Producer monitoring runs beyond startup, final snapshots are forced at EOF, and diagnostics include current targets, refill wakeups, native/display geometry and server thread activity. Hardware-first FFmpeg and stock Sage.jar remain unchanged.

### Validation boundaries

27 core/gesture tests, 31 playback tests, native loopback protocol test, 10 file/FFmpeg checks, and 27 offline Chromium checks; legacy tests retained. A 240-second synthetic recording verifies continued production beyond 180 seconds. Browser HLS buffering tests use mocks, not real MSE network playback; no user SageTV/SageMC/GPU/Safari field test or Python startup benchmark is claimed.


## 3.1.0 — 2026-09-23

### Python behavior port

Automatic 1–4 published-segment startup, HLS position zero, local 575-ms hold remote, double/middle/right trigger options, native click modes, repeat controls, Guide assist, side-button Back, literal keyboard entry, render-only view, full-duration timeline, aspect choices, mask/preview compositing and expanded buffer diagnostics.

### Correctness fixes

Undefined rebuffer pause variable; stale startup/status/seek events; duplicate and out-of-order mouse clicks; click leakage after holds/drags; premature ENDLIST recovery; recovery counters resetting indefinitely; overlapping status polling; native-browser timer binding; short completed clips waiting for nonexistent segments; old PWA shell masking upgrades.

### Server streaming

Cached keyed encoder probes; active files follow appended bytes rather than opening as finite files; dynamic segment rollover; final-byte drain and recording-completion duration; process identity and stop ordering; TS/PS stdin autodetection. Existing hardware-first behavior, stock Sage.jar and one browser-facing Jetty port are retained.

### Validation and boundaries

See the saved test transcript and port audit. Live SageTV/SageMC, Windows, hardware encoding, native Safari and measured comparison with Python remain unverified. Advanced Python backend controls and alternate Video.js pipelines are not all ported.

## 3.0.3 and earlier

Historical notes remain in `docs/README-v3.0.3-history.md`, `docs/handoff-v3.0.3-history.md` and the local-plugin manifest release history.
