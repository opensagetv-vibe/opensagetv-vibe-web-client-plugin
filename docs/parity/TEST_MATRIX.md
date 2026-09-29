# WebPlayer parity acceptance matrix

Parity revision2 — 2026-09-24. **BASE-01 PASS** from an actual fresh build/run.
**MSE-01 BLOCKED**: no real vendor playback was exercised. All other feature
acceptance groups below remain PLANNED/NOT_RUN unless their dedicated evidence
says otherwise. P01 fixture-reference decoder checks are input validation, not
TTX-01/DVB-01/SUB-02 completion. See [p01/REPORT.md](p01/REPORT.md).

| Checkpoint test | Result | Evidence |
|---|---|---|
| BASE-01 | PASS235 cases;71 browser subset | p01/evidence/baseline-result.json |
| MSE-01 | BLOCKED | p01/evidence/external-preflight.json |
| P01-TOOLS | PASS22 tooling tests | p01/evidence/p01-tools-test.log |
| P01-FIXTURES | PASS30 checks:23 hashes,3 text decodes,4 bitmap decodes | p01/evidence/fixture-reference-result.json |
| P01-CEA | PASS1 generated-source decoder check | p01/evidence/generated-cea-result.json |
| SERVER-ID | BLOCKED exact running Core/STV identity | p01/evidence/external-preflight.json |


| Test | Phases | Fixture | Path/actions | Required observation | Evidence boundary |
|---|---|---|---|---|---|
| BASE-01 | P01 | Exact original 3.2.4 | Existing native/crypto/GFX/display/stream suites | Untouched runtime regressions and current toolchain availability | Automated transcript; never substitute inherited counts |
| MSE-01 | P01,P16 | Generated H.264/AAC TS and HLS | Actual vendor bundles and HTTP | Startup, append/decode, pause, refill, seek and end | Real browser/Jetty; no player mocks |
| OWN-01 | P02 | Two different media with distinct subtitles | Two clients; concurrent seeks/stops | Independent session/epoch, no cross-client cues or shutdown | Automated plus real multi-tab/server |
| CAP-01 | P02,P05 | Versioned3.2.4 profiles | Upgrade and import/export | Literal CEA choices/Off/client ID/bitrate survive migration | Unit/browser persisted-state evidence |
| TTX-01 | P03 | Generated PAT/PMT/PES page888 and second service | Fragments188/192/204, continuity loss | Correct service/page/text/clear/PTS; bounded invalid data | Golden vectors; not field acceptance |
| TTX-02 | P03,P16 | Unchanged Breakfast or equivalent | Local/stock; TS/HLS; copy/transcode | Visible changing Teletext with OSD hidden, pause and large seek | Real-source screen+timeline evidence |
| TTX-03 | P03,P16 | Unchanged Classic Holby or equivalent | Second positive service/audio variant | Page selection, timing, Off/On and retained audio | Real source required for field PASS |
| TTX-NEG | P03,P04 | Taskmaster or generated DVB-only negative | Teletext discovery enabled | No fabricated Teletext/CEA; DVB stays independently available | Negative control |
| DVB-01 | P04 | Known bitmap2/4/8 bpp vectors | Split PES, multi-page, CLUT changes | Pixel/alpha/hash, placement, timeout/clear and malformed bounds | Golden pixel evidence |
| DVB-02 | P04,P16 | Taskmaster/UK DVB sample | Local DVB, PID36, late discovery | Visible bitmap, correct service, persisted mode, Off/On and seek | Real source/renderer evidence |
| CC-01 | P05 | CEA608+708 and real Teletext services | OFF/CC1/CC2/STV/DVB transitions | One intended renderer, real service inventory, unknown608 language | Stock server plus property-present/absent peers |
| CC-02 | P05 | Real captioned MPEG2 and H264 | Hardware/software video transcode | Original captions survive encoder and output clock mapping | Packets plus visible timed rendering |
| SUB-01 | P06 | Unicode SRT/WebVTT and embedded text | Delay/seek/Off plus hostile markup | Correct cue lifecycle; no HTML/script injection | Unit/browser |
| SUB-02 | P06 | PGS and VobSub/IDX or MKV tracks | Palette/forced/Off/fullscreen | Correct colors/clear/placement; not mislabeled Blu-ray playback | Pixel and real-source evidence |
| DVD-WIRE | P07 | Native protocol peer | INIT without OPENURL; probes;32–37; errors | Correct byte lengths, capacity, no discarded payload or deadlock | Real TCP byte assertions |
| DVD-DRAIN | P07,P08 | Tiny/equal-sized cells and long title | 0x80/0x100, pre/post-dataFLUSH | Finite drain, -2 only when ready, no early title completion | Protocol and output-consumption evidence |
| DVD-AV | P08 | Authored PS multi-audio PAL/NTSC | Private-stream mapping and transcode | Correct selected audio, cadence/clock across cell transitions | Actual FFmpeg plus browser A/V |
| DVD-STILL | P08,P09 | One-picture/menu-audio fixture | Idle menu, highlight changes, new cell | Stable background, responsive menu, no rebuffer deadlock | Visible actual browser menu |
| DVD-SPU | P09 | Authored SPU/RLE/CLUT/CHG_COLCON | RepeatedCLUT, enabled/disabled/forced | Pixel accuracy, timed clear, no future/stale highlights | Core vectors plus composed browser pixels |
| DVD-MENU | P09,P16 | Authored root/language/title menus | D-pad/Select/Back/chapter/audio/subtitles | All authored transitions and Stop; not just main movie | Stock server + actual browser |
| DVD-REAL | P09,P16 | Available menu and menu-less DVDs | Representative sustained playback | Navigation/audio/subtitles/seek/cadence/teardown | User media not bundled; unavailable remains pending |
| DISC-01 | P10 | VIDEO_TS, mountedISO, invalidISO path | Spaces/Unicode/duplicate basenames | Deterministic identity and precise mount/prerequisite failure | No privileged mount or automatic library mutation |
| DISC-02 | P10 | Multi-title extras-first disc | Explicit title/chapter/movie-only | Correct title identity and truthful menu-limited mode | No largestVOB/main-feature heuristic PASS |
| SEEK-01 | P11 | VBR long recording with known PTS/EDL | Direct and MediaServer/pipe, rapid skips | Measured landing tolerance; no transient-zero or doubleComskip | Record target/effective time/latency |
| LIVE-01 | P11 | Controlled growth/program switch | TemporaryEOF, PMT/codec changes, completion | Keeps filling, advances monotonic time, completes honestly | Source/follower plus actual playback |
| BUF-01 | P11 | Recording longer than configured reserve | Quickstart; paused fill; byte resume; quota | Reserve grows beyond startup and refills without producer restart | Actual browser buffer graph and server output |
| LONG-01 | P11,P16 | Three-hour-plus/5.5-hour-class source | Late seek, pause, OSD hide, stop/replay | No stuckOSD, leak or time-wrap/backing-up loop | State actual duration tested; no short smoke substitution |
| AUDIO-01 | P12 | Primary/NAR/multilingual AC3/MP2/AAC | Track, codec/layout and delay changes | Right audio; sign/delay correct; changes debounced | Synchronized fixture plus audible output |
| GPU-01 | P12,P16 | Same source and quality profile | AvailableQSV/NVENC/VAAPI/etc plus fallback | Observed requested/effective encoder/decode identity and strict rules | Unavailable vendor is not a pass |
| GFX-01 | P13 | ProductionSageMC + syntheticYUV/images | UnifiedOFF/ON, resize, preview, masks | No cut-off video/cache loss/ghostcaption; input remains aligned | Real STV plus deterministic render vectors |
| LIFE-01 | P13 | Activecaption/DVD + second session | Reconnect, pagehide/bfcache, sleep, cancel | No stale callback, orphanprocess, cross-session stop or secret leak | Browser/server lifecycle evidence |
| DIAG-01 | P14 | Synthetic injectedfailure and privatefields | TestCurrentVideo success/fail/cancel/export | Restored state, bounded redacted report, no media or credentials | Content scan plus restoration assertions |
| SMB-01 | P14 | Explicitly configured test share | Read-onlymedia; approveddiagwrite/retry | Independentprofiles, no deletion of user files, safe spool | Opt-in test destination only |
| EXT-01 | P15 | Separately approvedextendedCore/provider | Negotiate/fail/missingprovider/stockfallback | Actualdvd_mpegts_v1 proof; stock path unaffected | Optional-only; not required by stock gate |
| PKG-01 | P17 | Clean extracted finalproject | Build/manifest/source-WAR/installer compare | Version-consistent deployables and reproducible evidence | Fresh extraction and explicit publication boundary |

