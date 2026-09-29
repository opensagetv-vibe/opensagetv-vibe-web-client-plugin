# Changelog

## 3.2.30 — Unreleased

- Imported the owner-supplied 3.2.29 P14/DVD-startup-recovery tree into the
  `opensagetv-vibe-web-client-plugin` project without discarding its historical
  parity and validation evidence.
- Added the standard Vibe workflow, governance, release metadata, security,
  licensing, handoff, task, and repository-check contracts.
- Made the platform-matching OpenSageTV Vibe FFmpeg Plugin an installation
  dependency and changed production discovery to its `SageTVTranscoder`
  bridge. SageTV's stock `ffmpeg` remains untouched and is no longer selected
  directly by this web plugin.
- Retained explicit transcoder/legacy FFmpeg overrides for bounded regression
  fixtures and diagnostics.
- Added deterministic Linux/Windows plugin packages and unified build-image
  commands, then passed the complete Java, JavaScript, real-FFmpeg, package,
  Playwright, and Chromium validation suite.
- Normalized WAR and plugin-ZIP entry timestamps to `SOURCE_DATE_EPOCH` and
  added a repeat-build gate; two consecutive builds must now be byte-identical.
- Commissioned the canonical WAR on `.232` and proved plugin-owned FFmpeg 9.0.1
  discovery, QSV capability gates, generated hls.js/mpegts.js playback, real
  MPEG-2/AC-3 playback and seek, CEA, Teletext, DVB bitmap, and redacted/hash-
  verified diagnostic export.
- Kept unavailable H.265/file-subtitle/live-recording field rows and the
  unsuccessful physical DVD SPU/menu matrix explicitly open; see
  `docs/VALIDATION-3.2.30.md`.

## 3.2.29 — 2026-09-26

- DVD startup now uses the same staged recovery policy as ordinary HLS: full hardware decode/VPP/encode, then hardware encode with software decode/filters, then libx264 when fallback is allowed.
- A bounded 16 MiB MPEG-PS startup replay prefix survives failed DVD decoder starts, so fallback does not lose the short title/menu cell already consumed by the failed FFmpeg process.
- DVD diagnostics now expose fallback stage/reason and replay byte/truncation state.
- Fix native DVD menus that showed only the SPU/button highlight over a black background. The old browser path captured a menu still only when FLUSH arrived, which is too late for one-picture cells after Chrome has already released the decoded frame.
- Maintain a generation-scoped browser frame cache for DVD playback and promote the last proven decoded frame to the retained DVD still canvas at the native FLUSH/menu boundary.
- Keep the previous menu image visible while the next DVD generation is attaching or contains only SPU/no playable video; never replace a good retained menu image with black because a replacement cell produced no decoded frame.
- Release the retained still only after an actual decoded frame from the replacement generation has been copied. Late timeupdate/ended events from the frozen generation cannot erase the menu background.
- Tag the native DVD freeze event with its generation and expose DVD menu-frame cache/promote/miss state in support diagnostics.
- Preserve the 3.2.27 full hardware DVD MPEG-2 -> H.264/AAC transcoding path unchanged.

# DVD hardware HLS transcode startup fix 3.2.27 — 2026-09-25

- Fix the DVD FFmpeg command so hardware decode options (`-hwaccel`, hardware output format and device selection) are emitted before the MPEG-PS `-i pipe:0` input, as required by FFmpeg.
- This fixes the field failure where SageTV successfully entered native DVD mode and filled the bounded MPEG-PS ring, but no HLS session was created and playback never started.
- DVD-Video always transcodes MPEG-2 video to H.264 and audio to AAC for browser playback; an ordinary-recording `Copy` preference cannot accidentally turn DVD into MPEG-2 copy.
- Honor the browser-selected encoder (Auto/QSV/NVENC/VAAPI/AMF/VideoToolbox/Software) for DVD transcoding. Hardware-first continues to prefer hardware decode + processing + encode when the backend probe succeeds.
- Add regression coverage for DVD input-option ordering, forced H.264/AAC DVD output, encoder preservation, and the existing real FFmpeg DVD A/V/drain path.

# Native DVD role default fix 3.2.26 — 2026-09-25

