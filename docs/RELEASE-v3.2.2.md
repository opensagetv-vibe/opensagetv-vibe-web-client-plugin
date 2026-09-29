# SageTV Web Player 3.2.2 — Native authentication handshake

Date: 2026-09-24. Base: supplied SageTV-WebPlayer-Local-Plugin-v3.2.1.zip.

## Diagnostic finding

The supplied v3.2.1 diagnostic shows `firstFrameStarted:false`,
`lastMediaCommand:"none"`, no playback ID, and a reset after
`get property CRYPTO_ALGORITHMS`. The browser reports zero completed graphics
frames. Native type-5 reconnect was correctly not attempted before first frame;
after-first-frame recovery could not address this startup failure.

Inspection of the v3.2.1 source confirms it returned an empty crypto capability,
returned no symmetric key and rejected `CRYPTO_EVENTS_ENABLE=TRUE`.
SageTV's native server code rejects that capability when authentication is
required. **This is a strong match to the observed failure point, not proof of
the server's actual exception or its configuration.** No matching live server
log was supplied. The report does not distinguish local-auth policy from remote
classification, forced-nonlocal mode, or another server-side failure at this
point. Do not reset credentials or disable authentication to test this patch.

## Implemented

The Java plugin now performs the RSA/Blowfish branch of the Vibe Android native
handshake. It advertises only implemented algorithms with available JVM
providers. It handles the server public key and the wrapped session key as
binary data, generates a fresh 128-bit Blowfish key for each negotiation, and
uses RSA/ECB/PKCS1Padding for the response to `CRYPTO_SYMMETRIC_KEY`.

When the server enables encryption, all nonempty native event/reply bodies use
Blowfish/ECB/PKCS5Padding. The 16-byte reply header retains its original
plaintext payload length, matching the server's padded-length calculation.
Empty bodies remain empty. Graphics replies, property replies, keyboard/mouse
input, Sage commands, resize/repaint and filesystem replies share this writer.

Encryption toggles and their acknowledgements are serialized with all input:
the ACK uses the previous encryption mode, then subsequent messages use the
new mode. Invalid/truncated set-property packets, unsupported algorithms,
invalid public keys, premature enablement and mid-encryption key replacement
are rejected. Failed encryption never falls back to sending plaintext.

Native type-5 recovery is forbidden while event encryption is active, matching
Vibe's existing eligibility rule. Once the server disables encryption, the
normal first-frame/negotiation-gated recovery works again. Terminal failures
continue to expose Reconnect and Download diagnostics.

New diagnostics under `serverConnection.connection.crypto` describe the stage,
available/selected algorithms, key-established flag, current/prior encryption
state and message count. Key bytes and auth tokens are excluded from exported
property events and diagnostic values. Session keys are discarded on close;
no authentication cache or credentials are persisted.

## Unchanged / limitations

No selected server address, authentication property, account, saved profile,
port, stock Sage.jar, FFmpeg/MIM installation or streaming setting is changed.
The existing TS/HLS player, buffer controls, bitrate/audio/CC settings and
server-cached vendor bundles are retained. No asset preparation is needed solely
for this handshake patch; initial vendor setup is still needed for actual media
playback when not previously completed.

The server/STV remains login authority. A login screen is not an error and the
plugin does not bypass it or create a new account. Its appearance and successful
live sign-in on the user's SageTV/SageMC installation remain field tests.
RSA/Blowfish is implemented; the optional legacy DH/DES branch is not implemented
or advertised. GFX compression is still not advertised.

This implements a legacy native event protocol, **not modern TLS or end-to-end
video encryption**. It does not secure the browser-to-Jetty HTTP connection.
Use HTTPS/trusted networking for browser access; do not expose an unprotected
Jetty or native SageTV port based on this patch.

## Installation

Stop SageTV/Jetty and retain the prior WAR for rollback. Install
`dist/SageTVWebPlayer.war` at `jetty/webapps/SageTVWebPlayer.war`, then restart,
hard-refresh with Ctrl+F5 and confirm 3.2.2. Rename a separately downloaded
version-named WAR to the canonical name and do not deploy both. Close duplicate
MiniClient tabs. If an old expanded app persists, remove only the generated
`jetty/webapps/SageTVWebPlayer/` directory while stopped. Do not remove
Sage.properties, client profiles, recordings or the vendor cache.

The complete ZIP retains the consistent `sagetv-webplayer-v30-vibe/` root,
compiled WAR, local-plugin installer, complete source, tests and handoff.md.

## Validation and field acceptance

14 new authentication scenarios pass using real loopback TCP and actual JCA
RSA/Blowfish. They cover stock-sized 1024-bit and 2048-bit server keys, binary
key handling, encrypted first-frame replies, malformed/out-of-order requests,
all payload families and block padding, concurrent input during toggles, empty
payloads, secret redaction, session-key independence and encrypted/plaintext
recovery eligibility. A separate test using the untouched 3.2.1 WAR reproduces
its missing-capability reset with a synthetic authentication-required peer.

All inherited validation groups also passed across split runs: native protocol,
11 recovery cases, JavaScript core/playback, real synthetic file/FFmpeg and
caption extraction, mock Servlet API tests and 49 Chromium DOM/Canvas checks.
There are 186 named scenarios across counted groups, plus native-protocol,
legacy utilities, baseline reproduction and package-integrity checks. The
monolithic command hit the execution time limit after the HLS runtime group;
remaining suites completed separately. See docs/VALIDATION.md and saved logs.

These are **not** live SageTV authentication or playback tests. The exact server
exception/policy, successful real STV login, real mpegts.js/MSE playback,
physical GPU encoding, Windows/macOS JVM execution and native Safari remain
unverified. No playback-speed improvement is claimed by this handshake patch.

Field test: open the MiniClient, verify a first frame or server login screen,
and then test a recording. If startup still fails, download diagnostics before
Reconnect and collect sagetv_0.txt around the matching connection attempt. Do
not change bitrate/buffer settings to diagnose this pre-playback failure.

## Source references

- Vibe Android `source/dev/core/src/main/java/opensagetv/vibe/miniclient/MiniClientConnection.java`,
  blob `ce2a558a4f4960461f6b2436d9508c46e579b34c`: RSA/Blowfish key exchange,
  binary property replies, prior-mode encryption ACK and payload framing.
- Vibe Android `ConnectionReconnectState.java`,
  blob `06d1f7152608946dd4b4b05a572bba8ed9e5da6e`: encryption-gated reconnect.
- SageTV `java/sage/MiniClientSageRenderer.java`,
  blob `0d93dfbc812e2bb90a6202dc2d517a1d3dd7b3d7`: authentication requirement,
  algorithm rejection, public-key exchange, encryption enablement and
  server-controlled cached/login handling.

References describe protocol behavior, not a live-server test. Upstream
SageTV/Vibe notices are Apache-2.0; no Android or server binary is bundled.
