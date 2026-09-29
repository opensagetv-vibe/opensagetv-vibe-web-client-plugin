# P02 completion report — SageTV Web Player 3.2.5

Date: 2026-09-24

P02 is complete at its local shared-architecture exit gate. All eight WP02 tasks are DONE. This checkpoint intentionally does not advertise Teletext, DVB bitmap, PGS, VobSub or general text-subtitle decoders; those remain P03/P04/P06.

## Implemented

- Per-browser `PlaybackSessionContext` with independent source, seek and FLUSH generations. Stale async work and cue writes are rejected without affecting another browser session.
- Typed media/service inventory preserving source PID, codec, language, disposition, accessibility, Teletext service/page identity and DVB composition/ancillary page IDs. CEA-608 language remains Unknown when not evidenced.
- Versioned subtitle cue contract for text and straight-alpha bitmap rectangles, with explicit owner, session epoch, PTS, clear/end semantics and bounded payload/dimensions.
- Bounded, timestamp-ordered cue queue and 33-bit MPEG PTS clock mapping independent of SageTV OSD visibility.
- Original-source subtitle preservation before normal output `-sn/-dn`/video encoding. TS PID taps copy only selected packets; container subtitles use an isolated FFmpeg stream-copy side channel. Failure is diagnostic-only and does not stop A/V.
- `/api/subtitles` due-cue endpoint scoped to the current streaming session/epoch.
- Streaming profile schema 2 migration preserving existing bitrate/audio/display settings and literal CEA-608 CC1-CC4 / CEA-708 service selections. Per-recording audio/subtitle indices reset for the next recording.
- Truthful support/resource contract (`SubtitleCapabilities`, `SubtitleLimits`, `SubtitleWorkBudget`) so absent decoders are not exposed as working features.
- Decoder placement ADR: per-session Java/Jetty decoding; Vibe Teletext core adapted in P03; pure-Java raw-RGBA DVB adapter in P04; bounded PGS/VobSub adapters in P06; no Android runtime/JNI/Python service added.

## Executed validation

- `PlaybackArchitectureSmoke`: 33 PASS.
- `StreamingServletSmoke`: 16 PASS.
- `StreamingRuntimeSmoke`: 14 PASS, including real FFmpeg software transcode/remux and original-caption side-channel regression.
- `HlsRuntimeSmoke`: 10 PASS, including a 240-second producer that continues beyond the 180-second reserve target.
- `test-mpegts-streaming.js`: 17 PASS; the mpegts.js adapter remains mocked, while settings/CEA parser and real FFmpeg sidecar coverage are exercised.
- Native recovery/startup/authentication regressions: 11 / 13 / 14 PASS.
- Chromium baseline: 71 PASS across 15 remote/GFX, 12 layout, 12 streaming/settings, 10 recovery and 22 Vibe/display scenarios.

Long combined validation commands exceeded the execution timeout during FFmpeg/browser groups. Those interrupted groups were rerun separately and passed; no monolithic exit-0 is claimed. Evidence is under `docs/parity/p02/evidence/`.

## Explicitly not claimed

- No visible Teletext decoding yet (P03).
- No DVB bitmap decoding/rendering yet (P04).
- No PGS/VobSub/general text-subtitle feature UI yet (P06).
- No real pinned mpegts.js/hls.js MSE acceptance; P01 WP01-004 remains `EXTERNAL_ACCEPTANCE_PENDING` and P16 retains the final browser-delivery gate.
- No live SageTV server or physical GPU validation was performed for the P02 changes.
- No stock `Sage.jar`, STV, FFmpeg/MIM installation, server database, recording or user profile is modified by this package.

## Artifact identity

- Runtime version: 3.2.5
- WAR SHA-256: `d7cac13b6797163d8d3bbccee4c3b2f964bd7cac2fe1238ddd7fac77fecdb5d1`
- Local installer ZIP SHA-256: `90d4a97e8cbf1715492935960d43da4cf62fb2980818b84fa75559bae1548509`
- The WAR inside the local installer is byte-identical to `dist/SageTVWebPlayer.war`.

Next task: **WP03-001 — Teletext per-session ownership/adaptation**.