- Enable the completed stock MiniDVDPlayer capability role by default so unchanged SageTV can select DVD playback without an extra JVM flag.
- Default MiniClient capability negotiation now omits `MOUSE` from `INPUT_DEVICES` and advertises `MPEG2-PS` push, which is the stock role used by the existing native DVD wire/HLS/SPU backend.
- Keep `-Dsagetv.webplayer.nativeDvdProtocol=false` as an explicit troubleshooting escape hatch that restores the ordinary browser/PULL identity. Browser mouse events continue through the explicit web input endpoint; `TOUCH` remains advertised.
- Add regression coverage for default-on, explicit-off and explicit-on DVD role negotiation.
- Retain 3.2.25 STV caption control and 3.2.24 full hardware transcoding unchanged.

# STV-controlled browser captions 3.2.25 — 2026-09-25

- Fix STV/SageTV caption authority on the web MiniClient: the STV `VIDEO_CC_STATE` now drives the already-proven browser CEA-608 decoder instead of relying solely on the legacy event-225 path to become visible through stock STV graphics.
- STV Off clears/disables local captions; STV CC1..CC4 select the matching local CEA service immediately without changing the saved caption-authority mode.
- Preserve property-absent fallback as CC1 for older peers, and keep explicit local CC1/CC2/DVB modes unchanged.
- Add diagnostics for STV-controlled browser rendering and a dedicated regression for STV Off/CC1/CC2 transitions.
- Retain the full hardware decode/process/encode pipeline from 3.2.24 unchanged.

# OpenSageTV Vibe Web Client 3.2.30 — 2026-09-29

- Established the standalone stock-SageTV-compatible Vibe Web Client project
  from the preserved 3.2.29 baseline and synchronized runtime version 3.2.30.
- Uses the separately installed OpenSageTV Vibe FFmpeg Plugin 0.1.4 or newer
  through `SageTVTranscoder`; the WAR contains no FFmpeg/MIM executable and
  does not replace stock SageTV `ffmpeg` or require a modified `Sage.jar`.
- Includes browser MiniClient/SageMC playback, MPEG-2/H.264 HLS, hardware and
  software fallback, CEA/Teletext/DVB/ordinary subtitles, DVD menu/SPU
  navigation, seeking, diagnostics, and responsive Vibe controls.
- Complete local Java, JavaScript, deterministic package, generated-media,
  DVD/caption/recovery, and Chromium gates pass. Remaining physical long-run,
  multi-device, full DVD-title, Safari, and second-GPU rows stay explicitly
  open in `TASKS.md` and `docs/VALIDATION-3.2.30.md`.

# Full hardware transcode pipeline 3.2.24 — 2026-09-25

- Hardware-first now means hardware decode + hardware encode for every supported GPU backend, not encoder-only.
- NVIDIA tries NVDEC/CUDA → CUDA VPP → NVENC; Intel tries QSV decode → QSV VPP → QSV encode; VAAPI tries VAAPI decode/VPP/encode; AMD AMF tries D3D11VA/DXVA2 (or VAAPI where available) decode + AMF encode; VideoToolbox tries VideoToolbox decode + encode.
- Auto prefers a backend that can initialize both decode and encode over an encode-only backend.
- Startup fallback is now staged: full hardware → same hardware encoder with software decode/filters → libx264.
- Diagnostics expose decode accelerator, hardware-filter state, fallback stage, and whether the stream is end-to-end GPU.
- Add command-level coverage for all GPU backends and preserve the v3.2.23 MPEG-2 cadence/codec tests.

# Real-parameter MPEG-2/HLS field test 3.2.23 — 2026-09-25

- Keep MPEG-2 → H.264 compatibility fallback at source resolution and source frame cadence. For progressive sources, **Deinterlace = Automatic** no longer inserts an unnecessary `yadif` filter.
- Extend `/codec-test.html` with D/E fixtures matching the known real recording parameters: MPEG-2 Main, 1280×720 progressive, 60000/1001 (59.94) fps, ~6.3 Mb/s video with 19.4 Mb/s VBV ceiling, English AC-3 5.1 384 kb/s and Spanish AC-3 stereo 192 kb/s.
- D copies MPEG-2 video and converts the selected English AC-3 track to 48 kHz stereo AAC; E transcodes the exact same synthetic source to H.264 at 1280×720p59.94 and AAC.
- Retain the original A/B/C 360p30 codec controls. The real-parameter fixture remains synthetic and does not reproduce programme picture content or caption payloads.
- Add real-FFmpeg assertions that D remains MPEG-2 1280×720p59.94 and E becomes H.264 1280×720p59.94.

