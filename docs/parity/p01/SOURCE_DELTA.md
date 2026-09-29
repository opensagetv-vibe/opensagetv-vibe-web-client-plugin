# Frozen upstream delta procedure

Frozen commit: `f1ba340e05fe18eaaba939253a933c3dfe6d6caf`; actual tree
`06a4c99958b8e07f6ad84b0c0d721a7b92e71918`; task revision 71/v0.5.95.
The GitHub main-ref read in this session returned the same commit. The observation
is retained in `evidence/upstream-head.json`. It is not a background monitor.

```sh
python scripts/parity/p01_tools.py delta --observed-head <full-40-character-lowercase-SHA> --output /new/evidence/head.json
```

This compares only the supplied observed commit and never fetches, edits the pin,
creates a commit or changes GitHub. Obtain the observation from an actual GitHub
ref read; do not pass a guessed SHA. UNCHANGED exits 0; a changed head exits 2 with
REVIEW_REQUIRED; malformed input exits 1. A changed head requires an explicit
delta review with new permanent task/fix IDs. Only an approved lock revision can
move the baseline. Existing complete entries and their evidence stay preserved.

The reference-lock URLs remain pinned. Current remote HEAD alone cannot prove
unpublished/local Android changes, and none are included in this audit.
