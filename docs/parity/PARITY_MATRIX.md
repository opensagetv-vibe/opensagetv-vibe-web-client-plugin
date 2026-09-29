> Revision2 execution note: P01 recorded fresh baseline evidence and a 248-record
> normalized fix ledger. No row below gains new runtime support from that audit.
> See p01/REPORT.md for5 DONE/ 1 PARTIAL/ 2 BLOCKED P01 tasks and exact scope.

# Android → WebPlayer parity matrix

Planning revision 1 — 2026-09-24. Baseline runtime 3.2.4; upstream commit`f1ba340e05fe18eaaba939253a933c3dfe6d6caf`.

This is the **reviewed starting inventory**, not the exhaustive historical changelog audit. P01 closes the per-fix ledger. No missing/partial row is marked implemented in this package. Upstream status and browser status are separate.

| ID | Capability/fix | Upstream evidence/status | Browser3.2.4 evidence | Disposition | Planned phases | Sources |
|---|---|---|---|---|---|---|
| PAR-001 | Stock-server-first compatibility | FOUND-001 completed; U03 | Existing plugin-only 3.2.4 design | RETAIN_AND_VERIFY | P01, P07, P16 | B01 |
| PAR-002 | Quick startup and continuous reserve | Player/source buffering foundation; U01/U02 | Implemented TS/HLS policy; real MSE acceptance still pending in supplied build | RETAIN_AND_VERIFY | P01, P11, P16 | B01/B07 |
| PAR-003 | Full-browser layout and real Vibe icons | UI-001 completed; navigation resource | Implemented and user reports update works | RETAIN_AND_VERIFY | P13, P16 | B01/U14 |
| PAR-004 | Native first-frame safety, crypto and reconnect | Foundation and DVD-001 completed recovery subfixes | 3.2.3 first-FLIPBUFFER gate, crypto and recovery retained | RETAIN_AND_VERIFY | P01, P13 | B01/B05 |
| PAR-005 | Teletext descriptor/PES preservation | TTX-001 completed | P02 now preserves typed PID/page inventory and a bounded original-source branch; decoder/PES presentation remains P03 | PARTIAL_IMPLEMENTED | P02, P03 | B03/B04/U05 |
| PAR-006 | Teletext Level-1 page decode | TTX-002 completed | No Teletext decoder | MISSING | P03 | U05/B04 |
| PAR-007 | Teletext independent clock / no OSD freeze | TTX-002 completed | CEA has a clock; no Teletext cues | MISSING | P03 | U05/U06/B04 |
| PAR-008 | Teletext stock CC bridge | TTX-002 completed | GFX_SUBTITLES FALSE and callbacks rejected | MISSING | P05 | U06/B05 |
| PAR-009 | Multi-client Teletext ownership | Android singleton is source behavior, not server architecture | P02 adds per-browser source/seek/FLUSH epochs and stale-token rejection; Teletext decoder binding remains P03 | P02_IMPLEMENTED | P02, P03 | U05/B02 |
| PAR-010 | Virtual CC1/CC2 profiles | TTX-003 completed with later CC-007 policy | Literal 608/708 service settings only | MISSING | P02, P05 | U07/B03 |
| PAR-011 | Observed service/language inventory | TTX-003/CC-006 completed | P02 preserves PID, service/page IDs, disposition and observed-PMT versus metadata-only evidence; CEA-608 language remains Unknown | IMPLEMENTED_P02 | P02, P05 | B03/U03 |
| PAR-012 | DVB explicit mode/persistence | CC-004 and CC-007 completed | P04 adds explicit local DVB mode, discovered physical service selection and saved late-discovery re-resolution; P05 still owns stock authority integration | IMPLEMENTED_P04_LOCAL | P04, P05 | U01/U03/B03 |
| PAR-013 | DVB bitmap decoding/alpha/placement | Normal feature in U03 | P04 adds descriptor/PES/display-set decode and straight-alpha RGBA canvas rendering; real-source field timing remains pending | IMPLEMENTED_P04_AWAITING_FIELD | P04 | U03/B04 |
| PAR-014 | Ordinary DVB PID/disable selection | TTX-003/CC-006 completed | P04 handles media36/type1 by physical PID with on-demand startup, late discovery, unknown-PID fail-closed and sentinel 8192 disable; DVD commands remain separate | IMPLEMENTED_P04 | P04, P05 | U04/B02 |
| PAR-015 | CC separate from file/DVD subtitles | CC-005 completed | Only local CEA; separation must survive additions | MISSING | P05, P06 | U03/B03 |
| PAR-016 | One caption-render owner | CC-006 completed | No stock callback or bitmap owner yet | MISSING | P02, P05 | U03/U06/B05 |
| PAR-017 | Persisted choice after async discovery | v0.5.93 completed fixes | P04 re-resolves saved DVB PID/page/language after descriptor discovery; P05 still owns combined stock caption-authority persistence | PARTIAL_IMPLEMENTED_P04 | P02, P04, P05 | U02/B03 |
| PAR-018 | CEA608/708 details and transcode retention | Completed foundation; full protocol vectors required | A/53 branch and local decoder implemented, not universal styling certification | PARTIAL_AUDIT | P02, P05, P16 | B04/U03 |
| PAR-019 | SRT/embedded text subtitles | Ordinary subtitle feature in U03 | Explicitly not implemented | MISSING | P06 | B01/U03 |
| PAR-020 | PGS/file bitmap subtitles | Ordinary subtitle feature in U03 | Explicitly not implemented | MISSING | P06 | B01/U03 |
| PAR-021 | VobSub/DVD file palette context | DVD/SPU and ordinary-subtitle behavior | No file-subtitle adapter | MISSING | P06 | U08/E01/B04 |
| PAR-022 | Native DVD role/selection negotiation | FOUND-006; U03 stock extender role | Browser advertises MOUSE plus PULL-only; DVD path not implemented | MISSING | P07 | B05/U03 |
| PAR-023 | DVD INIT without OPENURL | MediaCmd completed fix | INIT returns1; media creation requires OPENURL | MISSING | P07 | U04/B02 |
| PAR-024 | DVD raw pushed media | FOUND-006 completed | PUSHBUFFER only returns16 MiB; payload is not consumed | MISSING | P07, P08 | B02/U04 |
| PAR-025 | DVD exact reply lengths | MediaCmd completed deadlock/alignment fixes | DVD32–37 return-1; no operational DVD session | MISSING | P07 | U04/B02 |
| PAR-026 | DVD transient EOS/drain/FLUSH | MediaCmd completed fixes | No DVD generation/drain protocol | MISSING | P07, P08 | U04/B02 |
| PAR-027 | DVD PS private audio substream routing | Completed DvdPsExtractor behavior | No DVD A/V conversion input path | MISSING | P08 | U11/B02 |
| PAR-028 | DVD PTS/cell/STC clock and cadence | Completed core/player fixes | No DVD clock translation | MISSING | P08 | U04/U11 |
| PAR-029 | DVD still/short/menu-cell buffering | Completed fixes; DVD-001 retains separate open acceptance | No native DVD playback | MISSING | P08 | U04/U11/U01 |
| PAR-030 | DVD SPU/RLE/CLUT/CHG_COLCON | FOUND-006 completed | No SPU renderer | MISSING | P09 | U08/U09/U12 |
| PAR-031 | DVD highlight/subtitle generation fixes | Completed DvdSubpictureDecoder behavior | No authored bitmap state | MISSING | P09 | U09 |
| PAR-032 | DVD titles/menus/chapter/seek controls | FOUND-006 and MATRIX-002 completed | No functioning DVD backend | MISSING | P09, P10 | U03/U04 |
| PAR-033 | Stock-compatible DVD local display refresh | FOUND-015 completed | Browser already separates layout; no DVD refresh context | PLATFORM_ADAPTATION | P08, P13 | U01/B01 |
| PAR-034 | Disc path/ISO and metadata safety | Stock source and U03 boundary | Direct/MediaServer files and basename fallback; no proven ISO route | PARTIAL_AUDIT | P10 | B02/B06/U13 |
| PAR-035 | Optional Core DVD transformed transport | FOUND-012/016/017 completed in extended path | No negotiated provider path | OPTIONAL_CORE_DEPENDENT | P15 | U01/U03/U10 |
| PAR-036 | Client Skip Menus/Skip Previews | U03 Core-dependent behavior | No supported configuration | OPTIONAL_CORE_DEPENDENT | P15 | U03 |
| PAR-037 | Functional property names/unknown handling | FOUND-016/017 completed | Partial generic fallbacks; explicit runtime contracts require audit | PARTIAL_AUDIT | P07, P13, P15 | B05/U10 |
| PAR-038 | Remote/pipe seek accuracy | FOUND-009 and seek fixes | Byte-proportional fallback is documented | PARTIAL_AUDIT | P11 | B07/U01 |
| PAR-039 | Transitional-zero and mux-end Push fixes | SEEK-001 subfixes complete; visual Pro gate OPEN | No ordinary Push decode path yet | ADAPT_ONLY_WHEN_APPLICABLE | P07, P11 | U02/U04/B02 |
| PAR-040 | Growing-file backward-position guard | PULL-001 completed, affected long-run follow-up remains | Follower exists; compare clock/reset safeguards | PARTIAL_AUDIT | P11 | B07/U01 |
| PAR-041 | Completed EOF/tail clamping | Completed earlier changelog fixes | Partial implementation; actual transport/edge cases need parity audit | RETAIN_AND_VERIFY | P11 | B02/B07 |
| PAR-042 | Progress-aware retry and bounded probe cache | FOUND-009/D1–D5 completed | Existing probes/recovery; compare exact source identity and progress | PARTIAL_AUDIT | P11, P12 | B07/U01 |
| PAR-043 | Primary audio versus NAR/commentary | U03 completed UK fixture behavior | Language/index selection; role metadata absent | PARTIAL_AUDIT | P02, P12 | B03/U03 |
| PAR-044 | Signed delay/session defaults/live debounce | AUDIO-002/003 completed; physical residual gates OPEN | -3000..3000ms via stream restart, not Android live AudioTrack | PLATFORM_ADAPTATION | P12 | B03/U01 |
| PAR-045 | HDMI passthrough/decoder selectors | Android native APIs and AUDIO-004/006 | Browser codec support is not HDMI output authority | PLATFORM_ADAPTATION | P12 | U01/U03/B01 |
| PAR-046 | Fixed quality settings/type migration | FOUND-013 completed Android fix | P02 adds versioned streaming profile migration while preserving bitrate/audio/display and literal CEA settings; broader quality-policy parity remains P12 | PARTIAL_IMPLEMENTED | P02, P12 | B03/U01 |
| PAR-047 | Frame step and rate/scan controls | Foundation and functional-rate extension | FRAMESTEP returns0; browser forward rate exists; no rate extension advertised | PARTIAL_AUDIT | P12, P15 | B02/B05/U10 |
| PAR-048 | Unified Y/UV graphics | UNIFIED-001 completed opt-in feature | Not advertised/not implemented | MISSING_OPTIONAL_CLIENT_FEATURE | P13 | B05/U01 |
| PAR-049 | GFX opcodes/cache/alpha/transform parity | Renderer refactor and foundation | Subset with unadvertised transforms/batch/YUV | PARTIAL_AUDIT | P13 | B05/U10 |
| PAR-050 | Input Stop/Back/focus/repeat | ONN-002/UI-001/SMB-002 completed | Browser equivalents and Vibe icons partly implemented | PLATFORM_ADAPTATION | P13, P14 | B01/U01 |
| PAR-051 | Background/foreground/session teardown | Completed foundation/lifecycle work | Existing recovery; browser lifecycle differs | PLATFORM_ADAPTATION | P13 | B01/U01 |
| PAR-052 | Diagnostic stats and bounded export | FOUND-002/004 completed | P14 bounded redacted ZIP + hash + failure/test/caption/DVD/transcode counters | IMPLEMENTED_LOCAL | P14 | B01/U01 |
| PAR-053 | Test Current Video real reversible checks | FOUND-003 completed | P14 explicit cadence/pause/resume/reversible-seek test with unsafe-case skip and state restoration | IMPLEMENTED_LOCAL | P14 | B01/U01 |
| PAR-054 | SMB/profile/diagnostic transactions | SMB-001/002 and v0.5.95 test preservation | Schema-v1 saved-default profiles plus admin-mounted diagnostic destination; no browser SMB credentials | PLATFORM_ADAPTATION_IMPLEMENTED_LOCAL | P14 | U01/U02 |
| PAR-055 | Cold-storage startup and long media | MATRIX-001 evidence and adaptive test allowance | General timeout/probe behavior; no field parity evidence | PARTIAL_AUDIT | P01, P11, P16 | U01/B07 |
| PAR-056 | Open long-recording OSD/DVD/audio/tablet work | ONN-003/DVD-001/AUDIO-001/005/006/DEVICE-001 remain OPEN in U01 | Not proven browser failures or completed upstream fixes | UPSTREAM_OPEN | P01, P13, P16 | U01 |
| PAR-057 | Unfinished cross-device and Blu-ray/DRM work | MATRIX-003/004 and EXT-002/003 pending | Not automatically included as completed parity | UPSTREAM_OPEN_OR_SEPARATE_SCOPE | P01, P10, P16 | U01 |
| PAR-058 | Android API migration, APK signing/store/ADB | EXT-001 and STORE tasks Android-specific | Not Java/Jetty browser-runtime work | NOT_DIRECTLY_PORTABLE | P01, P17 | U01/U03 |
| PAR-059 | Future HD200/HD300 firmware audit | EXT-004 explicitly future | No proprietary firmware port authorized | UPSTREAM_OPEN_OR_SEPARATE_SCOPE | P01 | U01 |

## Important classification rules

A completed Android task is only implementation evidence for that platform. An Android-only Activity, MediaCodec, AudioTrack, Surface or AndroidX preference fix requires a browser-equivalent behavior decision, not wholesale Java class copying. Native DVD and Teletext core logic can still be highly reusable.

Completed subfixes under upstream-open DVD-001, SEEK-001 and audio tasks are explicitly port candidates, but their uncompleted physical/device acceptance is not inherited as a pass. Upstream future firmware, Blu-ray/DRM and store work is not silently included as “already fixed.”

Browser implementations already present should be preserved and tested; do not replace them just because Android implements the same behavior with a different player. Proposed enhancements beyond the frozen source subset get new task IDs and a separate scope note.
