# P13 validation

Checkpoint: **v3.2.16-P13**

## P13-specific gates

- `tests/P13GfxLifecycleSmoke.java`: **8 PASS**
  - implemented/negotiated-off/unknown opcode classification
  - ledger encounter evidence
  - UNIFIED default-off and explicit opt-in property behavior
  - unsupported transform/batch capability policy
  - session diagnostic serialization
- `tests/test-miniclient-core.js`: **31 PASS**
  - includes format-256 Y/UV conversion, browser repeat ownership and shared pointer mapping
- `tests/test-miniclient-display.js`: **16 PASS**
  - includes shared Fit/Fill/Cover geometry used by video/subtitle/DVD overlays
- `tests/test-miniclient-playback.js`: **35 PASS**
  - includes requested versus observable worker diagnostics
- `tests/browser-p13-smoke.py`: **7 PASS**
  - default-off UNIFIED
  - opt-in replacement session
  - real production-DOM format-256 RGBA pixels
  - physical D-pad repeat/cancel
  - session-scoped pagehide retirement
  - bfcache fresh-generation restore
  - complete MiniClient service-worker shell

## Required inherited gates

P13 must not disturb the existing native GFX startup/recovery, media, caption or DVD paths. The release pass runs the established architecture/startup/auth/recovery/browser GFX suites plus P07/P09 native DVD protocol/SPU smoke and package/WAR verification. See `scripts/validate.sh` for the executable list.

## Boundary

These tests prove the browser implementation and generated format-256 conversion. They do **not** replace the required physical stock-SageTV DVB/Push OFF/ON A/B acceptance in `FIELD_ACCEPTANCE.md`.

## Final 3.2.16 release pass

Completed against the final source/runtime tree:

- P02 architecture: **34/34 PASS**
- P13 Java GFX/lifecycle: **8/8 PASS**
- MiniClient core: **31/31 PASS**
- MiniClient display/geometry: **16/16 PASS**
- MiniClient playback: **35/35 PASS**
- Native MiniClient protocol loopback: **PASS**
- Recovery: **11/11 PASS**
- Startup ordering: **13/13 PASS**
- RSA/Blowfish authentication: **14/14 PASS**
- Streaming servlet: **16/16 PASS**
- Real FFmpeg streaming: **14/14 PASS**
- P07 native DVD protocol: **PASS**
- P09 DVD SPU: **2/2 PASS**
- Production Chromium checks completed across P13/GFX/layout/recovery/Vibe display/Teletext/DVB/ordinary subtitles/DVD/streaming settings: **100 PASS**
- WAR/plugin/installer identity: **PASS**; installer embeds the exact final WAR and Java class target remains 52 (Java 8).

The P13 release pass intentionally does not relabel the previously established long HLS reserve result as a new P13 result because P13 did not change the HLS producer/buffer implementation. The real FFmpeg/streaming servlet and production browser streaming regressions above were rerun.
