# P11 Report — Seek, Comskip, growing files and sustained buffering

Checkpoint target: **v3.2.14-P11**.

P11 hardens the ordinary recording timeline and reserve lifecycle. It does not change the P07-P10 native DVD/disc ownership model.

## Implemented

- Replaced the old `duration × total bytes` pipe seek with `MpegTsSeekIndex`. Every non-zero byte anchor returned by this resolver is backed by an observed MPEG-TS video PES PTS.
- The resolver samples the SageTV virtual segment stream, refines a monotonic PTS bracket, supports 188/192/204-byte TS framing, and returns an anchor at-or-before the requested time.
- FFmpeg applies only the remaining timestamp residual **after** `-i pipe:0`. If no valid TS PTS index can be proved, the feeder starts at byte zero; no byte guess is made.
- Completed direct files continue using FFmpeg timestamp seeking and are never converted to bytes.
- HLS status now exposes seek strategy, requested time, PTS anchor, residual, anchor error, sample count and detected packet size.
- Browser absolute time is monotonic within one media generation. A stale MSE/pre-FLUSH timestamp more than 1.5 seconds behind the last proven time is held until the browser catches up.
- Server state applies the same last-proven-time guard. An explicit new seek generation resets the clock, so intentional backward seeks still work.
- Native DVD `SEEK` does not mutate the browser-local ordinary clock; title/chapter/menu navigation remains owned by SageTV's DVD VM.
- Comskip serializes one EDL-boundary seek at a time so 250 ms timer ticks cannot emit duplicate skips.
- `RecordingFollower` follows growing files/segment rollover, then gives a completed recording a bounded final-tail settle window before exact EOF.
- A source whose visible length shrinks below the active byte position is treated as replaced/invalid rather than serving the old offset against new data.
- Startup remains small; hls.js then expands to the configured sustained reserve. Paused playback still refills reserve but never resumes itself.
- Recovery now records a reason class and will not restart a cold decoder while source bytes continue to arrive.
- Existing per-session output limit, global stream-count limit, idle reaper, delayed retired-session cleanup and disk-space guard remain active. The PTS index is session-local and is never reused for another file identity.

## Seek accuracy contract

For pipe sources, `seekAnchorMs` is an observed video PTS anchor and `seekResidualMs = requested - anchor`. The byte position itself is not called frame accurate. When video is stream-copied, the first decodable displayed frame can still be constrained by the source GOP/keyframe layout; diagnostics preserve that distinction.

For sources where a monotonic TS PTS timeline cannot be proven, `seekStrategy=pipe-start+ffmpeg-timestamp` and `sourceAbsoluteStart=0` are intentional. Accuracy is preferred over a fast but unverified byte guess.

## Phase boundary

P11 does not add private MiniClient commissioning events or a second SageTV seek authority. Ordinary MiniClient seeks remain server-originated command 29; DVD navigation remains the stock VM; the separate simple-player modes use their own local/direct seek contracts.
