# Evidence navigation

## 13 November 2026 - keyboard transitions pass; API37 Review guard retained

Two actual-keyboard Home picker transitions pass through the original Chat
selector: Apply saves the synthetic choice, Cancel preserves the default, and
both render 16 messages. This does not reproduce or fix either original API26
failure. All 14 focused native methods pass; required checks pass 1,011 JVM/97
reports and lint 0 errors (51/50 warnings). New API26 inventory is 1,137; whole
new-source device acceptance is pending. App main/live services/public APKs and
owner data are unchanged.

Separately b995a28's full hosted cohort fails one API37 Review geometry guard:
538 starts/537 positives/one failure. Hash-verified raw output and its exact
receipt confirm the failing tap was not attempted and consent was absent.
Exact-source analysis infers native IME-window/inset disagreement; its direction
is not observed. No common API26 cause, waiver, retap or complete gate is claimed.
All four goal priorities and every original audit requirement stay active.

[Exact transition, guard and validation evidence](docs/contracts/2026-11-13-keyboard-transition-and-api37-review-guard.md).

## 13 November 2026 - rendered UI diagnostic counterexample passes

Failure-only test snapshots now distinguish selected navigation, rendered
message lists, locale, activity and IME state. Exact source comparison preserves
the original successful actions/waits/methods/assertions. A native controlled
counterexample proves 16 ready messages can coexist with Home and no Chat list,
then verifies rendered Chat with the same data. Its actual error/JSON path
preserves the cause and excludes private fixture canaries. This is no claimed
reproduction or fix of either original hosted failure.

All 13 focused API26 methods pass, including original Demo/runtime/predecessor
cases and the new privacy/render counterexample. Required build checks pass
1,011 JVM/97 reports and lint 0 errors (51/50 warnings). New API26 inventory is
1,136; complete new-source standard coverage remains pending. The last complete
verified gate remains source c9c0718. App behavior, live services, public APKs and
owner-phone data are unchanged. All four priorities and original gates remain.

[Exact diagnostic scope and validation](docs/contracts/2026-11-13-rendered-ui-failure-diagnostics.md).

## 13 November 2026 - explicit native owner candidate; exact UI replay retained

The public owner probe now has an explicit exact-pin candidate mode. Four
unreviewed/unpaired-mode counterexamples refuse before SDK execution; pristine
mode retains warning/exit 2. Three finite candidate owner trials exit 0: empty,
20 seeded room reads and held SQLite contention. The real close fence returns
in 1.219 s with both DBs open, then drains/closes without late reopen. All eight
helper cases, 440 policy cases and 15 runtime-model cases pass. Required Android
checks pass 1,011 JVM/97 reports and zero-error lint (51/50 warnings).

The original b543 hosted pair passes its five-method class sequence and one
537-case API26 shard replay, unchanged. The original failure is still unresolved.
Separately, c9c0718's complete raw standard gate verifies: 1,135 API26/1,136 API37
cases and exact hosted summary/APK binding. No old failure is relabelled fixed.
Latest inspected upstream still flags stable40/main104; no waiver/deployment.
All four goal priorities and original live/worker/phone/release gates remain.

[Exact candidate, replay and source-bound gate evidence](docs/contracts/2026-11-13-native-owner-candidate-and-ui-replay.md).

## 13 November 2026 - heartbeat candidate verified; second API26 failure retained

An exact-pin experimental two-file SDK patch closes heartbeat admission and
drains its single cleanup before database closure. The canonical source tools
verify all 23,586 files and pass eight real-helper/SQLite regressions. Separate
private native owner trials pass empty, seeded and actual held-SQLite scopes;
the close fence returns in 1.224 s with both profile DBs open, then closes them
after drain. Those trials exit 0 without late reopen. Unmodified upstream still
retains its warning/exit 2; public native candidate-mode integration is pending.

The full local suites pass 438 policy + 15 runtime-model cases and the required
Android build passes 1,011 JVM/97 reports and zero-error lint (51/50 warnings).
Android source, production APKs and live services are unchanged. The preceding
b5433027 API26 shard 0 now retains a separate scroll-preservation failure:
`RuntimeSelectionTest` cannot find `chat-messages`. Its raw artifact/log hashes
verify; no common cause with the original Demo failure or full gate is claimed.
All original audit and production/live/worker requirements remain active.

