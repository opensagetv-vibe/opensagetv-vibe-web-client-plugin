# OpenSageTV Vibe Web Client Plugin — active task list

Last updated: 2026-09-27

- [x] WEB-001 Safely inspect and import the supplied 3.2.29 P14 archive.
- [x] WEB-002 Preserve and inventory all historical Markdown/parity evidence.
- [x] WEB-003 Create the active `opensagetv-vibe-web-client-plugin` project.
- [x] WEB-004 Add common Vibe workflow, governance, licensing, and release files.
- [x] WEB-005 Require the Linux Vibe FFmpeg Plugin in the local installer.
- [x] WEB-006 Resolve production transcoding through `SageTVTranscoder` instead
  of directly selecting SageTV's stock `ffmpeg`.
- [x] WEB-007 Add deterministic Linux and Windows plugin manifests/packages.
- [x] WEB-008 Add Web Client commands/mounts to the unified build environment.
- [x] WEB-009 Run the full local generated, Java, JavaScript, package, and
  offline-browser validation suites.
- [x] WEB-010 Commission the development package on Vibe server `.232` without
  modifying stock `Sage.jar` or stock `ffmpeg`.
- [x] WEB-011 Verify required FFmpeg Plugin discovery/runtime status on `.232`.
- [x] WEB-012 Verify HTML5 HLS transcoding of ATSC MPEG-2 media on `.232`.
- [x] WEB-013 Verify applicable H.264/H.265, audio, seek, growing/live,
  CEA/Teletext/DVB/file-subtitle, diagnostics, and recovery gates on `.232`.
  H.264/audio/seek/CEA/Teletext/DVB/diagnostics/recovery passed. No indexed
  H.265 or ordinary-file-subtitle fixture and no active growing recording were
  available on `.232`; their generated/local gates passed and the missing
  physical rows remain explicitly unclaimed in `docs/VALIDATION-3.2.30.md`.
- [ ] WEB-014 Verify applicable authored-DVD startup, menu, SPU, navigation,
  title/chapter/audio/subtitle, fallback, and teardown gates on `.232`.
  The exact-path authored Vibe fixture now starts hardware HLS, advances video,
  shows the menu background and 4,716-pixel SPU highlight, opens Languages,
  processes DVD Return/DVD Menu, and repeats root-to-submenu navigation without
  a browser error. Generated forced hardware failures also reach playable
  software fallback after input EOS. Title/chapter/audio/subtitle selection,
  Stop/replay, representative physical/menu-less discs, and long-run teardown
  remain open; the complete DVD matrix is therefore not yet claimed.
- [x] WEB-015 Record exact generated versus physical evidence in the durable
  project documents and regenerate the source manifest.
- [x] WEB-016 Prepare local release artifacts but do not publish until the user
  gives final approval.
- [ ] WEB-017 Publish the explicitly approved 3.2.30 beta source repository and
  plugin artifacts, verify public hashes and repository checks, and submit the
  dependency-correct SageTV plugin catalog entry while retaining every open
  physical acceptance row as an explicit limitation.

## Imported historical task ledger

# v3.2.29 field fix — DVD menu retained frame

- [x] Cache real decoded DVD frames before one-picture menu cells disappear.
- [x] Promote cached frame at native FLUSH/freeze with generation matching.
- [x] Retain previous menu background through SPU-only/no-video cells; never paint black on cache miss.
- [x] Release retained still only after replacement generation decodes a real frame.
- [x] Add browser regression for post-FLUSH late events and no-video generation behavior.
- [x] Field verify authored DVD menu background + SPU highlight/button navigation on the authored Vibe fixture, including return-to-root and a second submenu entry.

# SageTV Web Player — Android parity task master

**Revision 11 — 2026-09-25 · Runtime 3.2.12-P09 · DVD SPU decode/highlight/browser overlay and native-key navigation integration implemented locally; authored real-disc acceptance remains field-open**

This is the authoritative checklist for this WebPlayer project. It does not replace the Android repository TASKS.md. The user requested all task phases in the project ZIP **before implementation starts**. P01 now contains the completed frozen-source intake, executed baseline tests, fixture/tooling work, live server identity record, and saved audit ledger. Real pinned vendor MSE playback remains an explicit external acceptance row. P02 runtime architecture is retained. P03 implements local DVB Teletext subtitle decoding/presentation, P04 implements DVB bitmap decoding/presentation, P06 implements ordinary text/PGS/DVD-subtitle presentation, and P07 implements the native DVD wire/input session while leaving visible DVD A/V to P08.

Reference: `opensagetv-vibe/opensagetv-vibe-android-client` at commit `f1ba340e05fe18eaaba939253a933c3dfe6d6caf` (v0.5.95 checkpoint, source/dev). The actual Git tree is `06a4c99958b8e07f6ad84b0c0d721a7b92e71918`.

Progress: **58/127 tasks complete**: P00 6/6, P01 7/8, P02 8/8, P03 7/8, P04 6/7, P05 4/7, P06 5/6, P07 7/8, P08 8/8. **P01 WP01-004, P03 WP03-008, P04 WP04-007, P05 stock-server rows, P06 WP06-004, and P07 WP07-001 remain external/field acceptance items, not PASS.** P08 MPEG-PS A/V is locally validated with real FFmpeg and production-browser fixtures; live stock-server/disc and physical-GPU observations remain field evidence, while P09 owns SPU/highlights/navigation.

## Status and history rules

- `[x]` means the stated acceptance criteria are satisfied with linked evidence. Source-code existence or an upstream Android checkbox is not browser acceptance.
- Keep permanent IDs and completed entries. New findings get new IDs. Reopening/reordering records the reason and revision; never rebuild the list from chat.
- An implemented item awaiting field proof remains unchecked with an explicit `IMPLEMENTED_AWAITING_FIELD` note. Missing hardware/fixtures/permissions are `BLOCKED` or `UNTESTED`, never PASS.
- For each implementation change update this file, `docs/parity/TASK_INDEX.json`, matrix rows, evidence and the ledger together. Preserve user settings and the stock Sage.jar boundary.
- Do not start optional P15, remote publication, database mutation, privileged mounts or share writes without their explicit approval. The planning update itself makes none of those changes.

## Phase order

| Phase | Deliverable | Dependencies | State |
|---|---|---|---|
| P00 | Preserve the working build and publish the plan | None | COMPLETE_PLANNING_ONLY |
| P01 | Complete the upstream fix ledger and regression baseline | P00 | COMPLETE_WITH_EXTERNAL_VENDOR_ACCEPTANCE_PENDING |
| P02 | Shared playback, track and subtitle architecture | P01 | COMPLETE_LOCAL_ARCHITECTURE |
| P03 | DVB Teletext subtitle decoding and continuous presentation | P02 | IMPLEMENTED_LOCAL_AWAITING_REAL_SOURCE_FIELD |
| P04 | DVB bitmap subtitles | P02 | IMPLEMENTED_LOCAL_AWAITING_REAL_SOURCE_FIELD |
| P05 | Stock SageTV CC authority and virtual CC1/CC2 | P03, P04 | IMPLEMENTED_LOCAL_AWAITING_STOCK_SERVER_FIELD |
| P06 | Ordinary text, PGS and VobSub/file subtitles | P02 | IMPLEMENTED_LOCAL_AWAITING_VOBSUB_FIELD |
| P07 | Native DVD protocol and pushed-media session | P02 | IMPLEMENTED_LOCAL_AWAITING_STOCK_SERVER_FIELD |
| P08 | DVD MPEG-PS A/V conversion, timing and still menus | P07 | COMPLETE_LOCAL_RUNTIME |
| P09 | DVD SPU subtitles, highlights and interactive navigation | P08 | IMPLEMENTED_AWAITING_FIELD |
| P10 | Disc sources, title selection and library metadata | P08, P09 | IMPLEMENTED_LOCAL_AWAITING_MULTITITLE_FIELD |
| P11 | Seek, Comskip, growing files and sustained buffering | P02 | IMPLEMENTED_AWAITING_LONGRUN_FIELD |
| P12 | Audio, playback controls and quality-policy parity | P02, P11 | IMPLEMENTED_AWAITING_FIELD |
| P13 | Remaining GFX, input, lifecycle and optional unified surfaces | P02 | IMPLEMENTED_LOCAL_AWAITING_UNIFIED_FIELD |
| P14 | Diagnostics, configuration profiles and server-share workflows | P02 | IMPLEMENTED_LOCAL_AWAITING_FIELD |
| P15 | Optional extended-Core/provider integration | P07, P08, P10 | OPTIONAL_NOT_STARTED |
| P16 | End-to-end parity acceptance matrix | P03, P04, P05, P06, P09, P10, P11, P12, P13, P14 | NOT_STARTED |
| P17 | Release, manifests and future parity maintenance | P01, P16 | NOT_STARTED |

Caption and DVD lanes can progress in parallel after P02. P05 integrates completed P03/P04. P09 depends on real DVD A/V from P08; it cannot be closed by protocol-only tests. P15 is optional and is not a dependency of P16/P17.

## Detailed tasks

## P00 — Preserve the working build and publish the plan

Capture the exact supplied 3.2.4 package and create a durable, source-grounded backlog before runtime implementation.

**Dependencies:** None. **Scope:** required.
**References:** U01, U02, U03, B01 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `README.md`, `handoff.md`, `CHANGELOG.md`, `PROJECT_MANIFEST.sha256`.

- [x] **WP00-001 — Freeze baseline identity.** Record the input ZIP, original manifest, packaged WAR, installer and runtime-file hashes. Keep the existing root folder and runnable version.
  **Acceptance:** All original manifest entries verify; the standalone and packaged WARs and the installer WAR are identical.
  **Evidence:** `docs/parity/PLANNING_VERIFICATION.json` and the planning documents; no playback claim.

- [x] **WP00-002 — Pin the requested upstream.** Use the requested Vibe repository source/dev tree and record its current commit, actual Git tree and task revision. Do not substitute source/existing or the older OpenSageTV client.
  **Acceptance:** Reference lock identifies commit f1ba340e05fe18eaaba939253a933c3dfe6d6caf and tree 06a4c99958b8e07f6ad84b0c0d721a7b92e71918; task revision 71 and v0.5.95 checkpoint are recorded.
  **Evidence:** `docs/parity/PLANNING_VERIFICATION.json` and the planning documents; no playback claim.

- [x] **WP00-003 — Record confirmed gaps.** Inspect the local media bridge, settings, caption extraction, capability replies and project limitations against the reviewed Vibe sources.
  **Acceptance:** The matrix distinguishes missing code, partial implementation, retained behavior, platform adaptation and upstream-open work.
  **Evidence:** `docs/parity/PLANNING_VERIFICATION.json` and the planning documents; no playback claim.

- [x] **WP00-004 — Publish all phases.** Create permanent task IDs, dependencies, implementation boundaries and acceptance criteria, plus a test/fixture matrix.
  **Acceptance:** Every phase has explicit inputs, outputs, tasks and an exit gate; no implementation checkbox is marked complete.
  **Evidence:** `docs/parity/PLANNING_VERIFICATION.json` and the planning documents; no playback claim.

- [x] **WP00-005 — Update project navigation.** Link the plan from README, handoff, architecture and changelog while preserving all inherited contents and historical validation.
  **Acceptance:** The next developer can find the authoritative task list without relying on this chat.
  **Evidence:** `docs/parity/PLANNING_VERIFICATION.json` and the planning documents; no playback claim.

- [x] **WP00-006 — Verify the planning archive.** Regenerate the full-project manifest and compare every original entry, runtime resource, build script, test, WAR and installer to the input archive.
  **Acceptance:** Only declared planning documentation and the manifest change; all original files remain present; package-level checks pass.
  **Evidence:** `docs/parity/PLANNING_VERIFICATION.json` and the planning documents; no playback claim.

**Phase exit gate:** Planning documentation and archive integrity only. This phase is not a playback, subtitle, DVD, hardware or regression-test pass.

## P01 — Complete the upstream fix ledger and regression baseline

Turn “all fixes” into an auditable inventory and reproduce the working browser baseline before changing it.

**Dependencies:** P00. **Scope:** required.
**References:** U01, U02, U03, U15, B01 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `docs/PYTHON_PORT_AUDIT.md`, `docs/VIBE_STREAMING_PORT.md`, `docs/VALIDATION.md`, `tests/`, `scripts/validate.sh`.

