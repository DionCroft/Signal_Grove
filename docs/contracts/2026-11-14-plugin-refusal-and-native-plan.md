# Plugin refusal review and native integration plan - 14 November 2026

The latest stable release was rechecked as Hermes v0.23.1. Two exact plugin
install/refusal findings are now reviewed under ten complete-module guards;
the original F0 baseline, every previous watch/review and the exact other 40
stable findings remain. All 485 portable cases pass, including four new tests.
Two behavioral counterexamples failed on the original checker: declared guarded
replacements were incorrectly rejected on older snapshots. Metadata validity is
now separate from current-context selection; missing/drifted/hybrid modes still
refuse. Every original assertion and historical digest is retained.

Seventeen official plugin-install cases across four canonical file runs pass on
pristine3b source. Three initial Windows long-path failures remain; only the
affected/unexecuted checks used a new shorter E: fixture, without source/assertion
or Git/OS policy changes. A separate plugin RPC file has 5 positives and 1 POSIX-only
skip, canonical exit 1; it is not accepted as a whole file. All 23,586 tracked SDK
inputs remain unchanged. Pristine shutdown and full interoperability stay open.

Fresh read-only Pi preflight binds ten runtime modules to pristine3b, confirms
both the native extension and operator module are absent, and retains services/
stamp. Supported plugin RPC does not expose the native owner/transport context
required by the workspace grant. The managed source adapter remains a separate,
undeployed integration; it must not be presented as a public plugin API.

## Exact upstream behavior and bounded acceptance

