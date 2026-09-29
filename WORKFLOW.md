# Common project workflow

Run `dev.cmd` or `./dev.sh` with `test`, `validate`, `build`, `install`, or
`all`. Commands delegate to the one sibling `opensagetv-vibe-build-env`
container. `install` reports the artifact boundary; `.232` commissioning is a
separate guarded operation.

`test` runs the complete generated/host regression set. `validate` checks
source, package, manifest, dependency, Java 8 bytecode, and browser contracts.
`build` creates the deterministic WAR and local SageTV plugin package. `all`
also enables the offline Chromium suites available in the build container.

The plugin must use the platform-matching OpenSageTV Vibe FFmpeg Plugin. The
legacy explicit FFmpeg property remains only for isolated regression fixtures
and diagnostics. Production discovery must resolve `SageTVTranscoder` and must
not silently select SageTV's stock `ffmpeg`.

Do not publish a plugin catalog entry, GitHub release, or public artifact until
all applicable stock-server and `.232` physical gates pass and the user gives
explicit final approval.
