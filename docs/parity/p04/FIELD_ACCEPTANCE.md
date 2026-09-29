# P04 real-source field acceptance

**State:** `IMPLEMENTED_AWAITING_FIELD`

Use at least one unchanged recording known to contain DVB bitmap subtitles, preferably a UK/European broadcast source with descriptor `0x59`.

Required P04 field checks:

1. Verify the intended DVB service is discovered by source PID, language, composition page and ancillary page without using an ffprobe ordinal.
2. Select DVB and record visible subtitle placement/transparency over PAL/HD video in Fit, Fill, Stretch and fullscreen. The SageTV GFX/video rectangle must not move.
3. Let multiple subtitle display sets update and clear while the SageTV OSD is hidden. Clears must occur from subtitle timing, not only when a new video segment arrives.
4. Change language/service, cycle DVB Off/On, seek away/back, FLUSH, stop/reopen and reconnect. No pre-seek/old-service bitmap may remain.
5. Exercise original/video-copy and available software/hardware transcode paths. The same source subtitle service must remain locally decoded outside the video encoder unless an explicit future burn-in mode is selected.
6. If the stock server issues ordinary-video media command 36/type 1, confirm the physical PID is honored; PID 8192 disables; an invalid PID does not select another service. Keep this test separate from DVD private-stream behavior.
7. Export diagnostics immediately on failure. A decoded-bitmap counter alone is not field acceptance; record what was visibly shown/cleared and at what playback state.

Generated fixtures, decoder hashes, or screenshots from the Android reference project do not close this row.
