# Signal Grove project state

Beta.17 is the public preview. The original-key Xiaomi upgraded from beta.16
and reconnected to the saved Hermes server. The physical network-disabled
test also ran Kokoro v1.1 alongside LFM3 at its 4,096-token context and verified
separate LFM3-Audio-2B speech and transcription. The next preview integrates
Kokoro v1.1 in Read aloud for Hermes and Phone Chat on ARM64. The next signed
release and the remaining live audit checks are open.
[Beta.17 release](docs/releases/1.1.0-beta.17.md) ·
[Phone voice evidence](docs/VALIDATION.md#offline-kokoro-and-lfm3-audio-on-xiaomi).

[Current goal](docs/GOAL.md#active-scope--27-november-2026) ·
[Execution ledger](docs/AUDIT_EXECUTION_PLAN.md).

## 27 November product and release audit

Hosted standard coverage passed at app source 1719943, with 1,281 cases on
Android 8 and 1,282 on Android 17. Both versions passed the separate public
LFM3 transfer checks. Today passed exact-request approval and reconnect
checks against isolated Hermes stable and main. Physical checks, live Hermes
acceptance and the signed release remain open.
[Current audit](docs/audits/2026-11-27/REPORT.md) ·
[Validation evidence](docs/VALIDATION.md#android-management-fixture-sign-in-ordering).

## Beta.16 released

Signal Grove 1.1.0-beta.16, code 121, is available as a signed preview.
[Download the APK](https://github.com/DionCroft/Signal_Grove/releases/download/v1.1.0-beta.16/hermes-agent-mobile-1.1.0-beta.16.apk) · [Release evidence](docs/releases/1.1.0-beta.16.md).

## Beta.15 released

Signal Grove 1.1.0-beta.15, code 120, is publicly available. The exact
source passed its Android 8 and 17 checks. The original-key emulator
upgrade preserved a saved task, and the selected dark palette persisted
after reopening. [Download the APK](https://github.com/DionCroft/Signal_Grove/releases/download/v1.1.0-beta.15/hermes-agent-mobile-1.1.0-beta.15.apk) · [Release evidence](docs/releases/1.1.0-beta.15.md).

## Beta.14 released

Signal Grove 1.1.0-beta.14, code 119, is available as an Android
preview. The exact source passed the Android 8 and 17 matrix and the
original-key emulator upgrade preserved a saved task.
[Download the APK](https://github.com/DionCroft/Signal_Grove/releases/download/v1.1.0-beta.14/hermes-agent-mobile-1.1.0-beta.14.apk) · [Release evidence](docs/releases/1.1.0-beta.14.md).

## Beta.13 released

Signal Grove 1.1.0-beta.13, code 118, is available as an Android
preview. Same-source Android 8 and 17 suites passed. The original-key
emulator upgrade and anonymous download were verified.
[Download the APK](https://github.com/DionCroft/Signal_Grove/releases/download/v1.1.0-beta.13/hermes-agent-mobile-1.1.0-beta.13.apk) · [Release evidence](docs/releases/1.1.0-beta.13.md).

## Beta.12 released

Signal Grove 1.1.0-beta.12, code 117, is available as a public prerelease.
The exact source on main passed 1,256 JVM tests, both lint variants,
1,263 Android 8 cases and 1,264 Android 17 cases. The original-signer
Xiaomi upgrade retained the Android installation, its saved Hermes
connection and a matching existing conversation. The public updater
feed offers the verified APK to beta.11 with previews enabled. All
five release assets were downloaded and checksum verified.
[Download the APK](https://github.com/DionCroft/Signal_Grove/releases/download/v1.1.0-beta.12/hermes-agent-mobile-1.1.0-beta.12.apk) · [Release evidence](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.12).

## Next live acceptance gate

The post-release read-only probe authenticated the existing gateway and
canonical home. Its registered status returned admission_unobserved.
The live project-control ledger and protected workspace-read policy were
absent. Complete the reviewed admission and activation prerequisites
before testing workspace attachment, exact attribution and approved
execution. Recover the scheduled occurrence's original receipt after
reconnect. [Validation](docs/VALIDATION.md#live-gateway-admission-diagnosis).

## Hermes Activity skill loads

Activity shows the resolved name of a full skill view reported by the
authenticated Hermes Runs endpoint. Errors, linked-file reads and
unchanged-skill stubs add no loaded-skill entry. The patched public
Hermes v0.23.1 suite passed 101 cases. The app passed 1,256 JVM tests,
both lint variants and seven focused compact Android 17 cases.
Live delivery on the maintained Pi and exact worker and workspace
attribution remain open.
[Validation](docs/VALIDATION.md#hermes-activity-skill-loads).

## Hosted Android 8 startup-switch failure

The original Android 8 shard on `6d63326` recorded 594 passing
methods out of 595. Its startup test could not find the setup toggle.
A fresh Android 8 emulator ran the same APKs with the verified stock
keyboard preparation. All five startup cases passed. The failing
method and its preceding 17 hosted methods then passed in their
original order. The full matrix remains the release gate.
[Validation](docs/VALIDATION.md#hosted-android-8-startup-switch-failure).

## Reviewed checkout held through queue reservation

The Windows broker holds the reviewed Git branch through the SQLite
reservation commit and rechecks the protected grant while the hold is active.
Checkout drift before reservation leaves no queue row. Claim and child creation
still perform their own checks. All 112 native Windows and 144 portable
workspace tests passed. The exact packaged fixture launched one inert child
and recovered the original receipt after reopening its owner broker.
The exact hosted workflow also passed 112 Windows and 47 Linux cases.
Live worker deployment and app-to-Pi-to-worker acceptance remain open.
[Validation](docs/VALIDATION.md#reviewed-checkout-held-through-queue-reservation).

## Hermes Activity MCP server attribution

Activity now shows the exact MCP server identity reported for a tool
dispatch. The tested public Hermes v0.23.1 overlay includes the MCP
and subagent observers and passed all 84 native cases. The app passed
1,254 JVM tests, both lint variants and seven compact Android 17
cases. The connected Xiaomi verified keyboard navigation over USB,
the published beta.11 digest and its existing Hermes connection.
Live delivery with the maintained Pi and exact worker, workspace and
skill attribution remain open.
[Validation](docs/VALIDATION.md#hermes-activity-mcp-server-attribution).

## Hermes live open-tool progress

Chat and Activity show the selected execution's currently reported
tool from the existing authenticated Runs status poll. The compact
Android 17 check verified the label before any tool stream event,
its removal when the tool finished, and the empty saved timeline.
All 1,252 JVM tests, both lint variants and seven focused device
cases passed. The native v0.23.1 progress case verified the same
ownership and lifecycle rules. Exact worker and integration
attribution remain open.
[Validation](docs/VALIDATION.md#hermes-live-open-tool-progress).

The earlier timestamp checkpoint `6d63326` passed hosted
verification. Its full standard Android device matrix is running.
[Hosted Android matrix](https://github.com/DionCroft/Signal_Grove/actions/runs/45499834488).

## Native Hermes v0.23.1 subagent delivery

The public v0.23.1 paired native suite ran the same 78 named tests.
The pristine copy failed only the missing-child-event regression.
The upstream patch passed all 78, including the child identity and
ordering checks and the loopback authority fixture. This validates
the public source in isolation. Live delivery on the maintained Pi
remains open.
[Validation](docs/VALIDATION.md#native-hermes-v0231-subagent-delivery).

## Hermes Activity server event time

Activity displays the event time reported by Hermes alongside the
phone's receipt time when both are available. Start and outcome times
stay attached to their own events across replay. The exact snapshot
passed 1,250 JVM tests, both lint variants and six compact Android 17
checks. Older history retains its receipt time. Live Hermes and
full-matrix verification remain open on this source.
[Validation](docs/VALIDATION.md#hermes-activity-server-event-time).

## Reported subagent outcomes and Hermes delivery

Activity now preserves reported child timeouts and interruptions and
shows an unknown outcome when Hermes omits or changes its status.
Older rows use their saved completion status. The corrected build
passed 1,248 JVM tests, both lint variants and six compact Android 17
checks. An isolated comparison reproduced missing canonical child
events on three audited Hermes revisions. The exact upstream proposal
restored both child events in order with no duplicate tool events.
Live delivery and worker attribution remain open.
[Validation](docs/VALIDATION.md#reported-subagent-outcomes-and-hermes-delivery).

The earlier live-step source `d834f37` passed the full hosted standard
matrix with 1,262 Android 8 cases and 1,263 Android 17 cases. The four
shard manifests, exact APKs and final coverage receipt were verified.
[Hosted validation](docs/VALIDATION.md#hosted-standard-android-matrix-for-the-live-step-source).

## Hermes Activity tool-call identities and receipt times

The Activity panel keeps calls separate by exact session, run,
generation, category and call ID. It shows when the phone received the
first start and the displayed outcome when both are stored. The exact
build passed all 1,246 JVM tests, both lint variants, and six focused
compact Android 17 cases. Full Android 8 coverage and live Hermes
attribution for the worker, workspace, skill and integration remain
open. [Validation](docs/VALIDATION.md#hermes-activity-tool-call-identities-and-receipt-times).

## Newer Hermes shutdown pins

The active shutdown workflow now covers Hermes v0.23.4 and the reviewed
main pin alongside the three previous revisions. The retained hosted run
passed all ten source cohorts and five comparisons. The active branch
reproduced all four paired reports for the two added pins byte for byte
and verified the eight delegation phase logs. Held SQLite writers left
the child and delegation outcome unknown without replay. Managed Pi
recovery and full compatibility remain open.
[Validation](docs/VALIDATION.md#newer-hermes-shutdown-pins).

## Scheduled worker receipt identity

The coordinator now matches the original worker receipt to the exact
signed approval as well as its existing execution and scope fields.
A different approval with the same execution ID leaves the outcome
unknown. The exact path also verifies the signed digest before queue
admission. All 110 native Windows cases and 981 portable cases passed.
Hosted verification passed all 47 Linux coordinator and 110 native
Windows cases. Live Pi-to-worker acceptance remains open.
[Validation](docs/VALIDATION.md#scheduled-worker-receipt-identity).

## Current step in Chat

Chat now shows a tool or decision wait from the selected running Hermes
conversation. The tested change uses identified calls and matching control
records. All 1,244 JVM tests passed, along with debug and release lint and
both debug APK builds. Six focused cases passed on a fresh Android 17
emulator. The local Android 8 fixture stopped before instrumentation.
The clean hosted matrix remains open. On the physical Xiaomi,
scrcpy hardware-keyboard navigation opened and closed On-device AI in
signed beta.11.
[Validation](docs/VALIDATION.md#current-step-in-chat).

## Live gateway admission prerequisites

An authenticated live status frame failed at the admission observation.
The reviewed home had no ledger and no explicit project-control section.
Four maintained-SDK cases established the initialization prerequisites.
A second isolated test ran one enrolled project turn alongside ordinary
chat. The reviewed observer reported both active turns, while the staged
workspace runtime's observer reported only the project turn. The
staged source also lacks the peer-request module used by scoped turns.
A verified two-file overlay and new source inventory are ready on the
Windows host. The next step is to stage a fresh isolated Pi generation,
repeat native verification there, and review the live scope separately.
The Xiaomi is connected with signed beta.11, code 116.
[Validation](docs/VALIDATION.md#live-gateway-admission-diagnosis).

## Authenticated scheduler checks on the current Hermes pins

Stable and main each passed the same 112 scheduler and history tests.
The real dashboard accepted a paused timetable edit only with
authentication, preserved the paused state and zero-tool allowlist,
and wrote only the owning profile's fixture job file. The native history
route still follows the actual job owner when the supplied profile is
a hint. Live Pi-to-worker execution and reconnect acceptance remain open.
[Validation](docs/VALIDATION.md#authenticated-scheduling-on-the-current-hermes-pins).

## Upstream Hermes layout inspection

The combined native workflow passed all 1,252 Linux policy methods.
Its stable source report reached the semantic review gate. Main moved
during the audit and initially stopped at the source layout gate.
All 245 watched paths at exact main `16fd059` were verified against
its Git tree, with ten relocations and no missing sources after
resolution. The exact replay published both reports and reproduced the
expected semantic review requirement on each pin. Its native fixture
passed again. Authenticated runtime qualification remains open.
[Validation](docs/VALIDATION.md#exact-stable-and-main-source-inspection).

## Authenticated gateway and Serve readiness

The coordinator's isolated cutover and recovery now use authenticated
readiness observations for both exact systemd service incarnations.
Eight readiness results match their journal and process snapshots.
The wrong home and invalid token were refused. All 1,252 hosted native
policy methods passed. The distinct upstream source inspection remains
open, along with the production Pi rollout and live workspace acceptance.
[Validation](docs/VALIDATION.md#authenticated-gateway-and-serve-readiness).

## Pinned Hermes environment removal

Exact stable and main removal helpers were checked against disposable
Windows files. Both removed a simple assignment; stable left a parse
error after reporting removal of a multiline entry. Main removed the
complete entry and preserved neighboring assignments. Authenticated
routes, native Linux locking and live acceptance remain open.
[Validation](docs/VALIDATION.md#pinned-hermes-environment-removal).

## Coordinator-driven service sequence

The disposable integration fixture runs the existing coordinator with
real systemd stop, reload and start actions for two owned services.
It injects a backup-stage fault after reviewing replacement startup
and submits a separate recovery approval against the original journal.
The hosted Linux fixture verified all eight service jobs and both
reloads. The original operation held at the injected failure and
separate recovery restored both original commands. All 1,252 native
policy methods passed. Live work drain, backup, provisioning and
authenticated readiness remain open.
[Validation](docs/VALIDATION.md#coordinator-driven-service-sequence).

## Journal-bound workspace service reload

The native service adapter can now execute the existing forward or
recovery reload stage on the pinned user manager. It checks both inactive
units and the approved journal prefix, consumes the reload intent once,
and verifies both loaded configurations before reporting success.
Windows policy tests passed 979 methods with 273 platform skips. The
hosted Linux suite passed all 1,252 methods. A disposable user manager
loaded both replacement commands, then restored both originals under
a separately approved fixture recovery. Production cutover remains open.
[Validation](docs/VALIDATION.md#journal-bound-workspace-service-reload).

## Reviewed service startup after reload

The native workspace cutover adapter now reads systemd's reload-required
state as part of the observed unit identity. Forward and recovery starts
require a freshly loaded configuration. A reviewed stop can still occur
before the reload, and a stale configuration after a start holds the
outcome for inspection using its original receipt.

Windows policy validation passed 974 cases with 273 platform skips.
The hosted Linux suite passed all 1,247 methods and verified the isolated
test unit stopped. The coordinated reload has an isolated fixture above. Live Pi activation
remains open.
[Validation](docs/VALIDATION.md#reviewed-service-startup-after-reload).

## Beta.11 published

The public version code 116 APK is built from `d6834a202bf5d4178fc1c27855312a6806da0986`.
The physical Xiaomi upgrade verified the retained server connection,
earlier messages, project details, local model, and earlier phone turns.
Anonymous HTTPS downloads verified all five assets. The beta.10 updater
completed the approval-based public upgrade and retained its preview
preference in a clean emulator. Live Pi-to-worker
workspace and automation acceptance remains part of the active audit.
[Release](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.11) ·
[Validation](docs/VALIDATION.md#beta11-public-release).

## Live workspace preflight after beta.11

Both Pi units still match the reviewed processes and loaded configuration.
The staged source, isolated PM facts and launcher match the reviewed
packet. The canonical candidate PM facts and activation policy are absent.
The live Unix socket authenticated the gateway's identity and home.
A separate bounded work-status request remained unproven. Workspace
activation remains held until scope and current work are verified.
[Validation](docs/VALIDATION.md#live-workspace-preflight-after-beta11).

## Native build paths in beta.11 APKs

The signed 73f68da candidate contained workstation paths in 16 native
libraries. The native build now uses relative paths for source and build
diagnostics. Both rebuilt APKs passed the path scan across all 32 native
libraries, and the release APK retained their dynamic library and
symbol contracts. All 1,243 JVM tests and both lint variants passed.
The rebuilt source needs its exact hosted matrix, signed production
build, and physical Xiaomi upgrade.
[Validation](docs/VALIDATION.md#native-build-paths-in-beta11-apks).

## Compact phone composer and standard device reconciliation

Phone Chat reserves space for the draft and its options before measuring
the transcript. The helper, Temporary chat and Personal profiles remain
available from the composer menu. Eight focused cases and the compact
keyboard/draft check passed on each of Android 8 and 17. The compact test
checks that the revised draft reached the editor before opening the helper
and remains after closing it.

The previous a6e3704 candidate now has a verified standard-device union on
both Android versions. The revised composer still needs the full matrix
on the final release source, original-key Xiaomi upgrade and updater
acceptance. [Validation](docs/VALIDATION.md#compact-phone-composer-and-standard-device-reconciliation).

## Hermes Activity approval and answer waits

Hermes Activity shows when a confirmed approval or answer request was received
and whether a response is sending or its delivery is uncertain. It limits
the summary to the selected conversation and run, checks a reported generation,
and omits prompts and choices. The original review handles the decision.
The same debug APK pair passed ten focused checks and the compact approval
check on Android 8 and 17. This change and the compact composer now share a
beta.11 source candidate that requires its own full matrix. [Validation](docs/VALIDATION.md#hermes-activity-approval-and-answer-waits).

## Demo denial and private Activity data

The beta.11 Android 8 shard ran 594 methods and passed 593. The
Demo approval fixture confirmed denial and a stored failed event,
then looked for raw result text that Activity deliberately omits.
The updated test checks the exact failed tool, the denial in Chat,
the public Activity status and absence of the raw result text.
Final-source device results remain open.
[Validation](docs/VALIDATION.md#demo-denial-activity-on-android-8).

## Hermes Activity payload privacy

The beta.11 Activity follow-up keeps tool identities, outcomes, timing
and subagent metadata in the timeline. Incoming tool previews and
subagent payloads can carry arguments, results or file lists. The
Runs Activity decoder retains the reviewed metadata, and the viewer
also applies that boundary to legacy saved rows. The original
conversation transcript remains available.

All 1,242 JVM cases passed and both lint variants reported zero
errors. Seven focused methods passed on each of Android 8 and 17,
including the live and legacy payload regression and read-only history.
The Activity change requires a newly signed beta.11 candidate and its
own full hosted matrix before the physical upgrade and publication.
[Validation](docs/VALIDATION.md#runs-activity-payload-privacy).

## Bilingual helper consent on compact Android

The earlier hosted API 26 consent shard passed 667/667. Its other shard
passed 593/594 and exposed a second bilingual helper tap failure.
The exact candidate companion now opens the helper from Chat options
and gives each consent and run control one native tap inside the visible
window. It keeps the original three exact payload assertions. All three
related checks passed on Android 8 and 17 at standard size, and the
bilingual test passed on Android 8 in the 1080 × 1120 viewport.
The next complete hosted run will verify this source at code 116.
[Evidence](docs/VALIDATION.md#bilingual-helper-consent-at-compact-size).

## Full-size updater and release preparation

The beta.11 branch defaults now match the intended code 116 release.
The README, feature matrix, changelog and draft notes separate candidate
features and figures from the published beta.10 download.
The hosted verify job for 6d85b6e supplied 118 JVM reports with 1,242
passing tests, both lint reports with zero errors, and a verified
1,245-case policy receipt. Full hosted device results remain a separate gate.

The previous owned-emulator installer driver exhausted its 192 MiB heap
while serving a full APK. The test companion now streams the signed
file from disk. Android 8 and Android 17 each passed the complete debug
116-to-117 updater and independently verified the retained private marker.
Both owned emulator baselines, updater preferences and source permissions
were restored. Continued release qualification requires the exact final
source matrix, a newly signed final-source APK, the Xiaomi upgrade and
the public download check.
[Validation](docs/VALIDATION.md#full-size-apk-updater-on-android-8-and-17).

## Private beta.11 inventory and current Pi workspace preflight

The signed code-116 build from commit 290f7fe matches the beta.10
certificate. Its validated 187-component SBOM includes all 32 native
members and a current review of 154 Maven coordinates, the pinned
llama.cpp revision and the packaged NDK C++ runtime. The full hosted
Android matrix for the compact-helper code has reached its four device
shards. The full release-source check and Xiaomi upgrade remain open.
[Validation](docs/VALIDATION.md#private-beta11-artifact-and-pi-preflight).

All 30 focused Activity, history, attachment, scheduler and recovery
methods passed on each Android API using the verified debug pair.
The exact signed private APK upgraded the owned Android 17 emulator
from public beta.10. Package Manager retained its app UID and original
installation time. The app retained Demo mode and the test task.
Physical Xiaomi data and connection acceptance remain open.

Read-only Pi observations match both live service incarnations, their
loaded configuration and the previously rehearsed staged candidate.
The private runtime has its own PM facts. The live runtime still needs
canonical PM preparation and the reviewed backup, drain, activation
and rollback checks. The production workspace policy remains absent.

## Compact phone helper and native keyboard testing

The beta.11 debug candidate reproduced an inherited Android 8 keyboard
above the helper. Its standard and 1080 by 1120 compact runs stopped
before the first consent tap. A separate, typed-draft handoff passed on
Android 8. That handoff exposed an unreachable launch control with the
Android 17 keyboard open.

The combined candidate's focused keyboard dismissal, scrollable body
and phone-chat menu passed the compact Android 8 consent case and all
12 Android 17 methods. The standard-height Android 8 cohort passed
10 of 12. Using the visible entry then proved the typed-draft handoff
on both APIs. Android 8 still brings up a keyboard after entering the
consent test, even starting with no visible parent keyboard. The consent
fixture centers the target in the intersection of the scroll body and
the native input frame. The final signed debug pair passed all 12 focused
methods on Android 8 and 17 and the Android 8 compact regression. The
typed draft, native search keyboard, and exact consent payload passed.
Full hosted coverage, the signed Xiaomi upgrade, and the release stay open.
[Validation](docs/VALIDATION.md#compact-phone-helper-and-native-keyboard-testing).

## Build-plugin advisory remediation

The Gradle buildscript now constrains the flagged library versions and
pins the reviewed Maven artifacts. OSV found zero indexed matches for
the resulting 118 exact plugin coordinates. All 1,242 JVM tests, both
lint variants and eleven device methods on each of Android 8 and 17
passed, including updater cancellation and integrity checks.
[Validation](docs/VALIDATION.md#build-plugin-advisory-remediation).

The resolved runtime inventory matches the signed rehearsal. The new
unsigned build has its own native hashes and provenance. Run the full
hosted checks on the updated source before the final signing, Xiaomi
upgrade and publication gates.

## Beta.11 rehearsal runtime and build-tool review

The exact signed rehearsal inventory returned zero indexed OSV matches
for 154 app runtime Maven versions and the pinned llama.cpp commit.
Each packaged C++ runtime matched its pinned NDK input after reproducing
the strip step. The separate build-plugin scope matched seven advisories
across six coordinates. None of the six coordinates is in the rehearsal
APK runtime graph. [Validation](docs/VALIDATION.md#beta11-rehearsal-runtime-and-build-tool-review).

The published AGP 9.5.2 dependency declarations retain several flagged
versions. Keep build-tool remediation under review and rebind the runtime
checks to the final APK. The full Android run passed its verify job and all
four device shards have started.
[Android CI](https://github.com/DionCroft/Signal_Grove/actions/runs/45306944580).

## Hermes client stamp retry compatibility

The exact main-pin SQLite component accepted a fresh retry that changed
only the owner-client stamp and returned the original receipt. A store
written before the stamp existed also reopened and recovered its original
queued and retired receipts. The specific intermediary commit that
wrote stamped digests retained its receipts but refused their retries
after upgrade. The checks created no duplicate admissions.
[Validation](docs/VALIDATION.md#hermes-client-stamp-retry-compatibility).

Keep this upgrade edge case open for managed-runtime acceptance. Preserve
the request identity and use authenticated receipt lookup to reconcile
a refusal or unknown result. Android run
[45306944580](https://github.com/DionCroft/Signal_Grove/actions/runs/45306944580)
covers the prior instrumentation commit.

## Focused Android keyboard and helper consent checks

The four Android 8 failures pass in the corrected nine-method cohort on
Android 8 and 17. The cohort also covers document review above the native
keyboard, exact reply copying, search controls and private draft retention.
All 1,242 JVM tests and debug lint passed. The changes affect instrumentation
only. [Validation](docs/VALIDATION.md#focused-android-keyboard-and-helper-consent-checks).

Run the full hosted Android matrix on the pushed correction. The signed
rehearsal remains private. The final-source matrix, Xiaomi upgrade,
updater and production publication gates remain open.

## Beta.11 rehearsal inventory and Android 8 failures

The signed rehearsal has a validated CycloneDX 1.6 inventory and matching
provenance. The files bind 154 Maven modules and 32 native APK members
to the original signed APK and source. Android 8 shard 0 passed. Shard 1
recorded four failures among 667 started methods. A focused local run on
the verified debug pair reproduced the helper test failure.
[Validation](docs/VALIDATION.md#beta11-inventory-and-android-8-shard-failures).

Correct and verify the four methods while retaining the exact hosted
artifact. Repeat the candidate matrix and rebind the release inventory to
the final signed source. The Xiaomi and release gates remain open.

## Original-signer beta.11 rehearsal and Android 8 retention

The code-116 beta.11 APK built from the clean feature-branch snapshot.
The verified signer matched public beta.10. An actual in-place upgrade
from the public APK on Android 8 kept the app UID, original install time,
Demo mode and the visible existing test task. Android 17 has the exact
beta.10 baseline for the later updater test.
[Validation](docs/VALIDATION.md#signed-beta11-rehearsal-and-android-8-retention).

Full Android device CI, final release source, Xiaomi upgrade,
reviewed release assets and GitHub publication remain open.

## Code-116 Android release rehearsal

A private unsigned build exercised version code 116 and the beta.11
version name. Manifest, 16 KB APK alignment and release lint passed.
The source guard held the output after a parallel Pi-source change.
[Validation](docs/VALIDATION.md#unsigned-beta11-release-rehearsal).
The final-source build, full device matrix, original-key Xiaomi upgrade
and signed GitHub release remain open.

## Workspace publication matches the protected operator path

The publication catalog now uses the policy path rendered for the
staged Hermes operator under protected account state. The exact Pi
bootstrap and review packet agree. All 113 native tests and the eight
owned file publication and rollback stages passed.
[Validation](docs/VALIDATION.md#protected-workspace-policy-publication-target).

Next prepare the full production transaction and fresh backup, drain
and health gates for separate approval. The staged policy remains absent
from the live Pi.

## Upstream main compatibility inspection restored

Two official main revisions had the same ten reviewed path relocations.
The strict checker now produces reports for both exact pins. The reports
still require review of 218 and 219 used-handler changes and 29 persistence
modules despite a complete source inventory.
[Validation](docs/VALIDATION.md#restored-upstream-main-source-inspection).

## Independent service-home dependencies verified

Supported Hermes PM prepared a new isolated service home using the pinned
candidate. The real published launcher selected that home's own facts and
site-packages. Native ContextGraph and PyYAML imports worked. The live
service home is still unprovisioned for the candidate.
[Validation](docs/VALIDATION.md#isolated-service-home-provisioning-and-launch).

Next compose the protected publication, verified backup, drain, health
and rollback steps for review. Keep live provisioning and deployment
behind separate approval. Android verify passed and four device shards
are running.

## Explicit-store deployment review artifacts

The review-only packet renders the exact two wrapper scripts and two
proposed systemd overrides for the new candidate. They passed syntax
checks in Pi fixtures. Production activation still needs canonical-home
PM provisioning, protected publication and fresh backup, drain, health
and recovery checks. [Validation](docs/VALIDATION.md#explicit-store-deployment-review-artifacts).

## Explicit-store Pi runtime and launcher verified

The fresh workspace-only runtime carried all 23,691 reviewed source and
vendor files. Its absolute tools path resolved from three directories
with no runtime override. Supported PM lock and sync passed, the SDK
launcher selected the exact prepared facts from all three directories
and refused an unprepared home. All 19 native workspace cases and exact
candidate rollback passed. The previous candidate, its failed launcher
probe and the two live units were preserved.
[Validation](docs/VALIDATION.md#explicit-store-pi-runtime-and-launcher).

Next render and verify the live review packet against this exact
candidate, including canonical-home PM, protected entrypoints, a fresh
encrypted backup, clean drain, authenticated health and recovery.
Seek separate approval before live activation. Run the physical upgrade
after the full exact-source Android matrix passes.

## Workspace activation inputs and launcher blocker verified

All nine files in the proposed read-only source change matched the staged
Pi candidate. The saved rollback control matched the live source. The
account-state policy path passed its protection check. The SDK launcher
selected the prepared isolated home's PM facts. The borrowed-home probe
exposed a working-directory-dependent tools path, and the canonical home
has no candidate PM facts. [Validation](docs/VALIDATION.md#workspace-activation-inputs-and-sdk-launcher).

Next prepare a fresh isolated variant with an explicit tools path and its
own PM facts. Verify launcher startup from multiple directories and the
unprepared-home refusal. Preserve the current generation and original
failure. The live packet still needs its protected entrypoints, backup,
health gates and separate activation approval. The exact-source Android
CI run has started.

## Isolated runtime selection and rollback verified

Three transient Pi services ran the exact original, workspace-only candidate,
and original runtime again. The confirmed workspace method count was 0, 4,
and 0. Each incarnation shut down on normal SIGTERM, the private selector
was restored byte-for-byte, and the fixture state and live service identities
were preserved. [Validation](docs/VALIDATION.md#isolated-pi-runtime-selection-and-rollback).

Next complete the private live activation packet for the single read-only
workspace binding, including the drain, fresh backup, health and recovery
checks. Obtain separate approval for activation and prove phone-to-worker
attachment after that review. The full Android CI run remains queued and
the Xiaomi is needed for the signed upgrade.

## Native successor built and focused Activity verified

The preserved first isolated PM attempt refused stale maintained
source before changes. The complete successor carries six changed
files and two additional modules that match the live inference and
Discord phone controls. All 23,691 members verify. Native PM passed
with only a vendor-path rebase and 19 native workspace authority cases
passed. The original runtime and its loaded config and PM facts remain
verified rollback inputs.
[Native validation](docs/VALIDATION.md#native-workspace-only-successor-and-rollback-inputs).

Five confirmed Activity and read-only history cases passed on both
Android APIs with the current candidate. The exact full matrix, signed
physical upgrade and release remain open.
[Android validation](docs/VALIDATION.md#focused-hermes-activity-on-the-android-candidate).

Next rehearse the bounded runtime rollback and prepare the exact
activation review. Await separate live approval before enabling the
one-project read policy or switching services. Verify app attachment,
live scheduling and the remaining audit in their own gates.

## Focused Android fixes and workspace-only source package

The active debug candidate passed all 1,242 JVM tests, both lint variants
with zero errors, and the two changed Android UI methods on each API 26
and API 37. The document test verified the exact citation and one native
review tap with the keyboard present. The model search kept the empty state
visible while typing and still required explicit model selection. Original
hosted failures and local timing warnings are preserved.
[Validation](docs/VALIDATION.md#compact-document-review-and-model-search).

The complete Pi workspace-only archive now has byte and mode verification
for all 23,689 source and vendor members. Native PM/runtime and rollback
checks are next before the separately approved live switch.
[Archive evidence](docs/VALIDATION.md#complete-workspace-only-pi-source-derivative).

Next run the full Android matrix for the updated source, then the signed
physical upgrade, main branch and release gates. Phone workspace attachment,
live scheduler acceptance and remaining audit items are still open.

## Real read enrollment and Android 8 release gate

The live Pi owner, profile and node match the existing protected Windows
read grant. Its one local project and eight terminal receipts verify. Three
closed sessions qualify by persisted metadata. The private source review
draft selects one and pins the exact read grant, operator transport, source
and original rollback bytes. All hosted workspace checks passed.
[Validation](docs/VALIDATION.md#real-owner-and-windows-enrollment-reconciliation).

Next complete managed generation and rollback verification before asking
for separate live activation approval. In parallel, reproduce the retained
API 26 document-review timeout on the exact build and verify its fix. The
phone-to-Pi attachment, scheduler rollout and signed APK remain open.
[Failed shard](docs/VALIDATION.md#android-8-document-review-failure-retained).

## Workspace-only installer and rollout preflight

The earlier controls-only overlay still registered the combined scheduling
methods. The explicit workspace-only mode now registers only four read
methods. The native v0.23.1 combined and workspace-only checks passed, and the
workspace-only candidate read through the real disposable Windows pipe.
The staged file hashes and stock scheduler inputs verify. Both live Pi services
are active on the original source. A read-only preflight confirmed a protected
account-state parent for the read grant.
[Validation](docs/VALIDATION.md#workspace-only-installer-and-native-authority-gate).

Next reconcile the actual owner, followed session and existing Windows grant,
then stage the matching protected binding and exact rollback for review.
Obtain separate approval before live activation or service changes. Phone
attachment, live scheduling and the next signed APK remain open.

## Native ARM64 scheduler and exact original receipt

The real Pi ran the approved isolated ARM64 environment with a fresh
owner-only scheduler profile. Native Hermes checked defer, denial, expiry
and worker-scope drift. One approved occurrence sent one signed admission
through the installed SSH route and launched one inert Windows child. The
reply was lost after admission. A new transport read the original completed
receipt, and repeating the approval was refused. An ordinary automatic
fixture schedule stayed due. The temporary Pi profile was removed.
[Validation](docs/VALIDATION.md#native-arm64-scheduler-and-exact-original-receipt).

A separate read-only workspace overlay matches the current v0.23.1 source
anchors and leaves all four scheduler modules byte-identical. The live
enabled job's exact spec passed the preservation check in memory on the Pi.
Its protected policy needs a separate rollout from the read-only workspace
capability because the live scheduler home crosses group-writable ancestors.

Next prepare the isolated read-only source and explicit owner grant for
review, then obtain separate activation approval before switching the live
service. Verify the app's actual read-only workspace attachment. Live
scheduler activation, the Xiaomi upgrade and the signed APK remain open.

## Real Pi-to-Windows reviewed SSH trial

The exact staged coordinator client ran in memory on the ARM64 Pi and used
the installed private SSH route to the disposable Windows broker. One
synthetic approval launched one inert child. The original completed receipt
survived both a lost submit reply and replacement of the disposable broker.
The live gateway and installed Windows source kept their observed identities.
[Validation](docs/VALIDATION.md#real-pi-to-windows-reviewed-ssh-trial).

Both live Pi services still use the verified upstream v0.23.1 inputs and
share a scheduler home whose path crosses two group-writable ancestors.
The protected scheduler reader refuses those paths. The live catalogue
contains one enabled and eleven disabled jobs. Windows reported eight
completed receipts and no active or uncertain queue entries at observation.

Next run the native ARM64 scheduler with protected isolated state and the
reviewed Windows route. The live rollout requires a reviewed resolution for
the ancestor permissions and preservation of the existing enabled job before
separate activation approval. The exact Android matrix is queued behind the
preceding device run. A physical Xiaomi was absent at this checkpoint.

## Reviewed Windows stdio and coordinator SSH candidate

The packaged Windows broker starts the reviewed pipe for already enrolled
scopes when its protected write configuration is present. The fixed stdio
helper forwards prepare, signed submit and original receipt observation.
The coordinator uses one submission and can poll the original receipt
through separate read-only requests. The scheduler refreshes the worker
checkout before taking its native claim locks and rechecks local policy
during the claim.

The exact Windows bundle accepted a signed request through a separate stdio
process, launched one inert child after the submit reply was lost, and
returned the original completed receipt. 109 native Windows, 142 workspace
and 34 scheduler checks passed, plus 15 staged bundle checks. Both staged
Hermes versions passed their eight native decision cases.
[Validation](docs/VALIDATION.md#reviewed-windows-stdio-and-coordinator-ssh-candidate).

Next verify the SSH exchange between isolated Pi and Windows instances
using the reviewed operator enrollment. The live scheduled run, original
receipt recovery and signed Android release still need their own evidence.

## Private Windows worker approval and receipt pipe

The isolated Windows broker now accepts three fixed reviewed operations over
an owner-verified named pipe. It can prepare, submit and observe. Signed requests can
carry the full supported task size. Original receipt scope is recorded with
the queue admission. The observer uses a read-only connection and can recover
that scope when a new broker instance replaces the original. Legacy result,
cancel and observe calls refuse reviewed IDs.

109 Windows native, 137 portable and 13 exact staged broker cases passed.
The staged broker received the signed approval over the real pipe, launched
one inert child and returned its original terminal state through the scoped
observer. [Validation](docs/VALIDATION.md#private-windows-worker-approval-and-receipt-pipe).

Next package and connect the pipe to the authenticated SSH helper and the
Pi scheduler, then verify the combined path in isolation before any live
activation.

## Consumed scheduler approvals and original worker receipts

The staged coordinator signs the exact worker scope only after checking the
original approved execution in the native scheduler ledger. The approval
keeps the original review digest and expiry. The dispatch sends through the
configured worker route once, then checks the original receipt through the
separate observation callback. Foreign and unsettled receipts remain unknown.

Git-object-verified Hermes v0.23.1 and main `242315c` each passed eight
native scheduling cases and a fresh-process check with the fixture route.
The 32 portable scheduling cases and 137 workspace cases passed.
[Validation](docs/VALIDATION.md#consumed-scheduler-approvals-and-original-worker-receipts).

Next implement the operator-enrolled private worker transport for preparation,
submission and scoped receipt observation, then connect it to the staged
Windows queue and test the full path before live activation.

## Protected worker approval intake

The Windows owner can reserve a reviewed request in the existing SQLite queue
using a short-lived authenticated approval and a separate protected write
grant. The review binds the authority, profile, project, exact checkout,
worker instance, task and original execution ID. The queue commits the original
ID and proof digest together. The broker checks the grant and native checkout
again at claim and child creation. Retransmission observes the existing row.
An uncertain claim remains held after broker recovery.

105 native Windows cases, 137 portable workspace cases and 12 exact staged
broker cases passed. [Validation](docs/VALIDATION.md#protected-worker-approval-intake).

Next bind the Pi's consumed scheduler decision to the prepared worker review,
then carry it over the authenticated owner route and verify original-receipt
recovery through that route. The installed broker and live Pi still require
a separately approved activation after isolated acceptance.

## Reviewed worker queue and launch candidate

The exact installed Windows source now has a staged candidate that
puts the checkout and independent write-grant checks around the native
SQLite claim and actual child creation. The reviewed path stays in the
interactive owning broker. It hands the already-verified task to the
child during process creation and retains uncertain launches under the
original receipt.

94 Windows provider tests and nine exact-source fixtures passed.
The fixtures used a disposable checkout and inert process. The installed
broker still matched the reviewed input hashes after validation.
[Validation](docs/VALIDATION.md#reviewed-worker-queue-and-launch-candidate).

Next provide the broker's reviewed lookup from protected owner policy
and the coordinator's original scoped approval. Rebuild the isolated
candidate with that authorization path and verify it before requesting
live activation.

## Exact commit in project approvals

The native scheduler now requires a full Git commit and branch when a
review grants writes to a registered project. The digest includes the
commit, and the scope is rechecked before native reservation. Signal Grove
shows the full commit beside the branch in both project approval dialogs.

A changed commit was refused before the fixture reserved anything. A
fresh review carried the new commit into one dispatch of the original
occurrence. The existing reconnect tests still recover the same receipt.
[Validation](docs/VALIDATION.md#exact-commit-in-project-approvals).

Next prove the real owner resolver issues this scope from the enrolled
checkout, then apply the ref hold at queue admission and final launch.
The live authorization and signed phone release gates remain open.

## Windows branch reference hold

The checkout guard now holds the approved branch at its reviewed commit
while the broker uses the admission context. A real verify-only Git
transaction locks both loose and packed branches, then aborts when the
guard exits. Windows passed 86 native provider checks, including 14
checkout cases at this checkpoint.
[Validation](docs/VALIDATION.md#windows-branch-reference-hold).

## Hermes Activity and compact Chat

Chat opens a timeline for the selected profile and conversation, including
read-only history. It shows retained tool and subagent activity, receipt time,
reported duration and the exact run's served and requested model information.
An empty saved reply now has an explanatory caption. Compact Chat reserves
space for history and keeps the connection state available through its icon.

The frozen Android build passed 1,241 JVM tests and both lint variants.
Android 8 and 17 each passed eight standard and three compact activity and
history cases. The exact Activity source also passed the full hosted
standard device gate on Android 8 and 17, succeeding after the earlier
Android 17 model-library failure. [Hosted results](https://github.com/DionCroft/Signal_Grove/actions/runs/45255879359). [Validation](docs/VALIDATION.md#hermes-activity-and-compact-chat).

The exact Windows checkout suite still passes. A disposable Git fixture
showed that the current admission guard can hold HEAD open while Git changes
the branch's commit. The next worker change must hold the verified branch
reference through queue admission and final launch. The scheduler's reviewed
scope also needs to carry and check the exact commit. The Pi workspace adapter
and protected policy still need isolated-to-live acceptance.

## Phone history inline model choices

The model filter now expands within the history search form, keeps the
keyboard visible and shares the date rows' full-width single-choice control.
All four history search cases passed in standard and compact runs on
Android 8 and 17. Android 17's separate history-view choice exceeded its
input-readiness deadline in the broader cohort and passed in isolation.
The full exact-source hosted Android and signed release gates remain open.
[Validation](docs/VALIDATION.md#phone-history-inline-model-choices).

## Exact reviewed Windows checkout

The worker can now capture and recheck its enrolled instance, root and Git
metadata identities, current branch and commit. It holds the checked root
and HEAD through the local admission check. Windows passed eight checkout
cases and all 80 worker cases. The reviewed execution queue and the final
native admission still need to use the check under a separate write grant.
[Native validation](docs/VALIDATION.md#exact-reviewed-windows-checkout).

## Managed Pi workspace candidate and worker preflight

The active Pi serve and gateway both match all five inspected upstream
v0.23.1 source inputs and have no workspace adapter. Separate source packets
for attached reads and the optional schedule holds match these inputs.
The exact workspace packet passed 22 native checks through a disposable
Windows pipe. The scheduler packet passed eight decision checks and one
fresh-process recovery. The installed worker observer can verify the
original terminal receipt without renewing the execution lease. The stock
worker start contract still needs exact branch and worker-instance admission.
The live protected policy prerequisite remains open.
[Pi and worker evidence](docs/VALIDATION.md#managed-pi-workspace-candidate-and-worker-preflight).

## Android scheduled project approval inbox

Hermes management opens a dedicated Project approvals inbox under Scheduled
jobs. The app reviews one held occurrence and its verified project, workspace,
branch, worker, task and access. Approve or Deny saves the original occurrence
before sending the decision. Defer leaves it held. Reconnecting reads the same
receipt, and an unknown result stays pending. The frozen candidate passed 1,241
JVM tests, both lint variants and 20 focused tests each on Android 8 and 17.
Both new approval cases also passed on a compact Android 17 layout with larger
text. Live Pi and registered worker acceptance remain open.
[Android validation](docs/VALIDATION.md#android-scheduled-project-approval-inbox).

## Decisions for exact scheduled project occurrences

The native owner methods now prepare, approve, defer, deny and observe
one held occurrence. A fresh worker-scope check and the native job locks
bind approval to the reviewed definition. Each occurrence has one
durable decision and native execution ID. A competing review and a lost
reply converged on one fixture dispatch. Fresh-process recovery recorded
an interrupted reservation as unknown and preserved it through ledger
pruning. Both source pins passed nine isolated checks. The Android
checkpoint above covers the inbox against a fixture. Actual worker dispatch
and managed Pi acceptance remain open.
[Validation](docs/VALIDATION.md#decisions-for-exact-scheduled-project-occurrences).

## Held scheduled project writes

The optional source gate preserves a registered project-write occurrence
before the stock ticker, provider, manual claim and execution paths.
An authenticated owner read returns the same due occurrence after reconnect
and process restart. A permitted ordinary job still uses the native
scheduler with its approved definition. Both pins passed seven isolated
checks. The later checkpoint adds native occurrence decisions. The optional
policy and decision methods are staged for testing.
[Validation](docs/VALIDATION.md#held-scheduled-project-writes-and-owner-inbox).

## Android reviewed Run once and original receipt

Scheduled job details now offer a one-run review with the exact task,
access, verified project and workspace, branch and worker. Approve, defer
and deny use the same authenticated review connection. Approval stores its
original request ID before sending, and a lost reply can be resolved only
by reading the matching native execution. The fixture passed 14 scheduler
and gateway cases on each of Android 8 and 17, plus both new compact
large-text cases. Managed Pi operator configuration, live worker
dispatch and the broader project approval inbox remain open.
[Android validation](docs/VALIDATION.md#android-reviewed-run-once-and-original-receipt).

## Reviewed Run once on the authenticated gateway

The optional owner methods prepare, confirm, defer and deny one exact
Run once review. Five real native dispatcher cases passed on each of
v0.23.1 and the reviewed main pin. A disconnect after saving the claim
left one original attempt that a new authenticated connection observed
to completion. The fixture used an explicit dispatcher and a deterministic
task. Managed Pi wiring, phone approval controls and deployment
acceptance remain open.
[Gateway and recovery evidence](docs/VALIDATION.md#reviewed-run-once-on-the-authenticated-gateway).

## Exact one-use Run once approval

The native approval binds the existing job to the verified profile and
connection, project/workspace, branch, worker instance, task, access and
policy revision. Confirming consumes that review once under the native
claim fences. A different connection or changed scope refuses confirmation.
Denial, deferral and expiry end the review. On both pins a fixture exercised approval
through the native executor and completed one original receipt. A separate
native worker process also adopted it, and fresh-process recovery kept an
interrupted fixture unknown without a second attempt. Hosted tests passed
33 Linux and 72 Windows cases. The gateway checkpoint above adds native
route coverage. Managed Pi launcher and phone checks remain open.
[Validation](docs/VALIDATION.md#exact-one-use-run-once-approval).

## Native pause-preserving Run once

The guarded native claim checks approval under both scheduler locks and
reserves the original execution in the same profile before saving the fire
claim. A live manual claim holds a due scheduled slot until settlement.
The pinned v0.23.1 and main runtimes each passed six isolated cases,
including recorded output and retention of a paused one-shot. The full
pinned scheduling regressions passed 126 and 147 cases. Native review
consumption is available internally. Authenticated dispatch and
detached-worker recovery remain open.
[Validation](docs/VALIDATION.md#native-pause-preserving-run-once).

## Native Run once receipt checkpoint

An optional native execution ID binds a reviewed manual request to one
retained Hermes ledger row. The authenticated observation route reads that
original row by request, canonical job and profile. Both pinned native
execution stores passed the competing insert and fresh-process proof, plus
24 upstream ledger tests each. Both pins also saved exactly one row in
a two-process race. The stable dispatcher passed 26 cases, and the hosted
checks passed 32 Linux and 72 Windows cases. Exact one-run approval and
live dispatch acceptance remain open.
[Validation](docs/VALIDATION.md#native-original-run-once-receipts).

## Android review and recovery for paused schedules

Schedule details now open a paused-job timing editor with native before-and-after
review, a separate save and original-receipt recovery for lost replies.
The corrected source passed 1,233 JVM tests, both lint variants, 12 focused
Android tests on each API, and both compact editor checks. The next scheduler
stage is to verify a native Run once admission and bind it to a durable
execution receipt. Live Pi and physical acceptance remain open.
[Validation](docs/VALIDATION.md#android-review-and-recovery-for-paused-schedules).

## Native timing review projection

Hermes now reports the before-and-after schedule, time zone and
repeat effect from the same projection used by its writer. Apply
requires that review digest under the native lock. Both native
cohorts, the prior scheduler regression suites and all 31 hosted
Linux cases passed. Live Pi validation and the wider release gates remain open.
[Validation](docs/VALIDATION.md#native-timing-review-projection).

## Strict paused scheduler edit source

The optional native timing editor now checks the exact reviewed job
under the cross-process lock and saves a recovery receipt with the
edit. Both pinned native scheduler cohorts and the authenticated
dispatcher passed isolated tests. The exact source passed all 28
hosted Linux cases, including five protected timing-edit cases.
Authenticated live Pi testing, run-once and scheduled project approvals
remain open.
[Validation](docs/VALIDATION.md#strict-paused-schedule-editing-source).

## Exact saved output on Android

A selected scheduler attempt offers a bounded, profile- and
execution-verified archive preview. Both debug builds, 1,232
JVM cases, ten focused cases on Android 8 and 17 and two compact
Android 17 cases passed. The earlier schedule-preview matrix
completed. The scheduler-history matrix stopped on a policy test
skip, now replaced with explicit portable boundary coverage.
Live Linux writer-to-app, strict schedule editing, run-once
and service logs remain open.
[Validation](docs/VALIDATION.md#exact-saved-output-on-android).

## Exact scheduler archive association checkpoint

The optional native hook binds a retained stock archive to the
execution that wrote it. An explicit owner read checks the ledger,
identity and digest before returning a bounded preview. Pinned
v0.23.1 staged preview/apply, 16 native dispatcher cases and all 23
hosted Linux cases passed. The traced native writer ran under its
output fence. The Linux writer-to-socket round trip, Android archive
viewer and service logs remain open.
[Validation](docs/VALIDATION.md#exact-scheduler-archive-association).

## Scheduler execution history on Android

Advanced schedule details can read the optional owner-bound Linux
ledger. Canonical profile verification, unknown outcomes, ordinary
dashboard history and cancellation are covered. The candidate passed
1,229 JVM cases and five focused device cases each on Android 8 and 17.
The two failures from the earlier Android 17 full shard passed together
in an isolated repeat. The fresh full matrix, run-to-output linkage,
strict edit, run-once and live Pi trial remain open.
[Validation](docs/VALIDATION.md#scheduler-execution-history-on-android).

## Scheduled execution ledger checkpoint

The optional Hermes source now has an authenticated owner/profile read for
Linux scheduler attempts and an exact durable receipt that can outlive its
one-shot job. Windows passed 928 portable tests and twelve native authority
cases. The exact source passed 17 hosted Linux tests, including eight
protected ledger/WAL cases. Exact output/log association, Android
integration, strict mutations and live Pi acceptance remain open.
[Validation](docs/VALIDATION.md#authenticated-scheduled-execution-ledger-source).

## Exact stored schedule checkpoint

Paused creation now verifies the exact reviewed schedule on both the
acknowledgment and readback. A substituted schedule keeps the original
acknowledgment for read-only observation. All 20 native schedule records
matched, and all 20 changed copies were refused. Five Android cases
each passed on Android 8 and 17. The two official trigger cohorts also
passed and confirmed stock pause/resume and job snapshot behavior.
Strict edit, run-once and authenticated output remain open.
[Validation](docs/VALIDATION.md#exact-schedule-readback-after-paused-creation).

## Android schedule preview checkpoint

The paused create review shows up to three local estimates, timezone
and UTC offset, and the missed-run policy. The review still requires an
explicit confirmation for one scoped paused create. The test passed at
both viewports on Android 8 and 17, together with 1,222 JVM cases and
zero-error lint. The preview requires a known zone for calendar
estimates. Existing schedule edit, run-once, authenticated output and
live acceptance remain open.
[Validation](docs/VALIDATION.md#calendar-and-missed-run-preview-in-the-schedule-review).

## Hermes scheduler profile and timezone checkpoint

Both exact upstream pins passed 112 scheduler cases with explicit UTC
for two Windows fixtures that assume UTC. Before that control each pin
had 110 passes and two filename reconciliation failures. The native
dashboard confirmed exact agent-job history and reproduced profile-hint
fallback and ownerless output rows. Two disposable profile stores per
pin passed integrity checks. Continue the local schedule forecast with
the verified timezone and missed-run policy, and require authenticated
server owner proof before displaying mixed output or enabling mutations.
[Validation](docs/VALIDATION.md#hermes-scheduler-and-scoped-run-history-on-stable-and-main).

## Hermes dashboard listener authentication checkpoint

The exact stable and main pins passed 129 common HTTP and WebSocket
authentication checks each on Windows. Main passed ten additional
companion-listener cases through the real in-process dashboard mount.
The hosted source scan published both reports, with review still
required. Live Pi TLS, browser/profile acceptance and unreviewed source
drift remain open.
[Validation](docs/VALIDATION.md#hermes-dashboard-listener-authentication-on-stable-and-main).

## Hermes main source inspection checkpoint

The exact main canary `242315c` can now read all ten relocated sources.
Their Git blobs match the earlier reviewed layout. The checker completes
with 212 changed used handler fingerprints and 29 changed persistence
sources requiring semantic review. It reports zero removals of used routes
or request field changes. The existing stable report and the prior
incomplete main run remain in the validation record.
[Validation](docs/VALIDATION.md#hermes-main-source-relocation-at-242315c).

## Integrated release candidate checkpoint

Source `6da9cd1` combines the phone context and history, scoped workspace
access, shutdown and unbound drain changes. The combined APKs passed 1,213
JVM checks and zero-error lint. Android 8 and 17 both read a registered
fixture through native Hermes authentication and a real Windows pipe;
the known heartbeat cleanup warning remains. The worker CI matrix passed.
The candidate Android matrix, upstream contract review, live Pi verification
and signed release gates remain open.
[Validation](docs/VALIDATION.md#integrated-audit-release-candidate).

## Phone history date picker checkpoint

The hosted Android 17 history test reported an undisplayed date option.
The four date presets now stay in the scrollable form with full-width
single-choice controls. Four history UI cases passed at standard and
compact sizes on Android 8 and 17, including the visible keyboard with
bilingual large text. The complete hosted matrix remains open.
[Validation](docs/VALIDATION.md#phone-history-inline-date-choices).

## Unbound gateway drain observation checkpoint

The drain actor checks the exact unbound count in a separate read-only
snapshot before accepting idle. Two isolated Windows trials with real
maintained SDK receipts reproduced the old status gap and proved both
settlement and expiry while the original fence stayed frozen. The full
portable policy suite completed with 923 passes and 273 platform skips.
The original-journal native socket scenario and all-profile drain remain
open. [Validation](docs/VALIDATION.md#unbound-work-during-the-original-gateway-drain-window).

## Native subagent recovery checkpoint

On Windows, both current Hermes candidate pins passed the original-owner
exit and next-process recovery, with their SQLite writer released during
stop or held until it returned. Hermes retained the original child
checkpoint, classified the held case as unknown and made no new model
calls. The previous mark was durable only in the released case. Both
Linux pins reproduced all four outcomes after the fixture preserved the
reaped owner's missing-file result. The all-profile drain and live Pi
gates remain open.
[Validation](docs/VALIDATION.md#native-recovery-of-an-in-flight-hermes-delegation).

## Phone helper consent checkpoint

The Android 8 helper case retained a hosted opt-in failure that did not
reproduce in the focused run. The repaired consent row accepts taps on its
label and sends only the previewed observation snapshot. Both focused cases
passed on Android 8 and 17, with zero build or lint errors. The complete
device matrix remains open.
[Validation](docs/VALIDATION.md#phone-helper-observation-consent).

## Authenticated Android owner and Windows worker checkpoint

Android 8 and 17 passed through the actual Hermes v0.23.1 owner dashboard,
prefixed native PKCE, refresh and a single followed socket. The same socket
read and previewed the disposable project through the real Windows pipe and
answered a subsequent heartbeat. The confirmed Room receipt and saved
credentials matched their pre-read values. Both Hermes stores passed SQLite
quick checks with zero admissions. The known heartbeat cleanup warning
remains open.

A read-only Pi check verified the active original maintained runtime and
the earlier v1 staging generation. A four-module source delta is verified
against that stage. Next, obtain scoped approval for a fresh Pi stage and
verify its ARM64 package and owner route before considering live activation.
[Evidence](docs/VALIDATION.md#authenticated-android-owner-and-native-windows-workspace).

## Android attached project browser checkpoint

The followed viewer now reviews the worker and registered root before listing
files. The client sends only the current session revision and generation and
the selected entry token. The frozen Android build passed 1,213 JVM tests,
both lint variants and both APK builds. The new reviewed-preview Compose
check passed on Android 8 and 17. The wire fixture verified that an older
gateway or a mismatched worker response leaves the original chat subscribed.
The Android owner-login interoperability and the live Pi-to-worker read are
still required before project-run approval and scheduling acceptance.
[Validation](docs/VALIDATION.md#android-attached-project-browser).

## Session-bound worker read checkpoint

The coordinator can derive one registered Windows workspace from a protected
association with a currently followed Hermes chat. The isolated v0.23.1
authority and native Windows pipe passed 10 new attachment checks in addition
to the prior 13. The two fixture paths were distinct. A concurrent workspace
move discarded the preview, and the obsolete lease was refused before the
next worker call. The source preserves the v1 worker read API.
Next, wire the followed Android viewer to the two attachment methods and
verify reviewed read-only browsing through the authenticated owner path.
[Validation](docs/VALIDATION.md#session-bound-worker-file-reads).

## Attached workspace and Windows read checkpoint

A disposable Hermes v0.23.1 session applied and read back a workspace move.
The native Windows broker prepared the same root, listed it and returned a
verified 26-byte preview. All 13 combined checks passed, along with the 18
original coordinator cases, 119 portable tests and 72 Windows-native tests.
The test preserved the chat row, subscription and project bytes and created
no turn or worker execution.

Hermes also refused a path missing on its host before any worker call.
Next, bind reads server-side to the verified session's current attachment and
an explicit worker registration. The worker's root can live on another host. Then verify the authenticated Android-to-Pi-to-worker
path before adding project approvals. Production enrollment and Pi activation
still require their own acceptance.
[Isolated evidence](docs/VALIDATION.md#attached-workspace-and-windows-worker-read-checkpoint).

## Hermes context read checkpoint

The followed viewer can read and refresh Hermes-reported context and
compression over a separate authenticated socket. Both new native Android
cases passed on API 26 and 37. Four broader workspace tap failures remain
retained. The same cases passed after isolation. Eight upstream authority
checks passed across v0.23.1, v0.23.2, v0.23.3 and the reviewed main pin.
Continue with verified read-only workspace attachment and bound worker
files before the project approval inbox.
[Context read and workspace cohort evidence](docs/VALIDATION.md#hermes-reported-context-and-compression).

## Phone context review

The request review reports exact formatted tokens against the active input
budget and gives the pre-format byte sizes for each included part. Earlier turns
opens the existing continuation review with the unsent draft. Larger windows
remain a separate experiment with pinned LFM3 on the Xiaomi.
[Context guide](docs/ON_DEVICE_AI.md#check-how-much-context-your-phone-model-has).

## Physical LFM3 context capacity checkpoint

The debug-only 4K, 8K and 16K trials captured short reply latency,
memory, overflow refusal and Stop on the Xiaomi. The near-window 4K
reply completed. The near-window 8K and 16K runs hit the 180-second
deadline. The phone passed the exact 512-token boundary that timed out at
generation on the x86_64 emulator. The production 4K cap remains the
qualified limit. Continue the separate Hermes-reported context read using
the existing authenticated session scope and the pinned session.stats
contract; workspace approval and scheduling acceptance remain open.
[Physical and emulator results](docs/VALIDATION.md#lfm3-context-capacity-probe-on-the-xiaomi).

## Beta.10 release checkpoint

Exact candidate d651a33 passed 1,203 JVM tests, both lint variants and
13 standard and seven compact checks on each of Android 8 and 17.
The original-key APK upgraded in place on the owner's phone. Its existing
Hermes conversations and project metadata loaded, and a real offline
LFM3 reply persisted. An Android 8 upgrade path from the published
beta.9 APK retained a local task. The hosted suite passed 1,241 Android 8
and 1,242 Android 17 tests. Beta.10 is published and the Hermes audit
stays active. Quick Capture is queued separately.

[Signed upgrade and test evidence](docs/VALIDATION.md#beta10-offline-routing-and-signed-upgrade-checkpoint) ·
[Hosted Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/44836131279).

## Compact phone chat menu checkpoint

The previous build reproduced the Android 8 Temporary chat visibility
failure on a compact viewport after drafting. Chat options puts both
Temporary chat and Personal profiles in the existing toolbar. The
same viewport passed with the candidate, along with its focused
Android 17 retest. The initial Android 17 save and Voice transcript
failures remain in the evidence. The full hosted matrix and physical
production gates are still open.
[Menu checks](docs/VALIDATION.md#compact-phone-chat-action-menu).

## Exact rebuild and hosted UI checkpoint

The app rebuild at `a97c462` verified the updated notice, packaged
native alignment and exact-APK offline generation and model switching
on Android 8 and 17. The earlier hosted Android 8 shard failed two
visibility assertions. The local focused and 13-case runs passed, so
the hosted discrepancy remains open. Continue with the hosted matrix,
physical ARM64 and signed release gates.
[Build and Android evidence](docs/VALIDATION.md#exact-runtime-packaging-and-hosted-android-follow-up).

## Native dependency release checkpoint

Development pins the verified b12556 llama.cpp fix for CVE-2026-133710.
The runtime-pin APK passed the Android 8 and 17 offline model checks.
The published beta.9 still uses b12255. Physical ARM64 and final
signed upgrade remain open.
[Runtime-pin evidence](docs/VALIDATION.md#verified-llamacpp-b12556-development-runtime).

## Cold-start model stage cleanup

The app reclaims incomplete manual import and replacement stages when
the private model library opens after a process restart. The normal
startup test passed on Android 8 and 17 and verified retained download
prefixes and existing model bytes. The exact build passed 1,203 JVM
tests and both lint variants with zero errors. Physical and production
checks remain open.
[Cleanup evidence](docs/VALIDATION.md#cold-start-cleanup-for-interrupted-manual-model-copies).

## Phone model library durability checkpoint

Model install, settings and deletion sync directory entries before returning.
The build passed 1,202 JVM tests and both lint variants with zero errors.
Verified LFM3 and Qwen inputs passed five offline Android cases across
both owned emulators. The older hosted Android 8 shard holds a timeout in
the phone startup test. The exact build then passed a normal Android 8 reboot
with the Qwen bytes and both chat turn states retained. Physical-phone,
the older Android 17 guest observation, full hosted and production checks
remain open.
[Model evidence](docs/VALIDATION.md#phone-model-library-durability) ·
[Reboot evidence](docs/VALIDATION.md#offline-qwen-history-after-a-normal-android-8-reboot).

## Phone dialogs and project cancellation checkpoint - 20 November 2026

The project cancellation fixture now targets the reachable dialog footer.
The Voice microphone stays in the phone chat toolbar and waits for speech
preferences. The chat keyboard releases focus before the three private
dialogs open. The exact build
passed 1,200 JVM tests, both lint variants with zero errors and 40 focused
methods on each of Android 8 and 17. Full hosted, live Hermes and
production-release acceptance remain open.
[Validation evidence](docs/VALIDATION.md#project-cancellation-and-phone-dialogs-with-the-keyboard).

## Current Hermes heartbeat shutdown checkpoint

The exact v0.23.3 and main pins reproduced the late database reopen.
A shared two-file candidate stops heartbeat admission at teardown and
cleans up register-once and periodic rows on exit. Windows native stop,
standalone exit, live SQLite close-gate and released-contention checks
passed. Hosted Linux passed the earlier canonical and lifecycle cases.
The candidate now also returns from a full Windows stop with the
heartbeat transaction still held and leaves SQLite open. Hosted Linux reproduced that outcome at both reviewed pins. The subagent
shutdown ledger times out under the same contention. Real subagent recovery
and live Pi checks remain open.
[Candidate and evidence](docs/contracts/2026-11-19-hermes-public-shutdown-review.md#full-hermes-stop-with-the-heartbeat-transaction-still-open).

## Latest offline setup checkpoint

The offline checklist shows model, storage and history state. Typed topic
search and deterministic recovery check cards work with the model unloaded
and networking off. Exact JVM and focused API 26/37 checks passed. Physical
ranking of the new recovery topic, full hosted and release gates remain open.
[Setup evidence](docs/VALIDATION.md#offline-checklist-and-setup-issue-search).

## Latest offline phone voice checkpoint

Phone chat has an offline speech panel with reviewed draft append and
read-aloud for completed replies. Device coverage includes the phone
voice path and shared Hermes Voice screen controls. Physical speech
and Bluetooth remain open, along with release checks.
[Voice evidence](docs/VALIDATION.md#offline-voice-in-phone-chat).

## Latest personal profile backup checkpoint

Each profile backup includes explicitly selected profiles and uses a
passphrase. The actual Android 17 document picker saved and restored a
profile offline. Fixture tests verified additive conflict handling
and preserved existing history. Physical, hosted and release checks
remain open.
[Profile backup evidence](docs/VALIDATION.md#separate-protected-personal-profile-backups).

## Latest personal profile checkpoint

On-device Chat supports local profiles, writing preferences and
explicit selection of individual notes for a single reviewed request.
An offline LFM3 trial used one chosen note and preserved the prior
history. Profile backup selection, physical testing, and the original
Hermes and release gates remain open.
[Personal profile evidence](docs/VALIDATION.md#personal-profiles-with-a-reviewed-phone-model-request).

## Latest project approval and search checkpoint

Project review keeps the confirmation controls together, and phone history
shows the selected date before Apply. Both Android versions passed 138
focused checks, including summary drafting and image-share review.
Full hosted, real Hermes and signed-release acceptance remain open.
[Android UI evidence](docs/VALIDATION.md#project-approval-footer-and-phone-history-filter-menus).

## Latest temporary phone chat checkpoint

Temporary chat keeps an independent foreground transcript. Ending or
backgrounding the chat clears it. The native request releases its
context while keeping the model loaded. Android 17 verified two LFM3
turns offline and an empty reopen. Personal profiles, reviewed memory,
backup controls and physical-phone acceptance remain open.
[Temporary chat evidence](docs/VALIDATION.md#foreground-only-temporary-phone-chats).

## Latest offline writing checkpoint

Writing tools provide editable English and Chinese presets, custom
templates, reviewed Android text intake, exact model and prompt review,
and local result notes. LFM3 generated offline and the note reopened
after relaunch on Android 17. Physical-phone acceptance remains open.
[Writing evidence](docs/VALIDATION.md#reviewed-offline-writing-tools).

## Latest selected documents checkpoint

Signal Grove can ask the phone model about reviewed TXT and Markdown
excerpts and about selected text PDFs on Android 15 and newer. Android 17
verified an offline LFM3 answer with an original page citation. The strict
build passed 1,179 JVM tests and 106 focused device tests per emulator API.
Physical-phone and signed-release acceptance remain open.
[Document evidence](docs/VALIDATION.md#offline-questions-about-pdf-pages).

## Latest publisher model catalogue checkpoint

Signal Grove can review a newer bundled publisher revision while
keeping earlier model files, chat bindings and exact queue receipts.
The exact APK pair passed 1,173 JVM tests and 103 focused tests on
each emulator API. The current publisher revisions already match the
packaged pins. A live Qwen download recovered its exact prefix after
process stop and verified the full publisher hash after explicit
resume. A real successor, physical device and release gates remain
open.
[Catalogue evidence](docs/VALIDATION.md#publisher-catalogue-revision-updates).

## Latest phone model replacement checkpoint

The development app can stage a user-selected GGUF and review both
hashes and required space. Installation adds the candidate beside the
previous model. An Android 17 offline normal-app picker trial tested
cancel and exact publication alongside a verified LFM3 file. All
1,170 JVM and 102 focused native tests per API passed with zero lint
errors. Catalogue replacement and physical lifecycle checks remain
open. The original Hermes and release gates remain active.
[Replacement evidence](docs/VALIDATION.md#reviewed-replacement-from-a-phone-model-file).

## Latest background model transfer checkpoint

A reviewed model can download with a foreground notification after
an explicit tap. The service keeps the existing network preference
and saved partials. The debug pair passed 1,167 JVM tests and 100
focused tests per API with zero lint errors. The Android 17 system
timeout closed the socket and retained the exact queue item without a
retry. Continue OF-06 with reviewed updates, a notification-channel
trial and physical and publisher-transfer checks.
Hermes integration and final-source release gates remain active.
[Service evidence](docs/VALIDATION.md#opt-in-model-downloads-while-away-from-the-app).

## Latest model download network checkpoint

The download queue offers a saved unmetered-only option. The bound
request stops when the selected network changes and keeps its saved
bytes for an explicit resume. The debug pair passed 1,165 JVM and
99 focused methods per emulator API with zero lint errors. Continue
OF-06 with supported foreground transfers and reviewed model
updates while retaining physical, Hermes and final-source release gates.
[Network evidence](docs/VALIDATION.md#model-download-network-preference).

## Latest model download checkpoint

The development app has a private ordered queue for reviewed catalogue
files, exact saved bytes and phase, and explicit pause, resume and
discard. The debug pair passed 1,162 JVM and 96 focused methods per
emulator API with zero lint errors. Continue OF-06 with network
preferences, supported foreground transfers and reviewed model
updates. Physical and final-source release gates remain open.
[Queue evidence](docs/VALIDATION.md#reviewed-download-queue-and-explicit-resume).

## Latest offline startup checkpoint

The development app can open On-device AI from the app icon using
the switch in Set up offline chat. The debug pair passed 1,158 JVM
tests and 94 focused methods per emulator API with zero lint errors,
including launcher, draft retention, Hermes return and explicit
destination coverage. Continue with physical acceptance, OF-06
model transfers and the original Hermes and signed-release gates.
[Startup evidence](docs/VALIDATION.md#optional-on-device-ai-startup).

## Latest guided setup checkpoint

The development app now guides users from phone model download or
import through explicit load and first chat. It shows existing model
and chat state. The signed debug pair passed 1,158 JVM tests and 89
focused methods per emulator API with zero lint errors. Continue
with the saved local startup preference, hosted and physical checks,
and all remote integration and signed-release gates.
[Guide evidence](docs/VALIDATION.md#guided-offline-chat-setup).

## Latest model library checkpoint

The development app filters installed models by name, architecture or
SHA-256, shows stored weights, saved download data and sampled free
storage, and surfaces Load beside the model name. The licence scrolls
independently of the consent footer. The debug pair passed 1,158 JVM
and 87 focused checks per emulator API with zero lint errors. Continue
with guided local setup and the startup preference. Physical,
integration and signed-release gates remain active.
[Library evidence](docs/VALIDATION.md#installed-model-library-and-fixed-licence-controls).

## Latest model catalogue checkpoint

The development branch offers an offline comparison of the exact LFM3
and smaller Qwen files. Each download names its source, licence, hash and
revision and requires separate confirmation. Partials and discard are
scoped by model hash. The debug pair passes 1,158 JVM and 85 focused
methods per Android emulator API with zero lint errors. Finish installed
search and storage visibility, guided local setup and the startup
preference. Physical resource and host/release gates remain active.
[Catalogue evidence](docs/VALIDATION.md#offline-catalogue-and-separate-model-downloads).

## Latest performance checkpoint

The development branch `feat/offline-history-backup` now includes model
performance presets and measured app memory, first visible text and
sampled throughput. The explicit LFM3 benchmark passed offline through
the Android 17 app. All four fixture chats matched their original hashes.
The exact debug pair passes 1,157 JVM and 83 focused methods on each
emulator API with zero lint errors. The physical Android 17 phone ran
the fixed LFM3 prompt with an independently verified disconnected state,
then recovered the private route to Hermes. All six chats, models and
labels retained their hashes. The copyable performance report passes
all 84 focused checks per emulator API, including a real clipboard test.
An offline 2 GiB Android 8 emulator refused LFM3, then loaded the smaller
Qwen model on request and saved its reply. The earlier chat and both
model hashes were preserved.
[Low-memory evidence](docs/VALIDATION.md#offline-low-memory-refusal-and-smaller-model-recovery).
[Report checks](docs/VALIDATION.md#copy-on-device-performance-details).
[Physical measurements](docs/VALIDATION.md#physical-lfm3-benchmark-with-networking-off).
[Measurements and build evidence](docs/VALIDATION.md#on-device-performance-measurements-and-offline-benchmark).

## Current implementation and evidence

The export component is on `feat/offline-markdown-export`, layered on
`feat/offline-chat-actions` at 88a8d05. The earlier history branch passed full Android run 44496075372 with
1,182 API 26 and 1,183 API 37 tests. Main stays at a992cf4.

Main `a992cf4` has the UI readiness/viewport fixes; identical-tree PR evidence
independently verifies 1,149 API26 / 1,150 API37 standard methods, no skips.
Main Android and Windows pass. The feature branch watcher follows the
reviewed cron move and reports 177 stable v0.23.2 fingerprint changes.
Main upstream still requires a review of eight replaced storage modules.
Paired upstream tests pass all 163 selected main cases and 106 of 107
stable cases. The stable inventory assertion fails after a read-only API
history fixture and passes by itself. Continue mapping the persistence
authority and mutation interfaces before updating watcher paths. A reviewed
patch cleared both isolated native Windows heartbeat modes on v0.23.2,
and all 50 selected upstream cases passed. Main requires a separate port.
Stable Linux, contention and owner runtime checks remain open.
[Shutdown evidence](docs/contracts/2026-11-19-hermes-public-shutdown-review.md#verified-v0232-shutdown-candidate).
[Paired test evidence](docs/contracts/2026-11-19-hermes-main-stored-history.md#paired-gateway-and-sqlite-tests). [Source evidence](docs/contracts/2026-11-19-hermes-cron-source-relocation.md).

Unreleased `feat/offline-chat-actions` implements reviewed edit/regenerate/fork,
scoped drafts, request provenance, passive reply/code readers, text/model/date
search and now reviewed optional titles/pin/archive with Active/Archived/All
views. Labels live outside transcripts, stay out of prompts and preserve model/
parent identity. Cancellation after publication reconciles files; exact explicit
deletion cleans labels and corrupt indexes refuse deletion before data changes.
Latest required builds pass 1,107 JVM cases/104 reports and zero-error lint
(51/50 warnings). The fresh same-build APK pair passes 61 selected native methods on each
API 26 and API 37 without skips, including three new export flow checks.
Six real toggle inputs per API each have one accepted DOWN/UP, IME retained and
no retry. The exact Save title/pin/archive/model assertion remains unchanged.
Rows show recorded activity time and copied/edited/regenerated branch labels.
[Current source/APK bindings](docs/contracts/2026-11-19-phone-markdown-export.md) and
[retained UI failures](docs/contracts/2026-11-19-history-native-readiness.md).
Markdown review and file export work through the Android picker. The real API 26
Downloads transfer matches the source. The full hosted Markdown export
matrix passed. Physical Markdown export, protected save and offline restore
with ID remapping are verified on the phone.

Earlier phone pairings verify one actual offline native branch/Stop method and
real UI fork/edit/regenerate. The matching search build upgrades in place with
all UIDs, 16 metadata files and both models retained. Actual offline/unloaded
Search/Clear/garden read preserves all four chat files and both models. The normal
code112 app is untouched. The organization app8bda/test2df upgrade now verifies
all UIDs, 15 cold metadata files, both models and all four actual UI chats.

One four-case synthetic phone run stalls before first render and is stopped.
Two later bounded diagnostics confirm MIUI rejects the test-host start with
result102; one exact matching foreground intent unblocks a real identification
case, which previously failed its adjacent branch label check. The corrected
companion passes that identification case on the owner phone. Full Android CI
for the history branch passes, including the corrected pin/archive assertion.
SQLite main/WAL and
other data bytes match; transient SHM is separately reconciled with read-only
integrity ok. Radio/wake settings restore; reopening configured Tailscale once
restores a connected VPN and private Pi ping. No API/model readiness is inferred.
[Physical evidence and limits](docs/contracts/2026-11-19-phone-history-search-physical.md).


The branch feat/offline-history-backup layers the version 1 archive reader and
writer on the Markdown export branch. Its build passed 1,117 JVM tests, both
lint variants and 63 selected device checks on each API 26 and API 37.
The restore review passed 1,123 JVM tests and both lint variants. It checks
chat/label capacity, remaps collisions and resolves only included parents.
Recoverable publication passed 1,132 JVM tests, both lint variants and 65
focused checks on each API 26 and API 37. The protected backup build passed
1,139 JVM tests, both lint variants and 67 device checks per API. The private
staging and Tink 1.24.0 build passed 1,146 JVM tests, both lint variants and
69 device checks per API. The latest backup screens and picker wiring pass
1,146 JVM tests, both lint variants and 72 focused Android checks per API.
[Exact app and companion evidence](docs/contracts/2026-11-19-phone-history-backup-ui.md).
[Wire format and checks](docs/contracts/2026-11-19-phone-history-backup-archive.md).

The context meter and overflow admission check pass 1,148 JVM tests, both
lint variants and 73 focused checks per emulator API. The pinned LFM3 boundary
passes on the owned Android 17 emulator.
[Context meter and limits](docs/contracts/2026-11-19-phone-context-meter.md).

Selected-turn continuation passes 1,150 JVM cases, both lint variants and
76 focused checks per Android emulator API, including a backup restore with
chat ID collisions. Source bytes and unsent drafts are preserved.
[Selected-turn evidence](docs/contracts/2026-11-19-phone-selected-turns.md).

Reviewed phone summaries pass 1,152 JVM tests, both lint variants and 81
focused checks on each supported emulator API. Approval saves the edited
summary and source references. The original chat stays readable, and the
new chat is searchable and exportable before its first new question.
[Summary evidence](docs/contracts/2026-11-19-phone-reviewed-summaries.md).

## Exact next action

The hosted Android 17 shard at f46452b ran 572 tests with one search
fixture failure before its keyboard bounds assertions. The corrected
fixture waits for a coherent native keyboard viewport and retains the
original assertions. The frozen app and test pair passed all 84
focused checks on each local Android 8 and Android 17 emulator.
The original bilingual search keyboard bounds passed in both hosted
device logs. The full matrix exposed a separate history model-picker
failure on Android 8 and a Kanban keyboard timeout on Android 17.
Resolve both cases and verify the subsequent hosted matrix.
[Exact hosted run](https://github.com/DionCroft/Signal_Grove/actions/runs/44617490182).
The prior run at 1590452 stopped before device jobs when one native
backup child-identity fixture errored. Retain the failed run and resolve
that case independently of the keyboard fixture.
[Policy failure evidence](docs/VALIDATION.md#hosted-native-backup-child-observation-failure).
[Search fixture evidence](docs/VALIDATION.md#native-keyboard-viewport-in-phone-history-search).

The complete hosted Android run 44560196101 passed at 3d2b24a. Verified
raw partitions and APKs cover 1,203 Android 8 and 1,204 Android 17
standard methods, including both corrected fixtures. The earlier policy
failure remains recorded. Verify the independently queued
[reviewed-summary run](https://github.com/DionCroft/Signal_Grove/actions/runs/44573654477). The physical protected restore and Markdown export are verified. The physical
label Save, Cancel and unloaded archive read are verified. The full hosted
Markdown export matrix passed 1,187 API 26 and 1,188 API 37
cases. On the later backup branch, one hosted Android 8 shard failed before
Home was ready. The other found Review backup disabled after fixture input.
The current Home and protected-backup fixtures pass 14 focused checks on
both APIs. Both historical hosted failures remain in the evidence.
Physical manual reviewed-summary creation and selected-turn provenance are
verified, with no model loaded. Confirm the separate copy-only selected-turn
action and model-generated draft on the physical phone.
[Evidence](docs/contracts/2026-11-19-phone-reviewed-summaries.md#physical-reviewed-summary-with-the-model-unloaded).
The short physical offline benchmark and model mapping release passed.
Complete physical Stop, sustained thermal/battery and low-memory
checks. Keep OF-01 accessibility,
lifecycle and the original audit gates open.

Original remaining scope: upstream/shutdown, SDK/gateway/unbound drain/concurrency/
PM/fleet/recovery, real workspace/worker/files/NAS, automation/rooms/integrations/
configuration, resource/security and final original-key production upgrade/release.
Published beta.9 is unchanged. No Pi service/gateway/credential/model/default/
grant changes. Isolated staging does not authorize live cutover or canonical PM/
policy/launcher/private-backup/restart changes.
