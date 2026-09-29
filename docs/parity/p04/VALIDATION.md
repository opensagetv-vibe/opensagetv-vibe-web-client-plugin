# P04 / 3.2.7 validation

P04 adds deterministic DVB bitmap acceptance without claiming a real-source field pass.

## P04-specific results

| Group | Result | Coverage |
|---|---:|---|
| Java DVB parser/session | 32 PASS | descriptor 0x59 identity, 2/4/8-bit exact RGBA, placement, timed clears, 188/192/204 TS, late discovery, PID/8192 control, unknown PID, seek/FLUSH/EOS, Teletext negative, bounded diagnostics |
| Browser DVB controller (Node) | 3 PASS | straight-alpha bitmap path, timeout clear independent of a new video segment, stop/poll lifecycle |
| Production Chromium DVB | 6 PASS | typed service UI, physical PID/page selection, exact RGBA canvas pixels, cover/aspect transform, timeout clear, text-renderer separation |

The exact RGBA result is checked against the independent SHA-256 stored in `tests/parity/fixtures/fixtures.generated.json`, not a value computed from the decoder implementation.

## Retained validation

`scripts/validate.sh` checks the new DVB classes in the WAR and runs both P03 Teletext and P04 DVB Java/Node suites along with the existing native, streaming, CEA, settings and browser regression groups. Browser Chromium groups remain explicitly mock-native/vendor where their test headers say so.

## Field boundary

`F-UK-DVB-REAL` is unavailable in this workspace. Therefore no unchanged real UK broadcast screenshot/video-clock timing result is reported as PASS. See `FIELD_ACCEPTANCE.md`.

## Split regression completion

The combined validation reached Chromium after all non-browser/P03/P04 Java+Node groups passed, then hit the tool execution window. That interrupted log is retained as `evidence/final-validate.log`; it is not called a monolithic PASS. The browser suites were rerun separately and passed: 15 remote/GFX, 12 layout/scheduling, 12 streaming/settings, 5 Teletext, 6 DVB, 10 recovery and 22 Vibe-display checks (**82 Chromium checks total**).

Real FFmpeg software validation also passed separately: 14 streaming/transcode/remux/caption checks and the 10-case HLS runtime suite, including the 240-second synthetic recording producing beyond the 180-second reserve target. No GPU or live SageTV server claim is made.
