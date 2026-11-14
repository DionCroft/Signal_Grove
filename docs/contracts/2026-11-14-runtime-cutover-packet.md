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

The [normal SDK provider/lifespan checkpoint](2026-11-14-sdk-provider-lifespan-pm-hold.md)
now verifies real bundled-provider PKCE/refresh and guarded SDK retirement over
one native-owned socket, with observed finalization and ended children. Its
overall gate remains held by a PM readiness warning for five tools. Supported
PM/artifact provisioning and complete transaction/live/phone acceptance remain;
no warning is suppressed and staging is not live approval.

## Staged readiness gate now closed, live transaction still open

The supported exact five-tool PM install and independent seven-tool doctor/hash/
probe review pass. A fresh normal SDK provider/native-retirement/lifespan fixture
now passes with no PM warning or fence refusal and ended owned children. The
earlier held receipt remains. This does not establish either canonical-home PM
selection or authorize live installation/service/permission/policy actions.
Implement/test the exclusive bounded executor, journal, recovery and rollback
before the concrete final live approval; retain all gates in this packet.
[Current evidence](2026-11-14-staged-pm-readiness-and-sdk-pass.md).

## Exclusive journal component tested; coordinator still open

The new source-only durable journal passes all 39 methods on the actual Pi,
including native lock contention and directory/inode/partial-write recovery
boundaries. It binds a previously observed directory identity and grants no
resume authority when reopened. It executes no service, permission, policy,
restore or replay action. Wire it into the fixed coordinator/native effect
verifiers and explicit reconciliation/rollback before final live review.
[Component evidence](2026-11-14-exclusive-cutover-journal.md).

## Fixed coordinator now tested; concrete native operations still open

The fixed nineteen-stage forward/thirteen-stage separate recovery coordinator
and durable authorization/history bindings pass all 95 focused methods on Pi.
Actual journal/files/locks are used with synthetic SDK/manager observations.
No failure retries or automatic compensation are introduced. Implement and test
the concrete native PM/backup/drain/retirement/service/publication/health adapters
before final live packet review; all canonical, permission, credential/data,
work-fence and original audit/release gates remain.
[Current coordinator scope](2026-11-14-fixed-cutover-coordinator.md).

## Actual service-manager component, without live activation

The pinned native user-D-Bus adapter now passes 23 Pi boundary methods and an
actual owned-service lifecycle with six exact job completions, six spent-action
refusals and eight verified payload/helper outcomes. All disposable units and
processes end; independent recovery conserves live state. This fixture seeds
non-service prerequisites explicitly, so it proves no real backups, canonical
PM, admission, publication/reload or full production cutover. Actual native
fault/unknown paths, remaining concrete adapters, all original work/data/fresh
backup/canonical PM gates and a final separate live review remain required.
[Exact native adapter scope](2026-11-14-native-service-manager.md).

## Current one-use dispatch claim

The original native adapter's own spent set did not exclude a replacement
adapter/reopened pending intent. The current journal-owned claim requires the
original live PID/operation and exact authorization, and refuses reuse, fork
and reopen. All 122 related Pi methods plus a fresh actual owned lifecycle pass;
source/raw/native cleanup independently verify. Real manager error/unknown paths
and all remaining adapters/fresh canonical PM/work/backup/health/recovery gates
remain before a separate complete live approval.
[Current source and exact evidence](2026-11-14-one-use-native-dispatch.md).

## Actual owned error/unknown component cases

The current adapter retains immutable last-attempt ACK/completion/effect phases.
All 124 related methods and five actual owned replacement/reopen/helper-error/
late-completion cases pass on Pi. Independent raw/native/journal review verifies
nine job identities and all owned cleanup, preserving the first observer-race
failure. Uncertain original intents are never verified by later observation or
replayed. This remains component-only; actual PM/backups/admission/artifact/
reference/mode/reload/live-user/health and complete forward/recovery gates stay
open before a separate live approval. No live operation is authorized here.
[Exact current evidence](2026-11-14-native-service-error-paths.md).

## Native file/mode primitives tested, live catalog/preparation still open

