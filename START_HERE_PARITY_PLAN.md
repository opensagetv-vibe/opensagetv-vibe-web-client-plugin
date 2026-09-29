# Current parity checkpoint — v3.2.18-P14 native-HLS codec field test

This is a historical parity-plan index imported with the 3.2.29 baseline. The
active project state is in `HANDOFF.md`, `TASKS.md`, and `README.md`.

P14 diagnostics/configuration/server-destination behavior is implemented locally. Continue with `docs/parity/p14/` for its historical evidence.

The important boundary is deliberate: browser support reports are bounded/redacted; automatic terminal-failure capture is off by default; browser profiles contain only saved defaults; and any external diagnostic write goes only to an administrator-configured **already-mounted server path**. The browser never receives SMB credentials or opens `smb://`.

WP14-006 remains field acceptance for a real mounted destination, forced failure, bounded spool, retry and redaction review. All earlier phase field gates remain unchanged.

Next planned phase: **P15 optional extended-Core/provider integration**. P16 end-to-end acceptance does not depend on P15.
