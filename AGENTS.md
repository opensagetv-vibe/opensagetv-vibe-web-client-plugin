# OpenSageTV Vibe Web Client Plugin instructions

Read `HANDOFF.md`, `README.md`, `TASKS.md`, `WORKFLOW.md`, and the directly
relevant parity document before changing code. Historical Markdown under
`docs/` is evidence and must not be silently rewritten as current proof.

Keep the plugin compatible with an unmodified stock SageTV `Sage.jar`. For
transcoding, depend on the platform-matching OpenSageTV Vibe FFmpeg Plugin and
its `SageTVTranscoder` bridge. Never replace SageTV's stock `ffmpeg` and never
bundle FFmpeg/MIM binaries in this repository.

Use the sibling `opensagetv-vibe-build-env` through `dev.cmd` or `dev.sh`.
Physical commissioning on `.232` is explicit and must preserve server data,
configuration, recordings, and unrelated plugins. Do not publish this plugin
or a public manifest until the user gives final approval.

For new test controls, use the stock-compatible Core MCP plugin and supported
SageTV APIs before considering a Core change. Generated, simulated, browser,
and physical evidence must be labeled accurately.

Release validation is impact-based: rerun only gates the release changes could
affect. Do not repeat unrelated completed gates. Run the full gate suite only
when the user explicitly requests it or a broad dependency/architecture change
requires it, and document that reason and scope.
