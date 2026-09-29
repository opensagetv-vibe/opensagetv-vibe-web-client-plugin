# OpenSageTV Vibe Web Client 3.2.30 validation

Date: 2026-09-27

This record distinguishes generated/local checks from live `.232` evidence.
It does not convert an unavailable or unsuccessful field row into a pass.

## Build and local validation

- Build environment: `opensagetv-vibe-build-env:u26-j11-release-v9`
  (`sha256:50194130a8a9c99d6a0f882d6665857d852354f38015904f19beb4d9189cd885`).
- `web-client-all`: PASS, including Java, Node, real FFmpeg runtime, package,
  Playwright, and Chromium groups.
- The generated 240-second growing-source suite passed.
- Linux and Windows local-plugin manifests require the matching OpenSageTV
  Vibe FFmpeg Plugin. The WAR contains no FFmpeg/MIM executable.
- Production transcoder discovery is restricted to the plugin-owned
  `SageTVTranscoder` bridge. Stock SageTV `ffmpeg` was neither replaced nor
  selected by the Web Client.

## `.232` deployment and browser evidence

- Final canonical WAR SHA-256:
  `64d070b54523490276441ee19d2ac486ce48574a8627fc29a1d09b4928c976f4`.
- `/api/health`: version `3.2.30`, transcoder available.
- `/api/assets`: ready; all 92 pinned vendor assets cached.
- Runtime bridge: `SageTVTranscoder`, backed by
  `FFmpeg 9.0.1-sagetv-mim-v0.4.9`.
- QSV H.264 encode, hardware decode, VPP/filter initialization, and synthetic
  codec HLS generation passed.
- Deployed hls.js decoded a generated 1280x720p59.94 H.264/AAC stream.
- Deployed mpegts.js decoded a generated 640x360 H.264 MPEG-TS stream.
- Real ATSC MPEG-2/AC-3 playback reached 1280x720 video; pause/resume and an
  exact relative 60-second seek passed. STV CC1 produced a visible CEA cue.
- A real UK H.264/AC-3 recording reached 1920x1080 with visible English
  Teletext and an effective `CC1 -> TELETEXT eng` mapping.
- A real UK H.264/AC-3 recording reached 1920x1080 with independently decoded
  DVB bitmap pixels and effective DVB-bitmap selection.
- Diagnostic report export passed against the deployed servlet: the ZIP
  contained `report.json`, `hashes.sha256`, and `README.txt`; the report hash
  verified; credential/caption payload probes were removed while a safe
  counter remained. No server share write was requested.
- Replacement playback and reconnect/recovery behavior were exercised during
  the real seek and MiniClient commissioning runs; the retained Chromium
  recovery suite also passed.
- The exact authored fixture
  `/var/media/OpenSageTV_Vibe_Tests/OpenSageTV_Vibe_Test_DVD` resolved to
  MediaFile `310259`. Hardware HLS video advanced; the root menu and its
  4,716-pixel SPU highlight were visible. Down changed the highlight in 0.85
  seconds, Enter opened Languages, DVD Return and DVD Menu each produced a new
  playable menu generation, and the root-to-submenu path passed a second time.
  The final browser state remained in DVD menu mode with no page errors.
- Generated DVD startup fallback deliberately failed both hardware stages,
  kept the final software generation open beyond the former startup timeout,
  and produced playable HLS after input EOS. This verifies fallback timing but
  does not replace the remaining physical-disc rows.

## Explicitly open field rows

- `.232` had no active recording when the live/growing check ran. The generated
  240-second growing gate is a local pass, not a live field pass.
- The available H.265 and ordinary-file-subtitle fixtures were not indexed as
  SageTV MediaFiles on `.232`, so those real-file rows were not runnable. Their
  generated/local suites passed.
- Authored title/chapter/audio/subtitle selection, Stop/replay, representative
  physical/menu-less discs, and long-run teardown remain open as
  WEB-014/WP16-004. The authored startup, still/background, SPU highlight,
  root/submenu navigation, return, and second-entry regression rows passed.
- No native Safari, Windows browser, or second physical browser/GPU field claim
  is made by this commissioning run.

## Publication boundary

The user approved public beta publication after this validation. Open field
rows above remain limitations and are not converted to passes by publication.
The published plugin must require OpenSageTV Vibe FFmpeg Plugin 0.1.4 or newer,
must not bundle FFmpeg/MIM, and must remain compatible with stock `Sage.jar`.