[Exact candidate provenance, tests and limits](docs/contracts/2026-11-13-heartbeat-lifecycle-candidate.md).

## 13 November 2026 - original Demo shard passes; shutdown cause traced

The original source1180 API26 shard1/2 passes all 598 cases locally after15
unchanged class-trial passes. The hosted failure remains unreproduced/unfixed.
Failure-only sanitized fixture snapshots preserve its assertions and timing.
A fresh original-key diagnostic pair passes 5 focused native cases; full
clean-head Android coverage remains pending.

Both default and optional-trace owner probes preserve the actual shutdown
warning/exit 2. The trace confirms a heartbeat callback writes after shared DB
closure. Seeded mode retains 20 real-owner room checks; no SDK/deployed fix is
claimed. Fresh stable/main scans flag 40/104 changed used handlers; the 3 newly
flagged model/profile handlers are statically triaged without waivers.

Required build checks pass1011 JVM/97 reports,422 policy and15 runtime-model
cases; lint has0 errors (51/50 warnings). Product behavior and public beta.9
are unchanged. Final production-phone, real worker/project/automation/integration
and all original acceptance obligations remain. See [exact evidence](docs/contracts/2026-11-13-demo-shutdown-diagnostics.md).

## 13 November 2026 - goal active; four acceptance priorities explicit

The owner resumed the existing full audit goal. Its explicit priorities are
API26 Demo failure investigation, upstream compatibility/shutdown review,
real project/workspace/worker/automation/integration acceptance, and final
production upgrade/release checks. Beta.9 preview availability is complete;
incomplete acceptance stays open. All original audit requirements remain.

Five unchanged source1180 Milestone03 class trials pass15 original native
methods on a fresh owned API26 emulator under the original CI settings.
They do not reproduce or fix the retained hosted failure. One original598-case
shard reproduction is running with all outcomes retained. No app/test assertion,
SDK source, live Pi/model/credential or user-phone data is changed.

[Resumed criteria and exact baseline scope](docs/contracts/2026-11-13-resumed-acceptance.md).

## 13 November 2026 - beta.9 preview published; full audit remains paused

At the owner's request, beta.9/code114 is public as an experimental prerelease
from exact APK source0ee08e3 in main history. Both original-key APKs and all six
assets verify through anonymous public downloads and GitHub SHA-256 metadata.
Production APK:71,052,938 bytes; debug:92,608,818. Checksums, certificate,
183-component runtime SBOM and honest source/validation provenance are attached.
Stable1.0.0 is unchanged. Use the production APK to update the normal app without
uninstalling; Include preview releases enables in-app discovery.

The source's complete standard Android gate and seven selected debug-phone
native positives retain their exact scopes. Upstream compatibility inspection,
final exact-production-phone upgrade/live/offline acceptance and the full
original audit remain incomplete. No failed result/baseline/source guard is
waived. This release-only request does not resume the wider paused goal.
Earlier entries below describe their historical publication/candidate states.

[Download, exact source and remaining checks](docs/contracts/2026-11-13-beta9-public-preview.md).

## 13 November 2026 - current check complete; owner-requested pause

The final actually offline cancellation/recovery method passes in27.984 s:
Stop during thinking and visible streaming, exact recovery, coroutine stop,
overflow refusal, unload/reload and retained original short-limit probes.
Seven selected native methods have positive evidence on the unchanged source0ee
debug app across identified companions; this is not the full twelve-method
external gate. The latest companion source6034/cdb0570164ff compiles and signs
with the original key. App code/defaults/caps are unchanged. Both app APKs,
models, UIDs and core rows are retained; only WorkManager's force-stop timestamp
changes. Exact private SQLite copies are removed and original networking is
restored. No phone test/model CLI/local build remains active.

Source0ee's complete1,135 API26/1,136 API37 standard gate remains accepted.
Source1180 has a retained one-case API26 Demo clarification failure, so its
complete gate fails. Source6034 Android CI is in progress at observation,
compatibility fails and worker CI passes; pending runs are not acceptance.
Beta.8 remains published and beta.9/code114 remains unpublished. Full upstream,
live project/worker/integration, phone and final-release audit rows stay open.
Work stops after this checkpoint at the owner's request; resume only on request.

[Paused evaluation and remaining work](docs/audits/2026-11-13/PAUSED_REVIEW.md).

