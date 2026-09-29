# Handoff — SageTV Web Player 3.2.4

## Current work

Both requested UI changes are implemented in this package, based exactly on
supplied 3.2.3. Read RELEASE-v3.2.4.md and VALIDATION.md under docs. No GitHub
repository changes were pushed. The Android source is a read-only reference;
this is the Java/Jetty WebPlayer project, not a changed APK.

## Important native safety boundary

Do not regress 3.2.3: unsolicited native UI events must remain gated until
AFTER the first FLIPBUFFER ACK. Do not send them from connect/INIT/STARTFRAME
or after a guessed delay. Crypto old-mode ACK and reconnect eligibility also
remain intact. Java behavior is unchanged in this patch; only version changes.
Never disable authentication or modify the stock server to compensate.
The earlier crypto-cause hypothesis was superseded by the server receiver NPE.
Historical incident evidence and handoffs are retained under docs.

## Overlay

navigation.xml blob b55d384b6dcb9c86bf2cfa771f1a9d615710ed77 from the requested
Vibe app controls the layout, not the obsolete OpenSageTV app. Source/ref and
all 30 icon checksums are in third_party/vibe-icons.json. 28 SVG path/color
conversions + 2 unchanged PNGs, no font/CDN. Verify with
`python scripts/verify-vibe-icons.py`. Keep license/NOTICE inside the WAR too.

The edge overlay is a child of stage, not a centered fixed card. Responsive
bottom-row reflow and stage-height container query prevent overlap on phones
and short landscapes. Native tagged dismiss actions are retained. X and green
Hide dismiss browser overlay rather than closing the tab. Help owns additional
keypad/volume/channel/timeline/browser actions. Playback check is explicitly a
local observation, not Android device commissioning. CC remains local CEA only.

## Video geometry

Core.stageRect supplies automatic full-stage or fixed logical-aspect canvas.
Core.videoPresentation separates full-video from explicit native small preview;
near-full = at least60% logical width and height, overridable with native mode.
Core.videoLayerRect maps full video to stage and preview to canvas axes.
core.fullscreenMatte recognizes opaque edge strips only in active full video.
miniclient.clearVideoMask NEVER changes bounds for ordinary black paint.
Only color-key may infer a legacy preview. Native source coordinates are
reported, not a newly implemented source-crop editor. Keep OSD translucency,
UI pointer mapping and image cache lifetime. No FFmpeg restart for layout.

Default browser videoFit cover; source=contain/Fit, fill=Stretch, zoom=cover.
The old implicit Source migrates once with displayPolicyVersion2; never reset
an explicit later Fit. Server aspect notifications are diagnostics, not an
implicit override of browser-selected fit. Diagnostics include videoLayout.

## Build and validation

    bash scripts/build-local.sh
    python scripts/package-local-plugin.py
    RUN_BROWSER_TESTS=1 bash scripts/validate.sh

Requires JDK --release8, javax.servlet3.1 API, Node, Python; optional FFmpeg and
Playwright/Chromium for runtime/browser suites. No Python runtime on deployment.
New tests: test-miniclient-display.js, browser-vibe-display-smoke.py. The latter
can reproduce old behavior with --baseline-war /path/to/v3.2.3.war. It uses the
production DOM/CSS/JS and actual local assets with synthetic native events.
Do not label mocks as live SageTV, hardware or mpegts.js/MSE coverage.

Full commands may exceed execution limits; separate group runs are valid
when each passes and all group evidence is retained. Current counts are in
BUILD_INFO.json/VALIDATION.md. Source-to-WAR and local installer equality must
be checked after final packaging; regenerate PROJECT_MANIFEST.sha256.

## Next field acceptance

Canonical WAR, restart, Ctrl+F5, confirm3.2.4 health+toolbar. Same recording:
menu hidden/shown, long-press overlay, Fill/Fit/Stretch, fullscreen/window
resize, small preview and resume. Keep stream settings unchanged initially.
Capture diagnostics before Reconnect on failure; do not include raw server
logs with Authorization headers in a public repository or release archive.
Retain stock Sage.jar/external FFmpeg/MIM. No hardware/MSE/Safari claims until
actually tested. Never replace user recordings or installed server metadata.