- [x] **WP01-001 — Exhaustive change intake.** Enumerate the pinned source/dev feature families, completed upstream task IDs, all changelog fix entries and relevant regression tests. Create a per-fix ledger with upstream evidence, browser disposition and a WP task. The supplied matrix is a reviewed starting inventory, not a claim that every historical commit has been audited.
  **Acceptance:** Zero unclassified in-scope fix entries at the pinned checkpoint. Any exclusion, duplicate or superseded behavior has a written reason; unseen/local Android changes are not invented.
  **Status r3:** DONE — all 1,066 frozen top-level changelog entries were scope-reconciled with zero unclassified; the 248 normalized functional ledger supplies per-fix browser disposition/WP targets, and 102 relevant regression-source files were inventoried from 162 total test files.
  **Evidence:** `docs/parity/p01/LITERAL_CHANGELOG_RECONCILIATION.json`, `docs/parity/UPSTREAM_FIX_LEDGER.json`, `docs/parity/p01/UPSTREAM_RELEVANT_TESTS.json`.

- [x] **WP01-002 — Resolve history and open items.** Use current TASKS and runtime behavior when old changelog entries conflict. Preserve open upstream DVD-001, SEEK-001 and audio/device acceptance items separately from completed subfixes.
  **Acceptance:** DVB is not reintroduced inside CC1/CC2 from an older note; upstream-open gates are not converted into browser passes.
  **Status r2:** DONE. Acceptance met at the stated audit/test boundary; no new playback feature is claimed.
  **Evidence:** `docs/parity/p01/SUPERSESSION_DECISIONS.md`, `docs/parity/p01/UPSTREAM_TASK_CROSSWALK.json`.

- [x] **WP01-003 — Rerun untouched baseline.** Run the existing Java, Node, browser, FFmpeg and packaging suites against the exact original 3.2.4 source and retain unedited transcripts.
  **Acceptance:** Baseline failures or unavailable dependencies are recorded before patching. Inherited 235-scenario claims are not relabeled as new executions.
  **Status r2:** DONE. Acceptance met at the stated audit/test boundary; no new playback feature is claimed.
  **Evidence:** `docs/parity/p01/evidence/baseline-result.json`, `docs/parity/p01/evidence/baseline-validation.log`.

- [ ] **WP01-004 — Exercise actual vendor playback.** Load the real pinned mpegts.js and hls.js assets and run browser MSE playback against the plugin using generated media, not mocked player objects.
  **Acceptance:** At least one real MPEG-TS and one real HLS browser path starts, advances, pauses and resumes with observed appended media.
  **Status r3:** EXTERNAL_ACCEPTANCE_PENDING — exact release metadata for mpegts.js 1.8.0 and hls.js 1.7.3 is recorded, but this sandbox could not materialize/navigate the vendor bundles; actual plugin MSE start/advance/pause/resume remains unexecuted and is not replaced by mocks.
  **Evidence:** `docs/parity/p01/evidence/external-preflight.json`.

- [x] **WP01-005 — Register fixtures and expectations.** Create a durable fixture registry for broadcast captions, DVD menus/stills, multiple audio/subtitle streams, VBR seeks, discontinuities, short and long media. Capture hashes, expected tracks/languages/PIDs/pages and safe sharing rules.
  **Acceptance:** Each fixture identifies its origin, expected outputs and allowed test operations. Missing private recordings are marked unavailable rather than replaced with a fake pass.
  **Status r2:** DONE. Acceptance met at the stated audit/test boundary; no new playback feature is claimed.
  **Evidence:** `docs/parity/p01/FIXTURE_REGISTRY.json`, `docs/parity/p01/evidence/fixture-reference-result.json`.

- [x] **WP01-006 — Stock and extended server separation.** Record exact Sage.jar identity and STV for the stock gate. Keep a separately labeled extended-Core test profile only when available and approved.
  **Acceptance:** No test against a Vibe/custom Core is accepted as proof of stock compatibility; test controls use documented public APIs.
  **Status r3:** DONE — the supplied live server log identifies SageTV 9.2.10.1054, `Sage.jar` on the server classpath and active `/opt/sagetv/server/STVs/SageTV7/SageTV7.xml`; extended Core remains separately unapproved. Deployed-file SHA-256 values are unavailable and explicitly not invented.
  **Evidence:** `docs/parity/p01/SERVER_IDENTITY.json`, `docs/parity/p01/SERVER_PROFILES.json`.

- [x] **WP01-007 — Regression preservation checklist.** Carry first-FLIPBUFFER gating, crypto ACK ordering, type-5 recovery, the actual Vibe overlay, 3.2.4 video placement, minimal startup and reserve filling into mandatory regression gates.
  **Acceptance:** Each inherited behavior has a test identifier and a rollback baseline.
  **Status r2:** DONE. Acceptance met at the stated audit/test boundary; no new playback feature is claimed.
  **Evidence:** `docs/parity/p01/REGRESSION_PRESERVATION.md`, `docs/parity/p01/BASELINE_TEST_INVENTORY.json`.

- [x] **WP01-008 — Source delta rule.** Before implementation or release, compare the then-current upstream to the frozen pin and add newly discovered fixes under new IDs; do not silently move the baseline.
  **Acceptance:** Every pin update has an explicit delta/revision ledger and unchanged completed task history.
  **Status r2:** DONE. Acceptance met at the stated audit/test boundary; no new playback feature is claimed.
  **Evidence:** `docs/parity/p01/SOURCE_DELTA.md`, `docs/parity/p01/evidence/upstream-head.json`.

**Phase exit gate:** Reviewed fix ledger, reproducible baseline and fixture registry exist; genuine failures and unavailable live tests remain explicit.

## P02 — Shared playback, track and subtitle architecture

Create the missing per-session infrastructure so captions, bitmaps and DVD state do not become disconnected special cases.

**Dependencies:** P01. **Scope:** required.
**References:** U03, U05, U07, B02, B03, B04 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java`, `src/main/java/org/opensagetv/webplayer/MediaProbe.java`, `src/main/java/org/opensagetv/webplayer/StreamOptions.java`, `src/main/java/org/opensagetv/webplayer/HlsSessionManager.java`, `src/main/java/org/opensagetv/webplayer/StreamCommand.java`, `src/main/webapp/js/miniclient-captions.js`, `src/main/webapp/js/miniclient-streaming.js`.

- [x] **WP02-001 — Session and epoch ownership.** Specify one playback-session identity with separate seek/FLUSH/source generations, selected tracks, clock mapping and cancellation ownership. Every callback and request must reject an obsolete session.
  **Acceptance:** Two simultaneous browser clients, a replacement recording and a stale response cannot alter one another’s video or captions.
  **Status r4:** DONE — local architecture/contract acceptance met; decoder-specific visible playback remains P03/P04/P06.
  **Evidence:** `docs/parity/p02/ARCHITECTURE.md`, `docs/parity/p02/VALIDATION.md`.

- [x] **WP02-002 — Typed service inventory.** Extend probing beyond audio ordinal/index to preserve source PID, codec, language, service/page IDs, composition/ancillary page IDs, accessibility, forced/default flags and evidence quality. Use bounded in-stream discovery when ffprobe metadata is insufficient.
  **Acceptance:** Inventory distinguishes observed services, metadata-only candidates and unavailable tracks; CEA-608 language remains Unknown.
  **Status r4:** DONE — local architecture/contract acceptance met; decoder-specific visible playback remains P03/P04/P06.
  **Evidence:** `docs/parity/p02/ARCHITECTURE.md`, `docs/parity/p02/VALIDATION.md`.

- [x] **WP02-003 — Shared cue contract.** Define versioned text and straight-alpha bitmap cues with session/epoch, presentation timestamp, clear/end semantics, canvas dimensions, placement and bounded payload size.
  **Acceptance:** Text and bitmap cues can share scheduling but never share an incorrect decoder or render-owner identity.
  **Status r4:** DONE — local architecture/contract acceptance met; decoder-specific visible playback remains P03/P04/P06.
  **Evidence:** `docs/parity/p02/ARCHITECTURE.md`, `docs/parity/p02/VALIDATION.md`.

- [x] **WP02-004 — Source-preserving subtitle branch.** Tap/remux selected original subtitle streams before the current -sn/-dn removal and before video encoding. Design a bounded subtitle side channel for both MPEG-TS and HLS delivery without sending source video twice to the browser.
  **Acceptance:** A hardware-encoded video does not need to preserve subtitle packets to retain captions; subtitle failure does not silently stop A/V.
  **Status r4:** DONE — local architecture/contract acceptance met; decoder-specific visible playback remains P03/P04/P06.
  **Evidence:** `docs/parity/p02/ARCHITECTURE.md`, `docs/parity/p02/VALIDATION.md`.

- [x] **WP02-005 — Decoder placement decision.** Approve the architecture for Vibe’s platform-neutral Java Teletext/DVD core and a browser-compatible DVB/PGS decoder adapter. Compare a vetted Java implementation with a capability-probed libavcodec helper. Keep dependencies isolated and licensed; Android Bitmap/Media3 classes are not JVM drop-ins.
  **Acceptance:** ADR records actual decoder APIs, build/deployment dependencies, license obligations, data limits, cancellation and fallback. No unimplemented decoder is advertised.
  **Status r4:** DONE — local architecture/contract acceptance met; decoder-specific visible playback remains P03/P04/P06.
  **Evidence:** `docs/parity/p02/ARCHITECTURE.md`, `docs/parity/p02/ADR_DECODER_PLACEMENT.md`, `docs/parity/p02/VALIDATION.md`.

- [x] **WP02-006 — Clock mapping and bounded queues.** Map source PTS, FFmpeg output timestamps, browser currentTime, caption delay and recording-relative time explicitly. Define wrap/discontinuity handling, queue bounds and backpressure rather than arbitrary sleeps.
  **Acceptance:** Pause freezes presentation, time seeks clear old cues, captions keep advancing when native OSD is hidden, and read-ahead cannot render future cues early.
  **Status r4:** DONE — local architecture/contract acceptance met; decoder-specific visible playback remains P03/P04/P06.
  **Evidence:** `docs/parity/p02/ARCHITECTURE.md`, `docs/parity/p02/VALIDATION.md`.

- [x] **WP02-007 — Configuration migration.** Version the profile schema. Preserve bitrate/audio/display/client settings; keep current literal 608 CC1–CC4/708 service selections as explicit compatible choices instead of silently changing their meaning into virtual slots.
  **Acceptance:** Old 3.2.4 profiles load without resets, explicit Off survives, per-recording indices do not leak into other recordings, and effective values remain visible.
  **Status r4:** DONE — local architecture/contract acceptance met; decoder-specific visible playback remains P03/P04/P06.
  **Evidence:** `docs/parity/p02/ARCHITECTURE.md`, `docs/parity/p02/VALIDATION.md`.

- [x] **WP02-008 — Capability truth and resource limits.** Define actual support per source, decoder, transport and renderer, including maximum bitmap dimensions, total decoded bytes, active taps, readers and CPU time.
  **Acceptance:** Malformed subtitle data, unsupported codecs and exhausted quotas produce bounded errors with normal video remaining usable.
  **Status r4:** DONE — local architecture/contract acceptance met; decoder-specific visible playback remains P03/P04/P06.
  **Evidence:** `docs/parity/p02/ARCHITECTURE.md`, `docs/parity/p02/VALIDATION.md`.

**Phase exit gate:** Versioned contracts, session isolation, bounded resources, configuration migration and source-preservation tests pass before decoder UI switches are enabled.

## P03 — DVB Teletext subtitle decoding and continuous presentation

Port the Vibe Level-1 subtitle-page behavior, including the OSD-hidden freeze fix, with server-safe multi-client ownership.

**Dependencies:** P02. **Scope:** required.
**References:** U03, U05, U06, U01, B04 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MediaProbe.java`, `src/main/java/org/opensagetv/webplayer/HlsSessionManager.java`, `src/main/java/org/opensagetv/webplayer/StreamCommand.java`, `src/main/java/org/opensagetv/webplayer/CaptionsServlet.java`, `src/main/webapp/js/miniclient-captions.js`.

- [x] **WP03-001 — Port with per-session ownership.** Adapt TeletextSubtitleEngine into one instance per playback instead of copying its Android process-global active Session/LOCK. Retain provenance and isolate parser state from other browsers.
  **Acceptance:** Two different page-888 recordings render independently; stopping either leaves the other unchanged.
  **Status r5:** DONE at the local/generated acceptance boundary. Real-source claims are confined to WP03-008.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`.

- [x] **WP03-002 — Discover actual services.** Parse PAT/PMT and stream type 0x06/descriptor 0x56, accepting subtitle service types 2 and 5 with language, magazine and page. Bound tables and account for program changes.
  **Acceptance:** Breakfast/Classic Holby-style positive fixtures discover expected pages; a Taskmaster-style DVB-only fixture reports no Teletext.
  **Status r5:** DONE at the local/generated acceptance boundary. Real-source claims are confined to WP03-008.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`.

