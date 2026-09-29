# SageTV WebPlayer — P01 completion checkpoint, revision 3

Date: 2026-09-24 (evidence generated into 2026-09-25 UTC). Runtime remains **3.2.4 unchanged**. This is an audit/baseline checkpoint, not a deployable playback release.

## Phase result

**P01 exit gate is met.** Seven of eight P01 tasks are complete. `WP01-004` remains **EXTERNAL_ACCEPTANCE_PENDING**, not PASS, because this execution environment could not materialize or navigate the pinned mpegts.js/hls.js bundles. The P01 exit gate explicitly permits unavailable live tests to remain visible; it requires the reviewed ledger, reproducible baseline and fixture registry, all of which now exist.

| Task | Status | Evidence |
|---|---|---|
| WP01-001 exhaustive intake | DONE | 1,066 literal frozen changelog bullets, zero unclassified; 248 normalized functional records; 102 relevant regression-source files from 162 test files |
| WP01-002 history/open items | DONE | Current caption/DVD/seek policy retained; upstream-open acceptance remains open |
| WP01-003 untouched baseline | DONE | Actual 3.2.4 build + validation exit 0; 235 counted scenarios including 71 Chromium |
| WP01-004 real vendor playback | EXTERNAL_ACCEPTANCE_PENDING | Vendor releases identified; actual MSE/plugin start/advance/pause/resume unavailable here |
| WP01-005 fixture registry | DONE | Synthetic fixture hashes/expectations plus unavailable real-source roles preserved |
| WP01-006 stock/extended separation | DONE | Live log: SageTV 9.2.10.1054, server mode, `Sage.jar`, SageTV7 STV path; optional extended Core not used |
| WP01-007 regression preservation | DONE | First-FLIPBUFFER, crypto, reconnect, Vibe overlay/display and buffer gates mapped to baseline suites |
| WP01-008 source delta | DONE | Current upstream main still equals frozen commit |

## Exhaustive intake

The frozen Android `CHANGELOG.md` blob is `650e8a65d1407fa5a6529240078b41ada2f91afe`. It contains 3,960 lines and **1,066 top-level bullet records**. Connector-side full-blob intake assigned every record a scope disposition and primary phase family with **zero unclassified records**. The literal census is intentionally many-to-one against the **248 normalized functional ledger records**: repeated physical tests, publication notes and superseded experiments are not treated as new browser features.

The frozen Git tree contains **162 test files**. A deterministic playback/media/caption/DVD/GFX/lifecycle filter identified **102 relevant regression-source files**, all retained in `UPSTREAM_RELEVANT_TESTS.json`. Their Android existence/pass state does not certify WebPlayer.

## Baseline evidence retained

The untouched v3.2.4 source was rebuilt and validated separately with browser tests enabled. Existing evidence records **235 counted scenarios**, including **71 Chromium scenarios**, plus protocol/legacy/syntax/asset/package checks. Generated FFmpeg and loopback protocol tests are real at their stated layer; vendor mpegts.js/hls.js transport is mocked in those browser suites and remains separate.

The production WAR SHA-256 remains `069ec4cd1b080f896311e98a63a6c9168bdc9091039f4326e1506f54f965742d`. No runtime source, compiled WAR, installer, user settings, SageTV database, recording, server or GitHub repository was changed in P01.

## Stock server identity

The supplied live server log identifies **SageTV 9.2.10.1054**, server mode (`client=false`), Java 11.0.32, Linux 6.12.54-Unraid, startup classpath `Sage.jar`, and active STV `/opt/sagetv/server/STVs/SageTV7/SageTV7.xml`. The Vibe FFmpeg plugin metadata in that log describes itself as using stock `Sage.jar` without modification. A Vibe Core MCP Standard plugin is present but is not treated as a replacement Core or as extended-Core acceptance.

The log does not provide cryptographic hashes of the deployed `Sage.jar` or STV; those fields remain null. Raw logs and Authorization headers are not copied into this package.

## External vendor row

Pinned release metadata was resolved for **mpegts.js 1.8.0** (GitHub release 192241874, asset `mpegts.js`, 272,955 bytes) and **hls.js 1.7.3** (release 387197146, asset `release.zip`, 7,704,258 bytes). This environment could not obtain/navigate those binary assets or the configured CDN, so actual plugin MSE playback cannot honestly be marked PASS.

This is exactly the kind of unavailable live row the P01 exit rule says to keep explicit. It remains a later field/acceptance check and does not silently become mock coverage.

## Next phase

P02 is now the next implementation phase: shared session/epoch ownership, typed track/service inventory, common text/bitmap cue contract, source-preserving subtitle extraction, clock/queue rules, settings migration and resource/capability truth. Do not jump to later subtitle/DVD UI enablement before P02 contracts pass.
