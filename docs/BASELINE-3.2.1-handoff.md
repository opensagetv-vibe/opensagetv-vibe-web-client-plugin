# Handoff — SageTV Web Player 3.2.1

## Current request/evidence

The user reported a black-screen message: `GFX connection ended: Connection reset`
after testing v3.2.0. The exact supplied ZIP is the baseline. No current server
log exists in this turn. Do not claim a specific server-side root cause or blame
FFmpeg: the message comes from MiniClientSession's native GFX socket catch.

The old native session source is byte-identical in v3.1.1 and v3.2.0. The confirmed
problem addressed here is missing native recovery plus an endless HTTP-only retry
loop and hidden recovery controls. See `docs/RELEASE-v3.2.1.md` for implementation,
exact audited Android reference blobs, install, test scope and limitations.

## Preserve

Stock Sage.jar; current Jetty port/context; separate FFmpeg/MIM repo/build;
continuous MPEG-TS/HLS, all v3.2.0 streaming/audio/CEA settings; native GFX/SageMC
renderer; long-press remote; user recordings and settings. No Python service.
No GitHub changes were pushed and no existing repository was modified remotely.

## Code changes

MiniClientSession.java: explicit lifecycle/event locks, pending-socket ownership,
media readiness latch, negotiated/first-frame-gated type-5 reconnect, partial-
packet discard, single-owner GFX recovery, stable event/reply serialization,
terminal cleanup, bounded command/stage trace, server DEINIT handling.

MiniClientSessionManager.java: striped per-identity startup locks; explicit old
session supersession; old close does not target newer IDs. Recently closed entries
are retained for diagnosis; entries older than ten minutes are pruned when a new
session starts. No background janitor. Identity matching uses normalized client
ID and server text, not DNS canonicalization or a global cross-process lock.

miniclient.js: distinguish live native recovery, dead native sessions and temporary
HTTP disconnection. Stop dead event loops; retain failure snapshot/session ID for
diagnostic export; reveal reconnect/diagnostic tools. No automatic new native
session retry storm. User explicitly clicks Reconnect after terminal failure.

miniclient.html/css: diagnostic button plus failure toolbar visibility. Active
version/cache markers are 3.2.1. Existing v3.2.0 streaming modules are functionally
unchanged. Java compile target remains 8/class version 52.

## Build/validate

`bash scripts/build-local.sh`
`python scripts/package-local-plugin.py`
`RUN_BROWSER_TESTS=1 bash scripts/validate.sh`

Tests use servlet-api.jar, javac supporting --release 8, node, FFmpeg/ffprobe, and
optional Python Playwright/system Chromium. No physical SageTV or GPU test is
performed by these scripts. New tests are MiniClientRecoverySmoke.java (11
loopback TCP/fake-peer cases) and browser-recovery-smoke.py (10 Chromium/mock-HTTP
cases). Existing test cases remain. Full results belong in
`docs/VALIDATION-v3.2.1.txt`; `PROJECT_MANIFEST.sha256` covers release contents.

The monolithic validation command exceeded the container limits twice (120 and
240 seconds). The second run completed all non-browser suites. All four browser
suites subsequently passed independently with exit 0. The combined transcript
records this split execution and retains the interrupted raw log. Do not call it
a monolithic validate.sh exit 0. Aggregate coverage: 172 named scenarios plus
native protocol/legacy utility checks. After a final failure-message wording
tweak, the WAR was rebuilt and native protocol/recovery plus browser recovery
were rerun successfully. Final archive checks verify source/WAR/class parity.

## Next acceptance

Have user install the exact WAR as SageTVWebPlayer.war, close duplicate browser
client tabs, restart Jetty/SageTV and Ctrl+F5. Do not leave both a versioned and
unversioned WAR deployed. Confirm 3.2.1. Test native startup, menu use and playback.
If reset recurs, capture Download diagnostics before Reconnect and sagetv_0.txt
at the same timestamp. Key fields: lastGfxCommand, firstFrameStarted,
reconnectAllowed, reconnectAttempts, lastTransportError, timestamped trace.

A pre-first-frame reset is intentionally not type-5 resumed. A server rejection,
unsupported/non-negotiated recovery or three failed handshake attempts remains
an actionable failure. Identify that cause from actual evidence rather than
adding arbitrary capabilities or erasing the client's settings.

Earlier streaming architecture and dependency boundaries are retained in
`docs/BASELINE-3.2.0-handoff.md`.