- [x] **WP03-003 — Reassemble transport and PES.** Retain fragment-safe TS/PES/data-unit framing, PTS and expected byte positions; handle supported TS packet sizes, continuity loss, duplicate packets, seek and source replacement.
  **Acceptance:** Split packets, partial headers, discontinuity and malformed lengths are exercised without stale page emission or unbounded memory.
  **Status r5:** DONE at the local/generated acceptance boundary. Real-source claims are confined to WP03-008.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`.

- [x] **WP03-004 — Match the proven decoder subset.** Port Hamming address/page/row decoding, erase-page handling and broadcast Latin G0/control behavior demonstrated by Vibe. Character-set, color or graphics features beyond that subset require separately identified enhancement tasks.
  **Acceptance:** Known packet vectors produce expected text and clear events. Documentation does not claim a complete interactive Teletext browser or all national sets.
  **Status r5:** DONE at the local/generated acceptance boundary. Real-source claims are confined to WP03-008.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`.

- [x] **WP03-005 — Independent presentation clock.** Deliver ordered cues from an independent lifecycle-bound clock while the native timeline is hidden. Serialize simultaneous clock drains and apply playback pause/delay correctly.
  **Acceptance:** A sustained OSD-hidden test continues to show changing cues; pause/resume and delayed-caption tests prove synchronization rather than packet counts alone.
  **Status r5:** DONE at the local/generated acceptance boundary. Real-source claims are confined to WP03-008.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`.

- [x] **WP03-006 — Track selection and restart safety.** Expose discovered Teletext pages through virtual CC profiles and an explicit service selector; re-resolve after late discovery. Clear pending text on seek/FLUSH/source replacement and honor Off.
  **Acceptance:** Rapid Off/CC1/CC2/Off, same-page reselection, late PMT discovery and seek-away/back show no frozen or duplicate caption.
  **Status r5:** DONE at the local/generated acceptance boundary. Real-source claims are confined to WP03-008.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`.

- [x] **WP03-007 — Preservation diagnostics.** Port a dormant, bounded Teletext preservation probe distinguishing no descriptor, descriptor-only, PES-only, data units and decoded presentation. Do not export decoded text or media payload.
  **Acceptance:** Diagnostics separately prove transport preservation and rendering, include cancellation, and remain inactive outside an explicit test.
  **Status r5:** DONE at the local/generated acceptance boundary. Real-source claims are confined to WP03-008.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`.

- [ ] **WP03-008 — Transport and field gate.** Test original-source, video-copy, hardware-transcode and software-fallback paths through MPEG-TS and HLS, followed by available unchanged UK recordings.
  **Acceptance:** At least one real-source visible/timing gate is required for a field PASS; generated-only results are labeled separately.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

**Phase exit gate:** Visible, synchronized Teletext pages survive OSD hide, pause, seek and track changes in supported delivery paths without affecting other clients.

## P04 — DVB bitmap subtitles

Add actual DVB bitmap decoding and alpha-aware rendering; do not approximate bitmap subtitles as Teletext or text.

**Dependencies:** P02. **Scope:** required.
**References:** U03, U07, U01, E01, B03, B04 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MediaProbe.java`, `src/main/java/org/opensagetv/webplayer/StreamCommand.java`, `src/main/java/org/opensagetv/webplayer/HlsSessionManager.java`, `src/main/java/org/opensagetv/webplayer/CaptionsServlet.java`, `src/main/webapp/js/miniclient-captions.js`, `src/main/webapp/css/miniclient.css`.

- [x] **WP04-001 — Discover DVB identity.** Keep the source PID and descriptor 0x59 service metadata, composition/ancillary page identities, language and hearing-impaired designation distinct from ffprobe array positions.
  **Acceptance:** Multiple services sharing a transport cannot be accidentally selected by a UI ordinal or CEA service number.
  **Status r6:** DONE at the local/generated acceptance boundary. Real-source visible/timing acceptance remains WP04-007.
  **Evidence:** `docs/parity/p04/REPORT.md`, `docs/parity/p04/VALIDATION.md`.

- [x] **WP04-002 — Decode complete display sets.** Implement the selected backend for page/region/object/CLUT/display-definition state, 2/4/8-bit pixels, transparency, version changes and partial PES. Preserve clear/end/timeout semantics.
  **Acceptance:** Known vectors verify exact pixels and alpha, display placement and clears; malformed dimensions/lengths are safely rejected.
  **Status r6:** DONE at the local/generated acceptance boundary. Real-source visible/timing acceptance remains WP04-007.
  **Evidence:** `docs/parity/p04/REPORT.md`, `docs/parity/p04/VALIDATION.md`.

- [x] **WP04-003 — Render on a separate bitmap surface.** Send timed bitmap cues or decoded rectangles through the shared contract; render above video with the same source-to-display transform, below browser controls as appropriate.
  **Acceptance:** PAL/HD, previews, Fill/Fit/Stretch, fullscreen and OSD transitions maintain subtitle geometry without changing video bounds.
  **Status r6:** DONE at the local/generated acceptance boundary. Real-source visible/timing acceptance remains WP04-007.
  **Evidence:** `docs/parity/p04/REPORT.md`, `docs/parity/p04/VALIDATION.md`.

- [x] **WP04-004 — Explicit DVB mode.** Add the single top-level DVB choice and a discovered-track selector. Keep DVB out of CC1/CC2 Auto/type mappings, matching the current Android policy.
  **Acceptance:** Selecting DVB persists after reopening settings and after asynchronous discovery; no Teletext renderer or event-225 text conversion is activated.
  **Status r6:** DONE at the local/generated acceptance boundary. Real-source visible/timing acceptance remains WP04-007.
  **Evidence:** `docs/parity/p04/REPORT.md`, `docs/parity/p04/VALIDATION.md`.

- [x] **WP04-005 — PID and disable control.** Provide the ordinary-video media-command-36/type-1 adapter and disabled-track handling without confusing it with DVD private-stream selection.
  **Acceptance:** PID selection, unknown PID, disable sentinel and late track discovery are tested independently of native DVD sessions.
  **Status r6:** DONE at the local/generated acceptance boundary. Real-source visible/timing acceptance remains WP04-007.
  **Evidence:** `docs/parity/p04/REPORT.md`, `docs/parity/p04/VALIDATION.md`.

- [x] **WP04-006 — Preserve through transcoding.** Keep bitmap subtitles outside the video encoder unless an explicit, separately tested burn-in mode is chosen. Capability-check any FFmpeg/library dependency.
  **Acceptance:** Hardware and software video encoding retain the same selected subtitle pixels; subtitle clearing does not wait for a new video segment.
  **Status r6:** DONE at the local/generated acceptance boundary. Real-source visible/timing acceptance remains WP04-007.
  **Evidence:** `docs/parity/p04/REPORT.md`, `docs/parity/p04/VALIDATION.md`.

- [ ] **WP04-007 — Lifetime and field validation.** Test Off/On, language change, seek, source PMT change, EOS and reconnect with bounded queues and no stale images. Validate available Taskmaster/UK source samples in addition to generated bitmaps.
  **Acceptance:** Visible captions and disappearance are recorded; “decoded bitmap count > 0” alone is not a PASS.
  **Status r6:** IMPLEMENTED_AWAITING_FIELD. Generated DVB vectors, source-PID selection, browser RGBA rendering, seek/FLUSH/EOS cleanup and retained regressions pass. No unchanged real UK DVB-bitmap recording was available, so visible/timing field acceptance is still open.
  **Evidence:** `docs/parity/p04/REPORT.md`, `docs/parity/p04/VALIDATION.md`, `docs/parity/p04/FIELD_ACCEPTANCE.md`.

**Phase exit gate:** DVB remains a distinct, selectable bitmap path with correct timing, transparency, position and cleanup across all supported transports.

## P05 — Stock SageTV CC authority and virtual CC1/CC2

Port current Vibe selection/ownership rules and stock callback compatibility without coupling CC to file/DVD subtitles.

**Dependencies:** P03, P04. **Scope:** required.
**References:** U03, U06, U07, U10, B02, B05 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MiniClientSession.java`, `src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java`, `src/main/java/org/opensagetv/webplayer/StreamOptions.java`, `src/main/webapp/miniclient.html`, `src/main/webapp/js/miniclient.js`, `src/main/webapp/js/miniclient-captions.js`.

- [x] **WP05-001 — Current caption-mode model.** Implement OFF, CC1, CC2, STV and DVB with two virtual CC profiles using Auto/Teletext/CEA-608/CEA-708 and discovered language/service choices. Keep literal legacy CEA choices addressable.
  **Acceptance:** DVB never resolves through a virtual CC slot; ordinary SRT/PGS/DVD language preference never enables broadcast CC.
  **Status r7:** COMPLETE_LOCAL. Implemented and covered by generated/vector/browser policy tests; no stock-server claim is required for this row.
  **Evidence:** `docs/parity/p05/REPORT.md`, `docs/parity/p05/VALIDATION.md`, `docs/parity/p05/FIELD_ACCEPTANCE.md`.

- [x] **WP05-002 — Evidence-based Auto resolution.** Use English-first, distinct-service preference where appropriate; distinguish real observed CEA from synthetic extractor placeholders. Do not invent CEA-608 language metadata.
  **Acceptance:** UK Teletext is not displaced by an empty synthetic CEA entry; two described services can resolve independently.
  **Status r7:** COMPLETE_LOCAL. Implemented and covered by generated/vector/browser policy tests; no stock-server claim is required for this row.
  **Evidence:** `docs/parity/p05/REPORT.md`, `docs/parity/p05/VALIDATION.md`, `docs/parity/p05/FIELD_ACCEPTANCE.md`.

- [ ] **WP05-003 — Legacy event-225 bridge.** Adapt LegacyExtenderCaptionBridge and TeletextCea608Bridge with serialized native writes, correct flags/time units and flush/reset ordering. Negotiate GFX_SUBTITLES/SUBTITLES_CALLBACKS only after the complete callback path exists.
  **Acceptance:** Stock STV Off/CC1/CC2 selection works through actual native callbacks; no private server patch or invented event is needed.
  **Status r7:** IMPLEMENTED_AWAITING_FIELD. Runtime path is implemented and locally/vector validated, but real stock SageTV/STV callback behavior remains to be field-verified.
  **Evidence:** `docs/parity/p05/REPORT.md`, `docs/parity/p05/VALIDATION.md`, `docs/parity/p05/FIELD_ACCEPTANCE.md`.

- [x] **WP05-004 — Single caption renderer.** Explicit local modes must reset/drain the legacy caption path before local presentation. STV mode must stop the local owner and respect actual server state when available.
  **Acceptance:** Switching STV -> CC1 -> DVB -> Off leaves only the intended caption renderer and no previously painted ghost text.
  **Status r7:** COMPLETE_LOCAL. Implemented and covered by generated/vector/browser policy tests; no stock-server claim is required for this row.
  **Evidence:** `docs/parity/p05/REPORT.md`, `docs/parity/p05/VALIDATION.md`, `docs/parity/p05/FIELD_ACCEPTANCE.md`.

- [ ] **WP05-005 — Stock-server authority fallback.** Handle stock servers that do not transmit VIDEO_CC_STATE; use the proven legacy callback contract rather than assuming the property always arrives. Keep ordinary DVB selection via command 36 separate.
  **Acceptance:** Both property-present and property-absent server peers pass; unsupported stock DVB control remains truthfully local-selectable.
  **Status r7:** IMPLEMENTED_AWAITING_FIELD. Runtime path is implemented and locally/vector validated, but real stock SageTV/STV callback behavior remains to be field-verified.
  **Evidence:** `docs/parity/p05/REPORT.md`, `docs/parity/p05/VALIDATION.md`, `docs/parity/p05/FIELD_ACCEPTANCE.md`.

- [ ] **WP05-006 — CEA regression and completeness audit.** Retest the current CEA-608/708 implementation against real service discovery, field/channel mapping, control/clear sequences, 708 windows and caption offset. Add missing relevant upstream behavior only with vectors and evidence.
  **Acceptance:** 608 CC1–CC4 and selected 708 services remain accessible; any styling/service limitation is documented rather than certified by a menu label.
  **Status r7:** IMPLEMENTED_AWAITING_FIELD. Runtime path is implemented and locally/vector validated, but real stock SageTV/STV callback behavior remains to be field-verified.
  **Evidence:** `docs/parity/p05/REPORT.md`, `docs/parity/p05/VALIDATION.md`, `docs/parity/p05/FIELD_ACCEPTANCE.md`.

- [x] **WP05-007 — UI and persistence.** Use the existing Vibe overlay caption icon to open compact nested settings with live inventory and effective-source labels. Preserve parent focus and explicit selection after track refresh.
  **Acceptance:** Keyboard/touch/remote navigation reaches every control; reopening the menu never silently changes DVB to STV or an unavailable service.
  **Status r7:** COMPLETE_LOCAL. Implemented and covered by generated/vector/browser policy tests; no stock-server claim is required for this row.
  **Evidence:** `docs/parity/p05/REPORT.md`, `docs/parity/p05/VALIDATION.md`, `docs/parity/p05/FIELD_ACCEPTANCE.md`.

**Phase exit gate:** Stock STV authority, virtual text-caption slots and explicit local DVB have mutually consistent, nonduplicating behavior with separate ordinary subtitles.

## P06 — Ordinary text, PGS and VobSub/file subtitles

Close the ordinary subtitle gap independently of broadcast CC and native DVD menu graphics.

**Dependencies:** P02. **Scope:** required.
**References:** U03, U07, E01, B03, B04 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MediaProbe.java`, `src/main/java/org/opensagetv/webplayer/MediaSourceFactory.java`, `src/main/java/org/opensagetv/webplayer/HlsSessionManager.java`, `src/main/java/org/opensagetv/webplayer/CaptionsServlet.java`, `src/main/webapp/js/miniclient-captions.js`, `src/main/webapp/miniclient.html`.

