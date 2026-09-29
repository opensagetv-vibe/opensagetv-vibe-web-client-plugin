# SageTV Web Player 3.2.3 — First-frame startup event ordering

Date: 2026-09-24. Base: supplied SageTV-WebPlayer-Local-Plugin-v3.2.2.zip.

## Server-log finding

The uploaded `sagetv_0(8).txt` records the failed browser client at server time
10:42:58.260. Immediately after `Got UI size update to 1598x1080`, the native
`MiniUIClientReceiver` throws `NullPointerException` at
`MiniClientSageRenderer.java:8098` and `KillUIMgr` destroys the interface.
The same sequence repeats in earlier attempts. This is the server-side failure,
not a browser cache, FFmpeg, MPEG-TS buffering, or authentication rejection.

Only approximately five seconds later, at 10:43:03.303, the construction thread
logs `CRYPTO_ALGORITHMS=null`; other capability results are also null. It then
states that the UI was asynchronously killed during construction and performs
cleanup. Thus the last command in the browser diagnostic was a consequence of
an already-failed native receiver. The earlier authentication-cause hypothesis
is superseded by this server log. The RSA/Blowfish implementation is retained
for servers that actually require it; no authentication setting is changed.

## Confirmed plugin defect and correction

The untouched 3.2.2 WAR replies to GFX INIT and immediately sends native event
192 (resize) and 193 (repaint), before any first graphics frame. This ordering
was reproduced against that exact WAR over loopback TCP. The source's native
UI event handlers access the UI root/video frame, which need not exist during
driver negotiation. The log and this early-event path identify a startup
ordering defect; the exact running server JAR was not available for an end-to-end
reproduction or source-line mapping beyond its recorded stack trace.

3.2.3 does not send unsolicited native UI events until after acknowledging the
first GFX FLIPBUFFER. It does NOT merely wait a fixed number of milliseconds.
INIT, property/crypto replies, image allocation replies, filesystem replies,
and media protocol responses still operate during initialization, so the server
can finish negotiation, login-frame drawing, and first-frame construction.

Initial dimensions remain advertised through native capability properties.
Resize requests arriving during startup are coalesced to the latest size. After
the first frame ACK, exactly one native resize and full repaint are sent; any
pending media update follows. This preserves full-window sizing without
sending a repaint to an unconstructed UI. Repeated frame flips do not repeat
that initialization burst. Regular post-startup resize/repaint behavior remains.

Premature keyboard, mouse, text, and Sage commands are discarded rather than
replayed into a newly displayed login/menu. Early media-update events are
coalesced. Close cancels pending work; driver re-INIT closes the event gate until
its next completed frame. Existing encryption and native-recovery rules remain.

## New diagnostic evidence

`serverConnection.connection.startupEvents` records:

- `ready`, `firstFrameCompleted`;
- `deferredUiEvents`, `droppedEarlyInput`;
- `pendingResize`, `pendingRepaint`, `pendingMediaUpdate`;
- `sentUiEvents`, `lastUiEventType`.

No input text, authentication header, password, or key is added to diagnostics.
The original full server log is NOT included in this distribution: it contains
HTTP Authorization headers. Treat it as sensitive before sharing publicly.

## Installation and retained behavior

Stop SageTV/Jetty, retain the current WAR outside its deployment directory, and
replace `jetty/webapps/SageTVWebPlayer.war` with `dist/SageTVWebPlayer.war`.
A separately downloaded versioned WAR must be renamed to `SageTVWebPlayer.war`.
Restart and hard-refresh the existing MiniClient page. Confirm 3.2.3 in both
the toolbar and `/SageTVWebPlayer/api/health`.

Do not disable authentication, erase client profiles, change media settings,
or replace Sage.jar for this fix. No Python service, different port, new media
asset download, or changes to FFmpeg/MIM are needed. Existing vendor cache,
stream profiles, bitrate/audio/CC controls, HLS fallback, TS delivery, graphics
cache, and long-press remote are retained.

A separate startup-log issue is the plugin repository's oversized numeric
version component (`2609231928`) causing `CorePluginManager.compareVersions`
to throw. It is not the native receiver exception above, and this WAR does not
modify installed plugin metadata or repair that existing repository entry.
The included local package uses the plain 3.2.3 version.

## Validation boundary

See `docs/VALIDATION.md` and the saved 3.2.3 transcripts for actual results.
New tests enforce absence of UI-event bytes before first frame, verify ordinary
protocol replies remain responsive, and cover delayed initialization, coalesced
resize, early input, media updates, driver re-INIT, close, and normal runtime
resize. The original 3.2.2 WAR reproduces its premature 192/193 burst.

These are real loopback sockets with a synthetic SageTV readiness boundary.
They are NOT live tests against the user's SageTV/SageMC installation. Browser
suites exercise real DOM/Canvas against mocked native/vendor transport. Actual
mpegts.js/MSE playback, physical GPU encoding, Windows/macOS, and Safari remain
unverified. Streaming code is not changed by this patch.

Field acceptance: reach the SageMC/STV interface or server-required login,
then resize the browser and test the long-press remote before media playback.
If startup still fails, download diagnostics before Reconnect and capture the
matching server-log exception. The gate must show no native UI events before
`firstFrameCompleted=true`.

## Source and history

- User-supplied log `sagetv_0(8).txt`, native receiver failure at 10:42:58 and
  subsequent cleanup at 10:43:03, correlated by browser session/client identity.
- Vibe Core `java/sage/MiniClientSageRenderer.java`, blob
  `7c92e9bc865aa417bb591212dde6729b7c4c7823`: asynchronous UI-event handlers and
  startup reply consumption. The checked source is not the user's exact JAR.
- Supplied plugin `MiniClientSession.java`: former GFX_INIT -> sendResize ->
  sendRepaint ordering, now gated after FLIPBUFFER acknowledgement.

The 3.2.2 README/handoff/build metadata are retained under `docs/BASELINE-3.2.2-*`.
Those historical authentication-cause hypotheses are superseded, not current
conclusions. No GitHub repository changes were pushed.