# Source probe fallback 3.2.22 — 2026-09-25

- Try `ffprobe` first for structured source metadata using the configured override, an executable next to FFmpeg, or PATH.
- If `ffprobe` is unavailable/fails/returns no tracks, automatically fall back to a bounded `ffmpeg -i` input probe using the same FFmpeg executable that performs playback.
- Parse video/audio/subtitle codec, source PID, language, resolution, field order, frame rate and channel count from FFmpeg input metadata when the fallback is used.
- Expose `probeMethod` in source diagnostics so reports show whether metadata came from `ffprobe` or FFmpeg fallback.
- Keep Auto/Copy playback policy unchanged; this only prevents missing `ffprobe` from forcing source codec metadata to Unknown.

# Copy passthrough policy fix 3.2.21 — 2026-09-25

- Explicit **Video processing = Copy** no longer uses a codec allow-list before FFmpeg starts. It now attempts `-c:v copy` for the source video stream, including when ffprobe metadata is unavailable.
- **Auto** remains conservative: it only copies codecs already considered browser-compatible by the existing policy.
- HLS Copy fallback is now codec-agnostic. If the browser timeline advances but no video dimensions are produced and **Allow fallback** is enabled, playback restarts at the same position as H.264.
- This fixes normal playback rejecting a source before FFmpeg could try the same stream-copy path exercised by `/codec-test.html`.

# Copy selector persistence fix 3.2.21 — 2026-09-25

- Fix **Video processing → Copy** not persisting when the settings panel was closed/reopened before the old explicit Save/Apply workflow completed.
- Selecting **Copy** now immediately saves the browser streaming profile when idle.
- When media is active, selecting **Copy** goes through the existing debounced Apply path and restarts only the media stream at the current reported position; native MiniClient/GFX remains connected.
- Keep Auto conservative, keep the 3.2.19 MPEG-2/H.264/HEVC Copy policy unchanged, and retain the guarded MPEG-2 0×0-video fallback to H.264.
- Add a Chromium regression that selects Copy, verifies local persistence, closes/reopens Settings, and confirms Copy remains selected.

# Copy video mode with MPEG-2 HLS checkpoint 3.2.19 — 2026-09-25

- Make **Copy** the single explicit no-video-transcode option; normal Auto remains conservative.
- Permit `mpeg2video` to use the existing FFmpeg `-c:v copy` HLS path when **Copy** is selected. H.264/HEVC remain supported by the same Copy option.
- Treat Deinterlace Auto as source pass-through in Copy mode. Resize/FPS changes or Deinterlace On are rejected because those operations require a video encode.
- Add one guarded automatic H.264 fallback when HLS time/audio advances but the browser reports 0×0 video after eight seconds. The restart preserves the absolute playback position and obeys **Allow fallback**.
- Bump browser streaming-profile schema to 8 and retain the 3.2.18 isolated codec-test page.
- Add policy and browser-lifecycle regressions for MPEG-2 Copy, incompatible processing rejection, same-position H.264 fallback, successful native decode with no fallback, and migration of the temporary `mpeg2copy` profile value to `copy`.

# Native HLS codec field-test checkpoint 3.2.18 — 2026-09-25

- Add an isolated `/codec-test.html` page for testing Apple/native HLS without changing normal SageTV playback.
- Generate fixed 8-second synthetic MPEG-2/AAC and H.264/AAC fixtures with the plugin-configured FFmpeg; no recording path or user FFmpeg arguments are accepted.
- Compare MPEG-2 video copied unchanged into HLS TS, the same MPEG-2 source transcoded to H.264, and H.264 copied unchanged into HLS TS.
- Keep the browser test native-HLS-only (no hls.js) so iPhone/iPad/Safari results measure the Apple media stack directly.
- Add bounded temp output, 30-minute reuse, strict media-path allowlisting, traversal rejection, and real-FFmpeg smoke coverage.
- This is a P14-derived diagnostic/field-test build; it does not start optional P15 Core/provider work or alter stock playback policy.

# P14 diagnostics/profiles/server-destination checkpoint 3.2.17 — 2026-09-25