- [x] **WP06-001 — Embedded and sidecar discovery.** Discover supported embedded text/PGS/VobSub tracks and explicitly associated sidecar files using the active authorized MediaFile. Preserve language/title/default/forced metadata.
  **Acceptance:** Track identities survive refresh and unrelated same-basename files or paths cannot be read through a subtitle request.
  **Status r8:** PASS_LOCAL. Exact-basename embedded/sidecar discovery and unrelated-path rejection pass; metadata and IDX language/substream mapping are retained without exposing browser-selected paths.
  **Evidence:** `docs/parity/p06/REPORT.md`, `docs/parity/p06/VALIDATION.md`, `docs/parity/p06/FIELD_ACCEPTANCE.md`.

- [x] **WP06-002 — Text conversion and rendering.** Provide SRT/WebVTT and only the additional text formats confirmed by the ledger through safe browser cues; document any ASS/SSA style loss or unsupported variants. Never insert subtitle text as executable HTML.
  **Acceptance:** Unicode, overlapping cues, clear/end behavior, basic positioning and hostile markup tests pass.
  **Status r8:** PASS_LOCAL. Unicode, overlap, hostile-markup, basic WebVTT positioning, correct expiration and production-DOM textContent rendering pass.
  **Evidence:** `docs/parity/p06/REPORT.md`, `docs/parity/p06/VALIDATION.md`, `docs/parity/p06/FIELD_ACCEPTANCE.md`.

- [x] **WP06-003 — PGS bitmap adapter.** Implement composition/palette/object/clear handling with correct canvas and forced flags through the selected vetted backend. Treat PGS subtitles in files separately from Blu-ray navigation.
  **Acceptance:** A PGS-bearing MKV renders and clears correctly; this does not count as BDMV/BD-J playback support.
  **Status r8:** PASS_LOCAL. Sidecar SUP and generated PGS-bearing MKV decode through FFmpeg bitmap adaptation to LOCAL_FILE straight-alpha RGBA and clear correctly; no Blu-ray navigation claim is made.
  **Evidence:** `docs/parity/p06/REPORT.md`, `docs/parity/p06/VALIDATION.md`, `docs/parity/p06/FIELD_ACCEPTANCE.md`.

- [ ] **WP06-004 — VobSub and palette context.** Support selected file-based DVD/VobSub bitmaps with required IDX/extradata/IFO palette context where present. Do not infer a correct DVD palette solely from a concatenated VOB.
  **Acceptance:** Palette-present, palette-missing and forced-only cases have visible verified behavior or explicit unsupported errors.
  **Status r8:** IMPLEMENTED_AWAITING_FIELD. Embedded DVD-subtitle decoding passes and IDX/SUB discovery requires palette/context; a genuine external palette-bearing IDX/SUB sample is still required for palette-present/missing/forced-only field acceptance.
  **Evidence:** `docs/parity/p06/REPORT.md`, `docs/parity/p06/VALIDATION.md`, `docs/parity/p06/FIELD_ACCEPTANCE.md`.

- [x] **WP06-005 — Independent selection and scope.** Add ordinary subtitle Off/Auto/track, language, delay and forced-only policy separate from CC. Define when intentionally enabling file subtitles and broadcast captions may coexist.
  **Acceptance:** Changing CC does not alter ordinary subtitles; Off remains authoritative; session-only and saved defaults are distinguished.
  **Status r8:** PASS_LOCAL. Schema-6 Off/Auto/Selected-track, language, delay and forced-only policy is independent of CC; selected track is session-only and production DOM proves intentional coexistence on separate surfaces.
  **Evidence:** `docs/parity/p06/REPORT.md`, `docs/parity/p06/VALIDATION.md`, `docs/parity/p06/FIELD_ACCEPTANCE.md`.

- [x] **WP06-006 — Seek and lifecycle coverage.** Use the shared epoch clock, bounds and cancellation; validate video copy/transcode, pause, subtitle offsets, seek, file end and rapid switching.
  **Acceptance:** No stale text/bitmap or orphan extraction process after stop/reconnect; unsupported formats never appear as working choices.
  **Status r8:** PASS_LOCAL. Shared epoch invalidates both queues on seek/flush/source replacement, workers stop on teardown, offsets/end/overlap are covered, and long HLS/real FFmpeg regressions remain clean. Pipe/concat ordinary extraction fails explicitly unsupported rather than appearing functional.
  **Evidence:** `docs/parity/p06/REPORT.md`, `docs/parity/p06/VALIDATION.md`, `docs/parity/p06/FIELD_ACCEPTANCE.md`.

**Phase exit gate:** Supported ordinary subtitle types have real decoding/rendering and independent controls; remaining format limitations are explicit.

## P07 — Native DVD protocol and pushed-media session

Implement the native MiniDVDPlayer wire session that the current browser bridge does not consume.

**Dependencies:** P02. **Scope:** required.
**References:** U04, U08, U10, U03, B02, B05 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java`, `src/main/java/org/opensagetv/webplayer/MiniClientSession.java`, `src/main/java/org/opensagetv/webplayer/MiniClientSessionManager.java`, `src/main/java/org/opensagetv/webplayer/HlsSessionManager.java`.

- [ ] **WP07-001 — Capability and role gate.** Audit stock MiniClientSageRenderer/VideoFrame selection of DVD paths, including INPUT_DEVICES and MPEG-2 capability identity. Do not merely add DVD_REMOTE_NAV or TV while pushed-media handling is absent; retain browser mouse/touch through a tested adaptation.
  **Acceptance:** Captured stock negotiation selects the intended native DVD path only when the backend is available; ordinary SageMC input is unchanged.
  **Status r9:** IMPLEMENTED_AWAITING_FIELD. `MiniClientCapabilityPolicy` keeps the normal browser/MOUSE role by default and provides an explicit native-DVD opt-in that omits the stock role-selection `MOUSE` token while retaining TOUCH and all WebPlayer mouse/touch event APIs. A real unchanged stock-server negotiation capture is still required before closure.
  **Evidence:** `docs/parity/p07/REPORT.md`, `docs/parity/p07/VALIDATION.md`, `docs/parity/p07/FIELD_ACCEPTANCE.md`.

- [x] **WP07-002 — INIT without OPENURL.** Add a lazily initialized DVD session with correct prebuffer capacity; distinguish real MPEG-PS/metadata/drain polls from startup bandwidth probes.
  **Acceptance:** INIT -> GETMEDIATIME -> empty poll -> DVD metadata -> PUSHBUFFER works without OPENURL, while four synthetic 16 KiB probes never start a DVD player.
  **Status r9:** PASS_LOCAL. Lazy pending sessions return native capacity/media time before OPENURL, ignore non-PS 16 KiB bandwidth probes, and activate only from DVD metadata, drain semantics or MPEG-PS pack data.
  **Evidence:** `docs/parity/p07/REPORT.md`, `docs/parity/p07/VALIDATION.md`.

- [x] **WP07-003 — Mandatory reply contracts.** Implement commands 32–37 with bounds-checked payloads and the required four-byte replies on success, missing-player and error paths. Keep SEEK reply length and ordinary media behavior unchanged.
  **Acceptance:** A real-socket peer verifies exact reply lengths with malformed data and interleaved control; no media-socket desynchronization or 30-second stall.
  **Status r9:** PASS_LOCAL. Real loopback MiniClient type-1/type-0 sockets verify commands 32–37, malformed NEWCELL error, interleaved GETMEDIATIME, and zero-byte SEEK without shifting subsequent replies.
  **Evidence:** `docs/parity/p07/REPORT.md`, `docs/parity/p07/VALIDATION.md`.

- [x] **WP07-004 — Real pushed-byte ownership.** Introduce a bounded DVD MPEG-PS input queue/spool and producer-consumer backpressure. Stop treating accepted media bytes as a capacity-only probe.
  **Acceptance:** Every accepted byte is accounted for exactly once; full buffers apply truthful backpressure and memory/disk use stay bounded.
  **Status r9:** PASS_LOCAL. The 4 MiB generation-aware ring accepts whole PUSH payloads or fails without partial writes, blocks only to a bounded deadline, and records exact lifetime/epoch accepted/read counts.
  **Evidence:** `docs/parity/p07/REPORT.md`, `docs/parity/p07/VALIDATION.md`.

- [x] **WP07-005 — Transient segment EOS and drain.** Implement 0x80 segment end and 0x100 drain polling with -2 only at the appropriate decoder/input boundary. Never declare a title complete simply because a DVD menu/cell ended.
  **Acceptance:** Short menu cells advance, long buffered titles are not discarded, and drain state cannot deadlock against the producer.
  **Status r9:** PASS_LOCAL. `0x80` marks only the current reusable segment generation; the generated consumer must consume input, observe segment EOF and mark the same decoder generation drained before a normal post-data `0x100` returns `-2`.
  **Evidence:** `docs/parity/p07/REPORT.md`, `docs/parity/p07/VALIDATION.md`.

- [x] **WP07-006 — FLUSH epochs and cancellation.** Ignore the harmless pre-data DVD initialization flush; post-data FLUSH creates a new byte/decoder generation while retaining required playback intent. Scope drain one-shot state to generation, not byte count.
  **Acceptance:** Two equal-sized consecutive menu cells work; stale reader callbacks and Stop/DEINIT cannot revive a retired DVD generation.
  **Status r9:** PASS_LOCAL. Pre-data FLUSH is a no-op; post-data FLUSH increments generation, cancels blocked old readers, resets epoch accounting and accepts an equal-sized replacement cell as new input.
  **Evidence:** `docs/parity/p07/REPORT.md`, `docs/parity/p07/VALIDATION.md`.

- [x] **WP07-007 — Clock and format commands.** Define STC/NEWCELL interpretation and explicit DVD clock conversion before accepting them. Preserve DVD four-byte GETMEDIATIME/PUSHBUFFER responses independently of optional ordinary-Push telemetry.
  **Acceptance:** Native time and all later command replies remain aligned across cell, seek and format changes.
  **Status r9:** PASS_LOCAL. NEWCELL signed offsets and unsigned STC wire values are converted from 45 kHz to 90 kHz with 33-bit wrap handling; a 45,000 STC reports 1,000 ms and socket replies remain four-byte aligned.
  **Evidence:** `docs/parity/p07/REPORT.md`, `docs/parity/p07/VALIDATION.md`.

- [x] **WP07-008 — Failure isolation.** Keep protocol readers responsive; run blocking FFmpeg startup/cleanup outside native reply locks with bounded deadlines and visible errors.
  **Acceptance:** Encoder startup failure leaves a valid GFX connection and a truthful failed playback state, not a forged successful DVD capability.
  **Status r9:** PASS_LOCAL_AT_P07_BOUNDARY. P07 does not start FFmpeg/decoder work in the native media reply path. Input backpressure has a bounded timeout and a failed write never partially accepts data; inherited GFX startup, recovery and authentication regressions remain clean. P08 owns the actual converter-startup failure gate when it attaches the MPEG-PS consumer.
  **Evidence:** `docs/parity/p07/REPORT.md`, `docs/parity/p07/VALIDATION.md`.

**Phase exit gate:** The local native DVD wire/input session accepts real bytes and controls, handles mandatory replies, drains and resets without changing GFX startup/authentication invariants. Stock role-negotiation capture remains WP07-001 field acceptance; visible DVD media is explicitly P08.

## P08 — DVD MPEG-PS A/V conversion, timing and still menus

Make native DVD media actually play in the browser while preserving VM navigation and original timing.

**Dependencies:** P07. **Scope:** required.
**References:** U04, U09, U11, U12, B02, B04 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/HlsSessionManager.java`, `src/main/java/org/opensagetv/webplayer/StreamCommand.java`, `src/main/java/org/opensagetv/webplayer/MediaProbe.java`, `src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java`, `src/main/webapp/js/miniclient-playback.js`.

