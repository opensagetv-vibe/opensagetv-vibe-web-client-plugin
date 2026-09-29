# P11 Field acceptance checklist

Use an unchanged stock SageTV recording and do not install private seek/control events.

- On a long VBR recording, seek near 10%, 50%, 90% and within the final two minutes. Capture `seekStrategy`, `seekRequestedMs`, `seekAnchorMs`, `seekResidualMs` and `seekAnchorErrorMs` from `/api/hls` status.
- Verify a pipe/MediaServer MPEG-TS uses `mpegts-pts-index`. If a source cannot be indexed, verify it reports the byte-zero/timestamp fallback rather than a proportional offset.
- Repeat +30/-15 and Comskip skips rapidly. The timeline must not jump to zero or repeatedly back up, and one EDL boundary must generate one logical skip.
- Pause for several minutes with a large configured reserve. The reserve should continue filling without autoplaying; resume should use the accumulated buffer.
- Follow an active recording through segment rollover and recording completion. Temporary EOF must not end playback; completed EOF must settle and stop rather than buffer forever.
- Exercise a 3-hour+ recording and, where available, a 5.5-hour-class recording with late seeks and normal OSD hide/show behavior.
- Change recordings/channels repeatedly. Old source PTS/index state, caption generations and HLS sessions must not leak into the next source.
- Confirm output directories and processes are retired after stop/recovery; low disk/session limits fail boundedly.
- Native DVD chapter/menu navigation must remain server-VM-owned and must not receive the ordinary Push seek-offset correction.
