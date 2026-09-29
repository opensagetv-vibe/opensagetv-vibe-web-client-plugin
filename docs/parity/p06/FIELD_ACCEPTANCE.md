# P06 field acceptance checklist

Use v3.2.9-P06 on a real SageTV server/media library. Capture the diagnostics export immediately after any failure.

1. Text: play an embedded or exact-basename SRT/WebVTT recording. Verify Off, Auto, explicit track, language, positive/negative delay, pause/resume, forward/back seek, file end and rapid recording switch. Confirm no stale cue remains after seek/stop.
2. Coexistence: intentionally enable an ordinary file subtitle and a broadcast CC mode. Confirm both are independently controllable and changing CC does not silently change ordinary subtitle selection.
3. PGS: use a real PGS-bearing MKV. Verify palette/alpha, authored placement, clear/end and seek behavior. This is file-subtitle validation only and is not Blu-ray menu/BD-J validation.
4. VobSub palette-present: use a real `.idx/.sub` pair with known language/palette and, if available, forced entries. Verify the selected language/substream, visible colors/alpha and forced-only filtering.
5. VobSub palette-missing: remove or rename the IDX and confirm the `.sub` is not offered as a working track. Do not accept guessed colors from a concatenated VOB.
6. Lifecycle: repeat text/PGS/VobSub tests during video copy and transcode modes where applicable, then reconnect the WebPlayer. Confirm no orphan FFmpeg subtitle process and no ghost subtitle surface.

Do not close WP06-004 from generated embedded `dvd_subtitle` alone; retain the genuine external IDX/SUB field evidence.
