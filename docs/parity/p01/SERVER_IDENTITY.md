# P01 stock/extended server separation

The supplied live SageTV log identifies the running server as **SageTV 9.2.10.1054**, server mode (`client=false`), Java **11.0.32**, Linux **6.12.54-Unraid**, with `Sage.jar` on the startup classpath. The MiniClient UI shown in the same log loads **`/opt/sagetv/server/STVs/SageTV7/SageTV7.xml`**.

Installed Vibe FFmpeg plugin metadata explicitly describes its operation as using stock `Sage.jar` without modification. The Core MCP component in the log is a Standard plugin; this checkpoint does not treat it as a replacement Core and does not use an extended/custom Core result as stock compatibility evidence. Optional P15 remains unapproved and disabled as a stock substitute.

The log does **not** provide cryptographic hashes of the deployed `Sage.jar` or STV XML, so `SERVER_IDENTITY.json` leaves those fields null. That limitation is retained instead of inventing a hash. No Authorization header, credential, client secret or raw request body is copied into this package.
