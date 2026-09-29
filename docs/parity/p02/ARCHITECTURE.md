# P02 shared playback / subtitle architecture

Status: implemented and locally validated on 2026-09-24. This phase adds infrastructure only. Teletext, DVB bitmap, PGS, VobSub and ordinary text subtitle decoders remain disabled until P03/P04/P06.

## Ownership

`PlaybackSessionContext` gives every MiniClient media lifetime a stable browser-session ID plus independent source, seek and FLUSH generations. Async work carries a `Token`; stale tokens are rejected. `MiniClientMediaBridge` advances source generation on replacement/stop, seek generation on seek/recovery, and FLUSH generation on FLUSH. `HlsSessionManager.Session` carries the matching token into stream diagnostics and the cue side channel.

## Typed inventory

`MediaProbe.Track` now preserves ffprobe stream index, source PID, codec/type, language/title, default/forced/accessibility dispositions, and evidence/availability. `TsServiceInventoryProbe` performs a bounded 4 MiB PAT/PMT scan and records Teletext descriptor 0x56 service type/language/magazine/page and DVB descriptor 0x59 language/composition/ancillary page IDs. Multiple descriptor services are retained separately under `Info.services`. CEA-608 language is intentionally reported Unknown rather than inferred.

## Cue contract and clock

`SubtitleCue` contract v1 supports text and straight-alpha bitmap cues with playback token, owner, PTS/duration, clear/end, source canvas and rectangle. `SubtitleCueQueue` is bounded to 2,048 cues / 16 MiB and drops stale epochs or over-limit work. `/api/subtitles` exposes only due cues for the current stream token. `SubtitleClock` unwraps 33-bit 90 kHz PTS and maps source time separately from browser presentation time and delay. A paused browser clock therefore cannot drain future cues; GFX/OSD visibility is not part of scheduling.

## Source preservation

`SubtitleSourceBranch` is independent of the main FFmpeg video output. For MPEG-TS services with a known source PID it copies only that PID into a bounded server-side side channel; container subtitle tracks use an isolated FFmpeg stream-copy helper. The normal A/V command still strips subtitle/data streams, so video encoding does not need to preserve them. Tap failures are recorded in session diagnostics and do not terminate A/V. Direct-file source preservation is implemented in P02; pipe/concat inputs fail truthfully instead of advertising unsupported preservation and are follow-up integration work in decoder phases.

## Configuration migration

Streaming profiles are schema 2. Existing 3.2.4 profiles with no version migrate in place. Existing `off`, literal CEA-608 CC1-CC4 and CEA-708 Service 1-63 retain their meaning. Per-recording audio and subtitle stream indices reset to Automatic for the next recording. New subtitle defaults are internal/future-facing and no P03/P04/P06 decoder selector is exposed by this phase.

## Resource limits

Central limits cover bitmap dimensions/pixels, text/cue payloads, queue bytes/count, TS discovery bytes, subtitle-tap bytes, simultaneous taps, tap wall time and a cooperative 25 ms in-process decoder work slice. Unsupported decoders remain `inventory-only` and `advertised:false` in `SubtitleCapabilities`.
