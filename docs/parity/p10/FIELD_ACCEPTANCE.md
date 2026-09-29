# P10 Field acceptance checklist

Use an unchanged SageTV library/database backup before any metadata repair experiment. Normal P10 inspection is read-only and requires no library changes.

- Verify a VIDEO_TS MediaFile and a parent directory containing VIDEO_TS are classified as native DVD.
- Verify an ISO that previously failed mounting reports the stock-server ISO mount stage rather than a generic decoder failure.
- Verify two different SageTV files with the same basename cannot be cross-selected after a mapped-root/path change.
- Verify spaces, literal `+`, Unicode and the server's actual Windows/UNC or POSIX path style resolve to the intended MediaFile.
- On a multi-title DVD with extras, request the known intended title explicitly and compare reported duration/chapters with the authored disc. Do not infer “main feature” from title number or VOB size.
- Verify movie-only capability is labeled non-interactive and disappears/fails clearly if a test FFmpeg without `dvdvideo`/libdvdread/libdvdnav is configured.
- If SageTV reports an impossible imported duration (for example 1 ms), confirm the plugin reports it as metadata health and does not seek, rewrite the library, clear history or reindex automatically.
- Verify VOB/MKV are shown as ordinary media, and BDMV/Blu-ray is shown as unsupported/pending disc scope.
