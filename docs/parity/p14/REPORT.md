# P14 report — diagnostics, profiles and server-destination workflows

Checkpoint: **v3.2.17-P14** (2026-09-25)

P14 replaces the earlier one-shot JSON troubleshooting path with bounded, redacted support workflows while keeping media access and administrator share configuration outside the browser security boundary.

## Implemented

- Added a bounded support ZIP endpoint (`/api/support`) containing `report.json`, `hashes.sha256` and a short README. Browser/test input is size-limited and recognized credential/content fields are redacted or omitted.
- Diagnostics now report the information needed to separate transport, decode, timing and presentation problems: playback epoch, effective encoder/decode mode, caption authority/resolved CC slot/render owner, codec/PID/Teletext page, queue depth/age/drop counters, ordinary subtitle/DVD-SPU counters, and native DVD state including cell/drain/audio/SPU information already exposed by the DVD session.
- Terminal-failure context is captured before playback teardown so a report requested after disconnect retains the failed stream snapshot. Optional automatic failure capture remains **off by default**.
- Replaced the old two-second observation with an explicitly invoked bounded **Test Current Video**. Normal recorded media checks cadence/buffering, pause/resume and a reversible seek. Short/unknown, growing/live and DVD/menu cases skip unsafe mutations with a reason. Pause intent, mute, volume, playback rate and position are restored in `finally`.
- An interrupted test stores a session-scoped recovery marker. A replacement session may restore position only when the SageTV media ID still matches; a different recording clears the marker without a seek.
- Added schema-v1 browser profile export/import. Import is previewed before Apply, unknown keys are bounded, and only whitelisted saved-default scopes are accepted. Client identity, current audio/subtitle track IDs, caption PID/page selections and administrator server settings are excluded.
- Added administrator-only server diagnostic destination support. The JVM must be configured with both `sagetv.webplayer.diagnostics.dir=<mounted path>` and `sagetv.webplayer.diagnostics.writeEnabled=true`. The browser never receives SMB credentials and never opens `smb://`.
- Server destination writes are diagnostic-only and independent from media/recording resolution. The explicit destination test creates its own unique temporary file, performs bounded write/read/hash/delete verification, and removes only that file. Filesystem failures redact the configured destination/spool paths before any detail is returned to the browser.
- Failed external report writes fall back to a bounded local spool (maximum 10 ZIP files / 10 MiB). Retry is explicit; report output uses a temporary file plus atomic move where supported.
- Settings/reporting remain usable in narrow/tall layouts. Nested support controls participate in keyboard/D-pad focus movement and return focus through the existing overlay model.

## Security boundary

P14 does not mount SMB shares, collect share passwords, reuse media credentials, write into recording directories, or treat a browser-supplied path as an administrator destination. A mounted filesystem path is configured only on the SageTV JVM. Status returned to the browser exposes only whether the destination is configured/enabled and its final path component, not the full path.

## Deliberately not claimed

- Generated/browser tests do not prove permissions, rename semantics or outage behavior on every real SMB/NFS mount.
- The bounded current-video test does not mutate DVD menus or growing/live recordings merely to obtain a PASS.
- P14 does not make automatic diagnostic capture mandatory or default-on.
- Existing field gates from earlier phases remain open where documented; P14 does not convert them into local PASS claims.

## Status

WP14-001 through WP14-005 and WP14-007 are **PASS_LOCAL**. WP14-006 is **IMPLEMENTED_AWAITING_FIELD** for an administrator-configured real mounted diagnostic destination, including a forced destination failure followed by spool retry. The P14 phase is therefore implemented locally with a small external-environment acceptance gate remaining.
