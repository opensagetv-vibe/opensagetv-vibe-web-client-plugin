# P12 field acceptance

Open field checks:
1. Use an unchanged recording/broadcast with primary audio plus NAR/commentary. Confirm preferred-language Auto selects the intended primary track and explicit NAR selection still works.
2. Exercise a stream whose PMT/audio layout changes during playback; verify late discovery and safe fallback without wrong-language audio.
3. With speakers/receiver/ARC where applicable, verify -4000..+4000 ms delay sign, reset, pause/seek and track changes audibly; record receiver/browser limitations separately.
4. Rapidly drag delay/bitrate and change audio track; verify only the settled configuration survives and the paused state/GFX session remain intact.
