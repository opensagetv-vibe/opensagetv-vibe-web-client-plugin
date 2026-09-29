# Vibe Android → web streaming mapping — 3.2.0

Reference repository: https://github.com/opensagetv-vibe/opensagetv-vibe-android-client

Read main on 2026-09-24. Observed root **tree** SHA `f1ba340e05fe18eaaba939253a933c3dfe6d6caf` (not a claimed commit SHA).

| Path | Observed blob SHA | Used for |
| --- | --- | --- |
| source/dev/android-shared/src/main/res/xml/transcoding_prefs.xml | 1ccc65e38dea4a50c6618dfd4c10a8513567dde6 | Video bitrate/FPS/resolution; audio codec/bitrate/channels; when-needed/forced encode |
| source/dev/android-shared/src/main/res/xml/playback_track_prefs.xml | 06895e73b36d5a30885d9a73f5599feb1b93a259 | Separate audio-language/track and broadcast caption service preferences |
| docs/PLAYER_SERVER_COMPATIBILITY.md | 91f936eac51d345bc0f4dac653674f7ea32b6065 | CEA versus Teletext/DVB/file subtitles; stock STV caption authority |
| README.md | 0d2e56ae2723f52238c0110e52108c0ce670ceff | Current architecture and stock event-225 path |

Older OpenSageTV/sagetv-miniclient results were not used as the authoritative reference. No Android code was modified.

The web UI follows these settings groups but uses browser-appropriate codecs/transport. All accepted fields participate in real server decisions/command generation or browser CEA rendering. There is no raw FFmpeg-argument editor. Source audio indices are per-recording; language is a persistent preference.

CC1–CC4 here are literal CEA-608 channels, not Android's virtual caption slots. CEA-708 selects one service. This browser-local implementation does not claim STV event-225 authority, language-based caption-slot discovery, Teletext, DVB, PGS/DVD or SRT. Those require further demux/decoder/renderer/protocol work, not just more dropdowns.

mpegts.js reference is pinned **v1.8.0**:
- https://github.com/xqq/mpegts.js/blob/v1.8.0/docs/api.md
- https://github.com/xqq/mpegts.js/blob/v1.8.0/src/player/loading-controller.ts

The loading controller returns early for config.isLive. This DVR port sets isLive:false so lazy reserve limiting/byte resume work for completed and growing recordings, while disabling live chasing. Java still follows source growth. Time seeks create server sessions; they do not rely on unsupported static TS time seeking.

Tests distinguish actual FFmpeg/CC packets/file IO from mocked Servlet API, native SageTV and mpegts.js. Actual vendor/MSE playback and physical GPU/server acceptance are outstanding. Full Android parity and a measured performance gain are not claimed. See RELEASE-v3.2.0.md.
