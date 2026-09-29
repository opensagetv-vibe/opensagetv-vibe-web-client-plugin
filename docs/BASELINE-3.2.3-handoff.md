# Handoff — SageTV Web Player 3.2.3

## Confirmed evidence / correction to earlier diagnosis

Use `docs/RELEASE-v3.2.3.md` as the current incident description. The uploaded
server log identifies the MiniUIClientReceiver NPE immediately following
initial UI-size notification; the crypto query is five seconds later after the
UI is killed. Do not attribute this case to authentication or browser caching.
Source inspection and exact-old-WAR TCP reproduction show 3.2.2 emitted resize
192 and repaint 193 before any frame. The exact runtime JAR is not available;
do not claim a live server replay or exact current-source line match.

## Implementation and invariants

Only MiniClientSession.java behavior changes (plus version/cache tags). All
startup gate state is owned by eventLock. Capability/crypto/GFX/media replies
must remain responsive before UI readiness. Never send unsolicited UI events
from INIT. Open the event gate after the first FLIPBUFFER reply is flushed,
not on TCP connect, INIT, STARTFRAME, a crypto query, or a sleep.

Startup resize requests retain the latest dimensions without prematurely
changing browser canvas state. On first frame: ACK, resize192, ordered browser
resize notification, full repaint193, optional coalesced media update201.
Drop early user input, do not save/replay it into a login/menu. Subsequent
FLIPs do not repeat the initial burst. Driver re-INIT closes the gate; close
clears pending work. Keep encryption previous-mode ACK and reconnect rules.

Diagnostics are connection.startupEvents. Do not log input text or secrets.
The uploaded server log contains HTTP Authorization; do not commit it or add
it to generated archives. No remote GitHub changes have been made.

## Build and validation

```bash
bash scripts/build-local.sh
python scripts/package-local-plugin.py
RUN_BROWSER_TESTS=1 bash scripts/validate.sh
```

Requires a JDK supporting --release8 and javax.servlet API, Node/Python for
tests, FFmpeg and optional Playwright/Chromium. Deployed runtime has no Python
requirement. New `MiniClientStartupSmoke` shares peer utilities with
MiniClientRecoverySmoke. Read the saved current-version test transcripts and
`docs/VALIDATION.md`, not historical test summaries, for actual results.

To demonstrate old behavior without installing it on a real server, extract
the supplied 3.2.2 WAR and run the new StartupSmoke with its WEB-INF/classes
FIRST on the classpath and --expect-early-ui. The normal startup test must pass
against current classes and rejects premature notifications. Auth/recovery
helper expectations were updated: startup resize/repaint now follow the first
frame, not INIT. Concurrent crypto input testing starts only after a frame.

## Field acceptance / unchanged scope

Install the single canonical WAR while stopped; restart and confirm3.2.3.
Verify a first SageMC/STV frame, browser resize, remote hold/menu, then media.
On failure collect diagnostic BEFORE Reconnect and the matching server log.
Do not disable auth, clear profiles, replace Sage.jar, or retune buffers to
address this native startup defect.

The log also shows an unrelated plugin version comparison overflow on a
10-digit timestamp component. This WAR does not repair installed metadata.
Use plain package version3.2.3; do not claim the updater issue is fixed here.

Retain mpegts.js/HLS fallback, native rendering/long press, streaming settings,
external FFmpeg/MIM and stock-server compatibility. Actual live SageTV/MSE/GPU,
Windows/macOS and Safari remain untested. No full GPU decode pipeline or full
Android caption parity is claimed. Historical READMEs/handoffs are under docs;
the earlier authentication-cause hypothesis is no longer the incident diagnosis.
