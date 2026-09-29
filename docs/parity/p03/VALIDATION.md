# P03 / 3.2.6 validation

Validation was executed in separate groups because the HLS long-reserve test intentionally runs for roughly four minutes and exceeded the combined command's tool timeout. The interrupted combined log is retained; individual interrupted groups were rerun. No monolithic exit-0 is claimed.

## P03-specific results

| Group | Result | Evidence |
|---|---:|---|
| Java Teletext parser/session | 28 PASS | `evidence/teletext-java.log` |
| Browser Teletext controller (Node) | 5 PASS | `evidence/teletext-js.log` |
| Production Chromium Teletext | 5 PASS | `evidence/chromium.log` |

The Java suite covers 188/192/204 transport framing, descriptor/page discovery, timed rows/update/clear, multi-client isolation, DVB-only negative detection, seek/FLUSH generations, delay, virtual-slot selection, Off behavior and payload-free staged diagnostics.

## Retained regression results

| Group | Result |
|---|---:|
| P02 playback/subtitle architecture | 33 PASS |
| Streaming servlet | 16 PASS |
| FFmpeg streaming/caption runtime | 14 PASS |
| HLS runtime/long reserve | 10 PASS |
| MPEG-TS/settings/caption Node | 17 PASS |
| Native recovery | 11 PASS |
| Native startup ordering | 13 PASS |
| Native authentication | 14 PASS |
| Chromium remote/GFX | 15 PASS |
| Chromium layout/scheduling | 12 PASS |
| Chromium streaming/settings | 12 PASS |
| Chromium Teletext | 5 PASS |
| Chromium recovery | 10 PASS |
| Chromium Vibe overlay/display | 22 PASS |
| **Chromium total** | **76 PASS** |

The HLS runtime rerun includes the 240-second synthetic source that continues past the configured 180-second reserve target.

## Fixtures

Deterministic project fixtures include `teletext-188.ts`, `teletext-192.ts` and `teletext-204.ts`. Each carries page 888 / English / PID 300 updates followed by a clear. Existing DVB-only fixtures are also used as negative Teletext inputs.

These generated fixtures validate the implementation mechanically. They are not substitutes for the real-source WP03-008 field gate.

## External/field rows still pending

No unchanged Breakfast, Classic Holby City, Taskmaster or comparable UK broadcast media file was available in the conversation/library/container. Therefore visible real-source timing across original-source, video-copy, hardware-transcode and software-fallback paths is **not** reported as PASS.

P01 WP01-004 real pinned mpegts.js/hls.js MSE acceptance also remains external-pending. Chromium vendor transports in these tests are mocked.