Earlier sections retain their original source/time scope.


## 13 November 2026 - helper passes; thinking-aware cancellation pending

The unchanged source0ee debug app with the source1180 companion passes the
actually offline six-topic helper/Chinese method (23.968 s). Six selected
current-app native methods now have positive evidence; generation still fails
because its fixture waits for visible output before requesting Stop. Exact
controls show the long-list request emits no answer in512 tokens. A test-only
correction checks Stop during thinking and a separate measured visible stream;
new compilation/physical acceptance is pending. Product code/defaults/caps and
old failures are preserved. The companion update retains23/23 metadata files,
both models, UIDs and both app APKs. Original networking is fully restored;
post-test retention remains separate.

Source0ee's complete standard emulator gate is independently reproduced from
five hash-verified artifacts:1,135 API26/1,136 API37 unique cases, no missing,
duplicate, failed or skipped cases, exact source/APK binding and hosted-summary
match. Five classes/twelve external methods stay outside this gate. New fixture
source needs its own CI. Stable40/inspected-main101 semantic reviews and the
entire original audit/final signed-release scope remain open. Beta.8 remains
published; beta.9/code114 remains unpublished.

[Exact checkpoint and limits](docs/contracts/2026-11-13-thinking-cancellation.md).

## 13 November 2026 - clean output and model-family passes; completion probes open

Source0ee08e3 is pushed and builds with1,011 JVM/97 reports, zero-error lint51/50
warnings and original signing/native/DEX identities. Five isolated actual offline
cases pass: garden, metadata, Qwen import/history, model switching and atomic
import. Combined helper/Chinese and generation cases remain failed at48/96 token
limits. Six helper stages finish; exact-context controls complete Chinese at75
tokens and the second reply at108. A test-only follow-up retains the original
limits and adds separate bounded exact-completion checks; fresh acceptance remains.

Upgrade preserves20/20 files exactly. After tests16 are identical; profile and
WorkManager bookkeeping are separately reconciled, with core rows/schema/work
tables preserved and only last_force_stop_ms advancing. Both original models,
package UIDs and encrypted snapshots verify; temporary private DB copies are
removed. Fresh150-version/native advisory queries return no known indexed
matches. Local release assets and183-component SBOM verify; no public release.

Exact-source parser/registry/sidecar/worker/Android verification passes; complete
API26/API37 union remains running. Stable40/main101 findings remain open, with
latest inspected main415fa33. All original audit and final production-release
requirements remain. [Full source, artifact, failure and retention scope](docs/contracts/2026-11-13-natural-output-phone.md).


## 12 November 2026 - actual helper/cancellation passes, garden fix pending APK

Installed code114 retains20 protected metadata files, two original models and
package identities. Separate offline JNI cases pass six helper topics/Chinese
and generation/cancellation/recovery; the failed initial batch remains failed.
Garden markers reproduce in a fresh case. Same-runtime/system/context comparison
finds forced reasoning closure leaks text; natural completion is clean. Source
restricts that budget to constrained helpers and passes51 native checks. The
48-token reload probe remains; known truncation requires separate exact96 completion.
No app replay or stored-history rewrite is added. Register the twelfth external
method without exclusion expansion. Fresh source/build/CI/phone/release gates and
all original audit work remain. [Exact evidence and correction](docs/contracts/2026-11-12-natural-chat-followup.md).

## 12 November 2026 - checked code114 and explicit garden model gate

Clean0bb325e code114 independently verifies1,011 JVM cases/97 reports, zero-error
lint, original signing and every ABI/DEX check. Production is71,052,646 bytes.
Debug/test APKs are hash-verified in the reconnected phone's Downloads; actual
installation/JNI/garden/helper/family/cancellation/upgrade acceptance is pending.
CI caught the omitted new physical garden method. It is now explicitly registered
as the twelfth external method in the same five classes; all eleven old methods,
prerequisites and standard exclusions remain. Both the corrected copy and authoritative
working tree pass all422 policies; the latter takes24.678s with no failures/skips.
Historical eleven-method reports retain their old scope. Exact-source public CI,
required compatibility review and publication remain separate, unresolved gates.
[Build identities, policy failures and corrected scope](docs/contracts/2026-11-12-external-garden-gate.md).

