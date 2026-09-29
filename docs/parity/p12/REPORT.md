# P12 report — audio, playback controls and quality-policy parity

Version 3.2.15 adapts browser/FFmpeg-supported portions of the Android audio and quality policy without claiming Android AudioTrack/HDMI APIs.

- Audio selection is deterministic: explicit track wins; otherwise preferred-language primary/default audio is scored above commentary, narration, descriptive-audio and visual-impaired tracks. Missing-language fallback is saved as best/default, first, or strict failure.
- Audio track IDs are session-only. Language/fallback/delay defaults persist across recordings.
- Signed audio delay is -4000..+4000 ms in 25 ms steps. Positive means audio later. Audio copy remains strict and is rejected when delay/channel conversion is requested.
- Rapid delay/track/quality edits are debounced for 450 ms before Apply-now stream rebuild. Rebuild uses the current absolute position and existing pause intent while keeping the MiniClient GFX connection alive.
- Effective status separates requested/effective encoder, software/copy decode, filtering, hardware encode/decode and fallback detail. Software decode/filter + hardware encode is not called end-to-end GPU.
- Video bitrate, keyframe interval (1..10 s), B-frames (0..3), resolution, FPS and deinterlace are encoder-only controls. Video copy ignores bitrate/GOP/B-frame settings.
- MiniClient native playback rate advertises 0.5..2.0x forward only. Reverse and frame-step remain unsupported and unadvertised.

Field-only boundaries remain real receiver/ARC audibility/latency and unchanged broadcast PMT/channel-layout transitions.
