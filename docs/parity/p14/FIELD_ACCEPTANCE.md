# P14 field acceptance

## WP14-006 — real administrator-mounted diagnostic destination

Before calling the server-destination workflow fully field-accepted:

1. On the SageTV server, mount the intended SMB/NFS/local diagnostic destination using operating-system/admin facilities. Do not expose credentials to the WebPlayer.
2. Configure the JVM with `-Dsagetv.webplayer.diagnostics.dir=<mounted diagnostic path>` and `-Dsagetv.webplayer.diagnostics.writeEnabled=true`, then restart the plugin/server as required.
3. Open MiniClient **Settings → Diagnostics, profiles and server destination**. Confirm status reports configured/enabled without disclosing the full server path or credentials.
4. Run **Test server destination**. Verify one plugin-owned temporary file is created, read/hash-verified and deleted. Confirm no recording/media file is created, modified or deleted.
5. Download/save a diagnostic report. Verify the ZIP arrives in the configured destination and contains `report.json`, `hashes.sha256` and `README.txt`; verify the reported hash.
6. Review the report for Authorization/cookie/password/token/session-key/share-credential leakage and for subtitle/media payload leakage. Counters/metadata may remain; actual subtitle text/media bytes must not.
7. Make the destination temporarily unavailable or non-writable and request another server save. Verify the report moves to the bounded local spool and playback remains independent.
8. Restore the destination and use **Retry spooled reports**. Verify the spooled report is transferred and removed from only the plugin spool.
9. Repeat enough failures to prove the spool stays at or below 10 files / 10 MiB and old plugin-owned spool entries are pruned rather than growing without bound.
10. Turn `sagetv.webplayer.diagnostics.writeEnabled` back off and restart. Verify external writes/test attempts are refused while browser ZIP download still works.

Record OS, filesystem/mount type, SageTV/WebPlayer versions, destination-test result, forced-failure/spool/retry result and report-redaction review. Do not include share credentials in the acceptance record.

## Current-video field spot check

On a normal completed recording longer than 30 seconds, invoke **Test Current Video** once while playing and once while paused. Confirm cadence/buffer evidence is present, the reversible seek returns to the original position, mute/volume/rate/pause intent are unchanged, and a different recording never receives a stale recovery seek. On a growing recording or DVD/menu session, confirm unsafe mutation is skipped with a reason.