- Replace the old observation-only playback check with an explicit bounded current-video test covering cadence/buffering, pause/resume and reversible seek on safe completed media; restore position/pause/mute/volume/rate and guard interrupted recovery by media ID.
- Add bounded redacted diagnostic ZIP export with SHA-256 report hash, terminal failure snapshot, recent test context, effective transcode mode, caption/subtitle timing/queue counters and DVD state without media/subtitle payloads or recognized credentials.
- Keep automatic terminal-failure capture default-off; manual browser download remains available even when external writes are disabled.
- Add schema-v1 saved-default profile export/import with preview, bounded unknown-key handling and explicit Apply. Client identity, current track/PID/page selections and administrator settings are excluded.
- Add administrator-configured mounted diagnostic destination support with explicit enable flag, bounded write/read/hash/delete test, atomic save, 10-file/10-MiB local spool, explicit retry and cooperative cancellation between file steps.
- Keep SMB/share credentials and full destination paths out of the browser, including redaction of configured destination/spool paths from filesystem failure details. Diagnostic output is independent of recording/media paths.
- Add P14 Java/Node/Chromium regression gates and narrow-window nested settings/D-pad focus checks.
- Real mounted-share failure/spool/retry remains WP14-006 field acceptance; earlier phase field gates are unchanged.

# P13 GFX/input/lifecycle checkpoint 3.2.16 — 2026-09-25

- Add an auditable GFX opcode ledger: implemented, negotiated-off and unknown opcodes are distinguishable in session diagnostics.
- Add default-off, per-session `GFX_YUV_IMAGE_CACHE=UNIFIED` negotiation and real format-256 Y/interleaved-UV browser conversion.
- Centralize pointer and video/overlay geometry so Fit/Fill/Stretch/Cover use one coordinate model across video, captions and DVD overlays.
- Add explicit physical-keyboard D-pad repeat ownership with keyup/blur/visibility cancellation.
- Add session-scoped pagehide retirement and bfcache fresh-generation reconnect; stale tabs cannot stop replacement sessions.
- Expand the service-worker shell to every current MiniClient module and report requested-vs-observable media worker behavior plus main-thread GFX truthfully.
- Keep unimplemented transforms/font/textured-diffuse/texture-batch/offline-cache contracts negotiated off. Stock UNIFIED DVB/Push OFF/ON A/B remains field acceptance.

# P12 audio/control/quality checkpoint 3.2.15 — 2026-09-25

- Prefer requested-language primary/default audio over commentary/NAR/descriptive tracks while preserving explicit track selection and configurable fallback.
- Expand signed audio delay to ±4000 ms / 25 ms and verify both signs with generated decoded-output calibration.
- Debounce live audio/quality changes and restart at the same reported position without reconnecting native GFX.
- Report requested/effective pipeline stages truthfully; CPU decode/filter + GPU encode is not end-to-end GPU.
- Add bounded keyframe interval/B-frame controls; copied video ignores encode-only settings.
- Advertise only proven 0.5–2.0x forward native rate; reverse/frame-step remain unsupported.
- Real broadcast PMT/layout transitions and receiver/ARC audibility remain field acceptance.

# P11 timeline/buffering checkpoint 3.2.14 — 2026-09-25

- Replace proportional pipe-source byte seeks with bounded MPEG-TS video PES PTS indexing and a post-input FFmpeg residual timestamp seek; unindexable sources never receive a guessed byte offset.
- Add seek diagnostics (`seekStrategy`, requested/anchor/residual/error, sample count and packet size) for field verification.
- Hold unexplained same-generation browser clock regressions at the last proven time and ignore browser-local DVD SEEK mutation because stock MiniDVDPlayer VM owns DVD navigation.
- Add bounded final-tail settle after recording completion and reject source shrink/replacement so stale byte identities cannot be reused.
- Suppress producer restart while source reads continue, classify recovery failures, preserve pause-time reserve filling and keep quota-reduced buffer caps.
- Serialize Comskip seek ownership to prevent duplicate EDL skips from overlapping timer ticks.
- P11TimelineSmoke passes a deliberately VBR TS seek, fail-closed unindexed source, final-tail growth and source replacement checks. Existing 240-second sustained HLS reserve remains PASS. WP11-008 multi-hour real-source acceptance remains field-gated.

# P10 source/metadata checkpoint 3.2.13 — 2026-09-25