## Proposed acceptance measurements (not results)

Define and record numeric tolerances before running each fixture; do not select limits after seeing results. Initial targets for review: subtitle presentation within 500 ms of the fixture clock after settling; menu/input action starts within a measured bounded interval; no progressive A/V drift; seek landing tolerances explicitly differentiated between re-encode/decoded and keyframe-copy paths. Use repeated startup/seek runs and report median/tail, not a single best number. These are proposed web acceptance criteria, not Android benchmark claims.

For the user’s quick-start/larger-reserve requirement, record first-byte, first playable append/frame, browser forward reserve, server reserve, production rate and refill events separately. A full reserve is impossible ahead of the actual live recording edge and browser quota can lower the effective target. Prove continued replenishment over a source materially longer than the configured reserve.

A feature can pass isolated vectors and still remain IMPLEMENTED_AWAITING_FIELD. Required field evidence includes exact runtime/build/upstream hashes, browser/OS and vendor library versions, stock Sage.jar/STV identity, source hash and expected streams, requested/effective settings, action times, observed clock/frames/audio, diagnostic report and visible output where allowed.

## Evidence record template

```json
{
  "test_id": "TTX-02",
  "status": "NOT_RUN",
  "runtime_version": "3.2.4",
  "runtime_war_sha256": null,
  "upstream_commit": "f1ba340e05fe18eaaba939253a933c3dfe6d6caf",
  "server_core_sha256": null,
  "server_kind": "stock|extended",
  "stv": null,
  "browser": null,
  "os": null,
  "vendor_bundle_versions": {},
  "fixture_id": null,
  "fixture_sha256": null,
  "requested_settings": {},
  "effective_settings": {},
  "started_at": null,
  "duration_seconds": null,
  "actions": [],
  "observations": [],
  "artifacts": [],
  "limits": [],
  "reason": "This feature gate has not run. Baseline and fixture-only checks are separate evidence."
}
```

## Safety

Tests use generated/authorized media. Do not publish user DVD/recording payloads or decoded private subtitle text. Remote tests must not retune unrelated clients, change recordings, clear watch history, delete files, overwrite settings or write to shares without the explicit narrow operation being approved. Recovery/cleanup is session-owned and repeatable.
