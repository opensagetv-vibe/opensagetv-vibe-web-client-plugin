# Current validation

The current 3.2.30 local and `.232` commissioning record is
[`VALIDATION-3.2.30.md`](VALIDATION-3.2.30.md). Historical validation below is
retained as evidence for the imported implementation and is not rewritten as
current physical proof.

---

# P04 / 3.2.7 validation addendum

P04 DVB bitmap-specific and retained regression results are in `parity/p04/VALIDATION.md` with raw logs under `parity/p04/evidence/`. The generated Java parser/session suite passes 32 checks, the Node bitmap controller passes 3 checks, and the production-DOM Chromium P04 smoke passes 6 checks. WP04-007 real UK DVB-bitmap visible/timing field acceptance remains pending; generated RGBA vectors are not reported as a field PASS.

---

# P03 / 3.2.6 validation addendum

P03 Teletext-specific and retained regression results are in `parity/p03/VALIDATION.md` with raw split-run logs under `parity/p03/evidence/`. The generated Teletext parser/session suite passes 28 checks, production browser Teletext adds 5 Chromium checks, and total Chromium regression coverage is now 76 checks. WP03-008 real UK broadcast field acceptance remains pending. No real vendor-MSE or physical-GPU claim is added.

---

# P02 / 3.2.5 validation addendum

P02 adds 33 Java architecture checks, 16 servlet checks (including `/api/subtitles`), real FFmpeg source-preservation coverage, schema-2 JS migration coverage, and reruns of existing HLS/native/browser groups. Split logs are under `docs/parity/p02/evidence/`. Real vendor MSE remains external-pending.

# Validation — 3.2.4

All required groups passed across split executions. The long command timed
out during HlsRuntimeSmoke after nine PASS cases. That complete suite then
passed separately with exit0. The combined browser command also timed out
during the last suite; the complete new suite passed on its separate run.
No monolithic exit0 is claimed. Saved transcript text is not edited to turn
incomplete runs into successes.

| Group | Count | Current evidence filename (VALIDATION-v3.2.4- prefix) |
| --- | ---: | --- |
| Core/gestures | 27 | validation.txt |
| Display policy, new | 14 | validation.txt |
| HLS lifecycle | 31 | validation.txt |
| Native recovery | 11 | validation.txt |
| Native startup safety | 13 | validation.txt |
| Native authentication | 14 | validation.txt |
| Growing file / FFmpeg HLS | 10 | hls-runtime.txt |
| Streaming Servlet API | 13 | stream-servlet.txt |
| Streaming / caption runtime | 14 | stream-runtime.txt |
| TS adapter / settings / CEA | 17 | ts-adapter.txt |
| Chromium remote/GFX | 15 | browser-legacy.txt |
| Chromium layout/scheduling | 12 | browser-layout.txt |
| Chromium streaming | 12 | browser-streaming.txt |
| Chromium recovery | 10 | browser-recovery.txt |
| Chromium Vibe overlay/display, new | 22 | vibe-display.txt |
| **Total counted** | **235** | **71 browser cases total** |

Additional checks:30 asset inventory entries, native protocol smoke, legacy
Node utilities, WAR target52, source/static asset byte equality, local-package
WAR/MD5, original3.2.3 black-clear shrink reproduction. PNG original Git blobs
and output icon SHA-256 verified; SVG geometry/colors match recorded sources.

## New display coverage

The original3.2.3 WAR under a1600x1000 synthetic browser shrinks video from
1599.4 CSS pixels wide to1391.5 when an inset black CLEAR_RECT is received.
The corrected production renderer keeps it at1600 through repeated OSD
show/hide, retains translucent OSD and clears recognized full-playback mattes.
Explicit small previews, exact native override and fixed-UI/full-video modes
were exercised. Full-window input mapping and stage resizing remain aligned.

The30 actual icon bytes decode, all edge groups are present, and bounds tests
cover1920x1080,390x844,700x390,320x568 and2141x1503 without icon overlap.
Tests click aspect/audio/video/caption/info/stats/help/keyboard/export controls,
check nested Escape, profile migration and absence of playback commands from
simply opening/closing the remote. The two-second check preserves pause intent.
The inherited gesture suite tests pointer holds/repeats and click suppression.

The local H.264 fixture truly decoded in Chromium (video dimensions/readiness
and visible test pattern), with screenshot captures for Fill, Fit and Stretch.
Screenshots use generated test content, not the user's copyrighted recording.

## Limits

Network navigation is administratively disabled in test Chromium. Test code
injects the shipped HTML/CSS/scripts and actual icon bytes via data URLs; media
is a generated MP4 blob. Native SageTV events and vendor player are simulated.
This proves local renderer/DOM/decode behavior, NOT actual HTTP/vendor MSE,
Windows/macOS, live SageTV/SageMC, physical GPU or Safari playback. Real OS
fullscreen/download behavior remains unverified (logical behavior is tested).
The geometry heuristic for near-fullscreen versus preview may need field
adjustment for unusual STVs; exact Native placement is available.

Java source differs from3.2.3 only in PluginVersion.java. The13 native startup
event-gating cases pass unchanged. No server, auth, FFmpeg or buffer algorithm
change is part of the UI patch. The user screenshots suggested the mask bug;
the synthetic reproduction confirms code behavior, not an exact live trace.
