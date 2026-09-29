# P03 completion report — SageTV Web Player 3.2.6

**Date:** 2026-09-24  
**Phase:** P03 — DVB Teletext subtitle decoding and continuous presentation  
**Base:** `SageTV-WebPlayer-Local-Plugin-v3.2.5-P02.zip`  
**Base SHA-256:** `b2fbf786a094de4a4739f59052f43f2b04711d282a3f4b6def8a41e20598cfd0`  
**Reference:** `opensagetv-vibe/opensagetv-vibe-android-client` commit `f1ba340e05fe18eaaba939253a933c3dfe6d6caf`

## Result

P03 is implemented at the local/generated acceptance boundary. Tasks WP03-001 through WP03-007 are complete. WP03-008 is `IMPLEMENTED_AWAITING_FIELD`: this workspace contains deterministic Teletext transport fixtures but no unchanged Breakfast, Classic Holby City, Taskmaster, or other real UK broadcast recording. A generated-vector/browser pass is therefore not relabeled as a real-source field pass.

P04 DVB bitmap work is not started by this checkpoint.

## Implementation

`TeletextSubtitleSession` is a per-playback adaptation of the Vibe Level-1 engine. The Android reference uses process-global active-session state; the server plugin instead binds parser state, selected service, PTS origin and cue publication to the P02 `PlaybackSessionContext` token. This prevents one browser or a retired source/seek/FLUSH generation from publishing into another client.

The decoder discovers PAT/PMT program state, stream type `0x06` descriptor `0x56`, subtitle service types 2 and 5, PID, ISO-639 language, magazine and page. It accepts 188-, 192- and 204-byte transport framing, checks continuity/duplicates, reassembles bounded PES payloads and Teletext data units, decodes Hamming-protected Level-1 address/page/row data, honors erase-page changes and emits only the proven Latin-G0/spacing-attribute subset. It does **not** claim an interactive Teletext browser, Level 2.5/3.5 graphics, every national character set, or ordinary news/information pages.

`TeletextSourcePump` reads the original authorized media source independently of the video encoder. Teletext therefore does not rely on QSV/NVENC/VAAPI/software video output preserving private subtitle PES. No second video copy is delivered to the browser. The reader follows the active source/session lifetime and parser state is bounded.

Browser presentation uses `/api/subtitles` plus the video element's playback clock. It is independent of the SageTV GFX/OSD frame lifetime, so cues can advance and clear while the STV OSD is hidden. Source seek and native FLUSH advance the P02 generation and clear old page/cue state before a new position is accepted. Caption delay is applied at presentation rather than by rewriting source PTS.

Streaming-profile schema 3 adds explicit `teletext`, preferred page and preferred language while preserving the existing literal CEA-608/708 values. The browser exposes discovered Teletext page/PID/language services. Teletext remains a text-caption path; DVB bitmap/PGS/VobSub/file subtitles stay disabled for later phases.

## Diagnostics

P03 adds bounded Teletext counters and a staged preservation status:

- `no-descriptor`
- `descriptor-only`
- `pes`
- `data-units`
- `decoded-presentation`

Diagnostics include counts, service identity and source-reader state, but do not export decoded subtitle text or media payload.

## Validation boundary

Generated fixtures prove parsing, timing, selection, clears, source generations and multi-client isolation. Chromium tests use the production DOM/controller and actual local assets but mocked native/vendor transport. Existing FFmpeg/HLS/native/browser regression groups were rerun in split execution. See `VALIDATION.md` and `evidence/`.

Not proved here:

- unchanged real UK Teletext broadcast playback/visible timing;
- every national Teletext character set/control extension;
- real pinned mpegts.js/hls.js MSE acceptance (P01 WP01-004 remains external-pending);
- physical GPU/Windows/macOS/Safari behavior;
- DVB bitmap, PGS, VobSub, ordinary text subtitles or DVD SPU.

## Phase status

- WP03-001 — DONE
- WP03-002 — DONE
- WP03-003 — DONE
- WP03-004 — DONE for the documented Level-1 subset
- WP03-005 — DONE at generated/browser-clock boundary
- WP03-006 — DONE
- WP03-007 — DONE
- WP03-008 — IMPLEMENTED_AWAITING_FIELD

Next implementation phase: **P04 — DVB bitmap subtitles**.

## Final build identity

- WAR SHA-256: `0c8b4ff17facab3fdfb1885ce488fa539ed37e4100f6a720c7aa9b6923600cdf`
- Local installer SHA-256: `d4dd1737de4d458a4736e64f14e85637ce292650d85ea33ef13ab2bfba60578a`
- Installer MD5 recorded by plugin manifest: `cfa13fbc8c84505f9a749487871ada1c`
