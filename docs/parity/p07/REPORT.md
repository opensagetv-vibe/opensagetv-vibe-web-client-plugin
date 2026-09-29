# P07 native DVD protocol report

Runtime checkpoint: **3.2.10-P07** (2026-09-25).

## Scope

P07 implements the stock SageTV `MiniDVDPlayer` wire/session boundary and owns real pushed MPEG-PS bytes. It intentionally does **not** claim visible DVD audio/video playback in the browser; PS/PES decode/conversion and browser presentation are P08.

## Implemented

- `DvdNativeSession` provides INIT-without-OPENURL state, lazy activation, commands 32-37, four-byte DVD response behavior, transient segment EOS, drain polling, FLUSH generations and bounded diagnostics.
- `DvdPushBuffer` owns accepted MPEG-PS bytes in a bounded 4 MiB generation-aware ring. Writes are all-or-nothing, apply bounded backpressure, and readers are cancelled by FLUSH/close instead of receiving stale bytes.
- `DvdPtsClock` converts stock wire NEWCELL/STC values from 45 kHz to the internal 90 kHz DVD clock and unwraps 33-bit PTS near the active clock.
- Startup bandwidth probes remain capacity probes: non-MPEG-PS 16 KiB packets do not activate or consume DVD input before metadata/real media arrives.
- `0x80` ends only the current reusable DVD segment. `0x100` returns `-2` only at the appropriate initial/post-FLUSH or input+decoder drain boundary.
- Pre-data FLUSH is ignored. Post-data FLUSH starts a distinct byte/decoder generation, so equal-sized consecutive cells cannot be confused by byte-count identity.
- Commands 32-37 validate payloads and always return the required four-byte success/error word. SEEK keeps the existing zero-byte reply so later media replies remain aligned.
- Session diagnostics now expose DVD state and the stock-role capability gate.

## Stock role gate

Stock SageTV chooses `MiniDVDPlayer` for remote DVD playback only when the MiniClient identity behaves like an extender; in the reviewed stock `VideoFrame`/`MiniClientSageRenderer` path, advertising `MOUSE` causes DVD to take the ordinary MiniPlayer/Placeshifter branch. P07 therefore centralizes this negotiation in `MiniClientCapabilityPolicy`.

During P07 the native role is deliberately **off by default** because P08 has not yet attached the MPEG-PS bytes to a visible browser A/V consumer. The normal browser identity remains:

- `INPUT_DEVICES=IR,KEYBOARD,MOUSE,TOUCH,TV`
- `PUSH_AV_CONTAINERS=NONE`

For protocol/negotiation field testing only, start the SageTV/Jetty JVM with:

`-Dsagetv.webplayer.nativeDvdProtocol=true`

Then the plugin reports:

- `INPUT_DEVICES=IR,KEYBOARD,TOUCH,TV`
- `PUSH_AV_CONTAINERS=MPEG2-PS`

Browser mouse/touch event APIs are unchanged; omission of `MOUSE` in this opt-in property is a stock server role-selection signal, not removal of the WebPlayer's mouse-event implementation.

## P07 row status

| Row | Local status | Notes |
| --- | --- | --- |
| WP07-001 | IMPLEMENTED_AWAITING_FIELD | Opt-in role negotiation implemented and source/test verified; capture from an unchanged stock server is still required. |
| WP07-002 | PASS_LOCAL | INIT -> media time/capacity/probe/metadata/push path works without OPENURL. |
| WP07-003 | PASS_LOCAL | Real TCP peer verifies commands 32-37, malformed errors and zero-byte SEEK alignment. |
| WP07-004 | PASS_LOCAL | Bounded all-or-nothing byte ownership and backpressure tests pass. |
| WP07-005 | PASS_LOCAL | 0x80 + 0x100 state machine requires input/decoder drain before `-2`. |
| WP07-006 | PASS_LOCAL | FLUSH generation cancellation and equal-sized replacement-cell coverage pass. |
| WP07-007 | PASS_LOCAL | 45 kHz/90 kHz unit mapping, GETMEDIATIME and reply alignment pass. |
| WP07-008 | PASS_LOCAL | P07 performs no FFmpeg startup in the native reply lock; bounded input failure remains visible while existing GFX/startup/recovery suites stay healthy. P08 owns converter-startup isolation. |

## Deliberate boundary

Do not flip `nativeDvdProtocol` to a release default and do not call native DVD playback complete until P08 consumes `DvdPushBuffer`, converts actual PS/PES A/V, maps browser time back to `DvdPtsClock`, and proves menu/title presentation. P09 remains DVD SPU/highlight/navigation rendering.
