# Complete hosted standard Android matrix

14 November 2026. The existing run for source `3ccc78899c5ec3e9f2213937a4bed29548e9d14b`
completed verify, all four device shards and coverage aggregation successfully.
No job was rerun or cancelled to obtain this result.
[Run and retained artifacts](https://github.com/DionCroft/Signal_Grove/actions/runs/43602931194).

| Android API | Distinct executed standard methods | Shards | Failures / skips |
| --- | ---: | --- | --- |
| 26 | 1,142 | 541 + 601 | 0 / 0 |
| 37 | 1,143 | 542 + 601 | 0 / 0 |

Independent recovery checks the actual raw start/success method identities,
exactly one positive result per method, disjoint shards and equality with the
standard inventory for each API. Inventory-only discovery does not execute test
bodies; only the separate execution logs establish the table. The initial
independent reader incorrectly expected a pass status for inventory-only JSON;
its assertion failed before acceptance, then was corrected to require explicit
`inventory_only`, `native_acceptance=false` and `test_bodies_executed=false`.
The original runner results and logs are unchanged.

All seven downloaded evidence ZIPs match their GitHub-published byte counts and
SHA-256 digests. Four partition receipts and the final aggregate bind the same
debug app/test pair:

| APK | SHA-256 |
| --- | --- |
| App | `fae16bbaa9c0f5cb337299928fc7dbdff60b967994bfa865fb33dc65066db010` |
| Test companion | `b8af92324e82da286149e2898a48d31c502817f3e88798ddf487b1b37dd1853c` |

The standard inventory explicitly excludes twelve external-model methods with
documented prerequisites. This result does not accept those methods, the physical
phone, a production-signed upgrade or live Hermes interoperability. Recent main
changes after the tested source are confined to documentation and the Python SDK
provider fixture/tests; Android/build inputs are unchanged at this checkpoint.

The original source1180 Demo failure and retained cea0cbf navigation failures stay
historical failures with unproven causes. Their later passing executions establish
non-recurrence in these environments, not a demonstrated cause or fix.

The separate compatibility run still fails upstream review. Recovered reports
identify forty stable used-handler changes at `3b421578588243f920886e6fe79a6faa87ea026e`
and 152 main changes at `cd582ed706cec677572d4da6f1c0dbdc5ca8c410`, including
source-context changes. No used route is removed; added room and management
surfaces and transitive behavior still need review. Do not reset the baseline or
infer compatibility from a green Android run.
[Compatibility run](https://github.com/DionCroft/Signal_Grove/actions/runs/43602931227).

The full audit, on-device plan, all four resumed priorities and signed-release
requirements remain active. Published beta.9 is unchanged. The isolated PM tool
install completed with zero drift; its independent doctor/readiness and fresh SDK
probe are still separate work, with no live deployment authorized by this result.

## Later source654d839: independently verified complete matrix

The already-started [run43609260527](https://github.com/DionCroft/Signal_Grove/actions/runs/43609260527)
also completes verify, four shards and final aggregation. Six archived receipts/
raw/build ZIPs match published sizes/digests. Actual raw method identities prove
the exact disjoint 1,142 API26 (541+601) and 1,143 API37 (542+601) unions, with
zero failures/skips. Actual extracted APK bytes bind to the same per-shard pair;
97 JVM reports total 1,011 zero-failure/error/skip cases, and both lint variants
have zero errors with 52 debug/51 release warnings. No job is rerun or cancelled.
Android/build inputs remain unchanged through the current administration-only
checkpoint. Original failure causes, twelve external-model methods, phone/live/
production upgrade and upstream used/transitive compatibility remain separate.
The earlier evidence and compatibility failures stay preserved.
[Exact latest hashes and acceptance scope](2026-11-14-one-use-native-dispatch.md).