The exact native file/mode component passes all 37 Pi methods/no skips and
independent source/raw/process/scratch/runtime/live review. It uses complete
role catalogs, original live journal claims, retained atomic exchanges and
durable effect receipts. Named modes are descriptor-bound/nonrecursive; explicit
runtime/policy recovery preserves retired entries and both old CLI forms.
It requires existing parents. Bind the renderer's ten exact paths/payloads,
prepare missing parents explicitly and retain composed packet evidence next.
These owned unit tests seed non-file gates and do not validate the actual live
path catalog, policy grants, full ancestry, PM/backups/admission/reload/live-user/
health or full forward/recovery. All those gates and separate live approval stay.
[Exact current component scope](2026-11-14-native-file-publication.md).

## Exact rendered catalog and owned composed recovery now verified

The renderer's ten exact outputs bind to native preimages/payload hashes and the
maintained policy parser. One-level parent preparation occurs under the same
original stage claim, recording witnessed new inodes before file publication.
All 62 Pi methods and a retained owned fixture pass: eight actual file/mode
stages, five publication parents, five retained entries, original CLI restoration
and newer-data preservation. Independent source/raw/native/history/policy review
conserves live state. Non-file stages are explicitly synthetic. Next capture the
actual reviewed Pi catalog read-only, then finish real canonical PM/backups/
admission/reload/live-user/health/graph and complete forward/recovery acceptance.
All protected ancestry, original audit/phone/release and separate live approval
requirements remain; no live path or permission is changed by this checkpoint.
[Current exact source and evidence](2026-11-14-rendered-publication-recovery.md).

## Actual publication catalog reconciled read-only

The actual reviewed Pi generation now passes native read-only catalog capture
and independent reconciliation: ten exact paths/preimages, matching staged
wrappers, the existing literal CLI symlink and three missing publication parents.
All seven reviewed 0775 directory identities remain unchanged. A separate
23-entry ancestry inventory has no additional projected blocker beyond those
seven proposed mode changes; it is not a protected-reader or descriptor-guard
acceptance. Source/facts/live service incarnations are conserved; zero live
writes, service actions, worker requests or inference calls. Full protected
ancestry, canonical PM/backups/admission/reload/authenticated health and complete
runtime-only recovery remain. The full goal and every original requirement stay
active; no APK change.
[Actual path/preimage and ancestry inventory](2026-11-14-actual-publication-catalog.md).

## Guarded publication ancestry checkpoint - 14 November 2026

The renderer now captures descriptor-anchored publication ancestry and the
selected source-root identity. The publisher binds its reviewed ancestry hash,
checks it throughout each claimed stage, records state transitions and accepts
only the seven actual named mode effects and witnessed new parents. The guarded
owned fixture completes eight forward/recovery stages, preserving five retired
entries, the original CLI and newer data. All 80 Pi methods pass/no skips;
Windows: 846 cases, 719 pass/127 platform skips. Independent raw/native/source/
history/policy review passes. Real preflight captures 20 existing directories
and three missing parents; publication correctly refuses before the seven
unchanged 0775 modes are secured. Full PM/backup/admission/reload/authenticated
health/history/graph and complete runtime-only cutover/recovery remain open.
Journal/PM/input ancestry needs its own backend binding; this publication guard
does not attest every operational path. No live change or APK change. Every
original audit/on-device/security/release requirement remains active.
[Exact source and scope](2026-11-14-publication-ancestry.md).

## Exact PM home selection checkpoint - 14 November 2026

A fresh no-site, source-bound observer now distinguishes exact PM home/store/
owner and committed selection from fallback interpreter paths. It executes only
six verified SDK module snapshots, binds the install stamp and expected facts,
detects native input/facts drift and refuses effects or unknown SDK imports.
All 14 Pi methods pass/no skips. Independent recovery verifies four actual
contexts: both canonical-home selections are missing; both owning homes have
committed selections. This confirms both canonical preparation prerequisites;
it is not PM currency, normal launcher or full readiness acceptance. Windows:
860 cases, 723 pass/137 platform skips. The native wrapper's final comparison
failed through private-variable shadowing after all five phases ended zero;
original failure stays failed, with component evidence recovered read-only.
No probe replay, PM action, live write or service change. Every original audit/
phone/security/release requirement remains active.
[Actual contexts and evidence](2026-11-14-pm-home-selection.md).

## Native PM preparation checkpoint - 14 November 2026

A journal-bound PM preparation actor now dispatches one supported full install
inside an exact finite native maintenance unit. It binds reviewed source/home/
store, the manager/controller incarnation and protected inputs, retains bounded
logs and receipts, then independently verifies currency, committed facts and all
four activation stamps. Actor/replacement reuse cannot replay a claimed step.
All **48 Pi cohort methods pass/no skips**, followed by one actual candidate
install in a fresh private home and an ARM64 dependency probe. Independent raw/
native/source review passes; both owned units are stopped with empty cgroups.
Windows: **883 cases, 733 pass/150 platform skips**. Thirteen new Linux methods
run in this cohort; the 25 service cases overlap earlier evidence.

