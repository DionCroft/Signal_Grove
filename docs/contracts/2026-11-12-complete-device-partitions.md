# Complete standard device partitions - 12 November 2026

## Behavior and acceptance

The standard CI set is divided using AndroidJUnitRunner's documented
numShards/shardIndex mechanism. The same runner's log-only mode enumerates cases
without executing their bodies. See the primary
[AndroidJUnitRunner reference](https://developer.android.com/reference/androidx/test/runner/AndroidJUnitRunner).

Each API26/37 job now owns one fresh emulator for shard 0 or 1. It keeps the
existing 100-minute command limit, test assertions, API26 keyboard preparation
and exact five-class/eleven-method external-model exclusion manifest. No retry,
SDK assumption, skip or additional class exclusion is accepted. The source
checkout must match GITHUB_SHA; both installed package hashes must match the
single app/test artifact pair from this workflow.

The log-only inventory is explicitly inventory_only with native_acceptance=false
and test_bodies_executed=false. A shard's positive native result is only a
partition result. The separate always-run coverage job requires all four reports,
matching source/APK identities, exact unchanged external prerequisites, bounded
unique case identities, matching raw-log hashes and one start/finish for every
reported positive case. Independent full inventories must agree within each API.
The actual native subsets must be disjoint and their union must equal that full
inventory. Missing, duplicated, foreign, failed, skipped, zero or partial cases
and altered logs refuse complete acceptance. API-specific inventories may differ.

The checker is a coverage gate, not a replacement for actual device results.
Its synthetic CLI fixture is labelled separately. Physical devices, licensed
models, long-lived production/lifecycle behavior and the signed release still
require their own evidence. Sharded execution does not prove the prior single
process's timing or explain an earlier timeout.

## Exact source and local evidence

Frozen source530, based on parent 32c6d6a6531053950b4b1335e7f3fd455fd3224f,
has digest `ffb7e065ddc31aa4739215060df576821221e52e233c81e4f320f1f3420800c4`. Required build tasks pass 1004 JVM tests
in 96 reports, both lint variants with zero errors (51/50 warnings), original debug
signing and all 32 packaged native-library ABI/alignment checks. There are 335
Python policy passes, including 15 new cases for the union, raw evidence,
inventory/native separation, no-replay timeout retention, APK binding and workflow.
Documentation is appended after this freeze; compiled/host non-document inputs
are checked unchanged before committing.

Debug app SHA-256: `02bbe540a05ef5d176e865246229f479b44705b5f961a56b1c9c36e8752b634c`.
Test companion SHA-256: `56309d5b81768249ef7ffbab97edad5f828e4d6155a8b766cd6b5c49b695b352`.
Android test sources/assertions are unchanged in this chunk.

Real catalog enumeration on the installed APK pair proves these inventory-only
partitions: API26 has 1132 cases, split 535/597; API37 has 1133, split 536/597.
These counts are observations, not hard-coded exceptions or native passes.
Native cohort 540 runs all four PluginSourceFlowTest methods and both
ProjectGatewayContractTest methods once per API, split 1/5. All 12 pass. The
native metadata/CAS refusal case named by the old timeout passes in this focused
context, which does not establish its old outcome or full-session behavior.
All temporary font/keyboard/animation settings are restored.

The actual six-method output per API is deliberately compared with the complete
native catalog. The union verifier refuses that focused output as incomplete.
CLI fixture 537 independently accepts a synthetic valid matrix and rejects an
altered log before writing a success report. It is never Android acceptance.

Upgrade 533 verifies the installed source/signers and preserves UIDs plus every
protected file before/after installation: 9 on disposable API26 and 1547 on the
existing API37 emulator, including private model/history stores. Native fixtures
perform their own bounded synthetic operations and restore their fixture vault.
No owner-phone/Pi deployment, model loading or saved-model/default change occurs.

## Preserved failures and observations

Disposable API37 fixture 531 exits before boot: its local SDK lacks the requested
init encryptionkey.img. The original PID is terminal and no device appears.
No restart, wipe or change to preserved AVDs/shared SDKs is used. Its original
launch/config/log and reconciled failure remain retained. Existing API37 is used
only after active-instrumentation checks and a protected upgrade.

Host supervisor 534 reused one host-log filename for two independently successful
upgrades. It stops before native 535; both actual upgrade receipts are reverified
without reinstalling. Native 536 then returns OK(0 tests) for the first proposed
single-class shard. The strict parser rejects it and restores settings. It is
not native acceptance. A distinct, enumerated two-class cohort 540 exercises both
nonempty shards; the original failed/empty attempt is retained.

Diagnostic 2581's unsharded API37 artifact remains timed_out at 6000.018 seconds,
991 starts/990 positive, 1126 declared. The retained TestRunner ring buffer ends
with the next case starting 23:39:28.125, while the host launched the suite at
21:59:40.8976861 and reported timeout/cleanup at 23:39:52.7536759. Native/host
clocks were not cross-sampled and early log entries are missing. These timestamps
do not prove a particular test hung or establish an exact remaining-case budget.
No earlier failure is relabelled and no blanket deadline increase is applied.

The parent daa3ec8 hosted jobs were still running and 32c6d6a Android CI was pending
during this checkpoint. They remain independently scoped. Their complete verdicts
and this new four-job native union must be retained before claiming exact-source
full standard acceptance. Prior full 2fde17d success covers only that source.

## Compatibility and broader scope

Public source artifacts for 32c6d6a are hash verified: latest ZIP
`1fbad2e53f85114bb4edfa1e50aae7b5c486b20c36191e967717d1511184452a`,
main ZIP
`b5637b5dab6a1b36e549ab1d37a24c7bd9f303edf80736efcd4a3ca9d02e8634`.
Latest resolves to v0.23.1 /3b421578588243f920886e6fe79a6faa87ea026e
with 47 changed-used-handler findings. Main resolves to
63a88403bf50835b99c514b61b4cc8f664d7a209 with 97. Neither reports removed used
routes/request fields. Parser, pinned registry and sidecar jobs pass; upstream
review fails remain unwaived. The original F0 baseline/guards are unchanged.

Private evidence retains source/build/proof 530, policies 532, upgrade 533 and
reconciliation 541, failed fixture 531/native 536, native 540 raw case/inventory
logs, synthetic CLI 537, and original 2581 archives. No generated/private artifacts
are committed. Full hosted acceptance, original audit, worker/root/file authority,
production integrations, physical/model/lifecycle, remaining source/advisory
review and signed release remain required. Published APK is still beta.8.
