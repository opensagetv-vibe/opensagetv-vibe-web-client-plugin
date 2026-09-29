# SageTV Web Player 3.2.4 — Vibe overlay and stable full-browser video

Date: 2026-09-24. Base: supplied SageTV-WebPlayer-Local-Plugin-v3.2.3.zip.

## Scope

Two user-requested changes: the actual Vibe Android overlay/icons instead of
the generic browser text grid, and video sizing that does not shrink/change
when native SageTV menus/OSD appear or disappear. No GitHub write/push occurred.
The generated ZIP retains the `sagetv-webplayer-v30-vibe/` root.

## Implemented overlay port

Reference: opensagetv-vibe/opensagetv-vibe-android-client, tree
`f1ba340e05fe18eaaba939253a933c3dfe6d6caf`, source/dev. The navigation.xml blob
is `b55d384b6dcb9c86bf2cfa771f1a9d615710ed77`. NavIconButton, dimensions,
transparent non-dimming dialog theme and #A0000000 button background were read
from the same app's resources. This is not based on the old OpenSageTV client.

Actual layout: Home top left; Help/Close top right; Options/Info/Back and D-pad
left center; green Hide plus Page Up/Down right center; Keyboard lower left;
seven transport icons along the bottom; two-row video/diagnostic tools lower
right. Solid-circle Select, triangle Video Info, cyan-underlined Audio and
subtitle-text icons are the source app's icons, not generic substitutions.

28 vector path sets were converted directly to SVG. Home/Close are unchanged
96px PNGs. `third_party/vibe-icons.json` records source blob IDs, shapes/colors,
and output checksums. Apache-2.0 notices are in the source ZIP and WAR. Icons
are local assets, also included in the service-worker shell; no new external
asset preparation is required.

Browser adaptations are explicit:
- Phone/short-stage layouts reflow/scale to keep buttons accessible. Android
  dp metrics cannot be identical to CSS pixels on every device.
- X and green Hide dismiss the overlay. Android X exits its Activity; the
  browser version intentionally does not close a tab or interrupt playback.
- Help also houses retained keypad, volume/channel, guide/recordings, timeline
  and render-only tools; those are not added to the Android edge layout.
- Video/audio/caption icons link to working browser controls, not Android-only
  player APIs. Existing local CEA support is unchanged; full Android caption
  authority, Teletext and bitmap/file subtitle support are NOT added.
- The test icon observes current browser time/frame progress for two seconds
  without starting/seeking/unpausing media. Paused/changed sessions report
  not-tested. It is not the Android physical-device compatibility test.

Native Home/Options/Info/Play/Stop actions dismiss the overlay as tagged in the
Android layout. Navigation/page/skip holds repeat and stop on release/cancel.
Opening/closing the overlay never itself sends a Play/Pause/Seek/Stop command.
Nested keyboard/tools/info panels have Escape and tab-focus containment.

## Video regression and correction

The original 3.2.3 browser clearVideoMask called applyVideoBounds for accepted
black clear/fill paint rectangles. A mask thus became a new video destination.
In a controlled test using the untouched 3.2.3 WAR and a 1600x1000 browser,
the same black-clear sequence reduced the CSS video width from about 1599px
to 1391px. This independently reproduces a concrete defect consistent with
the screenshots; it is not a replay of the user's actual native packet trace.

3.2.4 separates paint masks from video geometry. Near-full native destinations
(>=60% of the logical screen on both axes) use the browser stage in Browser
placement mode. Explicit smaller rectangles remain native menu previews.
That classification is a compatibility heuristic, not a new server protocol;
Follow SageTV rectangles exactly remains available for unusual STV layouts.

Color-key rectangles can still establish legacy previews. Ordinary black
paint no longer pans/shrinks video. Recognized opaque edge mattes in full
playback reveal the independent video plane; translucent native OSD panels
are preserved. Masks on offscreen surfaces are not treated as video bounds.
Native source rectangles are recorded for diagnostics; this patch does not
implement a general source-crop/overscan editor.

