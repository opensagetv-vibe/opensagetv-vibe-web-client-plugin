# Test plan — v1.0.0

## Automated/package tests

Run:

```bash
./scripts/build-local.sh
./scripts/package-local-plugin.py
./scripts/validate.sh
```

Checks include JavaScript syntax, synthetic MPEG-TS CEA-608/CEA-708 decode, web.xml/plugin XML parsing, local package MD5, WAR contents and Java 8 class version 52.

## SageTV server functional test

1. Install local plugin files from `dist/local-install/SageTVPluginsDev.d`.
2. Open `/SageTVWebPlayer/`.
3. Verify Server, Decoder assets and Compatibility fallback cards.
4. Prefetch decoder assets and confirm expected cache count is reached.
5. Load a known-good completed ATSC MediaFile; `/api/media` should show `streamAccessible:true`.
6. Open original raw stream and verify HTTP 200/206 and `video/mp2t`.
7. Use **Play Auto**; MPEG-2/AC-3 should select ORIGINAL + WASM before compatibility mode.
8. Verify first video and audio render, 10+ minutes A/V sync, volume and speed.
9. Seek forward/back repeatedly and confirm captions resynchronize.
10. Switch AC-3 audio tracks.
11. Test CEA-608 and CEA-708 samples separately.
12. Start an active recording, use Watch and Go Live, and observe a segment rollover if possible.
13. Disconnect server Internet after asset prefetch and verify Wasm replay remains functional from the local cache.
14. If FFmpeg fallback is configured, force Compatibility and test play/seek.

## Client matrix

At minimum test current:

- Chrome / Edge on Windows
- Firefox on Windows/Linux
- Safari on iPhone/iPad
- Android Chrome or WebView-class browser if desired

Record CPU load, dropped/stutter indicators, A/V sync and thermal behavior for 1080i MPEG-2.

## Failure capture

Save:

- browser console
- `/api/health`
- `/api/assets`
- `/api/transcoder`
- `/api/media?id=...`
- Decoder statistics panel
- ATSC/caption diagnostics panel
- relevant SageTV/Jetty log entries


## 1.1.0 performance/isolation tests

- Test the same 1080i recording over Jetty HTTP and HTTPS; compare `secureContext`, `crossOriginIsolated`, `wasmThreads`, first-frame time, render FPS and stutter counts.
- On iPhone, verify worker mode starts. If it fails, verify automatic single-thread retry is reported.
- Run 15+ minutes and confirm the watchdog remains GOOD/WARNING without false compatibility handoff.
- Artificially load the device (or use a slower device) and verify Auto mode switches to compatibility at approximately the same timestamp when FFmpeg is configured.
- Disable **Auto compatibility fallback** and verify the player only reports underperformance without changing modes.
- Download the diagnostic JSON after each test and attach it to the issue.


## v1.2 persistence and recovery tests

1. Play a completed recording beyond 30 seconds, stop, reload the page, and verify Play Auto resumes near the stored time.
2. Play within 45 seconds of the end and verify the resume bookmark disappears.
3. Select Spanish (or another available language), reload, and confirm the preferred language is selected again when present.
4. Cache all decoder assets, enable **Decoder assets: local cache only**, disconnect Internet access from the SageTV server/browser, and verify original+Wasm playback still starts.
5. For an active recording, enable **Start active recordings at live edge**, start playback, and verify it begins near live.
6. Interrupt the live stream/network long enough to stall playback and verify automatic reconnect attempts appear in status/diagnostics and return to live edge.
7. Verify keyboard shortcuts do not fire while focus is in an input/select/search field.

## v1.3 library / controls tests

1. Confirm recording cards load sagex thumbnails or gracefully fall back to text when no thumbnail exists.
2. Verify category/year/channel/watched metadata matches SageTV for several recordings.
3. Add/remove browser-local Favorites and reload the page; confirm persistence without changing SageTV Favorites.
4. Verify All, Continue Watching, Favorites, Recently Played, Unwatched and Recording Now views.
5. Verify category filter and all sort modes.
6. Confirm progress bars reflect saved resume positions.
7. For multiple recordings with the same SageTV title, verify Previous/Next navigates chronologically and Auto-play next starts the next recording only when enabled.
8. Test touch-visible -15s/+30s, mute, CC toggle and Favorite controls on iPhone.
9. Where supported, verify Media Session lock-screen/headset play/pause/seek/next/previous actions.


## v1.4 field checks

1. Load a 4-hour guide window and verify channel numbers, titles, times and current-airing highlight.
2. Schedule a future airing with Record, refresh SageTV, then Cancel Recording and verify state returns.
3. On a currently airing program, use Watch Live and verify a SageTV manual recording is created and the returned MediaFile begins original-TS playback.
4. Place a valid Comskip `.edl` next to a completed recording; verify marker count, manual Skip Commercial, and auto-skip.
5. Install/Add to Home Screen over HTTPS and verify standalone launch; disconnect Internet but keep LAN access and verify the app shell loads.
6. Enable 10-foot mode and navigate cards/buttons with keyboard D-pad and a standard gamepad.
7. Open/close recording Details repeatedly on iPhone and desktop.

## v1.5 field checks

1. Verify upcoming recordings match the SageTV schedule and unresolved conflicts are visible.
2. Cancel a test manual recording and verify SageTV removes it from the schedule.
3. Mark a test recording watched/unwatched and verify SageTV UI reflects the change.
4. Enable SageTV progress sync, play 60+ seconds, then verify SageTV resumes near that point.
5. Verify a pre-existing SageTV resume point is offered when no browser bookmark exists.
6. Test Channel −/+ while watching an active recording and verify the adjacent guide channel starts as a SageTV manual recording.
7. Delete a disposable recording through each delete mode and verify active recordings cannot be deleted.


## v2.0.0 pre-field-test completion

Added server Favorite rules, tuner/encoder status, recording options, server watch history, browser profiles, queue, favorite-channel guide filtering/logos, home dashboard, bulk non-destructive library actions, sleep timer, PiP hooks, and settings export/import. Field testing is now the gating activity for additional changes.
