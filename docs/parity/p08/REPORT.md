# P08 DVD MPEG-PS A/V conversion report

Runtime checkpoint: **3.2.12-P08** (2026-09-25).

## Scope

P08 consumes the generation-aware native DVD MPEG-PS input created in P07 and turns each stock SageTV DVD decoder epoch into browser-compatible H.264/AAC HLS while preserving the stock MiniDVDPlayer wire session. DVD SPU bitmap decoding, authored highlights and navigation rendering remain P09.

## Implemented

- `DvdPsInspector` incrementally observes MPEG-PS/PES boundaries across arbitrary PUSHBUFFER splits, including private stream 1, MPEG audio, video, SPU, picture starts, sequence ends and A/V PTS.
- Each post-FLUSH DVD generation is a separate FFmpeg decode epoch. Unrelated cells/titles are never concatenated into one decoder state.
- `DvdAudioStreamCode` maps SageTV's physical DVD selector to MPEG audio or private AC-3/DTS/LPCM substream identity. FFmpeg is mapped by physical stream id rather than UI ordinal; unsupported selectors fail instead of silently selecting another language.
- `HlsSessionManager.startDvd` feeds the P07 ring directly to FFmpeg's MPEG-PS demuxer and produces short-segment H.264/AAC HLS using the existing encoder policy.
- DVD HLS tracks decode/filter/encode acceleration separately through the existing transcoder diagnostics. The local environment has no physical GPU gate, so only the available software path is claimed here.
- The logical DVD clock remains server-owned. Browser cell-relative time updates `DvdPtsClock`; NEWCELL/STC offsets, 33-bit wrapping and post-FLUSH discontinuities preserve the logical title timeline. SPU-only PES PTS never consumes an A/V rebase.
- Video cadence is not blindly forced to 30 fps. The converter uses one-output-frame-per-input-frame deinterlacing and preserves authored PAL/NTSC cadence.
- One-picture menu cells and tiny audio-only cells can start from one published segment and drain normally. The browser captures the final authored menu frame to a dedicated still canvas across FLUSH until replacement video becomes visible.
- DVD buffering is separate from long-recording reserve behavior: one segment may start playback, startup buffering is bounded to 6 seconds and steady DVD reserve to 12 seconds.
- Converter exit plus browser consumption is required before P07's native `0x100` poll returns `-2`; FFmpeg EOF by itself cannot prematurely advance the DVD VM.
- Stop/DEINIT/FLUSH retire the active FFmpeg/HLS generation; stale readers remain generation-cancelled by P07.

## Row status

| Row | Local status | Evidence |
| --- | --- | --- |
| WP08-001 | PASS_LOCAL | Generated moving-title, one-picture and audio-only MPEG-PS feed real FFmpeg and produce playable HLS. |
| WP08-002 | PASS_LOCAL | A two-AC3 authored PS selects physical `0xBD81`; decoded output frequency verifies the second authored stream. |
| WP08-003 | PASS_LOCAL | Available software encoder produces H.264/AAC HLS through the normal policy. No physical GPU is present, so hardware vendors remain untested rather than failed. |
| WP08-004 | PASS_LOCAL | STC/NEWCELL/discontinuity clock tests preserve logical time and ignore SPU-only PTS for A/V rebasing. |
| WP08-005 | PASS_LOCAL | Generated PAL 25 fps survives conversion near 25 fps; no Android-specific Media3 timestamp patch was copied into the FFmpeg path. |
| WP08-006 | PASS_LOCAL | One-picture menu, audio-only cell and production-DOM still-frame handoff across FLUSH pass. |
| WP08-007 | PASS_LOCAL | Node/Chromium tests prove one-segment startup and bounded 12-second DVD reserve without changing ordinary HLS reserve policy. |
| WP08-008 | PASS_LOCAL | Generation-scoped converter lifecycle, FLUSH cancellation, browser progress/drain and stop cleanup are covered by local runtime tests. |

## Deliberate boundary

The stock native-DVD role remains opt-in for this checkpoint. P08 now provides visible A/V, but full DVD user experience still lacks P09's SPU subtitles, authored highlight compositor and navigation interaction. Do not call full DVD parity complete until P09 passes on authored and representative real discs.
