# P14 validation

Checkpoint: **v3.2.17-P14**

## P14-specific gates

- `tests/test-p14-support.js`: **13 PASS**
  - profile schema/scope whitelist and excluded identity/session/admin scopes
  - bounded unknown-key preview and future-schema fail-closed behavior
  - recursive secret/content redaction
  - caption resolved-slot/codec/PID/page/render-owner summaries without subtitle text
  - effective transcode summary
- `tests/P14DiagnosticSupportSmoke.java`: **15 PASS**
  - server sanitizer and content omission
  - ZIP/report/hash structure
  - external writes default-off
  - cooperative destination-test/spool-retry cancellation
  - bounded destination write/read/hash/delete
  - own temporary-file cleanup
  - atomic external save
  - basename-only destination status
  - configured destination paths are redacted from filesystem failure details
  - bounded spool fallback and retry
- `tests/browser-p14-smoke.py`: **11 PASS**
  - nested support D-pad focus and narrow-window reachability
  - explicit destination test and explicit spool retry
  - schema-scoped profile export
  - safe mutation skip for short media
  - normal long-media cadence, pause/resume, reversible seek and state restoration
  - redacted ZIP request with effective transcode/timestamp epoch
  - same-media interrupted-test restoration and different-media no-seek protection
  - automatic terminal-failure capture default-off / opt-in behavior
- `tests/browser-vibe-display-smoke.py`: **22 PASS** after P14 integration, including the Vibe overlay diagnostics action.

## Required inherited gates

P14 touches native/HLS status serialization, subtitle queue diagnostics, settings UI, lifecycle/failure handling and packaging. The release pass therefore reruns all inherited native protocol/startup/auth/recovery, streaming/FFmpeg, caption/subtitle/DVD, P12/P13 and production Chromium suites through `scripts/validate.sh`.

## Release regression summary

- Production Chromium suites: **111 PASS** total (including recovery 10, Vibe display 22, P13 7 and P14 11).
- Inherited native startup/auth/recovery, HLS/streaming/real-FFmpeg, MPEG-TS, Teletext, DVB, ordinary subtitles, DVD and P02/P11/P12/P13 gates were rerun in release-sized chunks after the combined validator reached the execution ceiling; all completed gates passed.
- The one inherited MPEG-TS fixture that still asserted profile schema 6 was corrected to schema 7, which was already the P13 project schema; the suite then passed **17/17**.

## Boundary

Local tests use generated/synthetic media and a temporary filesystem destination. They prove the bounded/redacted implementation and state-restoration rules, but they do not substitute for the real mounted-share exercise in `FIELD_ACCEPTANCE.md` or any earlier phase's real-broadcast/disc/device field gate.