- Require every MiniClient media path to resolve to one SageTV-authorized MediaFile; remove unsafe basename-first fallback and fail closed on duplicate names.
- Add safe literal-plus/percent/Unicode path decoding and unique stale-root suffix matching.
- Add disc taxonomy for VIDEO_TS, parent/mounted roots, ISO, physical DVD drive, VOB, imported MKV and BDMV with exact supported/unsupported stages.
- Add read-only `/api/disc?id=<MediaFileID>[&title=N]` inspection; no arbitrary path endpoint is introduced.
- Separate SageTV library/recording duration from explicit DVD-title duration and title-relative chapters.
- Probe installed FFmpeg for `dvdvideo`, `libdvdread` and `libdvdnav` before exposing an explicitly selected non-interactive movie-only title plan.
- Detect implausible imported duration/stream metadata without rewriting SageTV data or watch history.
- Keep ISO mounting server-owned and Blu-ray/BDMV/BD-J/encrypted/DRM outside the proven DVD scope.
- Six of seven P10 rows pass locally; WP10-004 remains field-gated on a representative multi-title authored DVD with a known non-first intended feature.

# P08 runtime checkpoint 3.2.12 — 2026-09-25

- Consume P07 native DVD MPEG-PS PUSH generations with real FFmpeg conversion to short-segment H.264/AAC HLS instead of treating accepted bytes as protocol-only data.
- Preserve cell/title decoder boundaries by starting a distinct converter epoch after each post-data FLUSH; do not concatenate unrelated DVD cells/VOBs into one demuxer state.
- Map SageTV DVD audio selectors by physical MPEG/private substream identity (AC-3/DTS/LPCM/MPEG) and fail unsupported/missing selectors visibly rather than selecting the wrong language.
- Map browser cell-relative playback back onto the server-owned DVD clock; keep SPU-only PES from consuming an A/V rebase and preserve the logical title timeline across discontinuities.
- Preserve authored PAL/NTSC cadence without a fixed 30 fps conversion rule.
- Add explicit one-picture menu still presentation and support tiny/audio-only cells; keep the previous authored frame visible across FLUSH until replacement video appears.
- Use a DVD-specific one-segment startup profile with bounded 6-second startup and 12-second steady reserve while retaining ordinary recording buffer behavior.
- Require both converter/input completion and browser consumption before reporting the native DVD drain sentinel. P09 still owns SPU subtitles, highlights and interactive menu navigation.

# P07 runtime checkpoint 3.2.10 — 2026-09-25

- Add lazy stock MiniDVDPlayer INIT-without-OPENURL state and distinguish synthetic startup bandwidth probes from real MPEG-PS/metadata/drain traffic.
- Own real DVD PUSH bytes in a bounded 4 MiB generation-aware ring with whole-payload acceptance, truthful free-space backpressure and exact lifetime/epoch accounting.
- Add commands 32-37 with bounded payload parsing and mandatory four-byte success/error replies while preserving the zero-byte MiniPlayer SEEK reply.
- Add 45 kHz NEWCELL/STC to 90 kHz DVD-clock conversion with 33-bit PTS wrap handling.
- Add transient 0x80 segment EOS, 0x100 drain/-2 state, harmless pre-data FLUSH suppression and post-data FLUSH reader generations; equal-sized replacement cells are distinct generations.
- Add an explicit stock native-DVD role gate. Normal P07 releases retain the browser/MOUSE/PULL identity; `-Dsagetv.webplayer.nativeDvdProtocol=true` enables the stock MiniDVDPlayer negotiation contract for field/protocol development while P08 remains responsible for visible A/V.
- Add direct bounded-state and real-socket wire regression tests. WP07-001 stock negotiation capture remains open; visible DVD playback is not claimed in P07.

# P06 runtime checkpoint 3.2.9 — 2026-09-25

- Added independent ordinary subtitle Off/Auto/Selected-track policy with language, delay and forced-only controls (profile schema 6).
- Added embedded and exact-basename sidecar discovery for supported text, PGS and DVD/VobSub tracks without browser-selected filesystem paths.
- Added sanitized Unicode text cues with overlap, authored basic WebVTT positioning and independent expiration; ASS/SSA advanced styling is flattened by the WebVTT adapter.
- Added PGS/DVD bitmap adaptation through FFmpeg into the existing DVB bitmap decoder and `LOCAL_FILE` straight-alpha RGBA browser canvas.
- Added a separate ordinary subtitle cue queue under the shared playback epoch, preserving coexistence with P05 broadcast captions and atomic seek/flush/source invalidation.
- Added production Chromium ordinary-subtitle smoke plus generated PGS-bearing MKV and embedded DVD-subtitle FFmpeg validation.
- WP06-004 remains external field acceptance for a genuine palette-bearing standalone IDX/SUB pair; prior phase field gates remain open.

