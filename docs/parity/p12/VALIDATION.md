# P12 validation

Local generated evidence:
- `P12AudioPolicySmoke`: primary-vs-commentary selection, strict fallback, signed delay command construction, quality control/copy semantics, pipeline truth, MiniClient rate contract.
- `test-p12-streaming.js`: schema 7 migration/defaults and bounds.
- `P12AvCalibrationSmoke`: generated 3 s A/V fixture with a short audible 1 kHz pulse. Decoded output measured onset at about 0.527 s (-500 ms), 1.027 s (0), 1.527 s (+500 ms), proving sign/reset behavior.
- Inherited architecture, DVD protocol/SPU, streaming, caption/subtitle and browser regressions are rerun before packaging.

This does not prove receiver/ARC latency, browser-specific AC-3 passthrough, or real broadcast PMT/channel-layout transitions.
