# Handoff — SageTV Web Player 3.2.2

## Goal and evidence

Continue from supplied v3.2.1. The user's new diagnostic reaches property
CRYPTO_ALGORITHMS then resets before STARTFRAME; no media command or playback
session exists. v3.2.1 returns an empty capability and rejects event encryption.
The upstream server rejects that when authentication applies. This patch fills
the verified missing protocol; a live server log is still required to establish
the actual authentication/locality predicate and exact server exception.
Do not describe the screenshot/report as proof of a firewall or FFmpeg problem.

## Changed implementation

`MiniClientCrypto.java` uses JVM JCA only. It implements RSA/Blowfish, with an
X.509 server public key and fresh 128-bit Blowfish secret wrapped using
RSA/ECB/PKCS1Padding. Public-key limits allow stock 1024-bit through 8192-bit RSA.
No DH/DES branch, server auth changes, credentials or token persistence.

`MiniClientSession.java` routes CRYPTO_SYMMETRIC_KEY as raw bytes, handles crypto
SET properties separately from text, checks unsigned property lengths, and
serializes event payload encryption and mode transitions under eventLock.
All 16-byte reply headers retain plaintext length; only nonempty bodies are
padded/encrypted. Enable/disable ACK uses OLD mode, no concurrent input can
cross that boundary. Mid-encryption rekey is rejected; crypto errors close the
channel rather than plaintext fallback. Socket recovery requires encryption off.
Close discards keys but retains nonsecret diagnostic state. Crypto tokens/keys
never enter browser property events or exported diagnostic values.

Existing rendering/media/buffering/settings behavior and selected server address
are unchanged. Preserve stock Sage.jar, existing Jetty port/context and external
FFmpeg/MIM. The handshake is native event compatibility, not browser HTTPS.

## Build / tests

```
bash scripts/build-local.sh
python scripts/package-local-plugin.py
RUN_BROWSER_TESTS=1 bash scripts/validate.sh
```

Java classes target 52. Build requires a JDK with --release 8 support plus
javax.servlet API (default /usr/share/java/servlet-api.jar). Deployed server needs
no Python. Build/test scripts use Python/Node and optional Chromium/Playwright.
Authentication tests require MiniClientRecoverySmoke on the test classpath.

Final validation groups pass across split runs: 186 named scenarios plus native
protocol, prior utility tests, baseline replay and package checks. The
monolithic command hit a container call limit after HLS runtime; remaining
suites separately exited 0. See docs/VALIDATION.md and per-group logs. New
MiniClientAuthenticationSmoke has 14 tests using actual JCA/TCP. Its optional
--expect-unsupported branch can run against the original v3.2.1 WAR classes to
reproduce the missing-capability failure. Use distinct classpaths; do not swap
production artifacts to run a baseline test on the user's server.

Do not call mock-browser media tests real mpegts.js/MSE playback. Vendor bundles
remain pinned server cache downloads/pre-seed, not embedded in the WAR. Live
server authentication, login UI, actual player/network, real media and physical
GPU are NOT tested. Windows/macOS/Safari are not tested. Do not claim a measured
speed improvement, full GPU decode chain or complete Android caption parity.

## Field acceptance

Install the single canonical SageTVWebPlayer.war while stopped; restart and
Ctrl+F5; confirm 3.2.2. Reach a first graphics frame or the server's configured
login screen, then test media. If login appears, server-configured credentials
remain required; do not bypass or change server authentication for this test.
On failure collect Download diagnostics BEFORE Reconnect plus sagetv_0.txt
around the same connection. Check connection.crypto stage, last command,
firstFrameStarted and encryptedAtClose. This is not a buffer-tuning failure.

## References / packaging

Use the requested opensagetv-vibe/opensagetv-vibe-android-client/source/dev as
reference, not the older OpenSageTV MiniClient. Source blob identities and
security boundaries are recorded in docs/RELEASE-v3.2.2.md and THIRD_PARTY.md.
No GitHub repo was modified/pushed. The ZIP root remains
sagetv-webplayer-v30-vibe/. Installer embeds exactly the final WAR; installer
XML has the correct MD5. BUILD_INFO.json records hashes and validation limits.
PROJECT_MANIFEST.sha256 covers deliverable files except itself. The original
user diagnostic is not included in the distributable package.
