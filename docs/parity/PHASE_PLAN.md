# Vibe Android → SageTV WebPlayer parity implementation plan

**Parity revision 2 · 2026-09-24 · Working runtime remains 3.2.4**

Current execution: P01 has 5 completed tasks,1 partial audit and 2 blocked external gates. See [p01/REPORT.md](p01/REPORT.md). The phase definitions and all permanent IDs below remain unchanged.

## Requested outcome

Bring the browser/server plugin up to the relevant fixed behavior in the requested Vibe Android client, beginning with Teletext, DVB bitmap subtitles and full DVD playback. Include the related caption authority, tracks, clocks, seeking, audio, recovery, diagnostics and configuration work instead of treating those formats as dropdown-only additions. The initial r1 delivered the phase definitions. Revision2 adds P01 audit/test tooling and evidence, **not new playback code**.

## Scope and source authority

The reference is `opensagetv-vibe/opensagetv-vibe-android-client/source/dev` at commit `f1ba340e05fe18eaaba939253a933c3dfe6d6caf`, the current main head read for this review. `TASKS.md` in that repository is revision 71 at the v0.5.95 checkpoint. This plan covers the frozen checkpoint, not unpublished local changes or future commits. [U01/U02]

The project ZIP retains the exact uploaded WebPlayer 3.2.4 production baseline with P01 documentation, test-tool and fixture additions. All runtime source, browser assets, tests, build scripts, compiled WAR and local installer are retained byte-for-byte. The original build metadata remains historical build metadata. The user reports the update works; that is retained as user feedback, not a claim that every existing test path was physically exercised. [B01]

The assessment below is grounded in reviewed source and project documentation. P01 is explicitly responsible for the exhaustive per-changelog/per-test fix ledger before a future release claims parity. No generic feature plan can honestly certify unseen or unreviewed fixes.

## Confirmed gaps in the supplied browser source

| Area | Actual 3.2.4 baseline | Consequence |
|---|---|---|
| Captions | `StreamOptions` only permits off/608/708; the side channel carries original video for A/53 extraction. | No actual Teletext PES/page decoding, DVB bitmap or ordinary subtitle rendering. |
| Native DVD | `MiniClientMediaBridge` returns -1 for commands 32–37; `PUSHBUFFER` returns capacity without consuming payload. | Cannot provide native DVD A/V, authored subpictures, VM draining or menus by changing settings alone. |
| Stock CC authority | `GFX_SUBTITLES=FALSE`; enabling `SUBTITLES_CALLBACKS` is rejected. | The Android event-225/STV path and single-renderer transitions are missing. |
| Inventory | Probe metadata lacks required PID/page/service/role/forced identity. | Language/virtual-slot/bitmap selection cannot be made reliable from track ordinal alone. |
| Other parity | Frame step unsupported; remote/pipe seeks are documented as proportional; full diagnostics/profile/disc behavior is absent or partial. | Audit and targeted adaptation needed rather than claiming the overlay port transferred the playback engine. |

These observations are tied to the exact source files and baseline hashes in `REFERENCE_LOCK.json`. A rejection/capability flag is not changed until the corresponding backend and reply contract are implemented. [B02–B07]

## Non-negotiable implementation boundaries

1. **Stock-server first.** Preserve stock Sage.jar and the separate FFmpeg/MIM installation. Optional Core/provider features are isolated in P15; no silent Core replacement. [U03]
2. **Preserve accepted behavior.** Keep 3.2.3 first-FLIPBUFFER safety, crypto ordering and reconnect logic; keep the actual Vibe edge overlay, full-browser video, previews, input mapping and separate fast-start/reserve policy. [B01]
3. **Three subtitle families.** CC1/CC2 = CEA/Teletext; explicit DVB = bitmap; ordinary subtitles = file text/PGS/VobSub/DVD SPU. Current policy supersedes older changelog options that briefly included DVB inside CC slots. [U01/U03/U07]
4. **Real DVD means authored interaction.** Native DVD needs INIT without OPENURL, pushed PS data, commands 32–37, STC/cell clock, drain, physical audio mapping and SPU/highlights. Main-feature conversion alone is a separately labeled mode, not menu parity. [U03/U04/U08/U09]
5. **Per-client state.** Android process-global Teletext ownership must be adapted to per-playback server state; one browser must not change another. [U05; proposed server adaptation]
6. **Truthful evidence.** Android passes, parser counters, enabled worker flags and mocked browser buffers are not web/GPU/real-source passes. Retain PASS/FAIL/UNTESTED/BLOCKED distinctions. [U01/U03/B01]

