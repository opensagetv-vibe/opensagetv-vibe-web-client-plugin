# P10 Validation — v3.2.13-P10

## Local deterministic evidence

`DiscSourceSmoke` covers:

1. Literal plus, `%20` and Unicode path decoding.
2. Duplicate basename rejection and unique parent+basename matching.
3. Stale mapped-root suffix recovery without arbitrary-path authorization.
4. VIDEO_TS / parent-mounted / ISO / VOB / MKV / BDMV taxonomy.
5. Explicit non-first title metadata parsing and title-relative chapter semantics.
6. A 1 ms duration on substantial media is reported as implausible server metadata; missing stream metadata is reported separately.
7. Runtime FFmpeg capability requires executable FFmpeg, `dvdvideo`, `libdvdread`, and `libdvdnav`.

The local environment reports all four movie-only prerequisites present with `/usr/bin/ffmpeg`.

## Inherited DVD regressions

P07 wire/session tests, P08 generated real-FFmpeg DVD A/V tests, and P09 SPU/highlight tests are rerun for this checkpoint. P10 does not alter commands 32-37, DVD PUSH ownership, drain semantics, MPEG-PS conversion or SPU presentation.

## What local validation does not prove

- A real multi-title commercial/authored DVD whose intended main feature is not title 1.
- Stock SageTV ISO mount behavior on the user's actual server/container filesystem.
- Physical optical-drive permissions/media.
- Blu-ray/BDMV/BD-J or encrypted/DRM playback; these are explicitly not claimed.

These remain field/environment acceptance, not synthetic PASS claims.

## Completed regression results for the packaged checkpoint

- P02 playback architecture: **34 PASS**.
- P10 DiscSourceSmoke: **7/7 PASS**.
- P07 native DVD protocol smoke: **PASS**.
- P07 MiniClient real-socket DVD wire smoke: **PASS**.
- P08 real FFmpeg DVD runtime: **5/5 PASS**.
- P09 DVD SPU core smoke: **2/2 PASS**.
- MiniClient core: **27 PASS**.
- MiniClient playback: **33 PASS**.
- MiniClient display: **14 PASS**.
- Native recovery: **11 PASS**.
- Native startup ordering: **13 PASS**.
- Native authentication: **14 PASS**.
- Streaming servlet: **16 PASS**.
- Real FFmpeg streaming runtime: **14 PASS**.
- MPEG-TS/settings/captions: **17 PASS**.
- HLS runtime: **10 PASS**, including the 240-second generated recording.
- Teletext: **28 Java + 5 browser-controller PASS**.
- DVB bitmap: **32 Java + 3 browser-controller PASS**.
- Ordinary subtitle real-FFmpeg smoke: **PASS**.

The monolithic validator exceeded the execution window during the long HLS group. That timeout is not counted as a result; the HLS and remaining streaming/subtitle groups above were rerun separately to completion.