- [ ] **WP08-001 — PS/PES boundary handling.** Feed the native PS input to the browser-compatible conversion path without concatenating unrelated titles/VOBs. Handle system/private-stream headers and partial packets safely.
  **Acceptance:** Both generated menu and moving-title PS streams produce actual decodable browser media.
  **Status r10:** PASS_LOCAL. P08 implementation is covered by real generated MPEG-PS/FFmpeg runtime tests plus production browser tests; live stock-server/disc and physical GPU evidence remain environment-unavailable and are not claimed.
  **Evidence:** `docs/parity/p08/REPORT.md`, `docs/parity/p08/VALIDATION.md`, `docs/parity/p08/FIELD_ACCEPTANCE.md`.

- [ ] **WP08-002 — Physical audio-stream identity.** Map the DVD wire audio code to its physical AC-3/LPCM/MPEG/DTS substream according to supported decoder capability, not the UI track ordinal; never mix private streams into one decoder.
  **Acceptance:** A multi-language disc changes to the requested authored audio stream; unsupported formats fail visibly rather than select the wrong language.
  **Status r10:** PASS_LOCAL. P08 implementation is covered by real generated MPEG-PS/FFmpeg runtime tests plus production browser tests; live stock-server/disc and physical GPU evidence remain environment-unavailable and are not claimed.
  **Evidence:** `docs/parity/p08/REPORT.md`, `docs/parity/p08/VALIDATION.md`, `docs/parity/p08/FIELD_ACCEPTANCE.md`.

- [ ] **WP08-003 — Browser-compatible output.** Use actual H.264/AAC conversion with the existing encoder policy; measure and preserve MPEG-TS/HLS latency and discontinuity behavior. Keep decoding, filtering and encoding acceleration reported separately.
  **Acceptance:** Software and an available physical hardware encoder each produce playable output; unavailable vendors remain untested.
  **Status r10:** PASS_LOCAL. P08 implementation is covered by real generated MPEG-PS/FFmpeg runtime tests plus production browser tests; live stock-server/disc and physical GPU evidence remain environment-unavailable and are not claimed.
  **Evidence:** `docs/parity/p08/REPORT.md`, `docs/parity/p08/VALIDATION.md`, `docs/parity/p08/FIELD_ACCEPTANCE.md`.

- [ ] **WP08-004 — DVD presentation clock.** Port relevant PTS wrap/cell-offset/discontinuity logic; keep SPU-only packets from consuming a pending A/V rebase. Map browser output time back to the server DVD clock.
  **Acceptance:** Cell transitions, PAL/NTSC, seeks and audio changes do not reset the title timeline to zero or accumulate drift.
  **Status r10:** PASS_LOCAL. P08 implementation is covered by real generated MPEG-PS/FFmpeg runtime tests plus production browser tests; live stock-server/disc and physical GPU evidence remain environment-unavailable and are not claimed.
  **Evidence:** `docs/parity/p08/REPORT.md`, `docs/parity/p08/VALIDATION.md`, `docs/parity/p08/FIELD_ACCEPTANCE.md`.

- [ ] **WP08-005 — Sparse timestamps and cadence.** Characterize FFmpeg behavior for sparse DVD MPEG-2 picture timestamps, frame rates and interlace/telecine; adapt the upstream behavior only when applicable to this conversion backend.
  **Acceptance:** A repeated authored cadence fixture and sustained motion show correct speed; Android-specific Media3 timestamp patches are not copied blindly into FFmpeg.
  **Status r10:** PASS_LOCAL. P08 implementation is covered by real generated MPEG-PS/FFmpeg runtime tests plus production browser tests; live stock-server/disc and physical GPU evidence remain environment-unavailable and are not claimed.
  **Evidence:** `docs/parity/p08/REPORT.md`, `docs/parity/p08/VALIDATION.md`, `docs/parity/p08/FIELD_ACCEPTANCE.md`.

- [ ] **WP08-006 — Still-frame menus.** Preserve authored still backgrounds, audio and button/subpicture updates even for one-picture menus and tiny audio-only or video-first cells. Provide an explicit still presentation state when the browser media queue ends.
  **Acceptance:** A menu remains visible and interactive with or without ongoing audio; the reserve target cannot prevent short cells from starting or draining.
  **Status r10:** PASS_LOCAL. P08 implementation is covered by real generated MPEG-PS/FFmpeg runtime tests plus production browser tests; live stock-server/disc and physical GPU evidence remain environment-unavailable and are not claimed.
  **Evidence:** `docs/parity/p08/REPORT.md`, `docs/parity/p08/VALIDATION.md`, `docs/parity/p08/FIELD_ACCEPTANCE.md`.

- [ ] **WP08-007 — Interactive buffering profile.** Separate DVD menu/drain buffering from long-title reserve filling. Evaluate the upstream 5-second/12-second Android values as reference evidence, not automatically optimal browser defaults.
  **Acceptance:** Menus start promptly, moving titles build a reserve, and pause/VM waits do not trigger blind playback restarts.
  **Status r10:** PASS_LOCAL. P08 implementation is covered by real generated MPEG-PS/FFmpeg runtime tests plus production browser tests; live stock-server/disc and physical GPU evidence remain environment-unavailable and are not claimed.
  **Evidence:** `docs/parity/p08/REPORT.md`, `docs/parity/p08/VALIDATION.md`, `docs/parity/p08/FIELD_ACCEPTANCE.md`.

- [ ] **WP08-008 — Seek/reload/teardown.** Keep server-owned disc seeks and local display refresh distinct; preserve audio selection, logical clock, menu state and pause intent where appropriate.
  **Acceptance:** Title seeks, chapter transitions, Stop, replay and repeated disconnects leave no orphan FFmpeg process, spool or clock pump.
  **Status r10:** PASS_LOCAL. P08 implementation is covered by real generated MPEG-PS/FFmpeg runtime tests plus production browser tests; live stock-server/disc and physical GPU evidence remain environment-unavailable and are not claimed.
  **Evidence:** `docs/parity/p08/REPORT.md`, `docs/parity/p08/VALIDATION.md`, `docs/parity/p08/FIELD_ACCEPTANCE.md`.

**Phase exit gate:** Real DVD menu/title PS is converted and presented in the browser with correct audio, clock, cadence, still behavior and bounded streaming; protocol-only success is insufficient.

## P09 — DVD SPU subtitles, highlights and interactive navigation

Port authored DVD subpictures and menu interaction, not just main-feature video.

**Dependencies:** P08. **Scope:** required.
**References:** U08, U09, U12, U03 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java`, `src/main/webapp/js/miniclient.js`, `src/main/webapp/js/miniclient-core.js`, `src/main/webapp/css/miniclient.css`.

- [x] **WP09-001 — Platform-neutral SPU core.** Adapt DvdSpuAssembler, DvdSpuDecoder, compositor, highlight, palette and related bounded core types with preserved license/source provenance. Replace Android Bitmap publishing with the shared browser bitmap contract.
  **Acceptance:** The upstream pure-core SPU vectors plus browser-adapter tests pass without adding Android runtime dependencies.
  **Status r11:** PASS_LOCAL.
  **Evidence:** `docs/parity/p09/REPORT.md`, `docs/parity/p09/VALIDATION.md`.

- [x] **WP09-002 — Full SPU control behavior.** Support fragment reassembly, RLE fields, display/stop/forced commands, authored coordinates, CLUT, alpha, CHG_COLCON region palettes and selected/activated button colors.
  **Acceptance:** Pixel/hash vectors cover normal and region palettes, truncated input and control loops; clears do not resurrect an old frame.
  **Status r11:** PASS_LOCAL.
  **Evidence:** `docs/parity/p09/REPORT.md`, `docs/parity/p09/VALIDATION.md`.

- [x] **WP09-003 — Correct subtitle enable semantics.** Handle DVD physical SPU selection and disable flags independently of ordinary-video DVB PID commands and broadcast Off. Permit authored menu highlights when title subtitles are off.
  **Acceptance:** Menu navigation stays visible with title subtitles disabled; a forced movie SPU is not shown against an authoritative Off policy unless an explicitly supported policy says otherwise.
  **Status r11:** PASS_LOCAL.
  **Evidence:** `docs/parity/p09/REPORT.md`, `docs/parity/p09/VALIDATION.md`.

- [x] **WP09-004 — Generation-correct scheduling.** Retain queued future SPU events across repeated same-CLUT/highlight commands; invalidate only on genuine session/stream/epoch changes. Recompose the currently presented frame, not a future buffered subtitle.
  **Acceptance:** Repeated CLUT and rapid navigation cannot cancel every pending subtitle, display a future menu or replay a cleared image.
  **Status r11:** PASS_LOCAL.
  **Evidence:** `docs/parity/p09/REPORT.md`, `docs/parity/p09/VALIDATION.md`.

- [x] **WP09-005 — Aspect and display alignment.** Map 720-wide PAL/NTSC subtitle canvases, sample/display aspect, preview and Fill/Fit/Stretch transforms to the same visible video rectangle.
  **Acceptance:** Highlight rectangles and authored buttons align in all supported display modes without shrinking the video when the OSD hides.
  **Status r11:** PASS_LOCAL.
  **Evidence:** `docs/parity/p09/REPORT.md`, `docs/parity/p09/VALIDATION.md`.

- [x] **WP09-006 — Remote and mouse interaction.** Send DVD D-pad/Select/Back/Menu/chapter actions to the server VM using supported native/public interfaces. Map pointer coordinates only through a verified DVD hit-test contract; unsupported pointer selection must not pretend to work.
  **Acceptance:** Root, language submenu, title menu, chapter and return transitions work; the long-press browser overlay still opens without clicking an authored button underneath.
  **Status r11:** PASS_LOCAL.
  **Evidence:** `docs/parity/p09/REPORT.md`, `docs/parity/p09/VALIDATION.md`.

- [ ] **WP09-007 — Authored and real-disc acceptance.** Use an authored fixture with multiple menu cells, equal-sized transitions, languages, chapters and SPUs, then available representative menu and menu-less discs.
  **Acceptance:** Visible menus/highlights plus advancing A/V and successful Stop are required; playing the largest VOB or main title alone cannot close this phase.
  **Status r11:** IMPLEMENTED_AWAITING_FIELD. Authored/representative real-disc acceptance is not available in this environment.
  **Evidence:** `docs/parity/p09/FIELD_ACCEPTANCE.md`.

**Phase exit gate:** Interactive root/submenu/title navigation, highlights, title subtitles, language/chapter selection and teardown work through the actual browser path.

## P10 — Disc sources, title selection and library metadata

Handle supported disc source forms and timeline metadata without hiding server filesystem or mounting failures.

**Dependencies:** P08, P09. **Scope:** required.
**References:** U03, U13, E02, B02, B06 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MediaSourceFactory.java`, `src/main/java/org/opensagetv/webplayer/SageApiBridge.java`, `src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java`, `src/main/java/org/opensagetv/webplayer/MediaProbe.java`, `src/main/webapp/miniclient.html`.

- [x] **WP10-001 — Disc source taxonomy.** Separate VIDEO_TS directories, a parent folder, already-mounted images, ISO files, individual VOBs, imported MKVs and BDMV. Resolve paths through authorized MediaFiles, not arbitrary user-supplied paths.
  **Acceptance:** Each input form has a documented supported route or exact unsupported stage; a VOB/MKV is not mislabeled a navigable DVD.
  **Status r12:** PASS_LOCAL. Source taxonomy is implemented for VIDEO_TS, parent/mounted roots, ISO, drive, VOB, MKV and BDMV. Only SageTV-authorized MediaFiles are inspected; VOB/MKV remain ordinary media.
  **Evidence:** `docs/parity/p10/REPORT.md`, `docs/parity/p10/VALIDATION.md`.
