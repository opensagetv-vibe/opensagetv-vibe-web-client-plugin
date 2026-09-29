# Planning addendum — proposed parity architecture (2026-09-24)

For future work read `parity/PHASE_PLAN.md` and `../TASKS.md`. They describe
planned changes, not runtime support in this3.2.4 WAR. Caption/DVB/DVD additions
must preserve the 3.2.3 first-FLIPBUFFER gate. The historical 3.1.1 geometry section
below mentioning unsolicited resize after INIT is superseded by that gate;
do not implement its old startup sequence. All inherited architecture text is
retained below for history, with current runtime 3.2.4 behavior unchanged.

---

# 3.2.4 UI/video update

The browser overlay is stage-owned HTML/CSS with local Vibe assets. Native UI
and full-playback video now have separate presentation rectangles: fixed UI
can remain fitted while full video fills the stage. Native previews and input
share the canvas transform. Paint masks are not geometry commands. Details
and invariants: RELEASE-v3.2.4.md and ../handoff.md.

## Retained architecture

# MiniClient architecture — 3.2.0

## New transport/profile layer

`StreamOptions → MediaProbe → StreamPlan → StreamCommand` resolves validated settings to source tracks and actual FFmpeg arguments. The existing MiniClient GFX/media connection still owns STV context and native controls. `HlsSessionManager` retains its historical name but owns both continuous TS and EVENT HLS sessions.

```text
SageTV MediaFile / MediaServer
    → one FFmpeg process
        → continuous stream.ts OR EVENT HLS segments
            → existing Jetty / same origin
                → mpegts.js OR hls.js/native HLS
                    → browser video element
        → optional original-video stream-copy stdout
            → server A/53 extractor → captions.bin
                → bounded caption JSON → local CEA decoder/clock overlay
```

New copy/transcode options operate independently of the transport. Portable GPU mode is hardware encode after CPU decode/filter; copy mode avoids video encode. Explicit profile fields affect real command construction and actual audio source selection. The original-video CC output has no second encode.

Continuous HTTP requests run in a 16-reader bounded async worker pool. A normal lazy-reader close retains the file/producer for exact byte resume. User time seeks and Apply-now create a new session. Browser reserve and server disk spool are distinct; periodic idle/free-space/output guards protect the latter. mpegts.js worker/MSE-worker flags are requested; GFX remains main-thread Canvas.

Caption choices are local CEA-608/708 only. No STV event-225, Teletext, DVB/PGS/DVD bitmap or SRT parity is asserted. The pinned vendor bundle is cache-on-demand, not embedded; actual vendor MSE validation is outstanding.

The sections below describe the retained 3.1.1 GFX/HLS baseline. New transport/settings/caption behavior above and RELEASE-v3.2.0.md take precedence over any HLS-only baseline wording.

---

# MiniClient architecture — 3.1.1

The current SageMC path is `miniclient.html`. Earlier API-player/libmedia architecture is preserved separately in `ARCHITECTURE-api-player-history.md`; it is not the threading or decoder model of the MiniClient page.

```text
SageTV native MiniClient service (stock Sage.jar)
   | GFX socket                  | Media command socket
Java GFX reader thread           Java media reader thread
   | ordered event queue         | OPENURL / SEEK / control
Jetty NDJSON response worker     HLS session manager
   |                             | direct file or threaded source follower
Browser fetch/event dispatch     external FFmpeg process
   |                             | H.264 + AAC, atomic EVENT HLS files
Ordered Canvas GFX queue          producer monitor + stderr-reader threads
(main thread, cooperative)        | Jetty manifest/segment request workers
   |                             v
GUI + coordinate mapping        hls.js loader/controller (main thread)
                                 | worker-enabled transmux (fallback possible)
                                 v
                              browser media engine / <video>
```

No Python server, replacement Sage.jar or additional browser-facing server is introduced. Native GFX/media and MediaServer connections remain internal services; the browser uses the existing Jetty context.

## Geometry

The usable window controls the logical size by default. Native INIT is followed by a 192 resize event and a 193 repaint event. Subsequent browser resize requests use the same wire path and emit ordered local resize messages. Only the primary drawing surface changes size; image and non-primary surface handles are retained. Fixed-resolution modes fit without crop. Video bounds and pointer coordinates use the displayed Canvas rectangle, not independent viewport scaling.

## Concurrency boundaries

Native GFX replies/input are serialized on one event-output lock. The server has separate media and GFX reader threads, so GFX backpressure does not make their socket reads one thread. Jetty allocates request workers. HLS producer, optional feeder, monitor and stderr are distinct activities. Metrics use a dedicated playlist snapshot lock rather than the process lifecycle lock; completion forces a final refresh.

Browser DOM/Canvas work remains on the main thread. Media events bypass image-decode waiting. Ordered GFX work yields after at most 128 commands or about 8 ms between yields; a single expensive Canvas operation can still exceed that duration. Read-ahead is bounded at 4096 queued events or approximately 8 MiB before waiting for progress (one received chunk/event can exceed the soft threshold). Server queue overflow is reported and closes the session instead of silently dropping arbitrary draw/image commands. This is not full worker-based rendering.

## Buffer stages

Startup: first published segment opens playback; HLS forward target initially 6 seconds.
Reserve: first fragment append/progress raises target to configured 30–600 seconds (180 default); loader continues and tops up while playing/paused. Backward target is 30 seconds. Browser quota pressure can lower the effective forward target.
Producer: unpaced file processing can finish before the viewer; disk segments survive until session cleanup. Growing input follows data as recorded. EVENT playlists are not allowed to force playback toward a moving producer edge. Refill checks do not repeatedly abort active downloads or restart FFmpeg while useful server reserve exists. Native HLS has browser-managed memory/loading policy.

There is no new disk-quota/rolling-window policy. Long recordings can accumulate substantial HLS storage. Pipe-fed seeking still uses byte-proportional approximation. Complete source-rectangle/transforms/diffuse/YUV/subtitle parity remains out of scope.
