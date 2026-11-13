# Signal Grove — paused implementation audit

Current owner-requested pause: [13 November review](../2026-11-13/PAUSED_REVIEW.md).
The original snapshot below is historical.

Historical snapshot. The user resumed work on 5 November 2026; see the current
[execution and acceptance ledger](../../AUDIT_EXECUTION_PLAN.md). The observations
and still-running counts below describe the time of the paused review.

Reviewed 5 November 2026. The user explicitly paused the long-horizon implementation goal before this audit. The goal is **paused**, with its original objective unchanged. This audit does not resume implementation, commit/merge the pending feature, publish a release, or start new builds/tests. Existing local and hosted validation was observed while already in flight. Application/test source remains identical to the frozen 401-file dependency build snapshot.

The original four correctness defects and the explicitly listed core bearer-API feature work have substantial implementation and regression evidence. The remaining effort is concentrated in management/automation coverage, actual interoperability, device validation and release closure. Passing source-contract checks must not be described as full Hermes feature parity or live integration certification. This review confirmed gaps and delivery risks; it did not reproduce a new critical runtime/security defect.

**Authoritative delivery state**

| Layer | Observed state |
| --- | --- |
| Published stable | `1.0.0`, code/history retained |
| Published preview | [`1.1.0-beta.4`](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.4), source `25c194bfba4c075bc70780a0a305e68b952229a0`, code 105; original signed production/debug assets still published |
| Local and remote main | `f2dc9de2ca64719837b1b3b58c753b506f84675b`: task-editing source; its full hosted API 26/37 suites and compatibility passed |
| Current branch/HEAD | `feat/kanban-task-dependencies` / `b678e8b9a679d280ce04f41a377c08a7f70ed50c`; committed task creation, not yet on main |
| Task-creation CI | [Android 42035583067](https://github.com/DionCroft/Signal_Grove/actions/runs/42035583067): verify and API 26 succeeded; API 37 still in progress at the audit observation. [Compatibility 42035588795](https://github.com/DionCroft/Signal_Grove/actions/runs/42035588795) succeeded |
| Dependency implementation | 13 existing changed/new files, uncommitted before this audit. No dependency commit or exact-head hosted run exists yet. This audit adds its own separate report |
| Development version | Code 106 / `1.1.0-beta.5`, unreleased; beta.5 release notes currently cover earlier project/board checkpoints and need final reconciliation |
| Existing dependency validation | Required Gradle checks passed; 499 JVM tests, zero failures/errors/skips. New 27-case suite passed on API 26/36/37 (81 passes); related 155 passed on API 26. Thus 236 accepted native passes at the observation; related 155 on API 36/37 still in flight |
| Artifact identity | App `f5bb29c488acf5e640cab48d7937fab3ad7b9973ae16e4d83c8aef6dd2cc69a8`; test `aef42c0579e08b66faf1ca3a0ea18cc19faa93ab4f3e55f341665ed75493810d`. Eight actual bilingual captures were previously inspected. Original signer/package/Room11 and device geometry are retained |

The 499 JVM and 236 native figures are different scopes and must not be presented as a full current-source device-suite result. The earlier failed build and corrected test-helper error remain recorded in [VALIDATION](../../VALIDATION.md). No final acceptance is inferred from a still-running process. The goal pause does not turn an already-started validation run into a passing or cancelled run.

**Requirement and feature audit**

| Area | Current evidence | What remains |
| --- | --- | --- |
| Four original defects | Desired-behavior regressions exist for original-target connection controls, multi-turn compatibility history, monotonic delayed controls and the verified cron prompt limit. See `ConnectionIsolationTest`, `CompatibilityHistoryTest`, `ControlStorageTest`, `JobAndReminderTest` and the validation record | Retain these as release gates; exercise the same interleavings against actual deployed Hermes, not only fixtures |
| Core bearer API parity | Capability/readiness, model/provider/runtime choices and session locks, skills/toolsets, fork/steer, usage/tool/public-subagent activity, stable conversation keys and image input/history/share are implemented | Live stable/prior-supported-server tests, provider fallback/accounting accuracy, real trust/elicitation and image-history interoperability. No fresh end-to-end execution was performed by this audit |
| Branding, pixel UI and updater | Signal Grove naming, original pixel theme/motion and reviewed GitHub/Android installation flow are implemented; public beta.4 has the canonical update feed | Physical-device UX, IME/large-text/accessibility/performance and update/permission/OEM checks for the next exact release. Preserve existing packages, signing keys, backup formats and asset compatibility |
| MCP, skills, plugins and provider management | Separate native authentication/profile selection; reviewed MCP provisioning/catalog/OAuth, skill discovery/install/update/locks, plugin install/toggle/remove/update/settings; main/auxiliary/MoA/provider accounts/pools/fallback/custom endpoints exist | Real-host/provider/MCP/channel interoperability; bundles; newer plugin bulk/automatic-update policy; unresolved server revision/CAS and projection limitations. Dynamic catalogs already cover new entries generically |
| Projects/workspaces | Stored project inventory and reviewed metadata CRUD/folder/archive/active-pointer controls exist in development source | Explicit chat-workspace adoption, repository/folder discovery and tree/file hydration. Setting the active project does not itself change a conversation's working directory |
| Kanban | Board inventory/administration, private task/history metadata, task editing and creation exist. Dependency add/remove has implementation and partial acceptance | Finish the pending chunk after resume, then status transitions, model/reasoning overrides, worker inspection/reclaim/reassignment, dispatch, event streaming, attachment bytes, pinned boards and reviewed path/transfer controls |
| Scheduling and automation | Basic bearer job CRUD/run controls exist. Advanced management supports inventory/detail, paused creation, and gateway pause/resume/remove | Rich advanced editing/execution/results/logs, room delivery, and authorized goal/heartbeat/loop monitoring/control. `GatewayCronAction` currently contains only Pause, Resume and Delete; do not infer these broader controls from inventory fields |
| Rooms/groups and cross-surface authority | No native room/group administration found in the reviewed management client/UI surface | Verify membership/lead/purpose/notes/room-delivery contracts and distinct room grants; then implement scoped reads and reviewed actions. API-owned sessions do not authorize importing arbitrary CLI/Desktop/chat IDs |
| Memory/persona/context/general configuration | Narrow, reviewed configuration slices exist for specific provider/plugin/channel features | Dedicated typed memory/persona/context/workspace administration and reset flows. Existing `/api/config` use for specific slices is not a complete configuration editor |
| Notifications/widgets | `BackgroundChecks.kt` polls best effort every 15 minutes, monitors tracked runs for the active connection, and routes to generic Chat/Tasks; widget shows local aggregates | Exact connection/conversation/run targeting, broader job/goal events, multi-connection policy and background/reboot/battery/OEM evidence. Push requires a deliberate authenticated server bridge/registration contract |
| Voice | Android recognition/TTS and consent policies exist | Physical microphone/speaker/Bluetooth/offline-service tests. Current inspected Hermes stable/main both advertise `audio_api=false` and `realtime_voice=false`; a native Hermes audio transport requires an actual supported contract/bridge |
| Rich rendering | `ChatScreen.kt` uses plain `Markwon.create(context)` and core Markdown | Assess Mermaid/math rendering and safe fallbacks; no Mermaid/TeX extension was found. Image support does not imply diagram/math rendering |
| Ongoing compatibility/security | Scheduled/manual/push compatibility checks, exact source boundaries, strict dependency verification and full device CI are present | Real-server contract execution, release-time dependency/advisory review, and explicit security automation/SBOM/provenance decisions. Only Android and Hermes-compatibility workflows are checked in; no recurring advisory/SBOM workflow was found. Hosted security settings were not inspected |

**Keeping up with Hermes**

The latest stable release observed is [v0.22.7](https://github.com/NousResearch/hermes-agent/releases/tag/v0.22.7), published 3 November at source `b200f5bc60e31588ba2cb60e288029c4a54ba5fa`. Today's main observation is [`84a0301415edd6d1385207ec96e4d7f2b6c1fcd9`](https://github.com/NousResearch/hermes-agent/commit/84a0301415edd6d1385207ec96e4d7f2b6c1fcd9). Existing successful compatibility-run artifacts were downloaded read-only and their advertised SHA-256 digests verified. They inspect these exact stable/main commits, report no tracked client review required, and contain no open original mobile findings. No new compatibility run was started for this audit.

| Recent upstream area | Audit disposition |
| --- | --- |
| Room leads/member state/purpose/shared notes and cron-to-room delivery | Native management work remains |
| Pinned Kanban quick-switch tabs | Native pinning remains; shared-current board switching is a different feature |
| Mermaid/math labels | Native rendering assessment remains |
| Team Gateway Telegram linking | Reviewed phone flow already exists; real connector/Portal/vendor behavior still needs validation |
| Slack thread follow-up silence | Specific policy/setting contract and mobile coverage need review; generic environment-field support alone does not establish this config option |
| Daybreak for subscription models | Verify the actual model/catalog/configuration mapping before adding a control. Current per-request `RuntimeChoice` models reasoning and service tier; no dedicated Daybreak field/control was found |
| New plugin catalog entries | Existing dynamic discovery is the appropriate foundation; validate relevant plugin/provider behavior live |
| Desktop/Web Access update/startup UX | Assess only applicable phone status/diagnostic information. Desktop-specific runtime/installer UI is not automatically a missing Android requirement |

Source-watch success concerns the selected routes/fields/helper semantics. It does not prove runtime permissions, plugin code safety, provider authentication, exact filesystem identity or full parity. Additional API surfaces need triage even while the canary is green. Optional Responses transport and generic file input should be explicit roadmap choices; recreating the agent/terminal/LSP/ACP runtimes on the phone is outside the companion architecture.

**Prioritized work after an explicit resume**

Priorities here are delivery priorities, not vulnerability severity ratings. The full paused objective is preserved; this order does not declare the remaining original requirements optional.

1. **P1 — Close the current delivery gap.** Obtain both existing related-suite terminal results; reconcile source/artifact hashes and the fresh owned-device readback; review/secret-scan and commit the dependency chunk. Respect each commit's own exact-head full Android/compatibility merge gate. Reconcile main versus creation versus dependency branches and the beta.5 release notes. The audit report is an additional file: future exact-path staging helpers must account for it deliberately rather than deleting it to fit the previous 13-file list.
2. **P1 — Establish real interoperability evidence.** Use an isolated real Hermes stable installation plus a prior supported version with a deterministic model/tool adapter and synthetic data. Validate native login/refresh, profile isolation, approvals/recovery, representative MCP/OAuth/skill/plugin/provider flows and project/Kanban mutations. Keep a separate main canary. A source parser and MockWebServer do not substitute for this layer.
3. **P1 — Validate physical Android behavior.** Use representative ARM64 hardware for microphone/TTS/Bluetooth, lifecycle/process death, LAN permission, large text/IME, notifications/reboot/battery and a matching-signer approved update that retains data. Current emulator evidence is useful and bounded; physical/OEM claims remain unsupported.
4. **P2 — Complete Kanban in coherent phases.** Status/override changes, worker observations/actions, dispatch, events/files, then pins/transfer/workspace mapping. Each needs verified contracts, narrow private reviews, exact scope, one-send uncertainty and independent observation. Dispatch dry-run can still reclaim/recompute, recorded PID is not worker identity/health, and status/ACK is not shutdown proof.
5. **P2 — Complete automation and collaboration.** Advanced cron edit/run/outcome coverage; room/grant foundations and cron delivery; then supported goal/heartbeat/loop monitoring/control. Research missing routes instead of sending CLI commands as ordinary chat and presenting them as native controls.
6. **P2 — Complete the remaining integration/configuration surfaces.** Plugin bulk/auto policy and bundles, channel/webhook/pairing-specific setup, memory/persona/context and explicit chat workspace adoption. Preserve secret handoffs, backend concurrency limits and the distinction between stored configuration and effective runtime.
7. **P2 — Tighten maintenance and UX.** The management view model is 1,326 lines/about 125 KB; the structured string file is 1,395 lines/about 358 KB. This concentration increases change/review cost; split feature controllers/state where useful while retaining shared receipt/scope guards. Reconcile stale release/support statements, add one concise implemented/released/live-verified matrix, and standardize contributor-facing artifact-aware validation commands. Tests themselves run in checked-in CI; owner-specific orchestration/evidence helpers currently also live outside the repository.
8. **P2 — Finish release/security acceptance.** Perform a fresh dependency/advisory review covering the actual resolved production graph and relevant tooling/native components; decide SBOM/provenance automation; finish signed exact-source release checks, checksums/asset verification, migration/retention, support matrix and community documentation. Never infer a safe dependency from checksum integrity alone. Keep the full release gate; improve test grouping/diagnostics using measured runtimes rather than weakening assertions or silently retrying.

**Evidence needed from the owner's environment**

A reachable real Hermes deployment with known version, separate inference/management addresses/authentication and a synthetic test profile is needed for live certification. Relevant test provider/MCP/channel accounts are needed only for the specific integration being validated. Physical Android/audio/OEM testing needs that hardware. These are genuine evidence dependencies, not reasons to claim all remaining implementation is blocked. No such access or action was requested during this paused audit.

**Resume criteria**

Before resuming, use this report to choose the next bounded delivery package and record its requirements and acceptance evidence. A feature is implemented when its UI/contract/regressions are present, merged when its exact source gates pass and it lands on main, shipped when matching signed assets are published, and live-verified only when the actual deployment/device evidence exists. Keep these four states distinct. The original long-horizon goal remains paused until the user explicitly resumes it.
