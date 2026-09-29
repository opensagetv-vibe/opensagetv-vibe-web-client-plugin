# Validation — 3.2.2

All required groups passed across split executions on the final compiled WAR.
The monolithic `RUN_BROWSER_TESTS=1 bash scripts/validate.sh` reached the HLS
runtime PASS and was interrupted by the 120-second container call limit while
compiling the next suite. No monolithic exit 0 is claimed. Streaming/Servlet
and Chromium groups were then run separately and exited 0.

| Group | Named scenarios | Evidence |
| --- | ---: | --- |
| Core / gestures | 27 | VALIDATION-v3.2.2.txt |
| Existing HLS lifecycle | 31 | VALIDATION-v3.2.2.txt |
| Native GFX recovery | 11 | VALIDATION-v3.2.2.txt |
| Native authentication (new) | 14 | VALIDATION-v3.2.2.txt |
| Legacy growing-file / FFmpeg | 10 | VALIDATION-v3.2.2.txt |
| Streaming Servlet API | 13 | VALIDATION-v3.2.2-streaming.txt |
| Streaming / caption runtime | 14 | VALIDATION-v3.2.2-streaming.txt |
| TS adapter / settings / CC | 17 | VALIDATION-v3.2.2-streaming.txt |
| Chromium remote / GFX | 15 | VALIDATION-v3.2.2-browser-gfx.txt |
| Chromium layout / scheduling | 12 | VALIDATION-v3.2.2-browser-gfx.txt |
| Chromium streaming settings | 12 | VALIDATION-v3.2.2-browser-settings-recovery.txt |
| Chromium recovery | 10 | VALIDATION-v3.2.2-browser-settings-recovery.txt |
| **Total counted** | **186** | Not physical playback tests |

Additional checks: native protocol wire smoke, prior Node utilities, baseline
3.2.1 rejection reproduction (VALIDATION-v3.2.2-baseline-reproduction.txt), and
source/WAR/installer/Java8 package integrity (VALIDATION-v3.2.2-package.txt).

## What is real versus simulated

Native tests use real TCP sockets and actual RSA/Blowfish encryption with a
synthetic server. New authentication tests check successful encrypted
first-frame replies, 1024/2048-bit server keys, random per-session keys, binary
key transmission, invalid/malformed requests, old-mode toggle ACKs, concurrent
keyboard input, all outbound body families, empty payloads, secret redaction,
and encryption-gated recovery. The baseline replay uses untouched v3.2.1 class
bytes; the fake server rejects an empty capability as the upstream auth-required
branch does. It is not evidence of the actual exception on the user's server.

FFmpeg and file tests use actual local processes and synthetic media, not the
user's recordings/GPU. Browser tests execute real DOM, Canvas and JavaScript
handlers, but mock native HTTP, media players and vendor mpegts.js/HLS objects.
They do not validate actual MediaSource/network playback or SageMC login.

The exact live authentication policy/exception, user account login, stock/Vibe
server behavior in situ, physical GPU, real mpegts.js/MSE playback, Windows/macOS
JVM runtime and Safari remain unverified. No live-server, security-audit, complete
Android parity or measured playback-speed claims are made.