The official [latest release](https://github.com/NousResearch/hermes-agent/releases/tag/v0.23.1)
was rechecked on 14 November. The reviewed immutable source is
`3b421578588243f920886e6fe79a6faa87ea026e`; a moving main is not accepted.
The full 132-line baseline-to-stable
[RPC-module diff](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/tui_gateway/methods_tools.py)
was retained with eight exact dependency diffs. Baseline source blobs and current
checkout hashes verify. One raw-host 429 left seven successful files intact; only
the missing module was obtained through the official connector and checked against
its Git blob and baseline hash. No source or outcome was invented.

`_source_moved_data` becomes `_refusal_data`: source_moved retains precedence;
exists is an additive typed refusal. The agent install caller changes only that
error helper. Inspected commit, source/catalog, force and enable arguments and
success/activation handling remain. Signal Grove keeps force=false, omits ref,
sends inspected_sha and only treats the exact typed source_moved marker as a
re-inspection condition. Other refusals do not authorize an automatic write/retry.
Desktop-half installation and new unattended actions are not enabled by this review.

Dependency review retains recall checks before dependency work, the per-plugin
publication lock and metadata reread, and explicit enable-refusal handling. The
catalog consent-message formatting and bounded live-catalog size change are
retained; transactional surface gates still precede scanning/publication. The
previous [lock review](2026-11-05-plugin-update-lock-review.md) and
[auth/plugin review](reviews/2026-11-06-auth-plugin-guards.md) remain separate
context. Whole-module fences bind gateway_rpc, plugin_core, plugin_install,
plugin_catalog_gate, plugin_catalog_loader, plugin_transaction, plugin_isolation,
plugin_legacy, plugin_auto_update and plugin_git. This is no claim that every
unrelated function, integration or live catalog effect is accepted.

The new helper is watched explicitly. Retirement requires both exact replacement
bodies and all ten current source contexts. Only the exact prior 2bd pin may lack
that introduced helper, in addition to original F0. Every earlier metadata value
is preserved: removing only this additive review reproduces the complete original
canonical JSON digest. Ten real-entry context mutations, missing-helper and
hybrid modes refuse. Two synthetic behavior cases prove older/current modes and
invalid declared digests. All original assertions and old golden hashes remain.

The first complete portable run after the metadata addition failed 12 tests:
partial synthetic modes and old-vector scope included the additive review.
Those failures are retained. Fixtures now complete guarded replacement contexts;
only the new review is projected from older golden vectors. Dedicated new tests
verify the entire old metadata digest and real new guards. Final 485 cases pass on
913 frozen inputs. No production comparison exception or golden constant changed.

## Actual native checks and limits

Four official canonical files pass 17 distinct cases: plugins_manage_install 6,
plugin_install_inspected_sha 4, inspected_sha_catalog 5, install_desktop 2. These use
actual dispatch and local Git repositories, not production plugin installs.
The original long-path run remains failed. Source, assertions and policy are
unchanged in the new shorter fixture; zero automatic file retries are allowed.
The separately attempted session_plugin_rpc file has 5 positives and one
platforms(posix) skip on Windows, so its canonical exit 1 and incomplete verdict
remain. No fake host OS or skip waiver is used. Every one of 23,586 tracked inputs
in the disposable upstream checkout is byte-identical. No pristine SDK shutdown
or whole Hermes compatibility pass follows from these plugin cases.

| Evidence | SHA-256 |
| --- | --- |
| Full baseline-to-stable RPC diff | `836d898186119da80fe1aeafd40f24dce3562aa7438064823d7c50b0ec2dac64` |
| Final 485-case portable log | `13d17736c1833258baf0e34625951e7a9794d7911484d7f176f53e2ffc92b6c6` |
| Original two-counterexample red log | `0c8d424fc44685afece866dc3ac76f78947730983e9d8296f995f95e552fb09f` |
| Original metadata canonical JSON | `635d2dec0c793d2379c332ab954c0eabf44706574430f1d6f48b89153502f9f2` |
| Read-only Pi preflight output | `5fd01137ffd4eca8f2173c93d8843a8ecb5f93e6cc3154fa9544a250f8fc391b` |

Every input of the earlier 72-case Windows native gate remains identical; it was
not rerun. App/build/native-model/signing inputs, owner data and beta.9 assets
are unchanged. The source4444 standard cohort remains 1,141 API26 / 1,142 API37;
external-model, final-source and physical-device gates remain independent.

## Native integration decision and implementation boundary

Ten selected deployed Pi source hashes match immutable 3b; the two reviewed
session_controls anchors occur once each. Neither signal_grove_workspaces nor
signal_grove_workspace_operator exists in the running generation. Service
identity/start properties and install-stamp hash remain unchanged; no config,
credential or job content was read.

The official [plugin RPC registration](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_cli/plugin_rpc.py)
and [native plugin dispatch](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/gateway/session_plugin_rpc.py)
pass params and session_id to handlers after session:control checks. They supply
no trusted NativeReadContext or owner/operator/registered-transport callback.
This is the documented plugin trust model, not a reported Hermes vulnerability.
The worker bridge must not reconstruct those identities from phone params or
weaken its owner/profile/operator checks to fit that interface. The optional
managed source adapter is explicitly version-bound; it is not a plugin that may
edit core files, nor a generally supported upstream extension API.

Before deployment:

1. Supply the missing operator module with explicit authority, exact profile/
   subject grants and fixed private transport, using protected enrollment.
   No broad grant follows from host login or project metadata. The current
   reviewed Windows validation grant does not grant all user projects.
2. Check protected policy freshly before/after reads; changed/revoked policy drops
   contents while retaining submitted state. Keep bounded reads, exact worker
   instance/project scope, duplicate refusal and no-replay recovery.
3. Derive a candidate from the maintained runtime, preserving custom fixes and
   exact manifests. Review the complete source/operator diff, verified encrypted
   recovery and live idle/receipt state before a separately approved switch.
   The previous source approval includes no deployment, grant change or restart.
4. Test real host-owner native HTTPS with the existing worker: explicit list/text
   reads, foreign/API/profile refusals, policy change, disconnect/unknown outcomes
   and unchanged services/models/credentials. Do not persist project contents on
   Pi. Then perform Android acceptance and explicit project enrollment/review.
5. Validate chat/worker/workspace adoption separately. A native session cwd on Pi
   is not a registered Windows working copy; file-read capability does not bind
   engineering execution or authorize dispatch/reassignment. NAS unsupported full
   file identity must remain explicit, without a weaker fallback.

Implement and validate the protected Pi operator binding against the existing
worker enrollment and fixed catalogued SSH route, then prepare its exact runtime
bundle, encrypted recovery and idle maintenance plan. Do not deploy or restart
under the earlier source-only approval. Validate real native HTTPS/Android reads
and independent chat/worker adoption afterwards; read access alone is not an
execution binding. Preserve every original automation/integration and final
phone/release acceptance item.

The [full goal](../GOAL.md) remains active. Original API26 failure causes, the
other 40 stable findings and separately inspected main behavior, pristine shutdown,
actual integrations/automation, doctor/graph findings and final production upgrade/
release requirements remain open. This checkpoint deploys nothing and publishes
no new APK.