## 12 November 2026 - bounded reasoning and reviewed helper schema

Code113 fully builds/signs (1,006 JVM cases, zero-error lint, all ABIs) but its
helper diagnostic fails0/6 without a reasoning limit and5/6 with the budget alone.
Code114 now implements the supported budget plus constrained unique reviewed-ID
selections. Integrated native vocabulary coverage passes49 checks; all six public
helper diagnostic topics pass at the original96-token total. Original48/96-token
physical assertions are restored. These diagnostics are not final APK/JNI proof.
Fresh build/CI/phone/upgrade/publication gates and all original audit work remain.
[Exact failed stages, correction and scope](docs/contracts/2026-11-12-bounded-helper-selections.md).

## 12 November 2026 - native answer projection, fresh gates pending

The pinned runtime diagnostic identifies the pre-closed reasoning prefill as the
source of the observed marker/repetition. Code113 prepares one upstream session
for template/sampling/parser and streams only its append-only answer channel.
The physical vocabulary regression passes29 checks and one bounded native CLI
turn produces a clean sentence. These do not certify the APK or helper. Fresh
JVM/lint/all-ABI/standard-CI and physical JNI/helper/family/recovery gates remain.
The helper shares a384-token reasoning/answer budget; strict reviewed-ID validation
and approval boundaries remain. Old history and its observed defect are retained.
[Correction and exact validation scope](docs/contracts/2026-11-12-native-answer-projection.md).

## 12 November 2026 - production upgrade/read checks and native output blocker

The installed original-key beta.9/code112 retains its identity and saved
connection. Refreshed current-server history and stored project details load.
An older cached missing chat remains preserved and is not counted as a live pass.
Production LFM3 completes a genuinely offline turn and its reply reloads with
weights unloaded, but exposes a reasoning-close marker and repeats a sentence:
native output acceptance and publication remain blocked. No history is rewritten.
The owner restores normal networking. Bounded resource points are not sustained
benchmarks. Fresh b599 complete standard CI independently verifies1,135 API26 /
1,136 API37 unique cases; external models remain separate. All original audit
rows, stable40/main101 compatibility findings and live worker/runtime limits stay.
[Exact observations, failures and scope](docs/contracts/2026-11-12-production-phone-offline-output.md).

## 12 November 2026 - fresh upstream main pin advances

The independently hash-verified b182 compatibility artifacts inspect stable
`3b421578588243f920886e6fe79a6faa87ea026e` with40 changed used handlers and
main `1773ba3bab962dc7755a64ddfb1235428d32b1ec` with101. The preceding inspected
main2bfa6b0 had100. The newly flagged key is `env_config:_normalize_root_model_keys`;
other transitive/semantic review remains required. Neither report removes a used
route; both require review. These are review findings, not proven bug counts.
No baseline, handler waiver, app/APK or live service changes. The failed verifier
expecting the older main count is retained; the same downloaded ZIPs and exact
reports are independently reconciled without another download.
[Current signed-candidate and release scope](docs/contracts/2026-11-12-reviewed-main-beta9-candidate.md).

## 12 November 2026 - fresh reviewed-main signed beta.9 candidate

Clean b182ae8 produces new original-key APKs: production 70,993,354 bytes,
debug 92,543,394 and test 7,901,783. Independent 1,006 JVM/96-report, zero-error
lint, signature/package/version/native/DEX and 183-component inventory checks
pass. Fresh advisory queries retain limited scope. Phone identities/original
models and encrypted debug metadata verify; the production APK is staged for
the normal installer. Production upgrade/live retention, exact-source CI,
compatibility and public download gates remain. Beta.9 is unpublished.
[Exact source, hashes, scope and retained failures](docs/contracts/2026-11-12-reviewed-main-beta9-candidate.md).

## 12 November 2026 - actual owner room reads and complete Android freeze

20 native-owner room checks, independent post-exit retention and four preflight
refusals verify. Both seeded/empty modes retain exit 2/one SDK shutdown warning.
Complete 2970 Android raw union verifies 1,135/1,136 unique cases; later Python/docs
retain 543 Android inputs. [Exact scope, failures and release work](docs/contracts/2026-11-12-native-owner-room-reads.md).

## 12 November 2026 - current-stable authority

