# Verified v3.2.4 integration gaps

These are observations of the unchanged files in this package, not new fixes.
The runtime files are byte-identical to the input Parity Plan r1 archive. See
`PACKAGE_VERIFICATION.json` and `evidence/baseline-result.json` for identity/tests.

| Baseline file / location | Observed behavior | Consequence / owner |
|---|---|---|
| `src/main/java/org/opensagetv/webplayer/StreamOptions.java`, constructor | Captions allow only off,608,708. | Teletext/DVB/STV/file controls are not implemented; WP02-007, P03–P06. |
| `MediaProbe.java` lines 9–19,28–46 in the same Java folder | Basic stream index/type/codec/language/title; no PID/page, role/forced or descriptor identity. | Typed inventory required before stable service selection; WP02-002. |
| `MiniClientSession.java`, property handler | GFX_SUBTITLES is FALSE; SUBTITLES_CALLBACKS=TRUE is rejected; VIDEO_CC_STATE is only forwarded as captionState. | Legacy event-225/STV renderer ownership is absent. Forwarding a property is not end-to-end selection; P05. |
| `MiniClientMediaBridge.java` lines 68–71 | PUSHBUFFER replies with capacity without consuming media. | A real native DVD Push backend is required; WP07-004. |
| `MiniClientMediaBridge.java` lines 102–103 | Commands32–37 return -1. | No native DVD metadata/CLUT/SPU/STC/stream/format implementation; P07–P09. |
| `MiniClientMediaBridge.java` lines 114–156 | OPENURL resolves MediaFile, falls back to first matching basename, and restarts a stream on seek. | Duplicate-basename safety, source identity and exact source-specific seek behavior need later work; P10/P11. |
| `src/main/webapp/js/miniclient-captions.js` | Existing 608/708 parser side channel, bounded history and player-clock update; no bitmap/text-file service renderer. | Reuse the proven clock/session protections but add a typed cue contract and source-preserving path; P02. |
| `src/main/java/org/opensagetv/webplayer/StreamCommand.java` | Video/audio FFmpeg path strips ordinary subtitles/data; original-video A/53 side channel exists. | Teletext/DVB/PGS source preservation is not solved by enabling a video encoder option; WP02-004. |
| `MiniClientMediaBridge.java` lines 90–101 | Frame step rejects; bounded positive media rates forwarded, no portable reverse implementation. | Capability-specific behavior needs P12/P13, not Android API copies. |

## What the successful baseline proves

The actual untouched source build and validate.sh passed: 235 counted scenarios,
including 71 Chromium scenarios, plus protocol/legacy/syntax/asset/package checks.
Real Java loopback sockets, RSA/Blowfish, file followers and FFmpeg software paths
were used where the suite names state that. Vendor MSE objects and SageTV peers
were simulated. The baseline does not prove live stock SageTV, physical GPU,
Safari, the user's media, or a feature that this source explicitly lacks.

## No inappropriate capability workaround

Do not change INPUT_DEVICES, PUSH_AV_CONTAINERS, GFX_SUBTITLES, DVD capabilities
or unified YUV flags merely to make a menu available. Keep the associated
reply/decoder/lifecycle contract and its acceptance test together. No such
runtime flag was changed by this P01 package.