## All task phases

| Phase | Work package | Completion boundary |
|---|---|---|
| P00 | Preserve the working build and publish the plan | Planning documentation and archive integrity only. This phase is not a playback, subtitle, DVD, hardware or regression-test pass. |
| P01 | Complete the upstream fix ledger and regression baseline | Reviewed fix ledger, reproducible baseline and fixture registry exist; genuine failures and unavailable live tests remain explicit. |
| P02 | Shared playback, track and subtitle architecture | Versioned contracts, session isolation, bounded resources, configuration migration and source-preservation tests pass before decoder UI switches are enabled. |
| P03 | DVB Teletext subtitle decoding and continuous presentation | Visible, synchronized Teletext pages survive OSD hide, pause, seek and track changes in supported delivery paths without affecting other clients. |
| P04 | DVB bitmap subtitles | DVB remains a distinct, selectable bitmap path with correct timing, transparency, position and cleanup across all supported transports. |
| P05 | Stock SageTV CC authority and virtual CC1/CC2 | Stock STV authority, virtual text-caption slots and explicit local DVB have mutually consistent, nonduplicating behavior with separate ordinary subtitles. |
| P06 | Ordinary text, PGS and VobSub/file subtitles | Supported ordinary subtitle types have real decoding/rendering and independent controls; remaining format limitations are explicit. |
| P07 | Native DVD protocol and pushed-media session | A stock-compatible native DVD session accepts real bytes and controls, handles all mandatory replies, drains and resets without changing the GFX startup/authentication invariants. |
| P08 | DVD MPEG-PS A/V conversion, timing and still menus | Real DVD menu/title PS is converted and presented in the browser with correct audio, clock, cadence, still behavior and bounded streaming; protocol-only success is insufficient. |
| P09 | DVD SPU subtitles, highlights and interactive navigation | Interactive root/submenu/title navigation, highlights, title subtitles, language/chapter selection and teardown work through the actual browser path. |
| P10 | Disc sources, title selection and library metadata | Disc input and metadata handling is deterministic and safe; environment limitations are explicit, and movie-only conversion is not substituted for interactive DVD. |
| P11 | Seek, Comskip, growing files and sustained buffering | Measured seeking, source growth/EOF and sustained buffering remain correct under real playback and control stress, not just mocked buffer snapshots. |
| P12 | Audio, playback controls and quality-policy parity | Browser-supported audio/quality/control behavior is verified and truthfully labeled; Android decoder selection, HDMI passthrough or receiver latency are not promised by browser settings. |
| P13 | Remaining GFX, input, lifecycle and optional unified surfaces | Relevant graphics/input/lifecycle parity is tracked and regression-tested, with no blind negotiation of unsupported surfaces or loss of the accepted overlay/display fixes. |
| P14 | Diagnostics, configuration profiles and server-share workflows | Diagnostics and profiles are useful, bounded and secret-safe; any server/share mutation is explicit and independent of normal playback. |
| P15 | Optional extended-Core/provider integration | Optional only. All tasks remain awaiting separate approval and do not block stock-compatible implementation or release. |
| P16 | End-to-end parity acceptance matrix | All required rows for advertised scope pass on the intended stock-server/browser paths; skipped, unavailable, mock-only and unresolved rows stay visible. |
| P17 | Release, manifests and future parity maintenance | Independent artifact/source validation, honest support notes, exact hashes, task evidence and a safe upgrade/rollback path are complete. |

The complete implementation tasks, file-level integration points and individual acceptance criteria are in [`../../TASKS.md`](../../TASKS.md). Dependency metadata and a machine-readable mirror are in `TASK_INDEX.json`.

## Proposed implementation architecture

This is a design target, not a description of code already shipped.

```text
Existing SageTV native GFX/media session
    | ordinary MediaFile/MediaServer source     | native DVD PS PUSH session
    +----------------- per-playback source/epoch owner ------------------+
                                  |
              original source subtitle/service extraction
                 | text / bitmaps / DVD SPU & highlights
                 | bounded session-scoped cue channel
                 |                               |
         FFmpeg A/V copy/transcode         subtitle adapters
                 |                               |
          TS or HLS delivery             timestamped text/alpha bitmaps
                 |                               |
           browser video -------- shared display/clock mapping
                 |                               |
                 +------- video + GFX + subtitle layers
                                  |
                      retained Vibe browser overlay
```

