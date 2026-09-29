# Validation — 3.2.3

All required groups passed across split executions on the final production WAR.
The monolithic validation command hit the container execution time limit during
HlsRuntimeSmoke after eight cases; its output ended with an incomplete
`Exception in thread "main"` line. That interrupted run is retained unedited.
The COMPLETE HLS runtime suite was then rerun alone and exited 0 with ten PASS
cases, including the 240-second fixture. All remaining groups separately
completed with exit 0. No monolithic exit-0 result is claimed.

| Group | Named cases | Evidence |
| --- | ---: | --- |
| Core / gestures | 27 | VALIDATION-v3.2.3.txt |
| Existing HLS lifecycle | 31 | VALIDATION-v3.2.3.txt |
| Native startup ordering (new) | 13 | VALIDATION-v3.2.3-final-native.txt |
| Native GFX recovery | 11 | VALIDATION-v3.2.3-final-native.txt |
| Native authentication | 14 | VALIDATION-v3.2.3-final-native.txt |
| Legacy growing-file / FFmpeg | 10 | VALIDATION-v3.2.3-hls-runtime.txt |
| Streaming Servlet API | 13 | VALIDATION-v3.2.3-streaming.txt |
| Streaming / caption runtime | 14 | VALIDATION-v3.2.3-streaming.txt |
| TS adapter / settings / CC | 17 | VALIDATION-v3.2.3-streaming.txt |
| Chromium remote / GFX | 15 | VALIDATION-v3.2.3-browser.txt |
| Chromium layout / scheduling | 12 | VALIDATION-v3.2.3-browser.txt |
| Chromium streaming settings | 12 | VALIDATION-v3.2.3-browser.txt |
| Chromium recovery | 10 | VALIDATION-v3.2.3-browser.txt |
| **Total counted** | **199** | Not live playback tests |

Additional checks: native protocol wire smoke, legacy Node utilities,
untouched 3.2.2 premature-event reproduction (VALIDATION-v3.2.3-baseline.txt),
source/WAR byte equality, local package MD5, class target52, and clean patch
whitespace (VALIDATION-v3.2.3-package.txt).

## Boundary and confidence

The user log shows native UI receiver NullPointerException after initial resize,
followed by UI teardown; crypto query occurs later. The untouched 3.2.2 WAR
reproduces its early resize/repaint packet sequence over real TCP. Tests enforce
that no unsolicited UI event is sent until first completed frame ACK, while
property/crypto/image/media replies still permit initialization to progress.

The peer models that readiness boundary; it is not a running SageTV server.
The exact user's server JAR is not bundled or executed. New build has not been
tested on that live installation. Browser DOM/Canvas/JS are real, native/vendor
transports are mocked; synthetic FFmpeg software encodes, parser and file IO
are real. No physical GPU, real mpegts.js/MSE, Safari, or Windows/macOS runtime
validation is claimed. No measured performance improvement is claimed.

The old recovery/auth tests were updated to expect initial resize/repaint after
first frame, not INIT. Auth concurrent-input tests now start after a completed
frame; cryptographic transition and padding assertions remain. The explicit
historical --expect-unsupported mode retains its old INIT event adapter.

## Field gate

Reach a rendered STV/SageMC or login frame, resize the browser, then test input
and media. In a new diagnostic, startupEvents.ready and firstFrameCompleted
should be true for an active UI. Before that boundary, sentUiEvents should be0.
No reset of server authentication, stored client profiles, or buffers is needed.