- [x] **WP10-002 — Path and duplicate-name safety.** Audit percent decoding, spaces, plus signs, Unicode, case sensitivity, UNC/mapped roots and basename fallback. Require an unambiguous match rather than selecting the first same-name recording.
  **Acceptance:** Two identically named files in different folders cannot be confused; nonexistent paths never launch unrelated media.
  **Status r12:** PASS_LOCAL. Literal plus/percent/Unicode decoding and stale-root suffix matching are tested. Basename-only fallback is forbidden and duplicate-name ambiguity fails closed.
  **Evidence:** `docs/parity/p10/REPORT.md`, `docs/parity/p10/VALIDATION.md`.
- [x] **WP10-003 — ISO mount boundary.** Detect and report native stock-server ISO mount prerequisites separately from browser/DVD decoding. Do not add sudo/root or silently change container privileges. Evaluate a plugin-owned read-only route only as an explicit architecture task.
  **Acceptance:** The supplied earlier ISO-mount failure is not hidden behind a generic decoder error; VIDEO_TS remains independently testable.
  **Status r12:** PASS_LOCAL. ISO is reported at the stock-server mount prerequisite stage. The plugin does not mount, elevate, call sudo/root or alter container privileges; VIDEO_TS remains independently identifiable.
  **Evidence:** `docs/parity/p10/REPORT.md`, `docs/parity/p10/VALIDATION.md`.
- [ ] **WP10-004 — Title and chapter metadata.** Use disc VM/IFO-derived identity and public SageTV APIs where supported; distinguish title duration, chapter time and recording time. Do not equate title 1 or the largest VOB with the main feature.
  **Acceptance:** A multi-title fixture with extras and non-first main feature selects and reports the intended title/chapter.
  **Status r12:** IMPLEMENTED_AWAITING_FIELD. Explicit-title dvdvideo parsing and title-relative chapter/timeline semantics are implemented. A representative multi-title authored disc with a non-first intended feature is still required for field acceptance.
  **Evidence:** `docs/parity/p10/REPORT.md`, `docs/parity/p10/VALIDATION.md`, `docs/parity/p10/FIELD_ACCEPTANCE.md`.
- [x] **WP10-005 — Movie-only compatibility option.** Design an explicitly labeled main-feature/title conversion option only after actual FFmpeg dvdvideo/library capability probing. Keep it separate from native-menu playback and optional Core transform negotiation.
  **Acceptance:** Movie-only mode never claims menu parity; absent libraries produce a clear capability message rather than silent fallback to concatenated VOBs.
  **Status r12:** PASS_LOCAL. Runtime probing requires FFmpeg dvdvideo plus libdvdread/libdvdnav. Movie-only planning requires an explicit successfully probed title and explicitly reports interactiveMenus=false and automaticMainFeatureSelection=false.
  **Evidence:** `docs/parity/p10/REPORT.md`, `docs/parity/p10/VALIDATION.md`.
- [x] **WP10-006 — Invalid imported metadata.** Detect impossible duration/stream metadata without modifying the library automatically. Offer a narrow documented repair path with user approval and backup requirements; broad reindex is not a playback side effect.
  **Acceptance:** A 1 ms imported duration is reported as server metadata, not treated as the user’s intended seek; history and recordings stay untouched.
  **Status r12:** PASS_LOCAL. Read-only metadata health detects 1 ms/implausible durations and missing stream metadata. It never repairs, seeks from, reindexes or rewrites SageTV metadata automatically.
  **Evidence:** `docs/parity/p10/REPORT.md`, `docs/parity/p10/VALIDATION.md`.
- [x] **WP10-007 — Disc scope and unsupported media.** Keep Blu-ray/BDMV, BD-J, encrypted-disc handling and secure/DRM playback separate from proven DVD work. Carry upstream-uncommissioned items as pending scope, not completed fixes.
  **Acceptance:** The support table explicitly distinguishes DVD Native, movie-only, ISO environment prerequisites and out-of-scope disc/DRM features.
  **Status r12:** PASS_LOCAL. Support scope explicitly separates native DVD, movie-only explicit-title compatibility, ISO environment prerequisites, ordinary VOB/MKV, and unsupported Blu-ray/BDMV/BD-J/encrypted/DRM media.
  **Evidence:** `docs/parity/p10/REPORT.md`, `docs/parity/p10/VALIDATION.md`.
**Phase exit gate:** Disc input and metadata handling is deterministic and safe; environment limitations are explicit, and movie-only conversion is not substituted for interactive DVD.

## P11 — Seek, Comskip, growing files and sustained buffering

Port relevant timeline/recovery fixes and retain quick startup followed by continuous reserve filling.

**Dependencies:** P02. **Scope:** required.
**References:** U01, U02, U03, U04, B02, B07 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java`, `src/main/java/org/opensagetv/webplayer/HlsSessionManager.java`, `src/main/java/org/opensagetv/webplayer/RecordingFollower.java`, `src/main/java/org/opensagetv/webplayer/SageMediaServerSource.java`, `src/main/java/org/opensagetv/webplayer/ContinuousFilePump.java`, `src/main/webapp/js/miniclient-playback.js`, `src/main/webapp/js/commercial-skip.js`.

- [x] **WP11-001 — Eliminate guessed time-to-byte seeks.** Replace the inherited proportional-byte fallback with a validated timestamp/index seek strategy for VBR and remote/pipe sources. Explicitly retain keyframe limitations where video is copied.
  **Acceptance:** Large forward/backward targets and tail seeks land within a documented measured tolerance; no guessed offset is reported as frame-accurate.
  **Status r14:** PASS_LOCAL. Pipe MPEG-TS seeks use observed video PES PTS anchors plus a bounded post-input FFmpeg residual; non-indexable sources feed from byte zero instead of a proportional byte guess.
  **Evidence:** `tests/P11TimelineSmoke.java`, `docs/parity/p11/REPORT.md`, `docs/parity/p11/VALIDATION.md`.
- [x] **WP11-002 — Stable seek and FLUSH timeline.** Carry the last proven time during bounded transitional states, and apply the mux-end versus epoch-start correction only to actual ordinary-Push paths that need it.
  **Acceptance:** Rapid skips/Comskip cannot target zero or repeatedly back up; DVD and direct-source clocks do not receive an irrelevant Push offset.
  **Status r14:** PASS_LOCAL. Browser/server same-generation clock regressions are bounded at the last proven time; explicit new seek generations reset the floor, and DVD SEEK remains stock-VM-owned.
  **Evidence:** `tests/test-miniclient-playback.js`, `src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java`, `docs/parity/p11/VALIDATION.md`.
- [x] **WP11-003 — Public server seek ownership.** Separate absolute recording seeks, VM chapter/navigation and browser-local buffered time. Use context-specific public Seek/Watch APIs when server ownership is required.
  **Acceptance:** The intended MiniClient alone moves; no private commissioning events 230–233, wrong UI context or duplicate EDL skip is emitted.
  **Status r14:** PASS_LOCAL. Ordinary MiniClient seek remains server command 29, DVD navigation remains the server VM, simple-player seeks remain local/direct, and Comskip serializes one logical skip per EDL boundary. No private 230–233 event path exists.
  **Evidence:** `src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java`, `src/main/webapp/js/commercial-skip.js`, `tests/test-commercial-skip.js`.
- [x] **WP11-004 — Growing and completed EOF.** Use available server recording state, segment growth and bounded tail rechecks. Handle real live program/PID/format transitions and exact completed EOF.
  **Acceptance:** Temporary EOF does not end a recording still being written; completed files do not buffer forever; old source state cannot leak into the next program.
  **Status r14:** PASS_LOCAL. RecordingFollower retains active EOF waits/segment rollover, adds a bounded final-tail settle after recording completion, and rejects source shrink/replacement.
  **Evidence:** `tests/HlsRuntimeSmoke.java`, `tests/P11TimelineSmoke.java`, `docs/parity/p11/VALIDATION.md`.
- [x] **WP11-005 — Quick start then reserve.** Preserve minimal first-play delay with separate 30–600-second configured reserve targets. Validate continuous TS byte-resume and HLS refill while playing and paused, accounting for browser quota and the real live edge.
  **Acceptance:** An actual browser sustains playback well beyond the initial buffered window; pausing fills available reserve without resuming itself.
  **Status r14:** PASS_LOCAL. Startup remains one/small segments then expands to the configured reserve; paused refill, quota reduction, loader wakeup and 240-second producer continuation tests pass.
  **Evidence:** `tests/test-miniclient-playback.js`, `tests/HlsRuntimeSmoke.java`, `docs/parity/p11/VALIDATION.md`.
- [x] **WP11-006 — Progress-aware recovery.** Classify source/read, probe, parser, browser decode, encoder, audio, quota and transport errors. Do not restart while useful reads or decoding progress continue; use bounded, reason-specific retries and same-position fallback.
  **Acceptance:** A slow or cold source is not repeatedly discarded; malformed media and genuine stalled producers still fail within documented bounds.
  **Status r14:** PASS_LOCAL. Recovery records a reason class, keeps the six-attempt budget, and requires both decode and source-read stagnation before producer restart. Existing browser/network/media local retries remain bounded.
  **Evidence:** `src/main/webapp/js/miniclient-playback.js`, `tests/test-miniclient-playback.js`.
- [x] **WP11-007 — Cache and spool lifecycle.** Scope probe/seek hints to exact file identity and invalidate on growth/changes. Bound per-session and global output, active readers and idle producers; preserve byte-resume contracts while cleaning retired epochs.
  **Acceptance:** Long recordings, concurrent clients and low disk cannot consume unbounded resources or serve overwritten offsets as original bytes.
  **Status r14:** PASS_LOCAL. PTS seek evidence is session-local, source shrink invalidates byte identity, and existing per-session bytes/global stream count/disk-space/idle reaper/delayed-retirement limits remain enforced.
  **Evidence:** `tests/P11TimelineSmoke.java`, `src/main/java/org/opensagetv/webplayer/HlsSessionManager.java`, `src/main/java/org/opensagetv/webplayer/ContinuousFilePump.java`.
- [ ] **WP11-008 — Long-run and changeover gate.** Exercise a three-hour-plus/5.5-hour-class recording, rapid control stress, late seeks, growing-to-complete transition and repeated recording/channel changes.
  **Acceptance:** Timeline/OSD clears normally, reserve refills, no clock drift loop or orphan work occurs; unavailable multi-hour live evidence remains pending.
  **Status r14:** IMPLEMENTED_AWAITING_FIELD. The generated 240-second sustained producer/browser-reserve gate and rapid-control unit regressions pass, but this environment does not contain a representative 3h+/5.5h real SageTV recording/changeover run.
  **Evidence:** `tests/HlsRuntimeSmoke.java`, `tests/test-miniclient-playback.js`, `docs/parity/p11/FIELD_ACCEPTANCE.md`.
**Phase exit gate:** Measured seeking, source growth/EOF and sustained buffering remain correct under real playback and control stress, not just mocked buffer snapshots.

## P12 — Audio, playback controls and quality-policy parity

Adapt applicable Android playback settings and safety fixes to the browser/FFmpeg backend without advertising Android-only APIs.

**Dependencies:** P02, P11. **Scope:** required.
**References:** U01, U02, U03, B03, B04 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/StreamOptions.java`, `src/main/java/org/opensagetv/webplayer/StreamPlan.java`, `src/main/java/org/opensagetv/webplayer/StreamCommand.java`, `src/main/java/org/opensagetv/webplayer/TranscoderManager.java`, `src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java`, `src/main/webapp/js/miniclient-streaming.js`, `src/main/webapp/miniclient.html`.

- [ ] **WP12-001 — Primary audio and source changes.** Preserve stream identity, prefer the requested primary-language audio over commentary/narration when evidence supports that policy, and safely handle late discovery, PMT changes and channel layout transitions.
  **Acceptance:** A leading NAR track does not override an explicit primary choice; absent desired audio is reported and fallback follows saved policy.
  **Status P12:** IMPLEMENTED_AWAITING_FIELD.
  **Evidence:** `docs/parity/p12/REPORT.md`, `docs/parity/p12/VALIDATION.md`, `docs/parity/p12/FIELD_ACCEPTANCE.md`.

- [ ] **WP12-002 — Output and delay scope.** Port applicable session/default scope, signed delay and effective-output reporting. Evaluate the Android -4000..+4000 ms/25 ms user controls without pretending browser output provides AudioTrack/HDMI APIs.
  **Acceptance:** Positive delay means audio later; both signs and reset are demonstrated against a synchronized fixture; settings say when a restart/re-anchor is required.
  **Status P12:** PASS_LOCAL.
  **Evidence:** `docs/parity/p12/REPORT.md`, `docs/parity/p12/VALIDATION.md`, `docs/parity/p12/FIELD_ACCEPTANCE.md`.

