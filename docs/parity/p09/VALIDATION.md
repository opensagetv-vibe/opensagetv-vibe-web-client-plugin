# P09 validation

Completed locally on 2026-09-25:
- `DvdSpuSmoke`: 2/2 PASS — fragmented assembly, RLE decode, RGBA composition, disabled-title/highlight behavior and FLUSH generation.
- `test-dvd-spu-controller.js`: PASS.
- Production Chromium `browser-dvd-spu-smoke.py`: 3/3 PASS — RGBA pixels, pointer transparency, shared video geometry.
- P07 `DvdNativeProtocolSmoke`: PASS.
- P07 `MiniClientDvdWireSmoke`: PASS.
- P02 `PlaybackArchitectureSmoke`: 34/34 PASS.
- Existing MiniClient core: 27 PASS; playback: 33 PASS; ordinary subtitle controller: 4 PASS.

No Android runtime dependency was introduced into the SPU core.
