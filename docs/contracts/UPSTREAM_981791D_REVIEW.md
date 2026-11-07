# Yuanbao plugin migration — source-bound compatibility review

The [ef5 compatibility run](https://github.com/DionCroft/Signal_Grove/actions/runs/42345791438)
failed with exit2 on six consumed handler fingerprints at upstream main
`981791d9d41fd2efde27f563873ce8c48ef1dac6`. This was real source drift.
The failed result remains failed. Stable v0.23.0 at
`2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` had no used-contract drift.
The original F0 baseline, every original contract field and all previous review
entries remain unchanged. Fresh final-source hosted gates are still required.

## Primary source and effects

The complete before/after sources were checked against Git blob IDs from GitHub;
current SHA-256 values also match the digest-verified main CI report. The full
353-line diff from prior reviewed main `038a1f3eff16963856d7aceb479e8b33058f68df`
was read. Four modules changed:

| Primary source at the reviewed pin | Complete source SHA-256 |
| --- | --- |
| [Channel catalog](https://github.com/NousResearch/hermes-agent/blob/981791d9d41fd2efde27f563873ce8c48ef1dac6/hermes_cli/web_server_messaging.py) | `dee21f5b9b5d38ba8e6d811648baf20fe16125870d0466d289848d504c6b2baa` |
| [Gateway config](https://github.com/NousResearch/hermes-agent/blob/981791d9d41fd2efde27f563873ce8c48ef1dac6/gateway/config.py) | `711847b5f740a5131bd2772bc8d737f94ab549faab8d8de18befe8202dbe7dd7` |
| [CLI config](https://github.com/NousResearch/hermes-agent/blob/981791d9d41fd2efde27f563873ce8c48ef1dac6/hermes_cli/config.py) | `f50d79e1ca5f051a78e51b162d0e88c27443acb3f6eaf1cd02074fad3b9aed5d` |
| [Legacy migration](https://github.com/NousResearch/hermes-agent/blob/981791d9d41fd2efde27f563873ce8c48ef1dac6/hermes_cli/left_core_migration.py) | `9e04fcd9a118c9a386c694ad72195e28253bfe2aac349cc6978d1cf3b6aa53da` |

Yuanbao leaves the built-in channel order and Platform enum. A new LEFT_CORE
record names catalog plugin `yuanbao`, bundled key `platforms/yuanbao` and
manifest alias `yuanbao-platform`. Both aliases map to the same catalog plugin;
the inverse map includes both. Existing mappings remain unchanged. The migration
also preserves legacy bundle/toolset disables, accounts for managed disable pins,
merges legacy/nested/top-level config and excludes home_channel parsing before
the platform plugin registers. The new notice describes Yuanbao's Labs support
limits. These are server responsibilities, not mobile installation instructions.

The changed `_home_env` keeps dotenv precedence, then consults a bound external
secret scope only for the requested home. Process environment fallback still
requires both explicit process_env and the launch home. `_worker_config_snapshot`
changes the missing-root-package condition; with the literal single-segment
`agent` it keeps the same ordinary missing-module outcomes. Unrelated dependency
failures must still propagate.

The added `/v1/room-members/runs/close` route is relative to the original F0
baseline. Its runs/rooms source blobs are identical between prior038 and981;
it is not a new delta in this review. Native room administration remains a
separate required audit package.

## Mobile behavior review

`GatewayPluginInventory` consumes server-provided key/name/source/status values;
`HostChannelInventory` consumes server-declared channel IDs and fields. There is
no client Yuanbao table or local alias translation. Catalog lookup preserves the
requested canonical name, inspection checks the selected entry, and installation
requires an explicit reviewed request. Settings/toggle/install paths bind the
management target, profile and generation. Toggle re-reads the inventory, checks
the exact key and all reviewed row metadata/collisions, persists a one-send
receipt and verifies readback. Unknown outcomes are retained, never replayed.
This review needs no Android behavior/schema/network/default change.

That is a source-level finding. It does not certify a live Yuanbao account,
provider, migration, every plugin, external secrets adapter or deployed Pi version.
No automatic migration/install or production gateway update was performed.

## Exact-source acceptance and evidence

The six new `reviewed_handler_variants` entries require **all four complete
module hashes** above through optional `required_sources`. Missing or changed
own/helper bytes prevent acceptance even when the selected AST is unchanged.
Malformed/empty/unknown/missing-own/oversized guards and misspelled entry keys
are rejected. Historical unguarded variants and the separately source-bound
auth-registry replacement policy retain their behavior. Reports explicitly list
`reviewed_source_guarded_variants_applied`; the review commit is provenance,
not a blanket acceptance for that or later commits.

Nine new policy tests cover original/reviewed modes, each missing/changed source,
same-AST byte changes and transitive helpers, changed/missing handler fingerprints,
legacy evidence, unrelated modules, malformed/typo guards and the exact six-entry
production policy. All166 parser tests and15 runtime-model tests pass. Saved
digest-verified stable/main reports recompare cleanly; all eight counterfactuals
(missing/changed identity for each module) re-open all six findings. This saved
snapshot result does not change the original hosted failure or establish that
upstream main has not advanced.

A separate controlled probe executed only the previously verified AST function
definitions with deferred annotations, fake imports and fake environments. It
projected literal LEFT_CORE identity fields without evaluating module globals,
callbacks or directory scans. Its39 cases cover preserved/new/unknown aliases,
worker-import fallback and home-scope precedence/read counters, plus static
catalog/enum deltas. No complete upstream module, real credential store, config
write, HTTP server or Pi deployment was executed. The probe is bounded review
evidence, not live runtime acceptance.

The original failed cohort's parser157/runtime15 jobs and8 pinned real-registry
cases passed independently. Its report archives were downloaded and SHA-256
verified before reading:

| Artifact | ZIP SHA-256 |
| --- | --- |
| main | `c3f3c040950b81d6332da4ee23920f60773a01aa83c2a5742db4e5ae26984cd7` |
| stable | `074105f5a341a53353bb85865cd18615565d56d24c6ee8941078b1afaf5597e7` |
| pinned registry | `b2b769edc1df83bb1099cf542e262b3b271fb4ad7c4ce8ad9cdad89c11ba0a18` |

Raw private reports and probe receipts remain on the mandated temporary storage;
no credentials or owner transcripts enter this repository. Full application
release gates, signed final-source assets and public download verification stay
pending. The broader audit in AUDIT_EXECUTION_PLAN remains active.

## Later reviewed source vector

The next canary advanced to d4; its changed config.py bytes correctly reopened
these findings. A [separate source review](2026-11-07-install-root-profile-review.md)
adds an additional exact four-module vector without replacing the981 evidence.
Both complete modes remain valid; unreviewed byte combinations still need review.