Automatic UI mode maps all stage CSS pixels, including logical-size rounding
and native size caps. Full video uses the stage even with a fixed-size UI.
Preview and pointer mapping use the same X/Y transform. Layout is updated on
native dimensions, media metadata/size, menu hints, browser/visual viewport
resize, fullscreen and overlay changes. These changes do not restart FFmpeg.

## Fill, Fit and Stretch

Default is **Fill browser** (`cover`): source proportions retained, no added
bars, possible edge crop on a differently shaped window. **Fit** (`source`)
shows the whole source with bars as required. **Stretch** (`fill`) shows the
whole source without bars, at changed proportions. Legacy Zoom, forced 4:3 and
16:9 remain available. Encoded black bars are not detected/removed.

The old implicit Source default migrates once to Fill with
`displayPolicyVersion=2`. Later explicit Fit choices are not overwritten.
Video placement Browser/native is separate from aspect mode. A SageTV Source
aspect event no longer overrides the selected browser policy. The aspect icon
cycles Fill -> Fit -> Stretch immediately, without a transcode restart.

## Unchanged boundaries

Only PluginVersion.java changed on the Java side. Native startup gating,
RSA/Blowfish, GFX/media ordering, type-5 recovery, server address/credentials,
stock Sage.jar and all FFmpeg/MIM files remain unchanged. No change to the
HLS/TS buffer algorithm, quality/audio/CC command construction or cache setup.
The JavaScript renderer remains on the browser main thread with its existing
cooperative scheduling; this is not a new GFX worker implementation.

## Installation and field test

Stop SageTV/Jetty. Back up the old WAR outside jetty/webapps. Install the ZIP's
`dist/SageTVWebPlayer.war` at `jetty/webapps/SageTVWebPlayer.war`. Rename the
standalone download to that name. Restart, Ctrl+F5, verify 3.2.4 in toolbar
and `/SageTVWebPlayer/api/health`. Do not deploy versioned and canonical WARs
together. Do not clear recordings, properties, client IDs or the vendor cache.
Use Automatic UI and remove stale `?ui=...` URL overrides for full-window UI.

Test the same recording: show/hide the native Info/OSD repeatedly, open/hide
the new remote, resize the browser, enter/leave fullscreen, then verify a
small menu preview. The video placement should not change merely because a
black clear/OSD overlay changes. Verify Fill/Fit/Stretch tradeoffs separately.
When investigating a remaining mismatch capture diagnostics immediately;
`videoLayout` records requested/effective rectangles and aspect policies.

## Validation

14 new Node display checks + 22 new Chromium scenarios. 30 asset entries are
verified for recorded SVG paths/colors, SHA-256, original PNG Git blobs and
HTML/offline-shell inclusion. 71 Chromium scenarios total pass across the
new and four inherited suites. Native startup13, recovery11, crypto14 and
all inherited stream/FFmpeg/caption groups also pass: 235 counted scenarios,
plus protocol/legacy utilities/provenance/package checks. See VALIDATION.md
and the unedited transcripts for the counts and individual evidence.

The long validation command hit the environment's execution time limit in the
HLS runtime group; that group and remaining suites passed separately. The
combined browser command also hit the execution limit during the final new
suite, which passed when rerun separately. No monolithic exit-0 is claimed.

Browser network navigation was administratively unavailable. Production HTML,
CSS and scripts were injected into Chromium; the actual bundled icon bytes
were loaded with data URLs and a generated H.264 MP4 was decoded via a blob
URL. Native events and HLS/mpegts.js transport were mocked. This validates
local decode/layout, not vendor MSE streaming. OS-level download/fullscreen
behavior, live SageTV/SageMC, the user's files, physical GPU, Windows/macOS and
Safari remain field tests. No performance increase is claimed for this patch.
