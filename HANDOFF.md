# Handoff — OpenSageTV Vibe Web Client 3.2.30

## 3.2.30 publication checkpoint

The user explicitly approved creation of the public
`opensagetv-vibe/opensagetv-vibe-web-client-plugin` repository and initial
plugin release. The source/privacy audit found no embedded credentials or
private network paths. Complete local Java, JavaScript, deterministic package,
generated fixture, DVD/caption/recovery, and Chromium validation passes.

The public package requires OpenSageTV Vibe FFmpeg Plugin 0.1.4 or newer and
uses its `SageTVTranscoder` bridge. Stock `Sage.jar` and stock SageTV `ffmpeg`
remain unchanged. The release is a beta because the explicitly open physical
long-run, complete DVD-title, Safari, and additional-device rows remain open.

# Historical handoff — v3.2.29 DVD startup recovery fix


## Active OpenSageTV Vibe 3.2.30 work

The supplied `SageTV-WebPlayer-Local-Plugin-v3.2.29-P14-DVDStartupRecovery.zip`
was imported as the baseline. The project now uses the common Vibe root
workflow and requires the platform-matching OpenSageTV Vibe FFmpeg Plugin.
Production transcoding resolves that plugin's `SageTVTranscoder` bridge; stock
`Sage.jar` and stock `ffmpeg` remain unchanged.

Local build/validation and `.232` commissioning are complete for all currently
runnable non-DVD rows. The exact-path authored Vibe DVD now starts hardware
HLS, renders its background and SPU highlight, enters Languages, handles DVD
Return/DVD Menu, and repeats the root-to-submenu path without a browser error.
Generated forced-hardware failure also proves final software fallback waits for
input EOS instead of timing out a valid short cell. Exact results and the still
open title/chapter/audio/subtitle, Stop/replay, physical/menu-less disc, and
long-run rows are in `docs/VALIDATION-3.2.30.md`. Do not publish the plugin or
public catalog entry until the user gives final approval.

## Historical 3.2.29 field fix

The 2026-09-26 field diagnostic showed MiniDVDPlayer pushing/draining MPEG-PS while the browser had no HLS session. DVD startup previously lacked the staged runtime fallback used by ordinary HLS. 3.2.29 adds full-hardware -> encode-only -> software retry and a bounded 16 MiB startup replay buffer. `tests/DvdStartupFallbackSmoke.java` deliberately kills the first full-QSV-style DVD start after bytes are consumed and verifies recovery at fallback stage 1 with playable HLS.

## Field symptom and root cause
- v3.2.27 starts DVD HLS playback with hardware transcoding, but a real DVD menu can show only the colored SPU/button highlight on a black background.
- The menu video is commonly a one-picture MPEG-2 cell. The browser previously sampled the `<video>` element only when SageTV issued FLUSH. By that point the short HLS cell can already be ended/cleared, so the background frame is lost while the independent SPU overlay remains.

## 3.2.29 fix
- Cache decoded DVD frames per native DVD generation while they are actually available.
- At FLUSH/freeze, stop sampling that generation and promote the cached frame to the retained DVD still canvas.
- Keep the prior retained menu background across SPU-only/no-video replacement generations. A cache miss never paints black over a valid retained frame.
- Hide the retained frame only after the replacement DVD generation produces a real decoded frame.
- Native freeze events now include the DVD generation; diagnostics add `dvdMenuFrame` generation/cache/promote/miss/visible state.
- DVD hardware decode/VPP/H.264 encode and AAC output from v3.2.27 are unchanged.

---

# Handoff — v3.2.27 DVD hardware HLS transcode startup fix

## Field failure and root cause
- The 3.2.26 capability fix worked: stock SageTV selected MiniDVDPlayer and pushed MPEG-PS into the browser MiniClient.
- Field diagnostics showed `dvd.active=true`, about 4.8 MiB pushed, the 4 MiB ring almost full, but `stream=null` / no HLS session.
- Root cause: `buildDvdHlsCommand()` appended FFmpeg hardware-decode input options after `-i pipe:0`. FFmpeg input options must precede the input, so the hardware DVD converter could terminate before producing an HLS segment.

## 3.2.27 fix
- Emit QSV/NVDEC/VAAPI/D3D11VA/DXVA2/VideoToolbox decode options before the DVD MPEG-PS input.
- DVD always uses H.264 video + AAC audio output; it never inherits ordinary-media `Video processing = Copy`.
- Preserve the browser encoder selection for DVD (Auto/QSV/NVENC/VAAPI/AMF/VideoToolbox/Software).
- Expected successful field diagnostics: an HLS session appears with `dvd=true`, `encoder=h264_qsv` (on the reported Intel system), `hardwareDecode=true`, `decodeAccelerator=QSV`, `hardware=true`, and `playlistReady=true`.
- The existing stock-JAR native DVD capability role, STV caption control, SPU/menu handling, and bounded push/drain protocol are unchanged.

---

# Handoff — v3.2.26 native DVD role default fix