Exact stable authority/wire 46 and attach-first 13 flows pass unchanged assertions,
57 critical guards and 532/520 module hashes. Three source/invocation controls and
422 portable/15 runtime/checksum policies pass. [Scope and remaining transport/
room/worker gates](docs/contracts/2026-11-12-current-stable-authority.md).

## 12 November 2026 - loader/start-entry and complete hosted evidence

87 real-source fixture cases, three source/invocation refusals and 422/runtime 15/
checksum policies pass. Complete d505 hosted coverage independently verifies all
1,132 API26/1,133 API37 unique cases. [Exact sources/adapters and remaining gates](docs/contracts/2026-11-12-native-loader-startup.md).

## 12 November 2026 - native workspace Review

31 API26 methods,16 exact native taps and eight Review observations pass. Full
hosted/API37/live release acceptance remains open. [Bound source/artifacts and
retained failures](docs/contracts/2026-11-12-native-workspace-review.md).

## 12 November 2026 - current stable authentication and clean release inventory

The final repository probe passes77 distinct cases against exact stable Hermes
3b42157: native sign-in/refresh, isolated history, private-grant refusal/routing,
saved-gateway callback,20 deployment-header/cookie cases and five actual async
admission/client-accounting cases.48 critical Git blobs, clean source HEAD and
946 loaded project-module hashes bind the fixture. Unknown source/revision
controls refuse before fixture creation. Synthetic identities/captured peers and
an explicit downstream handler adapter remain; this is not Pi/Android/TLS/full
canonical-socket or PM server-start acceptance. Earlier failed fixtures remain.
No compatibility baseline variant or used-handler waiver is added.

Clean main f9e3835 builds with1006 JVM/96 reports, lint0 errors51/50 warnings,
original signing/package/version identities and complete native/DEX alignment.
Fresh OSV query at16:39 UTC covers150 Maven versions with no matching known
advisories; the pinned native query also returns none, within index limits.
Private release inventory verifies95 dependency inputs against strict metadata,
32 packaged native files,183 CycloneDX1.6 components, certificate, checksums and
local build provenance. Six prepared files are local only, not a GitHub release.

Portable422/runtime15 policies pass. Fresh stable40/inspected-main100 findings
remain; the inspected main pin is2bfa6b0. Full hosted Android coverage, complete
dependency/semantic review, original-key physical production upgrade/live checks
and publication/download verification stay open. Prior phone retention and all
original worker/project/integration audit requirements remain intact.

[Exact scope, source and artifact identities](docs/contracts/2026-11-12-current-stable-auth-release-inventory.md).

Use [validation history](docs/VALIDATION.md),
[on-device validation](docs/ON_DEVICE_AI_VALIDATION.md) and dated
[contracts](docs/contracts/) for reproducible acceptance scopes and limitations.
Private raw logs, hashes, APKs and runtime observations remain on the execution
owner. A derived graph is navigation, not independent validation.

Documentation checkpoint, 12 November: README and [feature matrix](docs/FEATURES.md)
now consolidate implemented features and scoped current figures, with a genuine
synthetic candidate screenshot and preserved historical captures. Published
stable/preview metadata is rechecked; roadmap/usage/compatibility/release guidance
is reconciled. No executable/APK change or new functional pass is claimed.

Latest: [physical restart retention](docs/contracts/2026-11-12-physical-restart-retention.md)
records two original positive cases, one intentional preparation interruption and
one approved normal Android restart. Full owner retention and no automatic
load/replay verify. [Both keyboard states](docs/contracts/2026-11-12-ready-keyboard.md)
records the reproduced setup defect,422 policies, fresh signed build and both
native paths plus four unchanged focused cases/six exact taps. Older unresponsive
fixtures remain unknown; complete CI/production-upgrade/release gates stay open.

Earlier: [full foreground phone download](docs/contracts/2026-11-12-foreground-model-download.md)
verifies explicit resume/hash/atomic installation of the entire LFM3 file in
27,010 ms, scoped deletion and retained owner data, with sampled kernel/screen/
foreground checks and restored temporary setting. Earlier failed runs remain.

Earlier candidate: [download-cancellation fix and partial native acceptance](docs/contracts/2026-11-12-download-cancellation.md)
records two reproduced regressions,1006 JVM, original-key APK checks and current
phone/emulator cancellation/discard passes. Resume failures, phone kernel-freezer
recovery, retained data, exact APK hashes and remaining gates are explicit.

