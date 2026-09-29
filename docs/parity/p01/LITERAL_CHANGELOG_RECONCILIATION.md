# P01 literal changelog reconciliation

The frozen `CHANGELOG.md` blob (`650e8a65d1407fa5a6529240078b41ada2f91afe`) contains **1,066** top-level bullet records across 3,960 lines. The full frozen blob was parsed through the GitHub connector and every literal bullet received a scope disposition and primary WebPlayer phase family; **zero literal bullets were left unclassified**.

The 1,066 literal records are intentionally not treated as 1,066 independent browser fixes. Publication notes, repeated physical commissioning, test-harness changes, superseded experiments and Android-only packaging can repeat the same underlying functional behavior. `UPSTREAM_FIX_LEDGER.json` therefore remains the normalized 248-record functional ledger with reason, browser disposition, target WP IDs and planned tests. `SUPERSESSION_DECISIONS.md` resolves older policy conflicts.

`UPSTREAM_RELEVANT_TESTS.json` separately records all 102 regression-source files selected from 162 test files in the frozen Git tree. This closes the source-intake/census portion of WP01-001 without claiming those Android tests execute in WebPlayer.