## 3.2.26 field fix
- The native DVD wire/HLS/SPU implementation already existed, but capability negotiation still defaulted `sagetv.webplayer.nativeDvdProtocol` to false from the old P07/P08 development gate.
- Stock SageTV could therefore reject DVD playback with `ERROR (-19): There is no MediaPlayer that can playback the selected file` before any DVD media commands reached the plugin.
- Native DVD role is now enabled by default: `INPUT_DEVICES=IR,KEYBOARD,TOUCH,TV` and `PUSH_AV_CONTAINERS=MPEG2-PS`.
- `-Dsagetv.webplayer.nativeDvdProtocol=false` remains an explicit rollback/troubleshooting switch.
- Browser mouse input remains available through the web input endpoint; omitting `MOUSE` here is only the SageTV player-role negotiation signal.
- STV caption control from 3.2.25 and full hardware transcoding from 3.2.24 are unchanged.

---

# Handoff — v3.2.25 STV-controlled browser captions

## 3.2.25 field fix
- Stock STV/SageTV caption authority now uses the STV `VIDEO_CC_STATE` as the control plane while rendering CEA-608 locally in the browser.
- This fixes the field case where the SageTV playback menu showed `Captions (CC1)` but the web MiniClient displayed no captions, while explicit local CC1 worked.
- `VIDEO_CC_STATE=0` disables/clears the browser decoder; states 1..4 select CC1..CC4. Property-absent peers retain the existing CC1 fallback.
- Diagnostics report `browser-local-stv-controlled`, the observed STV state, and resolved CC service.
- Hardware decode/VPP/encode work from 3.2.24 is unchanged.


Hardware-first transcoding now attempts both hardware decode and hardware encode on NVIDIA, Intel/QSV, VAAPI, AMD/AMF and Apple VideoToolbox. NVIDIA/QSV/VAAPI also attempt GPU-resident deinterlace/scale where FFmpeg exposes a tested filter chain. If full hardware startup fails, HLS/fMP4 retries the same hardware encoder with software decode/filtering before libx264. Diagnostics now identify decode accelerator, GPU filter use and fallback stage.

For the reported Alder Lake-N case, expected successful diagnostics are `hardwareDecode=true`, `decodeAccelerator=QSV` (or VAAPI), `hardware=true`, and preferably `hardwareFilters=true`.

---

# Handoff — v3.2.23 real-parameter MPEG-2/HLS A/B test

## What changed

v3.2.23 keeps v3.2.22 ffprobe→FFmpeg probing and explicit Copy behavior, then tightens the MPEG-2 compatibility path around the supplied 1280×720p59.94 broadcast example. Progressive sources no longer receive an unnecessary `yadif` filter when Deinterlace is Automatic, and Source FPS/Source resolution continue through H.264 fallback unchanged.

`/codec-test.html` adds two synthetic fixtures generated from one source matching the known recording parameters: MPEG-2 Main 1280×720 progressive 60000/1001 fps, ~6.3 Mb/s video, English AC-3 5.1 384 kb/s and Spanish AC-3 stereo 192 kb/s. Test D uses `-c:v copy` plus AAC stereo audio conversion. Test E uses the exact same source, H.264 video at 1280×720p59.94, and AAC stereo. The original A/B/C controls remain.

## Field test

Open `/SageTVWebPlayer/codec-test.html`, regenerate the fixtures, run all tests, and compare D/E. On normal playback, use Compatibility — HLS + Copy with fallback OFF to prove native Copy behavior, or fallback ON to verify same-position H.264 recovery. Export diagnostics afterward; v3.2.22+ should report `probeMethod=ffprobe|ffmpeg` and the actual source codec.

## Version

3.2.23. Stock Sage.jar remains unchanged.

---

# Handoff — v3.2.22 ffprobe → FFmpeg source-probe fallback

## What changed

v3.2.22 keeps the v3.2.21 Copy passthrough behavior and fixes source metadata discovery when `ffprobe` is not installed alongside FFmpeg. The probe path now tries `ffprobe` first and automatically falls back to parsing bounded `ffmpeg -i` input metadata. Diagnostics report `probeMethod` so field tests can prove which path supplied the source codec.

- `videoMode=copy` now means: attempt `-c:v copy` for `0:v:0`, preserve source resolution/FPS/interlace, and let FFmpeg/HLS plus the browser determine compatibility.
- `videoMode=auto` remains conservative and unchanged.
- Explicit Copy is also allowed when ffprobe cannot identify the source codec.
- The browser's zero-dimension compatibility fallback is no longer MPEG-2-specific; any explicit HLS Copy session can fall back to H.264 at the same position when **Allow fallback** is enabled.
- The legacy `mpeg2Copy` status field remains for diagnostic compatibility.

## Test target

For a clean forced-copy test use **Player = Compatibility — HLS**, **Video processing = Copy**, Source resolution/FPS, and Deinterlace Auto/Off. Disable **Allow fallback** temporarily if you need to prove whether the browser itself decodes the copied codec.

## Version

3.2.22. The isolated `/codec-test.html` page remains available.
