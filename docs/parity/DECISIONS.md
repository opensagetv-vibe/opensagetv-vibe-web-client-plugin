# Parity architecture decisions and unresolved boundaries

Revision 1 — 2026-09-24. These are planning decisions, not implemented behavior.

| ID | Decision/boundary | Current disposition | Owner task |
|---|---|---|---|
| ADR-001 | Authoritative upstream | Freeze the requested Vibe source/dev commit; current task policy supersedes obsolete changelog behavior. Never copy source/existing as current. | WP01-001 / WP01-002 |
| ADR-002 | Stock server | Stock Sage.jar is the default and cannot be replaced by this port. Optional provider/Core work is isolated in P15. | WP01-006 / WP15-001 |
| ADR-003 | Decoder placement | Prefer reusable per-session Vibe Java for Teletext/SPU; choose and prove a licensed DVB/PGS adapter before implementing its controls. Java versus native helper is still an explicit design gate. | WP02-005 |
| ADR-004 | Per-client ownership | No Android singleton decoder in a multi-client servlet JVM. Bound all reader, caption, cache and temporary-file ownership. | WP02-001 / WP03-001 |
| ADR-005 | Captions versus subtitles | CC1/CC2 are virtual text services; DVB is a top-level bitmap mode; ordinary file/DVD subtitles are separate. Preserve legacy literal CEA selections on migration. | WP02-007 / WP05-001 |
| ADR-006 | Transcode preservation | Original subtitle extraction precedes packet stripping/video encoding. Do not require GPU encoders to preserve private streams. | WP02-004 |
| ADR-007 | Native DVD browser backend | Keep the stock DVD VM; consume actual PS Push and translate A/V for browser media. Menu/SPU state remains separate and clock-aligned. Do not satisfy full DVD with biggest-VOB playback. | WP07-004 / WP08-001 / WP09-007 |
| ADR-008 | Native role/capability changes | INPUT_DEVICES/TV/MOUSE and DVD capabilities affect Core behavior. Prove the exact stock path and pointer adaptation; do not enable role flags as a placeholder. | WP07-001 |
| ADR-009 | ISO and privileges | Existing mount failures are environment/source failures. No automatic sudo installation, privileged container changes or writable mounts. Plugin-owned ISO reading needs its own approved route. | WP10-003 |
| ADR-010 | Movie-only DVD mode | Explicitly separate title conversion from authored menu interaction and optional Core transformation. Probe actual dvdvideo/libdvdnav/libdvdread capability. | WP10-005 / P15 |
| ADR-011 | Audio/hardware claims | Browser output control is not Android AudioTrack/MediaCodec/HDMI authority. Distinguish CPU decode/filter, hardware encode and actual browser decode. | WP12-002 / WP12-004 |
| ADR-012 | Unified surfaces | Off by default until format 256/image lifetime/fallback and stock compatibility are proved; do not claim HD300 equivalence from one property. | WP13-003 |
| ADR-013 | Upstream unresolved work | DVD-001, SEEK-001, ONN-003, AUDIO-001/005/006 and affected-tablet evidence retain open portions. Port completed subfixes, not unearned pass labels. | WP01-002 / WP16-007 |
| ADR-014 | Android-only/future features | APK signing/store/API36/ADB implementation details do not directly port. Blu-ray/DRM and future firmware audits are separately tracked, not claimed as already fixed. | WP01-001 / WP10-007 |
| ADR-015 | User data and automation | No arbitrary path opening, automatic library repair, global reindex, watch-history reset, remote publication or share mutation as part of normal playback. | WP10-002 / WP10-006 / WP14-006 / WP17-006 |
| ADR-016 | Experimental GFX threading | Preserve cooperative GFX scheduling. True worker rendering is a separate measured architecture change, not an automatic consequence of async or mpegts.js flags. | WP13-007 |

## Open questions to answer through code/fixture inspection first

1. Which DVB/PGS adapter provides the required clear/timeout/alpha events with minimal deployment change and an acceptable license? Do not decide from the fact that ffmpeg prints a decoder name alone.
2. What exact stock Core role identifies the web session as a native-DVD-capable extender without losing required browser pointer behavior? Record Core version/hash and the full handshake.
3. How does the chosen TS/HLS DVD conversion path expose still-frame/drain boundaries without starving the VM or prematurely retiring a long-title buffer?
4. Which source forms are available in the user's installation: VIDEO_TS directories, already-mounted images, ISO files, and representative subtitle recordings? Source availability affects field acceptance, not whether parser work can proceed.
5. What independently measurable browser/FFmpeg clocks and frame/audio counters are available for the actual target browser? Avoid replacing missing measurements with configured values.

These questions are not a request to pause all work for broad clarification. Resolve what can be resolved from the code and generated fixtures; ask only for genuinely missing field media/hardware or consequential permissions when that gate is reached.

## No automatic inheritance of upstream test claims

Upstream physical success proves the Android implementation on those paths. It does not prove browser MSE, the server plugin's helper/build, a user's GPU or native Safari. Likewise, an upstream remaining open item is not automatically a newly observed browser failure. Record the exact distinction in the fix ledger.
