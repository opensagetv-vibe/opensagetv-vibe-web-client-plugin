# Architecture

```text
                         SageTV Server / Jetty
                                  |
                   +--------------+--------------+
                   |                             |
             SageTV MediaFile                 Web UI/API
                   |                             |
          +--------+---------+          /api/* /vendor/*
          |                  |
      Direct File      Sage MediaServer:7818
          |                  |
          +---------+--------+
                    |
              ORIGINAL MPEG-TS
                    |
          +---------+------------+
          |                      |
    /stream.ts (VOD)        /live.ts (growing)
          |                      |
          +----------+-----------+
                     |
                  Browser
                     |
        +------------+-------------+
        |            |             |
   Native HTML5   libmedia Wasm   ATSC CC parser
                    |             608 / 708
               MPEG-2 + AC-3       |
                    |          HTML overlay
              Canvas/WebAudio

 Optional only:
 MediaFile -> direct/SageTV MediaServer -> FFmpeg/MIM -> H.264/AAC HLS -> Jetty /hls/* -> hls.js/native HLS
```

Design rule: do not transcode merely because a browser cannot use `<video>` directly; first try client-side decode of the original stream.


## Real-device execution tiers (1.1.0)

1. Secure + cross-origin isolated: libmedia may use SharedArrayBuffer/Wasm threads; SIMD selected when supported.
2. No shared memory but Worker available: split browser decode pipeline across Web Workers.
3. Worker failure/unavailable: single-thread browser Wasm fallback.
4. Sustained real-time failure in Auto mode: optional server H.264/AAC compatibility handoff at current timestamp.

The current tier and probe results are exported in the diagnostic report.


## Session resilience (1.2.0)

- Browser-local `session-state.js` persists device/player preferences and VOD resume bookmarks.
- Resume entries are keyed by SageTV MediaFile ID + segment and are removed automatically near program completion.
- Active recordings default to the live edge when enabled. Decoder/network errors or a 12-second progress stall can trigger a capped exponential-backoff reconnect to `/live.ts?start=live`.
- Decoder runtime policy can be set to local-cache-only, which disables the pinned CDN fallback after the server asset cache is populated.

## Library / lean-back layer (1.3.0)

- `RecordingsServlet` exposes SageTV title/episode/description/channel plus category, year, watched state and a same-origin sagex thumbnail URL.
- `library-utils.js` performs client-side search/view/category filtering and deterministic sorting without mutating SageTV.
- Browser-local favorites, resume bookmarks and recent-playback history live in `session-state.js`.
- Recordings sharing the same SageTV title are treated as a navigable series for previous/next and optional autoplay-next.
- Media Session actions are advisory browser/OS integrations; core playback controls remain the source of truth.


## v1.4 guide, PWA and commercial markers

`/api/guide` exposes viewable-channel airings and explicit manual-recording operations through sagex. The web player never accepts a raw channel-tuning command; Watch Live uses SageTV's own manual recording scheduler and consumes the MediaFile it creates.

`/api/commercials` is constrained to an existing SageTV MediaFile ID and reads only adjacent Comskip `.edl` files. Marker decisions and seeks occur in the browser.

The service worker is an app-shell cache only. It bypasses `/api/`, original streams, live streams, compatibility transcodes and `/vendor/` decoder resources so media freshness/authentication semantics remain controlled by Jetty and the application.

## v1.5 DVR/library management

`/api/dvr` reads SageTV's scheduled recordings and unresolved non-recording airings. `/api/library` is the only mutating library endpoint and is scoped to SageTV MediaFile IDs. Watched-position sync is opt-in. Permanent delete operations require `confirm=DELETE` and reject active recordings. Live channel surfing reuses current EPG rows and the existing manual-record/watch-live path rather than adding arbitrary tuner-path access.


## v2.0.0 pre-field-test completion

Added server Favorite rules, tuner/encoder status, recording options, server watch history, browser profiles, queue, favorite-channel guide filtering/logos, home dashboard, bulk non-destructive library actions, sleep timer, PiP hooks, and settings export/import. Field testing is now the gating activity for additional changes.
