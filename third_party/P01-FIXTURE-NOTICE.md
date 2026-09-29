# P01 fixture provenance

The P01 fixture/tool scripts are new project test tooling. The Teletext Hamming
address/bit-order test conventions were compared with the Apache-2.0-licensed
Vibe Android TeletextSubtitleEngineTest.java at the frozen commit recorded in
docs/parity/REFERENCE_LOCK.json. The existing LICENSE-vibe-android.txt contains
the applicable Apache-2.0 license. The helper adds PES/PCR/parity/stuffing and
small deterministic test data; it is not a port of the runtime decoder.

FFmpeg decoder source was read for format requirements only; no FFmpeg decoder
source or executable/native library is included. Independent reference validation
requires a separately installed FFmpeg with the appropriate subtitle decoders.
Generated images/audio/video/subtitle payloads are artificial test data, not
private recordings, extracted commercial disc content, or evidence of Android
or browser field acceptance. No font file is distributed.
