# P11 Validation — v3.2.14-P11

## New deterministic evidence

`P11TimelineSmoke` uses real FFmpeg to generate a deliberately VBR 60-second MPEG-TS whose first and second halves have very different byte rates. It proves:

1. A 45-second seek resolves from observed PES PTS, lands at-or-before the target within the bounded local tolerance, and materially differs from the historical 75%-of-file byte guess.
2. The residual timestamp seek is placed after `-i pipe:0` and before stream mapping.
3. A non-TS/unindexable source returns byte zero rather than an invented time-to-byte mapping.
4. A completed recording that exposes final bytes just after its recording flag clears is drained during the bounded settle window.
5. Truncation/replacement while a byte position is active is rejected.

Browser/controller tests additionally prove:

- same-generation stale clock rollback is clamped but a new explicit backward seek generation is accepted;
- useful source-read progress prevents a cold-output producer recovery;
- overlapping Comskip timer ticks own exactly one asynchronous seek.

## Sustained buffering

The inherited production HLS runtime still passes **10/10**, including a real FFmpeg-generated **240-second** recording that continues producing beyond the normal 180-second browser reserve and ends with a clean ENDLIST. Browser playback tests continue to cover reserve promotion, pause-time refill, loader wakeups, quota reduction and non-dueling recovery.

## Field boundary

Local evidence does not replace the P11 long-run gate. WP11-008 remains open for a real 3-hour+/5.5-hour-class recording with rapid skip/Comskip stress, late seeks, growing-to-complete transition and repeated recording/channel changes. Field diagnostics must capture the seek strategy/anchor/residual values and verify no orphan producer/session remains.

## Final release regression matrix

The final 3.2.14 package was rebuilt after the P11 runtime changes and validated in split completed runs:

- P02 architecture: **34/34**
- P11 timeline/EOF/source-identity: **5/5**
- MiniClient core/playback/display: **27/27 + 35/35 + 14/14**
- Streaming servlet / real FFmpeg runtime: **16/16 + 14/14**
- HLS runtime: **10/10**, including the **240-second** sustained-recording case
- Production Chromium: **96** completed checks across GFX/remote, layout, streaming, recovery, Teletext, DVB, ordinary subtitles, DVD A/V, DVD SPU and Vibe display/overlay
- P07 native DVD protocol/socket, P08 FFmpeg DVD runtime and P09 SPU regressions: PASS

The installer ZIP embeds the exact final WAR byte-for-byte. Java classes remain target **52 / Java 8**.
