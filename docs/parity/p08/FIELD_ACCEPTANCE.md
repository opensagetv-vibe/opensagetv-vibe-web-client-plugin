# P08 field acceptance checklist

P08 local implementation is complete, but these checks should be captured on an unchanged stock SageTV server before native DVD mode becomes a default release capability.

1. Start the plugin JVM with `-Dsagetv.webplayer.nativeDvdProtocol=true` and confirm stock SageTV selects `MiniDVDPlayer`.
2. Play an authored DVD containing a moving title and at least two audio languages. Confirm visible H.264/AAC browser playback and select each authored audio stream from SageTV.
3. Enter a one-picture or static menu with and without ongoing audio. Confirm the background never blanks during VM waits/cell FLUSH.
4. Exercise PAL and NTSC material plus chapter/title transitions and confirm the logical time does not reset or drift at cell boundaries.
5. Pause/resume, title seek, Stop, replay and disconnect/reconnect repeatedly; confirm no orphan FFmpeg process or stale HLS epoch remains.
6. If a supported physical GPU is present, repeat moving-title playback and capture the reported decode/filter/encode accelerator fields. Do not mark an unavailable vendor as failed.
7. P08 does not validate SPU/highlight/menu-button rendering. Those observations belong to P09 and must not be used to close P08 by playing only the largest VOB/main title.
