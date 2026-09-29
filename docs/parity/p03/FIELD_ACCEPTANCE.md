# P03 real-source field acceptance

**State:** `IMPLEMENTED_AWAITING_FIELD`

Use one unchanged recording known to contain DVB Teletext subtitles and, when available, a second DVB-bitmap-only recording as a negative Teletext control.

Required P03 field checks:

1. Verify descriptor `0x56`, language, PID and intended subtitle page are discovered without manually fabricating a track.
2. Select Teletext, hide the SageTV OSD for a sustained period and verify multiple subtitle updates and clears continue at the video clock.
3. Pause/resume and apply positive/negative caption delay; presentation must freeze/resume with playback and delay direction must be correct.
4. Seek away/back and cycle Off/Teletext/CC modes; no pre-seek or duplicate text may remain.
5. Exercise supported MPEG-TS and HLS delivery with video copy and at least one available software/hardware transcode path. Teletext must survive because it is decoded from the original-source branch, not the encoded video output.
6. Export diagnostics immediately on failure. A report should distinguish no descriptor, descriptor-only, PES, data-unit and decoded-presentation stages without containing subtitle text/media payload.
7. Run a DVB-bitmap-only source and confirm it does not appear as a Teletext service.

A generated fixture, decoder counter, or Android screenshot from the reference project does not close this field row.
