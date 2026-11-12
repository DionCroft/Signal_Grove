# Channel registry/config context review - 12 November 2026

This accepts seven exact consumed-handler contexts for Hermes v0.23.1,
`3b421578588243f920886e6fe79a6faa87ea026e`. The official latest release was checked
as v0.23.1 during this review. The original F0 contract, every mobile watch and
all existing review entries remain unchanged; seven new entries are appended.
This does not declare full Hermes compatibility or publish a new Signal Grove APK.

## Complete primary source review

The five complete before sources at reviewed d4e7c34 were fetched from the
official repository and verified against their Git blob SHA-1 identities. The
current clean pinned checkout bytes match the independently verified stable
snapshot's complete source hashes. Full combined diff: 95 lines (84 registry,
11 CLI config). The catalog, gateway config and legacy migration are byte-identical.

| Current primary source | Complete SHA-256 |
| --- | --- |
| [Channel catalog](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_cli/web_server_messaging.py) | `dee21f5b9b5d38ba8e6d811648baf20fe16125870d0466d289848d504c6b2baa` |
| [Gateway config](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/gateway/config.py) | `711847b5f740a5131bd2772bc8d737f94ab549faab8d8de18befe8202dbe7dd7` |
| [Platform registry](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/gateway/platform_registry.py) | `05103cf65cf74935afa5660b140f012f22255a0b764b6c99c6c680a7a0d7f261` |
| [CLI config](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_cli/config.py) | `bdd14e65866864bbb6afad73fa3b2f4ba69097e783ac3cda18ac374dca33b763` |
| [Legacy migration](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_cli/left_core_migration.py) | `9e04fcd9a118c9a386c694ad72195e28253bfe2aac349cc6978d1cf3b6aa53da` |

The CLI config removes HONCHO_API_KEY from the explicit _ENV_CONFIG_KEYS set.
Its unchanged _is_env_config_key still matches the _API_KEY suffix, including
lowercase input; dotted config keys and nonsecret home-channel keys retain their
separate routing. The only production references to that set are its declaration
and this classifier. No credential route is downgraded or credential value read
by this review. _worker_config_snapshot and the migration table/alias/key-set ASTs
are unchanged; their prior four-module guards correctly refused the changed
complete config bytes until this context review.

PlatformEntry adds platform_hint_tool_notes with independent dict defaults. These
notes are prompt metadata for enabled tools, not new mobile channel-form fields.
The passive card builder still emits only ID/name/description/docs/env names and
required env names. Registry ensure_deps now separates active dependency work
from adapter construction, and deps_resolved_off_loop reuses a completed verdict.
A failed verdict must not re-probe or install on the loop; a successful verdict
may construct the already-resolved adapter. This does not authorize the phone to
install dependencies or start a real adapter during a read.

Signal Grove already consumes server-discovered IDs and declared fields. Its
HostChannelInventory discards saved non-list values and unconsumed metadata;
HostChannelsRepository checks owner/profile/home before and after reads and
requires a fresh private review plus one-send durable receipt for writes.
Unknown outcomes remain held. No Android schema, endpoint, model or default
change is needed for this specific context delta. Existing unknown-ID/private
projection and exact-review tests remain applicable to unchanged Android inputs.

## Exact acceptance and verification

The seven entries cover channel catalog order, Platform enum, worker config
snapshot, LEFT_CORE, legacy alias, legacy key set and PlatformEntry schema. Every
entry requires all five complete source hashes above and its exact AST hash.
Commit/branch labels are provenance, not authorization. Removing/changing any
one of the five dependencies reopens all seven findings, even with matching ASTs.

- **414 portable policy tests pass**, including six new exact-mode/negative
  cases. Unknown/missing handlers, changed/missing sources, unrelated canonical
  dispatch drift and historical-policy preservation remain enforced.
- **12 actual-source cases pass** on the clean pin in a disposable local home:
  unknown-plugin card/env ordering, omitted prompt/installer metadata, independent
  note defaults, failed/successful off-loop verdicts, four credential-key routing
  cases and unchanged Yuanbao aliases/inverse set. The sole factory call returns
  a synthetic dictionary; check/install callbacks stay at zero. Audited external
  network/process execution and writes outside the fixture are denied. This is
  a card/registry/config component proof, not HTTP login or real account delivery.
- **15 serving-model observer tests pass**; no inference/default changes occur.
- All **658** other pre-existing non-Markdown frozen-source 706 inputs remain
  hash-identical. The three changed existing policy/test inputs are exactly the
  baseline JSON and two policy tests. Prior Android build/1004 JVM/lint/signing/
  native-library and retained-data checks remain scoped to unchanged Android
  inputs; no new Android build, physical-device or APK result is claimed.

The first new test fixture omitted already-required introduced native handlers;
it was completed using the existing historical-authority fixture helper without
changing comparator rules. The first full 414-case run then caught the new entries
in the room-review golden. The original golden stays unchanged: tests now verify
both the exact historical projection and a separate complete current variants
digest. Git comparison proves removing only the seven new entries reproduces
the entire pre-review policy exactly. Failed channel-policy763.log is retained;
final channel-policy765.log and channel-catalog763/report.json pass.

Reproduce with the ordinary scripts/tests suite and, using the reviewed source's
dependency environment, `scripts/probe_channel_catalog.py --source-root
<clean-pinned-checkout> --work-root <fresh-approved-scratch>`. Owner raw evidence,
before blobs/diffs and recompare results stay outside Git on approved scratch.

## Remaining compatibility and delivery scope

The saved stable snapshot drops **47 -> 40** changed-used-handler findings,
closing exactly the seven reviewed contexts. All other categories/findings are
unchanged and mobile_review_required remains true. The saved main snapshot at
`fe6649291e60e1c88fb806d3e58de8b39e710d0e` stays **97 -> 97**: its complete
context does not match these five sources, so this review accepts none of its
unknown contexts. A fresh hosted comparison is still required after the push.

Native auth/authority/session/workspace and room contexts remain open. No Pi
trial/deployment, account pairing, dependency install, credential transfer,
public listener, project write, model switch or request replay occurs. The
separate Pi in-memory coordinator transfer trial remains pending explicit
approval after automatic review rejection; it must not be bypassed.

The full audit, worker/root/file controls, integrations/automation, derived graphs,
actual native sign-in/recovery/approval flows, phone/model/upgrade, advisories and
signed release remain in the active goal. Published APK stays v1.1.0-beta.8.
The owner still requires an original-key production APK as a versioned public
GitHub Release asset, verified public download/checksums and direct phone link.
