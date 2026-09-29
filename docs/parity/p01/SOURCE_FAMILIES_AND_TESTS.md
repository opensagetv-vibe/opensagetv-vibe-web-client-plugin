# Source-family and regression handoff

All Vibe references below are at the frozen source/dev commit `f1ba340e05fe18eaaba939253a933c3dfe6d6caf`.
Core production paths are under `source/dev/core/src/main/java/opensagetv/vibe/miniclient/`;
Core tests use the matching `src/test/java/` root. Android adapter paths are under
`source/dev/android-shared/src/main/java/opensagetv/vibe/miniclient/`.
Root `tests/` and `scripts/` references are repository-relative, not Core paths.

This is a source-family map and **identified test-target inventory**, not a claim
that every entire Android test file was executed or reviewed. The complete pinned
TeletextSubtitleEngineTest was read and its test-vector conventions informed the
synthetic fixture; the browser decoder has not been ported. The P01 audit cannot
be closed merely because the other target filenames are known.

| Family | Production targets | Identified test targets | Browser phases |
|---|---|---|---|
| Teletext | media/TeletextSubtitleEngine.java; media/TeletextPesProbe.java | media/TeletextSubtitleEngineTest.java; media/TeletextPesProbeTest.java | P02,P03 |
| Caption selection | media/CaptionSlotPolicy.java; media/TrackPreferencePolicy.java; media/SubtitleTrack.java | media/CaptionSlotPolicyTest.java; media/TrackPreferencePolicyTest.java; media/SubtitleTrackTest.java | P02,P05 |
| Legacy callbacks | video/TeletextCea608Bridge.java; video/LegacyExtenderCaptionBridge.java; video/LegacySubtitleCallbackPolicy.java | video/TeletextCea608BridgeTest.java; video/LegacyExtenderCaptionBridgeTest.java; video/LegacySubtitleCallbackPolicyTest.java | P05 |
| Native media/DVD | MediaCmd.java; dvd/DvdAudioStreamCode.java; dvd/DvdPtsClock.java | MediaCmdDvdProtocolTest.java; MediaCmdDetailedPushStatsTest.java; MediaCmdPlaybackRateTest.java | P07,P08,P11,P12 |
| SPU presentation | dvd/DvdSpuAssembler.java; dvd/DvdSpuDecoder.java; dvd/DvdSpuCompositor.java | dvd/DvdSpuAssemblerSelfTest.java; dvd/DvdSpuDecoderSelfTest.java; dvd/DvdSpuCompositorSelfTest.java; dvd/DvdRleRoundTripSelfTest.java | P06,P09 |
| Android adapters | android/video/DvdSubpictureDecoder.java; android/video/media3/DvdPsExtractor.java; android/video/BaseMediaPlayerImpl.java | tests/test_dvd_protocol.py; tests/test_sagetv_caption_authority.py | P02–P09 |
| Lifecycle/graphics | MiniClientConnection.java; ConnectionReconnectState.java; GFXCMD2.java | Further exact-file reconciliation remains WP01-001 | P13 |
| Fixture/workflow | scripts/create_authored_dvd_fixture.py; scripts/mcp_caption_test.py | tests/test_authored_dvd_fixture.py; additional MCP/generated fixture gates remain WP01-001 | P01,P14,P16 |

The exact original WebPlayer test-file inventory and SHA-256 values are in
`BASELINE_TEST_INVENTORY.json`. Its actual runner and unedited output establish
which baseline cases ran. Newly added P01 tools are tested separately and are
not inserted into the production validate.sh entry point in this checkpoint.

## Remaining exhaustive intake procedure

1. Acquire the exact pinned CHANGELOG bytes and verify Git blob SHA
   `650e8a65d1407fa5a6529240078b41ada2f91afe` (293702 bytes).
2. Run `p01_tools.py intake`; every literal top-level bullet retains its line
   range, text and hash. This tool only enumerates; it returns REVIEW_REQUIRED,
   never claims the classifications have been reviewed.
3. Reconcile each relevant bullet to a normalized UP record; explicitly mark
   duplicates, superseded experiments and Android-only/test-only work with a
   reason. Inspect all remaining relevant test files from the pinned tree and
   link exact test contracts rather than guessing from filenames.
4. Cross-check all current upstream named tasks and open parent gates, then
   change WP01-001 to DONE only when no relevant item is unclassified. Preserve
   all 248 existing UP IDs and all 127 WP IDs; append, do not silently renumber.
