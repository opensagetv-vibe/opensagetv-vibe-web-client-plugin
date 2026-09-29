# P13 report — GFX, input, lifecycle and optional unified surfaces

Checkpoint: **v3.2.16-P13** (2026-09-25)

P13 adapts the remaining relevant Android MiniClient renderer/input/lifecycle behavior to the browser while keeping unsupported HD-extender contracts negotiated off. The browser is not described as multithreaded merely because work is asynchronous.

## Implemented

- Added a server-side GFX opcode ledger. Every encountered command is classified as implemented, negotiated off, or unknown. The session diagnostic snapshot records encounter counts and the last unsupported/unknown opcode.
- Implemented explicit per-session opt-in `GFX_YUV_IMAGE_CACHE=UNIFIED`. Default remains OFF. A format-256 image allocates bounded Y and interleaved-UV planes and converts completed rows to the normal browser RGBA image/canvas path.
- Kept HD300 video-plane handle composition separate from browser video. `SETVIDEOPROP` continues through the ordinary source/destination rectangle path instead of treating a unified graphics handle as a browser decoder surface.
- Kept unimplemented transform/font/diffuse/texture-batch/offline-cache contracts negotiated off. They are no longer silently treated as parity.
- Centralized pointer scaling and video-content rectangle calculations in the MiniClient core. UI pointer mapping, retained DVD stills, DVB bitmap captions, ordinary bitmap/text subtitles and DVD SPU use the same Fit/Fill/Stretch/Cover geometry source.
- Added explicit browser-owned D-pad repeat with press/release/cancel ownership. Key repeat is cancelled on keyup, blur and hidden-tab transitions.
- Added `pagehide` retirement and bfcache `pageshow` reconnection. Retirement is session-ID-scoped, so an old tab cannot stop a replacement session with the same client identity.
- Expanded the service-worker shell to include every current MiniClient module, avoiding partial old/new script restoration after update.
- Diagnostics distinguish requested media worker use from worker behavior that mpegts.js/hls.js actually expose. GFX is explicitly reported as main-thread time-sliced rendering.

## Deliberately not claimed

- The browser does not advertise SageTV push/pop transforms, text/font-stream rendering, textured-diffuse rendering, texture-batch execution, or offline image cache support merely because those opcodes exist upstream.
- UNIFIED is not enabled globally. A capability string alone is not acceptance; unchanged-stock SageTV DVB/Push OFF/ON A/B field validation is still required.
- Browser timer/background semantics are not described as equivalent to an Android foreground service.
- No OffscreenCanvas/Worker GFX architecture is claimed in P13. The current measured design remains main-thread, frame/time-sliced.

## Status

Six P13 rows are closed by local/generated evidence. **WP13-003 remains IMPLEMENTED_AWAITING_FIELD** for an unchanged stock SageTV OFF/ON unified-graphics DVB/Push A/B run with repeated Y/UV updates and fallback verification.
