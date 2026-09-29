# P09 report — DVD SPU, highlights and navigation

Runtime: **3.2.12-P09**

Implemented locally:
- bounded private_stream_1 SPU fragment reassembly for physical substreams 0x20–0x3f;
- platform-neutral 2-bit DVD SPU RLE decode with display/start/stop/forced, authored coordinates, CLUT/alpha and bounded malformed-input handling;
- straight-alpha RGBA browser presentation on a dedicated `DVD_SPU` canvas;
- SageTV DVD CLUT and SPUCTRL highlight state, with immediate recomposition of the currently presented SPU;
- DVD physical subpicture selection/disable semantics isolated from ordinary DVB/CC paths;
- generation invalidation on genuine DVD FLUSH epochs without treating repeated highlight/CLUT updates as stream changes;
- PAL/NTSC 720-wide authored coordinate mapping through the same Fit/Fill/Stretch video rectangle as other bitmap overlays;
- remote D-pad/Select/Back/Menu/chapter keys continue through the existing native SageTV input path. Pointer overlay is deliberately non-interactive until a stock-server DVD hit-test can be field-verified.

WP09-001 through WP09-006 are locally implemented. WP09-007 remains field-open because an authored multi-menu DVD and representative real discs were not available here.
