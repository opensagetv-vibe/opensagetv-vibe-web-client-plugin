# P05 report — Stock SageTV CC authority and virtual CC1/CC2

Runtime checkpoint: **3.2.8-P05**.

Implemented locally:
- OFF / CC1 / CC2 / STV / DVB authority model with persisted virtual CC profiles.
- Virtual CC resolution is limited to Teletext, CEA-608 and CEA-708. DVB bitmap remains explicit and ordinary file/DVD subtitle preferences are not consulted.
- Auto resolution prefers observed PMT services over metadata-only placeholders, is English-first for CC1, and can select a distinct described CC2 service. CEA-608 language remains unknown.
- Stock `GFX_SUBTITLES` / `SUBTITLES_CALLBACKS` negotiation and event-225 wire output.
- A/53 cc_data -> extender callback records with 45 kHz PTS, duplicate suppression, playback-clock gating and reset/flush records.
- STV Teletext -> CEA-608 callback records. Observed Teletext suppresses A/53 fallback in STV mode to keep one renderer.
- `VIDEO_CC_STATE` is honored when present; peers that omit it retain callback fallback behavior.
- Existing DVB command-36 physical PID control remains independent.

Not field-certified here:
- Real stock SageTV/STV Off/CC1/CC2 behavior.
- Property-present versus property-absent peer behavior on a physical server.
- Real seek/flush ghost-caption check and 708 service/style coverage.
## 3.2.25 field correction
A real web-MiniClient field test showed that selecting `Captions (CC1)` in the stock STV did not make captions visible even though explicit browser-local CC1 worked. The web client had been logging `VIDEO_CC_STATE` without applying it to the local decoder. 3.2.25 keeps STV/SageTV as the authority/control plane but mirrors state 0/1..4 into the browser CEA decoder so the stock STV menu controls the visible captions. The legacy callback producer remains available; local explicit CC/DVB modes are unchanged.

