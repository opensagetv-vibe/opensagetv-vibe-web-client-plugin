# Mandatory preservation gates and rollback identity

Runtime 3.2.4; original WAR SHA-256 `069ec4cd1b080f896311e98a63a6c9168bdc9091039f4326e1506f54f965742d`.
All following baseline entry points were freshly executed successfully in P01.
Counts overlap where a suite serves several contracts: **do not sum this table**.
The unique counted suite total is 235; its browser subset is 71.

| Gate | Contract | Entry points | Suite counts |
|---|---|---|---|
| BASE-START | First completed FLIPBUFFER ACK opens unsolicited UI-event gate; early replies remain live. | tests/MiniClientStartupSmoke.java | 13 |
| BASE-CRYPTO | Old-mode encryption ACK ordering, key exchange, malformed rejection and secret redaction. | tests/MiniClientAuthenticationSmoke.java | 14 |
| BASE-RECOVERY | Bounded negotiated native recovery, no encrypted/early resumption, no post-close worker revival. | tests/MiniClientRecoverySmoke.java | 11 |
| BASE-PROTOCOL | Media/GFX command framing and exact reply ordering. | tests/MiniClientProtocolSmoke.java | additional smoke |
| BASE-REMOTE | Long press has no underlying activation; held controls stop on release/cancel; real icon mappings remain. | tests/test-miniclient-core.js; tests/browser-smoke.py; tests/browser-vibe-display-smoke.py; scripts/verify-vibe-icons.py | 27 / 15 / 22; 30 asset entries |
| BASE-DISPLAY | Browser stage remains full; masks do not become geometry; preview/source coordinate and Fit/Fill/Stretch rules retained. | tests/test-miniclient-display.js; tests/browser-layout-smoke.py; tests/browser-vibe-display-smoke.py | 14 / 12 / 22 |
| BASE-BUFFER | One-segment startup separate from reserve; paused fill, bounded recovery, correct natural EOF and stale-session rejection. | tests/test-miniclient-playback.js; tests/test-mpegts-streaming.js | 31 / 17 (vendor mocked) |
| BASE-FOLLOWER | Growing EOF waits, segment rollover, cancellation, 240s producer output and completed ENDLIST. | tests/HlsRuntimeSmoke.java | 10 (real FFmpeg software) |
| BASE-STREAM | Byte-offset resume, status, typed option validation, original-video A/53 retention and conservative fallback. | tests/StreamingServletSmoke.java; tests/StreamingRuntimeSmoke.java | 13 / 14 |
| BASE-SETTINGS | Settings and recovery DOM, caption timing and old async response rejection remain intact. | tests/browser-streaming-smoke.py; tests/browser-recovery-smoke.py | 12 / 10 |
| BASE-ARTIFACT | Correct canonicalWAR, Java8 class target, installer MD5 and original icon provenance. | scripts/validate.sh; scripts/build-local.sh | additional checks |

Unedited build/validation logs and exact toolchain versions are under `evidence/`.
Future subtitle/DVD code must run the affected gate immediately and the complete
baseline before a deployable update. Keep actual vendor MSE and real stock-server
acceptance separate; mock success is not a waiver of those tests.

## Rollback and no-install boundary

This checkpoint retains the original `dist/SageTVWebPlayer.war` and the original
local installer with that identical WAR. The build-validation output lived in a
separate disposable copy and is not substituted for the baseline distribution.
Keep the installed3.2.4 deployment, cache, client ID, profiles, recordings and
FFmpeg/MIM unchanged. Future installation uses only the canonical
`jetty/webapps/SageTVWebPlayer.war`; a backup must be outside `webapps`.

P01 does not introduce a profile schema or perform a deployment/rollback on the
user's server. The byte-level rollback artifact is verified; actual installation
and data-migration rollback remain release/field gates. No baseline checksum was
recomputed from an altered production file to conceal a difference.
