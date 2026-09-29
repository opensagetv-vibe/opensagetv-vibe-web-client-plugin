# P14 server diagnostic destination design

The WebPlayer uses an **already-mounted server filesystem path**, not a browser SMB client.

- Configuration authority: SageTV/JVM administrator only.
- Enable flag: `sagetv.webplayer.diagnostics.writeEnabled` (default `false`).
- Destination: `sagetv.webplayer.diagnostics.dir`.
- Browser visibility: enabled/configured state, destination basename, spool counts/bytes only. Filesystem error details redact the configured destination and plugin spool paths.
- Browser does not receive SMB usernames/passwords/tokens and never opens `smb://`.
- Diagnostic destination is separate from SageTV media authorization/resolution. Report operations never derive an output path from a recording path or browser parameter.
- Destination test owns one randomly named temporary file and deletes only that file.
- Report save is temp-write + move; failed destination writes use the plugin's bounded temp spool.
- Retry enumerates only `*.zip` files in the plugin spool and moves those reports to the configured destination.

This design intentionally avoids embedding an SMB library or credential store in P14. If a future authenticated connector is required, it must be separately threat-modeled and must not weaken this default mounted-path boundary.
