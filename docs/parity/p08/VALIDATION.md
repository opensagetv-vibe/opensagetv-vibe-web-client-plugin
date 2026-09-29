# P08 validation

Runtime: **3.2.12-P08**  
Date: **2026-09-25**

## New P08 real-FFmpeg runtime gate

`DvdPlaybackRuntimeSmoke` uses the installed FFmpeg executable, not a mocked decoder.

Result: **5/5 PASS**.

Coverage:

1. Generates a moving DVD MPEG-PS with MPEG-2 video plus two authored AC-3 tracks (440 Hz and 880 Hz), requests physical selector `0xBD81`, converts through the actual P08 HLS path and verifies the decoded output is the second authored track.
2. Generates a one-picture DVD menu cell and verifies it creates playable HLS, is identified as a still candidate and reaches native drain without waiting for a long reserve.
3. Generates an audio-only MPEG-PS cell and verifies it starts and drains without requiring a video track.
4. Generates a PAL 25 fps MPEG-2 PS and verifies the converted playlist remains approximately 25 fps.
5. Verifies 45 kHz STC mapping, SPU-only PTS isolation and logical-clock preservation across a post-data FLUSH epoch.

## Production browser gate

`browser-dvd-smoke.py` loads the production MiniClient DOM/JavaScript/CSS in Chromium.

Result: **4/4 PASS**.

- A short DVD HLS cell starts from one segment and promotes to the bounded DVD reserve.
- The current authored video frame is captured into the dedicated still canvas at a DVD generation boundary.
- Replacement video releases the still canvas only after new video data is available.
- FLUSH does not blank the previous menu frame while the replacement cell is pending.

`test-miniclient-playback.js`: **33 PASS**, including the two P08 DVD buffering tests.

## P07 wire regression

- `DvdNativeProtocolSmoke`: **PASS**.
- `MiniClientDvdWireSmoke`: **PASS** with commands 0/1/17/22/23/29/32-37 and exact reply alignment.

## Limits of this environment

No unchanged live SageTV DVD session and no physical hardware encoder are available in this execution environment. The software FFmpeg path is real; vendor hardware paths are not promoted from configuration/probe logic to a physical PASS. Full authored SPU/highlight/navigation acceptance belongs to P09.

## Inherited regression completion

- P02 architecture: **34 PASS**.
- Native recovery: **11 PASS**.
- Native startup ordering: **13 PASS**.
- Native RSA/Blowfish authentication: **14 PASS**.
- Streaming servlet: **16 PASS**.
- Real FFmpeg software streaming: **14 PASS**.
- HLS runtime: **10 PASS**, including the **240-second** growing-recording reserve test.
- MPEG-TS/settings/captions: **17 PASS**.
- Teletext: **28 Java + 5 controller PASS**.
- DVB bitmap: **32 Java + 3 controller PASS**.
- Ordinary subtitle runtime remains PASS.
- Production Chromium: **93 PASS** total (the prior 89 checks plus 4 new P08 DVD checks).

The monolithic validator reached the execution-window boundary after completing the new P08/native and several inherited groups. Remaining long groups were rerun individually to completion; a timeout is not counted as a pass or failure.
