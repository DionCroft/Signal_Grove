# Review-only Pi cutover packet and recovery reconciliation

14 November 2026. A concrete isolated command/unit packet is rendered and native
syntax-tested. It is **not activated**, and the live transaction is not yet ready
for approval: exact policy, canonical-home PM, work fencing and the full executor/
rollback review remain. The full audit and all four original priorities stay active.

## Actual artifacts and tests

`scripts/plan_pi_runtime.py` accepts a closed, non-credential deployment layout
and renders four source artifacts: client/serve wrappers under scripts/bin and
gateway/serve overrides under scripts/systemd. It emits review data only; no
filesystem installation, chmod, grant, service action or restore is implemented.
Literal normalized paths reject shell/systemd injection and foreign/account-wide
fleet roots. Caller-selected commands or credential fields are not inputs.

All gateway start, stop-marker and post-stop cleanup lines bind the same exact
SDK-published launcher/source/store. Serve preserves the existing protected
environment, private host selection and port behavior, checks its canonical home
and clears a transient runtime override. The client preserves an explicit
profile home, caller cwd and literal arguments. New named drop-ins retain old
fragments/90 overrides for reviewed rollback. Existing source is not overwritten.

The SDK publishes a fresh permanent command directory inside approved staging;
its executable is byte-identical to the earlier accepted launcher. Both wrappers
and all four source artifacts are staged. The real assembled units contain the
unchanged current fragment plus current 90 override and proposed 100 override.
Actual `systemd-analyze --user verify --man=no` exits zero for both assembled
units. All three real shell syntax checks exit zero. These commands parse/check
the staged files; they do not start, install, enable, reload or stop a service.

All 12 new renderer guard tests pass on the actual Pi with no skips. Windows
runs 576 portable tests: 573 pass and three actual-POSIX cases skip, as recorded
previously. No new Android build, phone upgrade or published APK is claimed.

Independent raw recovery checks logs, exact source artifacts, actual permanent
command bytes/modes, assembled unit files and current effective source hashes.
Every source input/native mode and all 29 separately recompiled native caches
verify, with original facts/services/permissions retained. All owned check
processes have ended. A first private review helper has an ineffective
self-comparison as its inventory-helper guard; that check is not accepted as
provenance. A separate read-only observer authenticates all five verifier modules,
renderer and tests against authoritative bytes before recomputing the complete
23,628-input runtime inventory. No native check or service operation is replayed.

## Exact named permission prerequisites

The current native observations identify seven group-writable entrypoint/policy
ancestors. Every one is owned by the operator; current mode is 0775 and proposed
mode is 0755. No directory is changed, and no recursive change is proposed.

| Directory role | Purpose of review |
| --- | --- |
| Account `.config` | Systemd configuration ancestry |
| Account `.local/bin` | Stable user command link |
| Owner Projects directory | Strict protected-reader ancestry |
| Fleet root | Strict protected-reader and runtime/source ancestry |
| Hermes `runtime-generations` | Runtime generation name protection |
| Fleet `scripts/bin` | Maintained wrapper source ancestry |
| Fleet `scripts/systemd` | Maintained unit source ancestry |

The first two project ancestors are the existing protected-policy reader blocker.
The other five protect the concrete proposed entry chains. Owner-only group
membership is not substituted for the strict reader's native mode checks. Any
change needs exact named approval, pre/post identity checks and reviewed rollback;
the reader is not weakened. Existing files, ownership and unrelated paths stay.

## Existing backup outcome reconciled read-only

The scheduled unit's last status is nonzero. Existing current-day encrypted full
and supplemental archives nevertheless have later verified-publication receipts.
Read-only inspection verifies age headers and local checksums, receipt identity,
and current encrypted/checksum asset sizes/digests in the existing private Release.
There are four matched remote assets. The full archive receipt records explicit
publication recovery, retained incomplete upload, no repeated native backup
generation and no retention. The supplemental receipt records age round-trip and
download verification. No new backup, download, upload, deletion or unit reset
runs during this reconciliation. The unavailable bounded journal is not evidence
of an absent earlier failure.

This is verified existing recovery, not a newly successful scheduled run or an
operational restore test. The full native backup is a live snapshot, not a frozen
fleet snapshot. A fresh verified pre-cutover backup and post-change backup remain
required by the fleet procedure; source rollback must not restore old DBs over
newer conversations or replay unknown admissions.

## Current worker variant reconciled without a request