The preferred first Teletext implementation is a per-session adaptation of the Vibe Java Level-1 engine. DVD SPU has a reusable platform-neutral core but still needs a web presentation adapter. The DVB/PGS backend is an explicit P02 decision: a vetted Java decoder or an isolated, capability-probed native helper must be proved before selection is exposed. No external library or helper is installed by this planning package. [U05/U08/U09/E01]

Source subtitles must be preserved before existing output mapping drops them. Bitmap paths use authored pixels and alpha; they are not converted to text or required to survive a hardware video encoder. Burn-in, where separately implemented, must be labeled as a different output mode with its own transcode implications. [B04; proposed architecture]

## Recommended execution sequence

**First:** P01 baseline/ledger and P02 contracts. Then run the subtitle lane P03/P04/P06 and the DVD lane P07/P08/P09 in parallel when resources allow. Integrate caption authority at P05; finish disc paths at P10. P11–P14 are shared behavior audits/adaptations and can proceed after P02 without waiting for every DVD field fixture. P16 validates the actual advertised stock scope; P17 packages and closes the ledger. P15 is optional and excluded from the stock critical path.

Focused tests run after each small change; the full combined real-browser/stock-server matrix runs at P16. Do not repeat every physical Android matrix for every web edit, and do not stop unrelated coding merely because an external device or private fixture is unavailable. Release claims remain blocked for the affected untested feature.

## Implementation decisions that must be recorded

| Decision | Default direction | Required evidence |
|---|---|---|
| Where subtitles decode | Per-session server adapters + browser presentation; reusable Vibe core where applicable | P02 dependency/license/performance/lifetime decision and golden vectors |
| Native DVD delivery | Stock DVD VM remains authority; plugin converts its pushed PS A/V for the browser | Real native reply/drain tests plus actual browser menu/title playback |
| Caption rendering owner | One active CC owner; stock bridge and local renderer explicitly hand off | Stock property-present/absent and mode-transition gates |
| ISO inputs | Honor existing server mount/read-only capabilities; no automatic privilege changes | Exact source-form tests and explicit environment errors |
| Optional Core transforms | Separate opt-in branch; do not block or contaminate stock package | Actual negotiated provider evidence, stock fallback and separate approval |
| Hardware/audio controls | Only effective supported browser/FFmpeg paths; no fabricated Android decoder/HDMI menus | Requested/effective telemetry and actual hardware/receiver evidence as applicable |

Detailed decisions and unresolved questions: `DECISIONS.md`.

## Acceptance and test material

`TEST_MATRIX.md` defines fixture roles and observable passes. At minimum, prove a real Teletext positive and negative source, actual DVB bitmaps, original CC across transcoding, ordinary file subtitles, authored root/submenu/still DVD navigation, representative moving DVD playback, long recording seek/buffer behavior and multi-client isolation. Existing packet/clock tests remain valuable but are not substitutes for visible/audible output.

Required tools are the existing JDK8-target/Servlet build, Node, Python development tests, FFmpeg/ffprobe, real vendor player assets and available browser automation. Real stock SageTV/STV, suitable subtitle/disc fixtures and physical GPUs/audio routes are separate acceptance resources. No new Python server, port or runtime service is required by this plan; decoder-helper needs, if any, are settled before implementation.

## Current status and handoff

P00 is complete; P01 is complete at its local audit/baseline exit gate with real vendor-MSE acceptance still external-pending; P02 is complete; and P03 Teletext and P04 DVB bitmap are implemented at the local/generated boundary. P03 WP03-008 and P04 WP04-007 remain `IMPLEMENTED_AWAITING_FIELD` until unchanged real broadcast sources pass their visible/timing matrices. P05 is the next runtime implementation phase after the outstanding field rows; P15 still requires separate approval.

Read `../../TASKS.md`, `PARITY_MATRIX.md`, `p04/REPORT.md`, `p04/FIELD_ACCEPTANCE.md`, `p03/FIELD_ACCEPTANCE.md`, `DECISIONS.md`, `TEST_MATRIX.md` and `REFERENCE_LOCK.json` before the next implementation turn. Preserve the accepted first-FLIPBUFFER/overlay/video-geometry behavior and do not classify DVB bitmap as Teletext.

## References

Source IDs [Uxx], [Bxx] and [Exx] resolve in `SOURCES.md` and the exact reference lock. The source-derived behavior is distinguished above from the proposed Java/Jetty/browser adaptation.