Earlier candidate: [uncompressed DEX and scoped emulator checks](docs/contracts/2026-11-12-uncompressed-dex-candidate.md)
records1004 JVM, original signers/native alignment, zero-error lint51/50 and10
API26 local-AI UI passes. UIDs/core hashes survive emulator upgrades; the native
installer resolves a host timeout without replay. Two HTTPS timeout failures,
partials, fresh stable40/main100 handler findings and remaining release gates
are retained. Known-indexed advisory checks do not certify full security. The
original-key production candidate is not published or installed on the phone.

Earlier: [six physical offline cases](docs/contracts/2026-11-12-physical-offline-model-acceptance.md)
verify real LFM3/helper and Qwen/import/model-family behavior on the retained-data
owner phone. Original models, preferences and earlier chat remain; the owner's
confirmed added turn is preserved. Normal networking is restored. Five external
model methods and production release/upgrade gates remain. Earlier emulator
failures are retained separately.
[Live deadline component](docs/contracts/2026-11-12-pipe-exchange-deadline.md)
records exact Windows deployment, unchanged receipts/task/policy/source, verified
scoped recovery/ACLs and hosted66-case proof. Pi status/prepare pass but list fails
before submission, with cause unproven. No full Pi/HTTP/WS or public APK acceptance.

The corrected main-branch Windows read transport gate passed all 50 distinct
native cases (run 43150240082, source 642eaa2), with exact source and artifact
hash verification. The earlier SID text-alias test failure remains retained.
This does not certify live enrollment, NAS support, phone or release acceptance.


[Explicit enrollment checkpoint](docs/contracts/2026-11-12-worker-read-enrollment.md)
adds 58 local native and 373 policy passes, 1004 JVM cases, the actual registered
foreground worker read and retained-data API26 upgrade plus two project contract
cases. New hosted/production/physical/release acceptance remains separate.


[Managed host checkpoint](docs/contracts/2026-11-12-managed-worker-read-host.md)
adds 65 native/387 policy and frozen-source build/device verification, plus the
private seven-process actual-worker read and encrypted component recovery.
Persistent deployment, native coordinator/phone and complete release acceptance
remain separate. The previous 6d9863c hosted 58-case artifact is verified.


[Persistent owner bridge deployment](docs/contracts/2026-11-12-persistent-owner-read-bridge.md)
records the actual idle cutover, local/Pi read paths, preserved receipts/denials,
source/path/ACL checks, encrypted recovery and independently verified hosted
65-case artifact. Doctor and graph findings remain explicit. Native coordinator
actor/profile, Android/NAS and full signed APK delivery are still separate gates.


## 12 November 2026 - native coordinator workspace-read source

407 portable policy cases and 12 strict actual-native authority/contract cases pass.
All 661 pre-existing non-Markdown frozen-source 706 inputs are hash-verified
unchanged; no new Android/device/APK result is claimed. No Pi/runtime deployment
occurs; Windows read daemon and existing receipts stay unchanged. [Evidence,
reproduction and full remaining scope](docs/contracts/2026-11-12-native-coordinator-workspace-reads.md).


Exact-helper-envelope follow-up: 408 portable / 21 coordinator-specific and
12 strict actual-native cases pass (private proof 752). The new Pi transfer
trial was rejected before execution and is pending explicit approval; no
production or new APK acceptance is claimed.


## 12 November 2026 - stable channel registry context

414 portable, 12 real-source component and 15 model-observer checks pass. Exact
five-source review closes only seven stable findings (47 -> 40); saved main
stays 97 -> 97. Original policy/golden and Android inputs are preserved. [Scope,
reproduction and remaining gates](docs/contracts/2026-11-12-channel-registry-context-review.md).
No Pi trial/deployment, account delivery, phone or new APK acceptance is claimed.


## 12 November 2026 - complete standard hosted Android gate

Six artifact digests and complete raw native case unions verify: API26 1132,
API37 1133, JVM 1004/96 reports, both lint variants zero errors and 32 native
ABI/page alignments. All 545 current build/gate inputs match the tested commit.
External-model acceptance stays false; CI debug signer differs from retained
owner signing and no APK is installed/published. [Exact scope and remaining
release checks](docs/contracts/2026-11-12-complete-standard-android-gate.md).
