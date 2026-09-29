# P02 validation

Local executable evidence:

- `PlaybackArchitectureSmoke`: session/seek/FLUSH isolation, two-client separation, cue timing/bounds, PTS wrap, typed ffprobe metadata, PAT/PMT Teletext and DVB identities, schema migration, PID-only source tap, real FFmpeg container subtitle stream-copy, and truthful capability limits.
- `StreamingServletSmoke`: current-session `/api/subtitles` due-cue behavior plus existing continuous-stream/caption security checks.
- `test-mpegts-streaming.js`: schema-1 migration to schema 2 and preservation of literal CEA choices alongside existing buffering/CEA tests.
- Existing native startup, auth, recovery, FFmpeg/HLS, layout and browser suites remain regression gates.

This phase does not claim visible Teletext/DVB/PGS/VobSub playback. Real vendor mpegts.js/hls.js MSE remains the separate P01/P16 external acceptance row.

## Executed results

- PlaybackArchitectureSmoke: **33 PASS**.
- StreamingServletSmoke: **16 PASS**.
- StreamingRuntimeSmoke: **14 PASS**.
- HlsRuntimeSmoke: **10 PASS**.
- Node MPEG-TS/settings/captions: **17 PASS**.
- Native recovery/startup/authentication: **11 / 13 / 14 PASS**.
- Chromium baseline: **71 PASS** across 15+12+12+10+22 scenarios.

Combined validation commands exceeded the execution timeout during long FFmpeg/browser groups; the interrupted groups were rerun independently and passed. `evidence/summary.json` records this split execution. No monolithic exit-0 is claimed.