- [ ] **WP12-003 — Debounced live changes.** Apply rapid offset/quality/audio-track changes through one cancelable, same-position operation after settling; preserve pause, chosen captions and the native GFX session.
  **Acceptance:** Rapid slider input cannot spawn many encoders, deadlock readers or let an obsolete change win.
  **Status P12:** PASS_LOCAL.
  **Evidence:** `docs/parity/p12/REPORT.md`, `docs/parity/p12/VALIDATION.md`, `docs/parity/p12/FIELD_ACCEPTANCE.md`.

- [ ] **WP12-004 — Encoder/decode truth.** Report requested versus effective decoder/filter/encoder and fallback reason. Preserve strict no-fallback/copy semantics and separate server GPU encoding from browser decoding proof.
  **Acceptance:** Presence of a codec name or low CPU is not accepted as hardware evidence; currently CPU-decode/filter + hardware-encode is not labeled end-to-end GPU.
  **Status P12:** PASS_LOCAL.
  **Evidence:** `docs/parity/p12/REPORT.md`, `docs/parity/p12/VALIDATION.md`, `docs/parity/p12/FIELD_ACCEPTANCE.md`.

- [ ] **WP12-005 — Remaining quality controls.** Audit Android bitrate/FPS/resolution/keyframe/B-frame/deinterlace and remux preferences against the FFmpeg plan. Add only supported knobs with valid cross-field constraints and migration.
  **Acceptance:** Every enabled control changes verified command/runtime behavior; copied video clearly ignores encoding bitrate/GOP settings.
  **Status P12:** PASS_LOCAL.
  **Evidence:** `docs/parity/p12/REPORT.md`, `docs/parity/p12/VALIDATION.md`, `docs/parity/p12/FIELD_ACCEPTANCE.md`.

- [ ] **WP12-006 — Rate, frame-step and transport controls.** Reconcile existing 0.25–4x browser rate handling with upstream functional capability names and actual supported behavior. Add frame-step/seek-scan only with a real backend contract; do not simulate unsupported reverse playback as accepted.
  **Acceptance:** Return values match effective action; server and local controls do not fight, and DVD navigation retains its key mappings.
  **Status P12:** PASS_LOCAL.
  **Evidence:** `docs/parity/p12/REPORT.md`, `docs/parity/p12/VALIDATION.md`, `docs/parity/p12/FIELD_ACCEPTANCE.md`.

- [ ] **WP12-007 — A/V calibration and regression.** Add an explicitly invoked generated synchronization/calibration test that restores prior media state, mute and settings. Keep receiver/ARC acceptance distinct from timestamp/controller tests.
  **Acceptance:** Actual audible/visible synchronization, track changes, pause/seek and teardown are measured where equipment exists; upstream-open audio gates remain open otherwise.
  **Status P12:** IMPLEMENTED_AWAITING_FIELD.
  **Evidence:** `docs/parity/p12/REPORT.md`, `docs/parity/p12/VALIDATION.md`, `docs/parity/p12/FIELD_ACCEPTANCE.md`.

**Phase exit gate:** Browser-supported audio/quality/control behavior is verified and truthfully labeled; Android decoder selection, HDMI passthrough or receiver latency are not promised by browser settings.

## P13 — Remaining GFX, input, lifecycle and optional unified surfaces

Audit and adapt relevant renderer/lifecycle fixes while protecting the working full-browser display and long-press overlay.

**Dependencies:** P02. **Scope:** required.
**References:** U01, U10, U14, B01, B05 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MiniClientSession.java`, `src/main/java/org/opensagetv/webplayer/MiniClientCrypto.java`, `src/main/webapp/js/miniclient.js`, `src/main/webapp/js/miniclient-core.js`, `src/main/webapp/css/miniclient.css`, `src/main/webapp/sw.js`.

- [ ] **WP13-001 — GFX opcode parity ledger.** Compare draw/image/cache/font/surface/transform/batch handling against the pinned Android code and native server contracts. Implement missing relevant operations instead of silently ignoring them.
  **Acceptance:** Each encountered opcode is implemented, safely negotiated off, or explicitly reported with a reproducer and task.
  **Status P13:** PASS_LOCAL.
  **Evidence:** `docs/parity/p13/REPORT.md`, `docs/parity/p13/VALIDATION.md`, `docs/parity/p13/FIELD_ACCEPTANCE.md`.

- [ ] **WP13-002 — Preserve startup and recovery.** Keep first-FLIPBUFFER gating, binary crypto handling/old-mode ACKs, serialized replies, generation-safe type-5 retry and cache ownership. Characterize any currently unadvertised ZLIB path before enabling it.
  **Acceptance:** Early UI event/NPE, encrypted-session boundaries, partial packets, retry rejection and shutdown races stay covered.
  **Status P13:** PASS_LOCAL.
  **Evidence:** `docs/parity/p13/REPORT.md`, `docs/parity/p13/VALIDATION.md`, `docs/parity/p13/FIELD_ACCEPTANCE.md`.

- [ ] **WP13-003 — Opt-in unified graphics.** Implement format-256 Y/UV image allocation/update/conversion, cache lifecycle and unsupported-video-handle fallback before advertising GFX_YUV_IMAGE_CACHE=UNIFIED. Keep current off behavior unchanged.
  **Acceptance:** Repeated Y/UV updates work and stock DVB/Push compatibility is validated in OFF/ON A/B runs; a capability string alone cannot close the task.
  **Status P13:** IMPLEMENTED_AWAITING_FIELD.
  **Evidence:** `docs/parity/p13/REPORT.md`, `docs/parity/p13/VALIDATION.md`, `docs/parity/p13/FIELD_ACCEPTANCE.md`.

- [ ] **WP13-004 — Full-window and subtitle transforms.** Use one consistent geometry model for UI, video, previews, DVD highlights, bitmap captions and pointer mapping. Respect 3.2.4 mask-versus-geometry separation and OSD translucency.
  **Acceptance:** Menu hide/show, resize, fullscreen, high DPI and Fill/Fit/Stretch do not reintroduce cut-off video or shifted click targets.
  **Status P13:** PASS_LOCAL.
  **Evidence:** `docs/parity/p13/REPORT.md`, `docs/parity/p13/VALIDATION.md`, `docs/parity/p13/FIELD_ACCEPTANCE.md`.

- [ ] **WP13-005 — Browser input equivalents.** Audit Stop, D-pad repeat/cancel, Back, keyboard entry, browser shortcuts, long-press and touch across overlays/menus. Port behavior, not Android scan-code or API-level implementation details.
  **Acceptance:** No stuck repeat, duplicate selection, browser Back accident or DVD key theft across mouse/touch/keyboard tests.
  **Status P13:** PASS_LOCAL.
  **Evidence:** `docs/parity/p13/REPORT.md`, `docs/parity/p13/VALIDATION.md`, `docs/parity/p13/FIELD_ACCEPTANCE.md`.

- [ ] **WP13-006 — Visibility and session recovery.** Handle tab background/foreground, sleep/network interruption, bfcache/pagehide, reload and duplicate tabs with session-scoped timers and cancellation. Define browser timer-throttling limits rather than promising Android Service behavior.
  **Acceptance:** A retired tab cannot stop a newer session; return/reconnect restores only the intended state with no stale captions.
  **Status P13:** PASS_LOCAL.
  **Evidence:** `docs/parity/p13/REPORT.md`, `docs/parity/p13/VALIDATION.md`, `docs/parity/p13/FIELD_ACCEPTANCE.md`.

- [ ] **WP13-007 — Concurrency and telemetry.** Audit main-thread work, media-event priority, image decode, subtitle pumps and server locks. Measure real worker availability; propose GFX OffscreenCanvas/Worker only as separately scoped architecture if needed.
  **Acceptance:** “async” is not called multithreading; diagnostics distinguish configured from observed worker/decoder behavior and retained main-thread tasks.
  **Status P13:** PASS_LOCAL.
  **Evidence:** `docs/parity/p13/REPORT.md`, `docs/parity/p13/VALIDATION.md`, `docs/parity/p13/FIELD_ACCEPTANCE.md`.

**Phase exit gate:** Relevant graphics/input/lifecycle parity is tracked and regression-tested, with no blind negotiation of unsupported surfaces or loss of the accepted overlay/display fixes.

## P14 — Diagnostics, configuration profiles and server-share workflows

Bring over relevant troubleshooting and configuration behaviors with browser/server-specific security boundaries.

**Dependencies:** P02. **Scope:** required.
**References:** U01, U02, U03, U15, B01 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MiniClientServlet.java`, `src/main/java/org/opensagetv/webplayer/TranscodeStatusServlet.java`, `src/main/java/org/opensagetv/webplayer/SageApiBridge.java`, `src/main/webapp/js/miniclient.js`, `src/main/webapp/js/miniclient-streaming.js`, `src/main/webapp/miniclient.html`.

- [ ] **WP14-001 — Useful parity diagnostics.** Add active subtitle codec/PID/page, resolved CC slots, render owner, queue age, timestamp epoch, DVD cell/drain/SPU/audio state and actual effective transcode mode. Export counters, not media/subtitle content or secrets.
  **Acceptance:** A report distinguishes missing packets, failed decoding, mistimed cues and invisible presentation without exposing Authorization, session keys or share credentials.
  **Status P14:** PASS_LOCAL.
  **Evidence:** `docs/parity/p14/REPORT.md`, `docs/parity/p14/VALIDATION.md`, `tests/test-p14-support.js`, `tests/P14DiagnosticSupportSmoke.java`.

- [ ] **WP14-002 — Real Test Current Video.** Replace the current two-second observation with an explicitly invoked bounded test for cadence, buffering, safe pause/resume, reversible seeks and subtitle/disc state where applicable.
  **Acceptance:** Original position, playback/mute and settings are restored on success, failure and cancellation; unsafe live/DVD-menu operations are skipped with a reason.
  **Status P14:** PASS_LOCAL.
  **Evidence:** `docs/parity/p14/REPORT.md`, `docs/parity/p14/VALIDATION.md`, `tests/browser-p14-smoke.py`.

- [ ] **WP14-003 — Durable report export.** Provide bounded redacted ZIP reports with hashes, recent tests and failure snapshot, retaining immediate export before Reconnect. Make optional automatic capture default-off and lifecycle-safe.
  **Acceptance:** Reports survive a failed stream without unlimited logs, media payloads, authentication data or stale cross-client content.
  **Status P14:** PASS_LOCAL.
  **Evidence:** `docs/parity/p14/REPORT.md`, `docs/parity/p14/VALIDATION.md`, `tests/P14DiagnosticSupportSmoke.java`, `tests/browser-p14-smoke.py`.

- [ ] **WP14-004 — Configuration import/export and scope.** Audit Android settings transactions/profile behavior and design schema-versioned browser/server profile import/export. Separate client identity, per-session overrides, saved defaults and administrator settings.
  **Acceptance:** Import preview/migration is explicit, unknown keys are bounded, unrelated preferences survive, and interrupted test restoration is recoverable.
  **Status P14:** PASS_LOCAL.
  **Evidence:** `docs/parity/p14/REPORT.md`, `docs/parity/p14/VALIDATION.md`, `tests/test-p14-support.js`, `tests/browser-p14-smoke.py`.

- [ ] **WP14-005 — Server-side share/mapping equivalents.** Inventory useful SMB media/config/diagnostic workflows. Prefer already-mounted server paths or a plugin-owned authenticated connector; browsers do not receive SMB credentials or directly open smb:// URLs. Keep diagnostic destination independent from the media share.
  **Acceptance:** A security-reviewed design exists before dependencies or writes; read-only media verification never creates files.
  **Status P14:** PASS_LOCAL.
  **Evidence:** `docs/parity/p14/SERVER_DESTINATION_DESIGN.md`, `docs/parity/p14/REPORT.md`, `tests/P14DiagnosticSupportSmoke.java`.

- [ ] **WP14-006 — Approved connection tests and spooling.** For an explicitly configured writable diagnostics/config destination, implement a bounded write/read/hash/delete test, retry spool and atomic output with cancellation. Require opt-in for external writes.
  **Acceptance:** No tests write into recordings or delete user files; failure leaves a bounded recoverable export and a redacted stage-specific error.
  **Status P14:** IMPLEMENTED_AWAITING_FIELD.
  **Evidence:** `docs/parity/p14/REPORT.md`, `docs/parity/p14/VALIDATION.md`, `docs/parity/p14/FIELD_ACCEPTANCE.md`, `tests/P14DiagnosticSupportSmoke.java`.