# P05 runtime checkpoint 3.2.8 — 2026-09-25

- Added SageTV caption-authority modes OFF / CC1 / CC2 / STV / DVB with persistent virtual CC1/CC2 profiles.
- Added evidence-based Auto resolution for Teletext/CEA services while keeping DVB bitmap selection physically separate.
- Added negotiated legacy event-225 caption callbacks with presentation-clock gating, CEA reset/flush semantics, and stock property-absent CC1/CC2 fallback.
- Enforced single renderer ownership so STV callback captions and local browser captions do not duplicate each other.
- Bumped streaming profile schema to 5 while preserving schema-4 settings and defaulting new authority state safely to OFF.

# P04 runtime checkpoint 3.2.7 — 2026-09-25

- Add per-playback DVB bitmap subtitle decoding with descriptor 0x59 PID/language/composition/ancillary-page discovery and a bounded original-source reader.
- Decode DVB display-definition/page/region/CLUT/object state, 2/4/8-bit RLE pixel strings, transparency and timed page clears into straight-alpha RGBA cues.
- Render DVB on a dedicated browser canvas using the same Fit/Fill/Stretch/fullscreen video transform while leaving MiniClient GFX/video bounds unchanged.
- Add explicit DVB mode plus discovered service/PID/page/language controls; keep DVB outside Teletext, CEA virtual caption slots and ordinary file/DVD subtitle paths.
- Add ordinary-video media command 36/type 1 source-PID control with on-demand startup, late discovery, fail-closed unknown PIDs and disable sentinel 8192.
- Keep DVB decoding outside FFmpeg video encoding; no default subtitle burn-in is added.
- Generated Java and browser-controller acceptance passes. WP04-007 real unchanged UK DVB-bitmap visible/timing field acceptance remains pending and is not claimed.

# P03 runtime checkpoint 3.2.6 — 2026-09-24

- Add per-playback DVB Teletext Level-1 decoding adapted from the pinned Apache-2.0 Vibe Java core without its process-global active-session state.
- Discover descriptor 0x56 service types 2/5 with PID/language/page identity and handle 188/192/204-byte transport framing, PES/data-unit reassembly, Hamming-protected headers/rows and erase-page clears.
- Decode from the original authorized source independently of video copy/QSV/NVENC/VAAPI/software encoding; no second video stream is delivered to the browser.
- Present timed text from the browser video clock even while SageTV GFX/OSD is idle; seek/FLUSH/source generations clear stale page/cue state.
- Add Teletext mode, discovered service/page/language controls, schema-3 profile migration and staged payload-free diagnostics.
- Generated parser/controller/Chromium and inherited regression groups pass in split execution. WP03-008 real unchanged UK broadcast field acceptance remains pending and is not claimed. P04 is not started.

# P02 runtime checkpoint 3.2.5 — 2026-09-24

Implemented P02 shared playback/track/subtitle infrastructure. See `docs/parity/p02/ARCHITECTURE.md` and `VALIDATION.md`. No P03/P04/P06 decoder is advertised yet.

# P01 audit completion checkpoint r3 — 2026-09-24 (runtime unchanged)

- Reconciled all 1,066 top-level entries in the frozen Android changelog with zero unclassified scope records; retained 248 normalized functional fix records for browser disposition/task mapping.
- Inventoried 102 relevant regression-source files from 162 frozen-tree test files.
- Established the supplied live stock-server identity as SageTV 9.2.10.1054 with active SageTV7 STV path; preserved the no-extended-Core stock gate and documented missing binary hashes.
- Retained the untouched successful 3.2.4 baseline and fixture evidence.
- Resolved pinned release metadata for mpegts.js 1.8.0 and hls.js 1.7.3, but left real vendor MSE playback explicitly external-pending because this environment could not obtain/run those assets.
- Marked P01 exit gate complete locally with WP01-004 still not PASS. No runtime source/WAR/installer/server/user-setting changes. P02 is next.

---

# Parity checkpoint r2 — P01 audit, baseline and fixtures (2026-09-24)

- Kept runtime 3.2.4 and every original source/resource/test/build/deployable byte.
- Added248 normalized change dispositions and 68 named upstream-task mappings.
  Strict exhaustive literal fix/test intake remains PARTIAL, not falsely done.
