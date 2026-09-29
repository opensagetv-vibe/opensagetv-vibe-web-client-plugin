# P05 / 3.2.8 validation

Local implementation gates passed on 2026-09-25.

- P02 shared playback architecture: **34 PASS**.
- P05 caption-authority policy: **6 PASS**.
- P05 legacy event-225 bridge vectors: **4 PASS**.
- MiniClient playback regression: **31 PASS**.
- Native recovery/startup/authentication: **11 / 13 / 14 PASS**.
- Streaming servlet: **16 PASS**.
- Real FFmpeg software stream/caption path: **14 PASS**.
- MPEG-TS/settings/caption browser adapter: **17 PASS**.
- P03 Teletext Java/Node: **28 / 5 PASS**.
- P04 DVB bitmap Java/Node: **32 / 3 PASS**.
- HLS runtime: **10 PASS**, including the 240-second synthetic growing-recording case.
- Chromium regressions were run in split execution and passed: remote/GFX **15**, layout **12**, streaming **12**, Teletext **5**, DVB **6**, recovery **10**, Vibe display **22** (**82 total**).
- Java class target remains **52 / Java 8**.

The combined validator was also exercised, but the environment execution window interrupted it inside the intentionally long HLS/browser sections. Those remaining groups were rerun separately; timeout is not recorded as a pass.

Field acceptance remains separate. Generated callback packets and mocked stock-property behavior do **not** substitute for a physical stock SageTV/STV run. WP05-003, WP05-005 and WP05-006 therefore remain `IMPLEMENTED_AWAITING_FIELD`.