- [ ] **WP14-007 — Accessible settings and reporting.** Adapt the upstream non-truncating nested dialogs and D-pad focus behavior to the existing Vibe browser overlay. Show relevant decoder/track choices dynamically and preserve focus on return.
  **Acceptance:** Narrow/tall/short windows and touch/keyboard use reach all settings without fake unsupported choices or losing the selected mode.
  **Status P14:** PASS_LOCAL.
  **Evidence:** `docs/parity/p14/REPORT.md`, `docs/parity/p14/VALIDATION.md`, `tests/browser-p14-smoke.py`.

**Phase exit gate:** Diagnostics and profiles are useful, bounded and secret-safe; any server/share mutation is explicit and independent of normal playback.

## P15 — Optional extended-Core/provider integration

Track relevant upstream extension work without making it a requirement for the stock-server plugin.

**Dependencies:** P07, P08, P10. **Scope:** optional.
**References:** U01, U02, U03, U10 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `src/main/java/org/opensagetv/webplayer/MiniClientSession.java`, `src/main/java/org/opensagetv/webplayer/MiniClientMediaBridge.java`, `src/main/java/org/opensagetv/webplayer/TranscoderManager.java`, `docs/parity/DECISIONS.md`.

- [ ] **WP15-001 — Extension capability negotiation.** After separate approval, negotiate only supported functional DVD_DISC_* and VIDEO_PLAYBACK_RATE names and keep unknown GET/SET behavior safe. No new VIBE_* aliases are emitted to imitate removed protocol names.
  **Acceptance:** A stock peer ignores/does not offer the extension and retains its proven native path; an extended peer reports actual negotiated features.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.
  **State:** Optional; awaiting separate approval. Does not block stock-server work.

- [ ] **WP15-002 — Provider-neutral DVD representation.** Implement dvd_mpegts_v1/transformed_main_feature only against a verified extended-Core/provider contract; keep native interactive DVD separate.
  **Acceptance:** Transport telemetry proves the provider path really ran; fallback is explicit, not incorrectly labeled transformed hardware playback.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.
  **State:** Optional; awaiting separate approval. Does not block stock-server work.

- [ ] **WP15-003 — Skip menus/previews and hybrid policy.** Treat client-requested menu/preview skipping and hybrid-native transition as optional Core-dependent behavior. Do not patch Sage.jar or change global transcoder settings under a plugin-only task.
  **Acceptance:** Unsupported stock options are hidden/disabled with a reason; authored native navigation remains available.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.
  **State:** Optional; awaiting separate approval. Does not block stock-server work.

- [ ] **WP15-004 — Negotiate richer metadata/rate safely.** Consume richer completed/growing metadata and rate controls only when actual support is established. Keep public APIs and ordinary FF/REW/skip behavior for stock peers.
  **Acceptance:** Loss or absence of an extension cannot break local playback, controls or caption ownership.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.
  **State:** Optional; awaiting separate approval. Does not block stock-server work.

- [ ] **WP15-005 — Separate release and rollback.** Package any approved Core/provider changes in separate artifacts/branches with exact compatibility and reversal instructions; never silently include them in the WAR.
  **Acceptance:** The stock package works with the original Sage.jar and external MIM/FFmpeg; optional dependencies do not block stock parity sign-off.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.
  **State:** Optional; awaiting separate approval. Does not block stock-server work.

**Phase exit gate:** Optional only. All tasks remain awaiting separate approval and do not block stock-compatible implementation or release.

## P16 — End-to-end parity acceptance matrix

Prove the browser implementation on real playback paths rather than inheriting Android or mocked-test results.

**Dependencies:** P03, P04, P05, P06, P09, P10, P11, P12, P13, P14. **Scope:** required.
**References:** U01, U03, U15, B01 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `tests/`, `docs/TEST_PLAN.md`, `docs/VALIDATION.md`, `docs/parity/TEST_MATRIX.md`.

- [ ] **WP16-001 — Unit, malformed and isolation gates.** Run parser/decoder golden vectors, full protocol reply tests, timing/epoch cases, resource bounds and two-client concurrency before field testing.
  **Acceptance:** Every changed module has positive, negative, cancellation and wrong-session evidence.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

- [ ] **WP16-002 — Real browser delivery matrix.** Exercise actual mpegts.js, hls.js and native HLS where supported with the plugin, not player stubs. Cover copied and transcoded output, foreground/background and cold/warm assets.
  **Acceptance:** Every advertised transport/browser combination has advancing decoded A/V and timing evidence or remains explicitly unavailable/untested.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

- [ ] **WP16-003 — Caption matrix.** Validate Teletext positive and negative sources, DVB bitmap, CEA, ordinary text and file bitmaps with STV/local modes, language/service changes, offset, OSD hidden, pause and seek.
  **Acceptance:** One intended caption owner, correct visible content/clear timing and no transport-specific subtitle loss.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

- [ ] **WP16-004 — DVD matrix.** Exercise authored root/submenus/stills, multi-title and menu-less discs, physical audio mapping, SPU/highlights, chapters, time seeks and Stop/replay.
  **Acceptance:** Real browser video plus authored menu interaction is required; protocol logs or main-title playback alone do not count.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

- [ ] **WP16-005 — Hardware and fallback matrix.** Use available server GPUs and browsers with explicit requested/effective codec evidence; test strict modes and allowed fallback. Record each untested vendor/OS separately.
  **Acceptance:** Hardware encoding/decode claims are backed by observed evidence, not configuration. Android physical passes do not transfer to Chrome/Safari.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

- [ ] **WP16-006 — Long-run and stress matrix.** Run sustained recording/live/DVD playback, rapid skips/settings/track changes, reconnections, malformed input, disk pressure and multi-client tests.
  **Acceptance:** No leak, deadlock, stale ownership, silent cue corruption or repeated zero-timeline loop; test duration and resource peaks recorded.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

- [ ] **WP16-007 — User acceptance and issue closure.** Reproduce the user’s affected media with available safe fixtures and capture immediate diagnostics for failures. Keep upstream-open receiver/device cases pending when evidence is absent.
  **Acceptance:** The user-visible Teletext, DVB and interactive DVD acceptance rows are explicitly signed off or listed as unresolved; no universal “all fixed” claim.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

**Phase exit gate:** All required rows for advertised scope pass on the intended stock-server/browser paths; skipped, unavailable, mock-only and unresolved rows stay visible.

## P17 — Release, manifests and future parity maintenance

Deliver independently verifiable builds and a durable parity ledger without regressing the working installation.

**Dependencies:** P01, P16. **Scope:** required.
**References:** U01, U02, B01 (see `docs/parity/SOURCES.md`).
**Existing integration points:** `README.md`, `handoff.md`, `CHANGELOG.md`, `BUILD_INFO.json`, `PROJECT_MANIFEST.sha256`, `scripts/package-local-plugin.py`, `dist/`.

- [ ] **WP17-001 — Close the fix ledger.** Reconcile every pinned upstream relevant fix with implemented behavior, evidence and final status. Keep superseded, platform-specific and optional entries with reasons.
  **Acceptance:** No unresolved in-scope fix is silently omitted; source and browser statuses remain distinct.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

- [ ] **WP17-002 — Versioned release metadata.** After actual code changes and tests, assign the runtime release version and synchronize UI, health, build, local installer and release notes. This planning revision does not bump 3.2.4.
  **Acceptance:** Only implemented/verified support is listed; stock-versus-optional requirements and remaining limitations are visible.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

- [ ] **WP17-003 — Independent build and archive check.** Build from a clean Git-less extraction, run required suites, verify compiled/runtime resources against source, and verify the installer contains the same WAR.
  **Acceptance:** Reproducible build evidence, unedited test transcripts and exact artifact hashes are retained; no raw private logs are packaged.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

- [ ] **WP17-004 — Preserve upgrade and rollback.** Keep the root folder and canonical WAR install path, client identity, profiles, player cache and external FFmpeg/MIM. Document rollback data compatibility and do not clear user recordings/properties.
  **Acceptance:** Existing 3.2.4 settings migrate in place, and rollback is tested against saved baseline artifacts.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

- [ ] **WP17-005 — Keep task history durable.** For every completed or reopened task, update TASKS.md, its mirror, evidence and revision ledger in the same change. Never silently remove completed IDs; add new findings under new IDs.
  **Acceptance:** Task/progress summaries are generated from the project files, not reconstructed from chat.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

- [ ] **WP17-006 — Publication and upstream deltas.** Create commits/PRs/tags/releases or remote uploads only on explicit user request. Schedule no background monitoring automatically; future upstream reviews record a new pin and delta.
  **Acceptance:** Local ZIP delivery is distinct from approved remote publication; no surprise changes to either repository.
  **Status r5:** IMPLEMENTED_AWAITING_FIELD. Generated 188/192/204-byte TS, negative DVB-only, production DOM/clock, seek/FLUSH, selection and transport-independent source-decoder tests pass. No unchanged UK broadcast recording was available in this execution environment, so the required real-source visible/timing field PASS remains open.
  **Evidence:** `docs/parity/p03/REPORT.md`, `docs/parity/p03/VALIDATION.md`, `docs/parity/p03/FIELD_ACCEPTANCE.md`.

**Phase exit gate:** Independent artifact/source validation, honest support notes, exact hashes, task evidence and a safe upgrade/rollback path are complete.

## Revision ledger

| Revision | Date | Change |
|---|---|---|
| 8 | 2026-09-25 | Implemented P06 ordinary subtitles: exact-associated embedded/sidecar discovery, sanitized text cues, PGS/DVD bitmap adaptation to LOCAL_FILE RGBA, independent schema-6 controls and shared lifecycle. WP06-004 genuine external IDX/SUB field acceptance remains open. |
| 7 | 2026-09-25 | Implemented P05 caption authority: persisted OFF/CC1/CC2/STV/DVB model, evidence-aware virtual slot resolution, event-225 A/53 bridge, Teletext-to-CEA608 STV bridge, single-renderer ownership and VIDEO_CC_STATE fallback behavior. Stock-server field rows remain open. |
| 6 | 2026-09-25 | Implemented P04 DVB bitmap per-session decoder, original-source pump, exact 2/4/8-bit RGBA/alpha vectors, browser bitmap canvas, explicit physical service selection and ordinary-video media36/type1 PID control. WP04-007 remains real-source field acceptance pending; P05 is next. |
| 5 | 2026-09-24 | Implemented P03 Teletext per-session decoder, original-source pump, service/page selection, browser-clock presentation, diagnostics and generated/Chromium tests. WP03-008 remains real-source field acceptance pending; P04 not started. |
| 4 | 2026-09-24 | Completed P02: per-session source/seek/FLUSH epochs, typed PID/service inventory, versioned text/bitmap cue contract, bounded original-source subtitle branch, decoder-placement ADR, PTS/presentation clock mapping, schema-2 profile migration, and explicit capability/resource limits. Added local executable coverage; no Teletext/DVB/PGS/VobSub decoder UI is enabled. |
| 3 | 2026-09-24 | Completed the P01 local exit gate: reconciled 1,066 frozen changelog bullets with zero unclassified, inventoried 102 relevant upstream test files, identified the live stock server/version/STV from supplied logs, retained the untouched 235-case baseline and fixture registry, and left real pinned vendor MSE playback explicitly external-pending. No runtime implementation or P02+ code change. |
| 1 | 2026-09-24 | Created P00–P17 and permanent WP task IDs; pinned the exact input/upstream; added source-gap, decision and test matrices; verified documentation-only packaging. No runtime implementation started. |

| 2 | 2026-09-24 | P01: completed current-policy reconciliation, untouched baseline rerun, fixture registry, preservation gates and delta tooling. Retained WP01-001 PARTIAL and WP01-004/006 BLOCKED. Added248 normalized UP records/68 upstream-task mappings, fresh 235-case baseline and synthetic decoder evidence; production source/WAR unchanged. |

## v3.2.23 real-parameter MPEG-2/HLS field test — complete locally

- [x] Keep H.264 compatibility fallback at Source resolution/FPS and skip Auto deinterlace for probed progressive sources.
- [x] Add codec-test D/E fixtures using MPEG-2 Main 1280×720p59.94 plus AC-3 5.1/stereo source tracks matching the supplied recording parameters.
- [x] Verify D output remains MPEG-2 1280×720p59.94 with AAC stereo and E becomes H.264 1280×720p59.94 with AAC stereo.
- [ ] Field-run D/E on Chrome and iPhone Safari and save result JSON.
- [ ] Retest the real SageTV recording with Copy fallback OFF/ON and export diagnostics for direct comparison.