- Freshly built and ran the untouched baseline with 235 counted scenarios,
  including 71 Chromium; real vendor MSE and live stock Core remain blocked.
- Added isolated baseline/source-delta/preflight/manifest tooling and synthetic
  Teletext/DVB/PGS/text/CEA/A-V/HLS/reserve fixtures with hashes and safe use rules.
- Retained independent reference-decoder proof and rejected initial fixture
  evidence separately; no browser subtitle or DVD support is claimed.
- Updated permanent task/evidence state:11/127 complete, P01 five complete,
  one partial, two external gates blocked. No GitHub/server/profile writes.

---

# Planning revision 1 — Android parity phases (2026-09-24; runtime unchanged)

- Add permanent P00–P17/WP task phases, dependencies and acceptance criteria
  for Teletext, DVB bitmap, native DVD and the remaining relevant Android fixes.
- Add reviewed gap/source/decision/test matrices, a frozen upstream/base lock,
  an exhaustive-intake ledger template and machine-readable task mirror.
- Keep optional extended-Core/provider work, Android-only features and upstream
  unresolved acceptance explicitly separate from stock-compatible requirements.
- Preserve every original runtime source, browser resource, test, build script,
  WAR and installer byte-for-byte. Keep runtime/build version 3.2.4.
- Regenerate the complete project manifest and verify documentation-only scope.
  No playback tests were rerun and no new runtime support is claimed.

---

# 3.2.4 — Vibe overlay and full-browser video

- Port actual Vibe Android edge layout and 30 source icon assets; package source
  provenance and license notices. Browser tools are in Help, not a giant grid.
- Connect video/audio/CC/diagnostic/statistics/aspect icons to working actions.
- Fix clear/fill mask operations changing video destination when OSD hides.
- Browser-owned full video with native small-preview and exact-native options.
- Fill default and one-time Source migration; Fit/Stretch remain selectable.
- Preserve translucent OSD, pointer mapping, cached images and native startup gate.
- 14 new display and 22 new browser scenarios, including real local H.264 decode.
- All required groups passed in split runs; live SageTV/GPU/MSE still untested.

# 3.2.3 — First-frame UI-event ordering (2026-09-24)

- Diagnose the server log's pre-frame MiniUIClientReceiver NPE; crypto query was a later symptom.
- Gate unsolicited UI events until the first FLIPBUFFER reply; keep initialization replies live.
- Coalesce startup resize/repaint/media updates and drop early user input, never replay at login.
- Preserve latest window dimensions, runtime resize, image handles, native recovery and encryption.
- Add first-frame/event-gate diagnostics and 13 real-TCP synthetic-server startup cases.
- Reproduce original 3.2.2 premature resize/repaint against its untouched WAR.
- No auth policy, Sage.jar, media configuration, FFmpeg/MIM, or installed plugin metadata changes.
- Live SageTV/SageMC verification remains required; see current validation evidence.

# 3.2.2 — Native authentication handshake

- Implement native RSA/Blowfish authentication handshake instead of returning empty CRYPTO_ALGORITHMS.
- Preserve binary public/session keys; encrypt nonempty reply/event bodies with original plaintext header lengths.
- Serialize old-mode enable/disable acknowledgements with input and property replies.
- Respect server authentication settings; do not change target host, credentials, or login policy.
- Suppress type-5 recovery while event encryption is active; retain diagnostics without exporting keys.
- Add 14 real-JCA/loopback authentication scenarios and reproduce the 3.2.1 failure with a synthetic auth-required peer.
- Live server reason/policy and physical SageMC login/playback remain unverified.


# 3.2.1 — 2026-09-24

Fixed native GFX recovery negotiation and type-5 reconnect, partial-frame handling,
worker startup ordering, socket cleanup/cancellation, and same-client-ID in-plugin
replacement. Terminal browser failures no longer endlessly reopen a dead native
session's HTTP stream. Recovery/diagnostics controls remain visible in render-only
and fullscreen views. Added bounded connection metadata and failure snapshot.
Added 11 real-loopback/fake-server and 10 production-browser/mock-HTTP regression
scenarios. This does not identify the source of the original server reset and is
not a live SageTV/GPU validation claim. Existing streaming settings are unchanged.

# Changelog

## 3.2.0 — 2026-09-24

