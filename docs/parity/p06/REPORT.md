# P06 report — ordinary text, PGS and VobSub/file subtitles

Runtime checkpoint: **3.2.9-P06** (2026-09-25).

P06 adds ordinary file subtitles without changing the broadcast-caption authority model from P05. Ordinary subtitle cues use `SubtitleCue.Owner.LOCAL_FILE` and a dedicated `PlaybackSessionContext.fileCues()` queue. The queue shares the same source/seek/flush generation token as broadcast Teletext/DVB/CEA so stale callbacks die together, but polling one family cannot consume the other.

## Discovery and security

`MediaProbe` now exposes supported ordinary embedded subtitle tracks and only exact-basename associated sidecars in the active authorized MediaFile directory: `.srt`, `.vtt`, `.ass`, `.ssa`, `.sup`, and `.idx` with its matching `.sub`. Browser requests never supply a subtitle filesystem path. A lone `.sub` is not advertised because DVD/VobSub palette/context cannot be inferred safely. Language/title/default/forced metadata is retained when the source container/IDX provides it.

## Text path

SRT/WebVTT are emitted as text cues; associated `.vtt` and `.srt` are parsed directly so authored timing/basic WebVTT positioning is preserved. Embedded text and ASS/SSA go through FFmpeg's WebVTT adapter. ASS/SSA typography/animation/advanced styling is intentionally flattened. Browser presentation uses DOM `textContent`, never executable subtitle HTML. Unicode, overlap, independent expiration, final clear and hostile-markup vectors are covered.

## Bitmap path

PGS and DVD/VobSub bitmaps are not burned into video. FFmpeg is used only as a bounded bitmap codec adapter to DVB-subtitle transport; the existing P04 `DvbBitmapDecoder` then produces the shared straight-alpha RGBA cue contract with owner `LOCAL_FILE`. This keeps composition/palette/object/clear behavior in the already-tested bitmap decoder and reuses the same browser canvas machinery. Generated tests include a PGS-bearing MKV and an embedded `dvd_subtitle` MKV.

Standalone VobSub `.idx/.sub` support is implemented only when the associated IDX provides the needed mapping/palette context. A genuine external palette-bearing IDX/SUB pair was not available in this execution environment, so WP06-004 remains field acceptance rather than a synthetic PASS.

## Selection and lifecycle

Streaming profile schema is now 6. Ordinary subtitles have independent Off / Auto / Selected-track mode, language, delay and forced-only settings. The concrete selected track number is session-only; saved preferences retain safe Auto/Off defaults instead of reusing a recording-specific index. Broadcast captions and ordinary subtitles may intentionally coexist because they have distinct policy, queues and presentation surfaces.

Direct local authorized media supports the P06 extraction worker. Pipe/concat sources currently return an explicit unsupported ordinary-subtitle extraction state rather than pretending to work; this does not affect their existing video/audio/caption paths.
