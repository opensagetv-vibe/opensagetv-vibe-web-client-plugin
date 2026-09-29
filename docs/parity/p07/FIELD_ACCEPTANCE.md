# P07 stock-server field acceptance

This checklist closes **WP07-001 only**. It does not turn P07 into a visible DVD-playback pass; P08 is required for that.

1. Use an unchanged stock SageTV server/STV and the 3.2.10-P07 WAR.
2. Add JVM system property `sagetv.webplayer.nativeDvdProtocol=true` and restart SageTV/Jetty. How the JVM option is supplied depends on the SageTV installation/service wrapper.
3. Connect the browser MiniClient and capture native property negotiation. Verify `INPUT_DEVICES=IR,KEYBOARD,TOUCH,TV`, `PUSH_AV_CONTAINERS=MPEG2-PS`, and MPEG-2 remains present in `VIDEO_CODECS`.
4. Verify ordinary browser D-pad/touch/mouse-generated commands still reach SageMC. The browser event implementation remains present even though the stock-role property omits the literal `MOUSE` token.
5. Start a DVD only long enough to capture stock role selection and early protocol diagnostics. Export diagnostics immediately. Expected P07 evidence is `dvd.pending=true`, an INIT count, four-byte media replies and optional early metadata/PUSH activity.
6. Do **not** score lack of visible DVD A/V as a P07 regression. P08 has not yet connected queued MPEG-PS to the browser renderer. Avoid prolonged playback with the P07 opt-in role because the bounded input will truthfully apply backpressure when no P08 consumer drains it.
7. Remove the JVM property after the negotiation capture unless actively developing P08. The default P07 release retains the existing browser/MOUSE role.

Required evidence to close WP07-001: stock server/version identity, negotiated property values, proof the stock DVD player path selected MiniDVDPlayer/native push, and a browser input check.