Continuous MPEG-TS/mpegts.js, validated quality/encoder controls, source audio track/language/codec/channels/delay and original-video A/53 extraction with browser-local CEA-608/708. Reference: opensagetv-vibe/opensagetv-vibe-android-client/source/dev. Retains stock Sage.jar/Jetty/GFX/remote/HLS. Adds byte-resume/idle/output guards and regression tests. Vendor assets are server-cached, not bundled. Actual vendor/MSE and physical GPU/SageTV remain untested; full Android subtitle/CC parity is not claimed. See docs/RELEASE-v3.2.0.md.

## 3.1.1 — 2026-09-24

### Browser window and native graphics

Remove the 1280-pixel display cap. Default UI resolution follows the usable window (bounded at 1920×1080 render pixels); fixed 720p/1080p modes remain available and are contained without cropping. Notify SageTV with native resize/repaint at INIT and subsequent size changes. Keep cached image/surface handles when resizing. Canvas, video bounds and pointer mapping use one display rectangle.

### Fast start and continuous reserve

Automatic startup now waits for the first published segment regardless of producer rate. The HLS loader starts with a six-second forward target, then promotes to the configured reserve after the first append/progress (180 seconds default, 30–600 configurable). Low-delay mode no longer forces a small reserve. EVENT playlists retain recording position instead of finite live-edge catch-up. A no-progress loader wakeup works before starvation and while paused; available server data is not mistaken for a producer failure. Respect memory-pressure reductions and keep only 30 seconds of backward buffer.

### Scheduling and diagnostics

Media lifecycle events bypass the GFX image-decode queue. Ordered GFX work yields every 128 commands or approximately 8 ms; this is cooperative main-thread scheduling, not a GFX worker. Queue overflow is explicit, not silent command loss. Producer monitoring runs beyond startup, final snapshots are forced at EOF, and diagnostics include current targets, refill wakeups, native/display geometry and server thread activity. Hardware-first FFmpeg and stock Sage.jar remain unchanged.

### Validation boundaries

27 core/gesture tests, 31 playback tests, native loopback protocol test, 10 file/FFmpeg checks, and 27 offline Chromium checks; legacy tests retained. A 240-second synthetic recording verifies continued production beyond 180 seconds. Browser HLS buffering tests use mocks, not real MSE network playback; no user SageTV/SageMC/GPU/Safari field test or Python startup benchmark is claimed.


## 3.1.0 — 2026-09-23

### Python behavior port

Automatic 1–4 published-segment startup, HLS position zero, local 575-ms hold remote, double/middle/right trigger options, native click modes, repeat controls, Guide assist, side-button Back, literal keyboard entry, render-only view, full-duration timeline, aspect choices, mask/preview compositing and expanded buffer diagnostics.

### Correctness fixes

Undefined rebuffer pause variable; stale startup/status/seek events; duplicate and out-of-order mouse clicks; click leakage after holds/drags; premature ENDLIST recovery; recovery counters resetting indefinitely; overlapping status polling; native-browser timer binding; short completed clips waiting for nonexistent segments; old PWA shell masking upgrades.

### Server streaming

Cached keyed encoder probes; active files follow appended bytes rather than opening as finite files; dynamic segment rollover; final-byte drain and recording-completion duration; process identity and stop ordering; TS/PS stdin autodetection. Existing hardware-first behavior, stock Sage.jar and one browser-facing Jetty port are retained.

### Validation and boundaries

See the saved test transcript and port audit. Live SageTV/SageMC, Windows, hardware encoding, native Safari and measured comparison with Python remain unverified. Advanced Python backend controls and alternate Video.js pipelines are not all ported.

## 3.0.3 and earlier

Historical notes remain in `docs/README-v3.0.3-history.md`, `docs/handoff-v3.0.3-history.md` and the local-plugin manifest release history.

## v3.2.12 - 2026-09-25
- P09: added platform-neutral native DVD SPU assembly/RLE decode and straight-alpha browser overlay.
- Added DVD CLUT/alpha and SPUCTRL selected-button highlight recomposition without resetting queued future SPUs.
- Kept DVD physical subpicture selection independent from broadcast DVB/CC and ordinary file subtitles.
- Added generation-safe SPU lifecycle across DVD FLUSH and shared Fit/Fill/Stretch geometry; the overlay is pointer-transparent.
- Native D-pad/Select/Back/Menu/chapter commands continue through SageTV's existing MiniClient input path. Pointer DVD hit-testing remains field-gated.
