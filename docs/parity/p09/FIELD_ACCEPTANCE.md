# P09 field acceptance still required

Use an unchanged stock SageTV server with `-Dsagetv.webplayer.nativeDvdProtocol=true` and an authored DVD containing root/submenus, still menu cells, multiple chapters/languages and SPU highlights.

Required observations before WP09-007 can be PASS:
1. Root and submenu backgrounds are visible while idle.
2. D-pad changes the authored selected-button highlight and Select activates it.
3. Back/Return/Menu transition through the server DVD VM.
4. Title subtitles can be disabled without hiding menu highlights.
5. Subtitle/audio language and chapter changes affect the authored streams.
6. Equal-sized cell transitions and repeated highlight/CLUT updates do not resurrect stale SPU frames.
7. Stop tears down A/V and SPU state; replay starts cleanly.
8. Repeat on at least one representative menu-less disc.

Pointer selection remains deliberately unclaimed until stock-server coordinate hit-testing is captured and verified.
