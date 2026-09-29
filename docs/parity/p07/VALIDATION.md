# P07 validation

Runtime: **3.2.10-P07**  
Date: **2026-09-25**

## New P07 deterministic tests

`DvdNativeProtocolSmoke` validates the stock-role gate, NEWCELL/STC 45 kHz -> 90 kHz mapping and 33-bit wrap, bounded all-or-nothing ring writes, backpressure timeout, FLUSH cancellation, 16 KiB startup-probe rejection, lazy DVD activation, commands 32-37 state/bounds handling, exact accepted/read byte accounting, `0x80` segment EOS, `0x100` drain, and equal-sized replacement-cell generations.

Result: **PASS**.

`MiniClientDvdWireSmoke` uses real loopback MiniClient type-1/type-0 sockets. It validates exact wire response alignment for commands 0, 1, 17, 22, 23, 29 and 32-37, including malformed metadata and an interleaved zero-reply SEEK.

Result: **PASS**.

## Inherited native regressions

- P02 `PlaybackArchitectureSmoke`: **34 PASS** with real FFmpeg available.
- `LegacyCaptionBridgeSmoke`: **4 PASS**.
- `MiniClientProtocolSmoke`: **PASS**, including mouse/text input.
- `MiniClientRecoverySmoke`: **11 PASS**.
- `MiniClientStartupSmoke`: **13 PASS**.
- `MiniClientAuthenticationSmoke`: **14 PASS**.
- Streaming servlet/API: **16 PASS**.
- MPEG-TS/settings/captions: **17 PASS**.

These prove the DVD additions did not change first-FLIPBUFFER UI ordering, type-5 recovery, RSA/Blowfish framing, ordinary mouse input, caption authority, or shared subtitle lifecycle.

## FFmpeg and long-running regressions

- Real FFmpeg software streaming/remux/caption path: **14 PASS**.
- P06 ordinary subtitle runtime: **PASS** (`tracks=2`, `pgsCues=4`, `dvdCues=3`).
- HLS runtime: **10 PASS**, including the 240-second synthetic recording continuing beyond the 180-second reserve target and ending cleanly.

No physical GPU or live SageTV media playback is claimed by these tests.

## Subtitle regressions

- P03 Teletext Java: **28 PASS**.
- P03 Teletext controller: **5 PASS**.
- P04 DVB Java: **32 PASS**.
- P04 DVB controller: **3 PASS**.

## Production Chromium regressions

- Remote/GFX: **15 PASS**.
- Layout/scheduling: **12 PASS**.
- Streaming settings/controller: **12 PASS**.
- Recovery: **10 PASS**.
- Teletext: **5 PASS**.
- DVB bitmap: **6 PASS**.
- Ordinary subtitles: **7 PASS**.
- Vibe overlay/display/local video: **22 PASS**.

Total Chromium checks: **89 PASS**.

## Execution note

The monolithic validator was also exercised with FFmpeg intentionally disabled to cover package/WAR checks and the native suites; that aggregate invocation reached the execution-window boundary after the inherited Teletext section. The remaining DVB/FFmpeg/HLS/browser groups were rerun separately and passed as listed above. A timeout is not counted as a pass or a failure.

## P08-only acceptance not claimed

P07 never treats queued PS bytes as visible DVD playback. There is intentionally no native DVD FFmpeg/browser consumer in this phase. Moving-title decode, physical audio selection, still-menu presentation, browser A/V timing and converter-startup failure handling with real DVD media are P08 gates.
