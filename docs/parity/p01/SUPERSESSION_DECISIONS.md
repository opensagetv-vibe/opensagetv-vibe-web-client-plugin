# Current policy versus historical fixes

Reference: Vibe source/dev at `f1ba340e05fe18eaaba939253a933c3dfe6d6caf`, TASKS revision 71.
Each of the 248 normalized ledger records has a disposition and permanent WebPlayer
task mapping; the 68 named upstream tasks separately retain current parent status.
Neither an Android checkbox nor a release publication closes a WebPlayer task.

| Subject | Current port decision | History that must not be reinstated |
|---|---|---|
| Virtual CC1/CC2 | Text-caption services: CEA and Teletext; two distinct services where supported. | The short-lived DVB-inside-CC-slot menu is superseded by CC-007. |
| DVB | Separate top-level local bitmap mode; preserve PID and distinct subtitle command36/type1. | Do not convert bitmap pixels into event225 or label DVB as Teletext. |
| Ordinary subtitles | SRT/file text, PGS and DVD/VobSub remain separate from broadcast CC. | VIDEO_CC_STATE must not turn on an arbitrary file subtitle preference. |
| Local/STV ownership | Explicit local mode flushes the old legacy renderer before painting. | A stale server-painted caption must not survive beneath local output. |
| Stock Core | Legacy caption callback/native DVD contracts are separate from optional metadata/policy extensions. | An updated-Core test is not stock compatibility. Unknown stock identity remains unverified. |
| DVD protocol | INIT without OPENURL, actual PS bytes, four-byte DVD replies, decoder drain and physical stream IDs. | Generous capacity/discarded Push, ordinary track ordinals or main-VOB concatenation are not DVD parity. |
| DVD transforms | Provider-neutral dvd_mpegts_v1 / transformed_main_feature only when negotiated in optional P15. | Old MIM-specific and VIBE_* advertisements are not current wire contracts. |
| Seeking | Preserve stable media time through FLUSH, distinguish mux-end timestamp from epoch start, and retain source progress. | Old blanket zero-time reports and blind reprepare experiments are not universal recovery rules. |
| Output/buffering | Subtitle time follows playback, not how far FFmpeg has produced; title reserve must not deadlock short menu cells. | Do not copy a five-second DVD reserve into every browser startup or short navigation cell. |
| User identity/settings | Preserve normal stored identity and explicit client overrides; tests restore their temporary settings. | Old fixed DEV001 enforcement, unconditional uninstall or global defaults are superseded. |
| Platform backends | Port relevant behavior/contracts; identify Android-only Surface/AudioTrack/MediaCodec/ADB fixes. | A browser cannot acquire a MediaCodec, Android store or receiver validation merely by copying the setting. |

## Open parent gates stay open

The upstream crosswalk records 51 completed named parent tasks and 17 open ones.
In particular DVD-001 retains exact type-5 recovery/teardown/Unified acceptance;
SEEK-001 retains final affected-device visible landing acceptance; AUDIO-001,
AUDIO-005 and AUDIO-006 retain receiver/ARC or matched-device measurements. The
completed code subfixes are candidate behavior, not proof these parent gates
passed. ONN-003 persistent OSD, affected tablet DEVICE-001, deferred cross-device,
Blu-ray, DRM, future firmware work and store/signing tasks remain distinguishable.

## Intake boundary

WP01-002 is complete for these current-versus-historical decisions. WP01-001 is
still PARTIAL: literal changelog paragraph and full relevant-regression-file
reconciliation has not been completed. Repeated historical summaries are not
silently counted as distinct fixed features, nor silently omitted as proof of
exhaustiveness. Use the pinned-blob intake tool and append exact mappings before
closing that task.
