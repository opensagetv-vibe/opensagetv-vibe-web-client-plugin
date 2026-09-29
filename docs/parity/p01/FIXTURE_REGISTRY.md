# P01 fixture registry

Revision 2. **23 available synthetic files and 12 explicitly unavailable fixture groups.**
The complete origin, SHA-256, stream expectations and allowed operations are in
[FIXTURE_REGISTRY.json](FIXTURE_REGISTRY.json). Current vector hashes are shipped;
FFmpeg-encoded media must be re-hashed after regeneration with another toolchain.

| ID | Availability | Location / missing source |
|---|---|---|
| F-TEXT-SRT | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/unicode-overlap.srt` |
| F-TEXT-VTT | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/unicode-position.vtt` |
| F-TTX-188 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/teletext-188.ts` |
| F-TTX-192 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/teletext-192.ts` |
| F-TTX-204 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/teletext-204.ts` |
| F-DVB-2 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/dvb-2bpp.ts` |
| F-DVB-4 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/dvb-4bpp.ts` |
| F-DVB-8 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/dvb-8bpp.ts` |
| F-PGS | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/white-rectangle.sup` |
| F-EMPTY | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/empty.ts` |
| F-TRUNCATED | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/truncated.ts` |
| F-MSE-TS | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/av-h264-aac.ts` |
| F-MSE-HLS | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/hls/index.m3u8` |
| F-MSE-HLS-segment000 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/hls/segment000.ts` |
| F-MSE-HLS-segment001 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/hls/segment001.ts` |
| F-MSE-HLS-segment002 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/hls/segment002.ts` |
| F-MSE-HLS-segment003 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/hls/segment003.ts` |
| F-MSE-HLS-segment004 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/hls/segment004.ts` |
| F-MSE-HLS-segment005 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/hls/segment005.ts` |
| F-MSE-HLS-segment006 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/hls/segment006.ts` |
| F-MSE-HLS-segment007 | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/hls/segment007.ts` |
| F-RESERVE | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/long-reserve-240s.ts` |
| F-CEA-AUDIO | AVAILABLE_SYNTHETIC | `tests/parity/fixtures/mpeg2-cc-multiaudio.ts` |
| F-UK-TTX-REAL | UNAVAILABLE | Unchanged authorized UK Teletext positive sample |
| F-UK-DVB-REAL | UNAVAILABLE | Unchanged authorized UK DVB bitmap sample |
| F-DVB-NEG-REAL | UNAVAILABLE | Authorized DVB-only/no-Teletext negative sample |
| F-DVD-AUTHORED | UNAVAILABLE | Authored DVD with root/language menus and short/equal-sized still cells |
| F-DVD-REAL | UNAVAILABLE | Authorized representative menu and menu-less VIDEO_TS sources |
| F-DISC-ISO | UNAVAILABLE | Read-only authorized ISO and mounted VIDEO_TS pair |
| F-VOBSUB | UNAVAILABLE | Authorized/generated matched IDX+SUB and embedded VobSub sample |
| F-PGS-EMBEDDED | UNAVAILABLE | Independently authored PGS-in-MKV with full palette/forced changes |
| F-TTX-MULTI | UNAVAILABLE | Generated two-service/language, corrupted/duplicate/continuity-loss Teletext stream |
| F-DVB-ADVANCED | UNAVAILABLE | Generated fragmented multi-page DVB with partial-alpha and CLUT/version changes |
| F-VBR-LONG | UNAVAILABLE | Authorized three-hour-plus/5.5-hour VBR source with known PTS/EDL |
| F-PROGRAM-CHANGE | UNAVAILABLE | Generated growth/PMT/audio-codec/video-resolution transition corpus |

## Evidence boundaries

The generated Teletext inputs are subtitle-only MPEG-TS with one English page-888
service, source PID 300, and text updates at source PTS values 90000/180000/270000 on a 90 kHz clock. The three
framing variants are 188, 192 and 204 bytes; the 204-byte tail is framing padding,
not a Reed–Solomon FEC test. Independent FFmpeg/libzvbi decodes both authored text
updates in all three. The test uses an explicit one-second decoder duration and
does not prove browser cue clock alignment or authored Teletext clear timing.

DVB vectors carry PID 336, composition/ancillary page 1, 720×576 display, a 6×2
region at (100,400), and a 4×2 opaque white object. PGS has the same opaque object
and coordinates. Independent FFmpeg decoding verifies exact composed RGB pixels,
preceding clear frames and subsequent clearing. It does not prove every palette,
alpha, region or forced-subtitle case. The one-fps FFmpeg overlay sampler displays
the two visible frames at sample indices 2 and 3; this is not the browser clock
acceptance test. Partial transparency and malformed/fragmented page coverage are
explicitly in the unavailable/remaining advanced fixture groups.

The H.264/AAC TS is about eight seconds, the HLS VOD has eight one-second segments,
and the reserve recording is 240 seconds. The six-second MPEG-2/AC-3 source has
three language-tagged audio tracks in NAR, English, Spanish order and encoded
CEA-608 data that the unchanged WebPlayer decoder reads as HI. These assets are
not proof of real vendor MSE, narration preference selection or audible output.

Text fixtures contain Unicode, overlapping cues and hostile markup for P06.
Do not insert their raw content into innerHTML. Empty and truncated TS inputs are
negative controls, never successful-playback fixtures.

## Generate and validate without touching recordings

From the project root (Python 3.10+; FFmpeg/ffprobe with the required test codecs):

```sh
python scripts/parity/generate_fixtures.py --output /new/disposable/p01-fixtures
python scripts/parity/validate_fixtures.py --fixtures /new/disposable/p01-fixtures --output /new/disposable/fixture-result.json
node tests/parity/test_generated_cea.js /new/disposable/p01-fixtures/mpeg2-cc-multiaudio.ts
```

Generation refuses a non-empty destination and reads no user media. Validation
refuses to overwrite its evidence. No executable, authoring package, decoder
library, font file or private recording is bundled. Fixtures do not enter the WAR.
