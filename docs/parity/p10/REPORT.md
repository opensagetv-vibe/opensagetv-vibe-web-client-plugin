# P10 Report — Disc sources, title selection and library metadata

Checkpoint target: **v3.2.13-P10**.

P10 keeps the P07-P09 native DVD VM, MPEG-PS A/V and SPU/menu presentation paths intact while making disc source resolution and metadata deterministic. The browser/plugin may inspect only a SageTV-authorized `MediaFile`; `/api/disc` accepts a MediaFile ID and never an arbitrary filesystem path.

## Implemented

- Classifies authorized sources as VIDEO_TS, parent/mounted VIDEO_TS, ISO, physical DVD drive, individual VOB, imported MKV, BDMV/Blu-ray or ordinary media.
- VOB/MKV are explicitly ordinary/non-navigable media and never mislabeled as interactive DVD.
- Safe MiniClient path decoding preserves literal `+`, percent-encoded spaces and UTF-8/Unicode.
- Exact SageTV `GetMediaFileForFilePath` resolution wins. Stale-root recovery requires a unique parent+basename (or longer) suffix; basename-only fallback is forbidden.
- Windows/UNC-style paths compare case-insensitively; POSIX paths remain case-sensitive.
- ISO is reported as a **stock-server mount prerequisite**. The plugin does not call `sudo`, mount filesystems or change container/JVM privileges.
- Read-only metadata health reports impossible/implausible durations and missing stream metadata without changing SageTV history, recordings or library data.
- Explicit DVD title probing uses FFmpeg `dvdvideo` only after runtime proof of the demuxer plus `libdvdread` and `libdvdnav`.
- Title/chapter metadata is explicitly title-relative and is not conflated with SageTV library/recording duration.
- Movie-only compatibility is an explicitly selected **title** plan. It never auto-selects title 1, largest VOB or a guessed main feature, and never claims interactive-menu parity.
- Blu-ray/BDMV, BD-J, encrypted-disc and DRM playback remain separate unsupported/pending scope.

## API

`GET /api/disc?id=<MediaFileID>` returns source taxonomy, FFmpeg DVD capability, metadata health, timeline semantics and scope. Optional `&title=N` performs a bounded explicit-title `dvdvideo` probe and returns chapter/stream metadata plus the movie-only compatibility plan.

The title parameter is an integer only; there is no path parameter.

## Timeline contract

- `libraryPlaybackDurationMs` / `libraryFileDurationMs`: SageTV MediaFile/library view.
- `titleProbe.durationSeconds`: selected DVD title duration from the `dvdvideo`/IFO navigation view.
- `chapters[].startSeconds/endSeconds`: relative to the selected DVD title.
- No title is identified as “main” automatically.

## Phase boundary

P10 does not replace native-menu playback with title conversion. P07-P09 remain the authoritative interactive DVD path. Movie-only is a separately labeled compatibility plan and remains unavailable when required FFmpeg DVD libraries are absent or the requested title cannot be probed.
