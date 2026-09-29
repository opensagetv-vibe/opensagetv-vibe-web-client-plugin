# P06 validation

Local/generated validation completed on 2026-09-25.

## Deterministic Java / FFmpeg evidence

`OrdinarySubtitleSmoke` validates exact-basename sidecar association, rejection of unrelated same-prefix files, WebVTT timing/positioning, Unicode and inert hostile markup, sidecar PGS to `LOCAL_FILE` RGBA, an embedded PGS-bearing MKV, and a generated embedded `dvd_subtitle` MKV through the same bitmap adapter.

The existing architecture/regression suite remains clean at 34/34. P03 Teletext remains 28/28; P04 DVB remains 32/32. Real software FFmpeg streaming/transcode regression remains 14/14 and the 240-second growing-recording HLS test passes.

## Browser evidence

`tests/test-ordinary-subtitles.js`: 4/4 controller vectors.

`tests/browser-ordinary-subtitles-smoke.py`: 7/7 production DOM/controller checks:

- hostile markup is inert `textContent` and Unicode survives,
- basic WebVTT line/position/alignment reaches the DOM,
- straight-alpha RGBA bitmap pixels reach the file-subtitle canvas,
- ordinary and broadcast surfaces coexist independently,
- text and bitmap overlays use the displayed video rectangle,
- bitmap duration expiry clears correctly,
- overlapping text cues expire independently.

Existing browser regression groups are rerun before final packaging; P06 adds no claim of a live SageTV or external VobSub field run from this environment.

## Remaining field boundary

WP06-004 requires a genuine standalone palette-bearing `.idx/.sub` sample to verify palette-present, palette-missing and forced-only visible behavior. A lone `.sub` is deliberately rejected. Pipe/concat ordinary-subtitle extraction is also explicit unsupported in this checkpoint; no false capability is advertised.