The first actual sync committed dependencies and passed SDK currency, but left
activation stamps missing. Its outer attempt remains failed/held, reconciled
read-only without replay or manufactured stamps. The corrected command uses
Hermes' supported full-install path in a different fresh acceptance home. Two
earlier pre-dispatch fixture failures remain recorded. The source inventory,
41 SDK caches, existing facts, seven modes and live service incarnations conserve.
Both canonical-home selections remain unprepared. Full operational ancestry,
fresh encrypted backups, admission/retirement/reload, authenticated health/
history/graph and complete runtime-only forward/recovery acceptance remain before
the concrete final packet and separate live approval. Every original audit,
phone/local-model/security/release requirement stays active. APK/build inputs
are unchanged.
[Source, failures and exact scope](2026-11-14-native-pm-preparation.md).

## Shared operational ancestry checkpoint - 14 November 2026

The journal and production PM actor now bind one reviewed descriptor ancestry
with the publisher. Ten explicit roles cover journal, canonical-home context,
old/new sources and stores, backup output/program/identity parents and executor.
Every journal check revalidates that boundary. Production PM refuses a missing
boundary, an unguarded/different journal or a different role/path; hashes alone
do not substitute for native witnesses. The publisher advances a witness only
after observing its actual mode/parent effect, before writing a guarded receipt.

All **208 affected Pi methods pass/no skips**, including 21 operational boundary
cases and the existing journal/publication/PM/service cohorts. A separate actual
SDK install shares the real-root guard with its journal in a fresh isolated home;
currency, four activation inputs, ARM64 imports and retained data verify.
Independent raw/source/native review passes. A read-only actual-path observation
also verifies ten existing operational roots. Windows: **905 cases, 737 pass/168
platform skips**; 165 platform methods have scoped Pi evidence, three older cases
retain scope. Both owned units stopped with empty cgroups; source, 41 SDK caches,
existing facts, seven real modes and live service incarnations are unchanged.

Parent ancestry does not verify backup contents, a decrypt/restore, program/key
file inputs, admission, reload or authenticated health. Both canonical-home PM
selections remain unprepared. Next implement the fresh encrypted-backup adapter,
then finish the full bounded transaction/recovery and concrete final packet
before separate live approval. All original API26, upstream, project/worker/NAS,
automation/integration, phone/local-AI/security and signed-release tasks remain
active. Android/build inputs and the published beta.9 APK are unchanged.
[Source and acceptance scope](2026-11-14-operational-ancestry.md).

## Backup archive checkpoint - 14 November 2026

The backup content component now verifies the exact pinned SDK-selected member
set, streamed CRC/hash/size bounds and SQLite integrity in fresh private images.
It rejects duplicates, extras/missing files, unsafe ZIP types, corrupt content,
empty/truncated SQLite images and attempts to classify a `.db` as a byte copy.
A reproduced pre-publication counterexample confirms SQLite accepted zero bytes
as an empty database; strict header/page checks now refuse it.

All **22 Pi methods pass/no skips**. A separate actual SDK/age fixture verifies
11 selected state members, seven exclusions, two committed uncheckpointed WAL
rows, matching encrypted/decrypted ZIP hashes and retained source data. Only
generated fixture data and a disposable private key are used. Successful fixture
plaintext is removed by exact native identity. Independent raw/source/ZIP/SQL/
crypto readback passes; both owned units stopped. Windows: **927 cases, 757 pass/
170 platform skips**; 167 platform methods have scoped Pi evidence and three
older cases retain scope. All 23,628 source inputs, 41 SDK caches, existing facts,
seven real modes and live service incarnations conserve.

This is content verification plus a native fixture, not the production backup
transaction, private Release publication, fleet freeze or operational restore.
Next implement the journal-bound one-use backup transaction and private upload/
download verification around this path, then finish admission/reload/health and
the full bounded runtime recovery packet before separate live approval. Every
original audit, local-AI/phone/security and signed-release requirement stays
active. Android/build inputs and the beta.9 APK are unchanged.
[Exact source and scope](2026-11-14-backup-archive-verification.md).