Fresh worker preflight initially refuses before import/request because worker.py
differs from the old trial. Static inspection proves exactly two declared additive
anchors: the maintained passive receipt observer and a closed observe RPC branch.
The installed observer matches its maintained workflow-hardening source; all six
existing observer tests pass with scratch on the execution owner. Workspace RPC,
host daemon and read provider remain byte-identical to the earlier read trial.
The current worker is not claimed byte-identical to that earlier base file.

The reviewed-variant preflight passes on the actual Windows host: active owner,
fresh ready projection, unchanged read-runtime component, protected policy/registry,
one existing local validation-project grant and the same eight completed receipts.
It sends zero worker requests and changes no source, grant, registry or daemon.
Private routing metadata matches the freshly observed Pi authority. The failed
old-hash preflight and a partial source snapshot remain. This is not real project
execution, NAS identity support, a new grant or a successful phone workspace read.

## Next concrete stage

The subsequent guarded-client route check passes independently in isolated
staging; it does not prove the still-required provider session/pinned native
connection or complete transaction. Current API26 navigation failures are
retained separately and keep final phone/release acceptance open.

The [guarded client and old-stop PM review](2026-11-14-retirement-client.md)
adds a canonical-home PM prerequisite for the old loaded stop/cleanup launcher.
Its explicit-interpreter start does not establish those facts. The full executor
and supported provider-session connection remain open; no live action is approved.

The subsequent [native helper and private-policy check](2026-11-14-native-systemd-and-policy.md)
now passes in isolated staging. Its current inventory has 41 validated native
caches. The earlier 29-cache observation above remains historical. These results
do not activate this packet or complete its bounded transaction review.

Complete the bounded live transaction/executor with no-replay rollback. Preserve
the accepted exact private policy and real isolated systemd stop-helper evidence;
never invoke the cgroup reaper directly in an agent/shared cgroup. Only an
explicitly owned transient acceptance unit may contain disposable sentinel
processes; ordinary fixture files stay in approved control-plane staging.

After all preparation, request separate approval for the actual canonical-home
PM provisioning, seven named mode changes, reviewed policy, new source/drop-ins/
CLI link, fresh encrypted backups and idle-only two-service cutover. Gateway
external drain must be observed with current identity/zero work; serve retirement
must prepare/commit its process-owned fence. Status snapshots alone do not grant
retirement. Busy/unknown work holds the transaction. Stop old loaded units before
reload; rollback changes runtime references, preserves new data and never
replays tasks. Current staging approval authorizes none of those live changes.

Continue the original API26 cause, upstream used/transitive review (last observed
40 stable/144 main used-handler changes), actual project/workspace/worker/NAS,
automation/integration, doctor/graph and final signed-production phone/release
requirements. Neither this parser packet nor a single fixture grant closes them.

## Evidence identities

| Evidence | SHA-256 |
| --- | --- |
| 576-case Windows portable log | 5a96f55dc7f26e6816d1b678c814baeb44783bc96da9af1bb87d850b4e74f649 |
| 12-case actual Pi log | 1ce7020f6e0ea6675a1cd6b58fd5a13237c9a6693f26484608b6337a16424b90 |
| Native assembled-unit verification log | e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855 |
| Independent raw packet and current references | 905ab0d484713d107cc5e39b7a89dbae88c397dbe6c5f12921a74360edd1a18c |
| Independent complete helper provenance | f2a43baa3631c01d76ba1d5586d65c0181a5dc3fb33cced4ce5527ccb4fe5f2f |
| Existing private backup receipt/asset reconciliation | 35c0f1db0238469982228921a579f46528df36147f8065eb142bd0212ea3a0d6 |
| Declared current worker variant review | 6199029bd4261bdb1e21b80182b81338a75d80b8f2de4bda5436370b3f62e1f3 |
| Fresh current worker preflight | 2638896bfd699d8261384842e226b492864a8a0759927383c6a3af27aa2afd97 |

The [subsequent single-socket HTTP component](2026-11-14-pinned-retirement-http.md)
adds a tested existing-connection adapter with no reconnect/retry/login/refresh.
Its 18 real TCP cases and 623-case portable suite use synthetic native-owner and
provider seams. Actual kernel socket ownership, supported provider sign-in and
the complete bounded executor/rollback remain prerequisites. No live action is
authorized or performed by that source/test addition.

The subsequent [native socket-owner checkpoint](2026-11-14-native-socket-owner-and-api26.md)
adds a real procfs observer and verifies it on the isolated Pi: 59 component unit
methods/no skips and four guarded TCP exchanges on one fixture-owned connection.
Provider/protocol are synthetic; normal provider/full SDK listener/lifespan and
the complete transaction still require acceptance. All owned checks are terminal.
Its separate Android review also verifies the completed 1,142-method standard
API26 union. Historical causes, full matrix and signed-production gates remain.
