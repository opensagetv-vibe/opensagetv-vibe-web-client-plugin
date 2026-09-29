# Fixture development corrections, not runtime changes

The first reference-decoder attempt failed. Its result is retained separately as
`evidence/fixture-reference-initial-failure.json`; those rejected vectors are not
bundled. The delivered vectors and validator passed the later reference run.

Teletext construction was corrected to use the required 45-byte PES header,
184-byte PES length multiple, PCR, odd text parity, subtitle/suppressed-header
flags and erase-page headers. A descriptor alone was not treated as decoding.

The 8-bit DVB CLUT now declares its 8-bit entry flag. Padding the 6-pixel region
around the 4-pixel object also prevents an end-line code sitting beyond the
region edge. Expected RGBA metadata includes the transparent padding.

The initial one-fps FFmpeg bitmap test assumed display on sample indices 1/2.
The independent framesync path actually sampled the correct visible images on
2/3. The final test therefore measures exact pixel shape/color and ordered
show/clear with preceding/following empty frames. It **does not** claim PTS
synchronization; P04/P06 must measure their own browser clock tolerance. No
browser subtitle timing limit or production decoder was relaxed to pass this
fixture check. Detailed visible indices remain in the report.
