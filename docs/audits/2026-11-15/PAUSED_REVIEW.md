# Signal Grove paused review - 15 November 2026

The goal API confirms **paused at the owner's request**. Every original audit,
local-AI and release requirement remains. Resume only when requested. This
wrap-up saves existing work and verifies download availability; it starts no
new implementation, deployment or release qualification.

## Source and APK delivery

- Reviewed SDK source is already on main at
  62c8bbe0cad0a5c212225a30251269f698900ad0. This paused main checkpoint changes
  documentation only.
- Seven unfinished route-diagnostics/navigation-helper changes are saved and
  pushed only to feat/on-device-ai at
  a2a67b2ec55b04a9e1ede71e19b4d033487fd00e.
  [Investigation, exact build and failed test](https://github.com/DionCroft/Signal_Grove/blob/a2a67b2ec55b04a9e1ede71e19b4d033487fd00e/docs/contracts/2026-11-15-route-diagnostics-investigation-checkpoint.md).
  They are not merged into main because the focused API37 gate fails.
- [Beta.9/code114](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.9)
  remains the latest published experimental preview; stable 1.0.0 is unchanged.
  **No newer stable APK is qualified or published.**
- [Download the normal production-package beta.9 APK](https://github.com/DionCroft/Signal_Grove/releases/download/v1.1.0-beta.9/hermes-agent-mobile-1.1.0-beta.9.apk).
  Install over the matching existing app without uninstalling. The optional debug
  asset is a separate package. Ordinary users need no test companion. Enable
  Include preview releases for in-app discovery; installation requires approval.
- This pause review anonymously re-downloads the production APK and SHA256SUMS.
  Both match the published sizes and hashes. APK: **71,052,938 bytes**,
  SHA-256 1837ea8f2ea94abcee63c38f630e14e72f92902fab9963d5c98756dff501f832.
  Checksums: **519 bytes**, SHA-256
  fea58bfe819225b22dcf0b2237cca10db617a1446739db4cddcaade637812594.
  The six existing assets include the debug APK, public certificate, runtime SBOM
  and provenance. [Exact release evidence](../../contracts/2026-11-13-beta9-public-preview.md).

Published APK source is 0ee08e37c90e009a70bb290301c81433a11517a5, not today's main.
Source pushes do not update release binaries. Later private debug code114 builds
are not replacements for the published production asset.

## Completed implementation and verified milestones

Implementation and scoped evidence do not certify every Hermes version,
integration, model or phone.

| Area | Completed |
| --- | --- |
| Version milestones | Public 0.2.0 sessions/tasks/jobs, 0.3.0 voice/activity/approvals, 0.4.0 notifications/widget/automation foundations and documented 1.0.0 community release |
| Branding and interface | Signal Grove name/docs, original pixel UI/animations, five themes, reduced motion, scalable text, English/Chinese/pinyin and phone/tablet navigation |
| Connections and updates | Private HTTPS/Tailscale setup, profile prefixes, encrypted credentials, explicit reviewed actions and approval-based GitHub updates with checksum/signature checks |
| Hermes chat | Capability-based readiness/transports, streaming, history search/paging, existing messages, models/providers, locks/forks/steering, tools/skills/subagents, image input/history and approvals/clarifications |
| Owner setup | Earlier production upgrade verified Pi connection, refreshed current conversations/earlier messages and stored project details; missing cached sessions remain distinguishable from live sessions |
| Model awareness | Verified serving model distinct from server default and conversation selection; Automatic follows the freshly verified serving model; reasoning/service presets |
| Phone LLM | Actual offline LFM3 and Qwen2.5-0.5B native phone evidence; separate local chat/history, streaming/Stop/recovery, load/unload/context, compatible GGUF import, verified download/resume and licence/hash/storage/delete controls |
| Offline helper | Static setup guide without a model; versioned source-backed setup/network/auth/models/history/projects helper; separate local/remote authority and reviewed unsent-draft handoff |
| Tasks, voice, monitoring | Local notes/due dates/priorities/filters/reviewed JSON transfer, opt-in notifications/task widget/supported schedules, separate Dictation/Read aloud and physical read-aloud/stop-on-leaving evidence |
| Management foundations | Canonical read-only conversations/rooms, project-conversation navigation, stored projects, providers/accounts/credential pools/fallbacks, MCPs/skills/plugins/channels/Kanban and schedule controls |
| Authentication and worker reads | Profile/TLS/auth/no-replay regression checks; reviewed HTTP operator-binding fix; worker read bridge/native transport and isolated Pi/worker component evidence |
| SDK audit progress | Original isolated SDK configured freeze/original-journal drain/normal native stop; fresh-copy missing peer dependency/unbound-status correction; 16 read-only checks using two actual native turn receipts, with profile/freeze/UNKNOWN guards |
| Validation and packaging | 1,011 JVM cases, zero-error lint with retained warnings, earlier exact-source full standard emulator gates and scoped phone/model/retention evidence; public beta.9 original-key APK/checksums/certificate/SBOM/provenance |

Local inference has no implicit Hermes tools, credentials, remote-project
authority, cloud fallback or approval rights. Arbitrary GGUFs and phones are not
certified. On-device dictation remains unavailable with the recognizer installed
on the owner's phone; offline text inference and read-aloud are separate.

## Latest results and preserved failures

| Check | Actual result and limit |
| --- | --- |
| Investigation build | Required JVM/lint/debug app/test assembly pass; 1,011 cases /97 reports, no failures/errors/skips; lint 0 errors/fatals, 51 debug /50 release warnings |
| API26 focused | 16/16 positive, no skips, exact source/installed APK binding |
| API37 focused | 15/16 positive; PhoneDraftRoutingTest.cancelReviewKeepsTheRemoteDraftAndPhoneFilesUnchanged fails because Cancel is not entirely above the native keyboard |
| Original Demo tests in these selected runs | Both pass and retain actual content-free callback/controller traces; this does not explain or fix the older API26 failures |
| Prior workspace fixture | 27 focused methods per API26/API37 pass at a separate earlier snapshot; not current investigation or complete release acceptance |
| Last independently reviewed full main emulator gate | Source 09306e6: 1,142 API26 /1,143 API37 unique standard methods; later-source qualification remains separate |
| Windows component policy suite | 1,158 distinct cases: 885 positive /273 platform skips; native component observations are not extra policy methods |
| Main 62c8bbe hosted checks at pause | [Windows passes](https://github.com/DionCroft/Signal_Grove/actions/runs/43724650561), [upstream inspection fails](https://github.com/DionCroft/Signal_Grove/actions/runs/43724650516), [Android pending](https://github.com/DionCroft/Signal_Grove/actions/runs/43724650530) |

A zero native process exit does not override a JUnit failure. The strict parser
refuses the API37 run; its original operation/log remain intact. A separate
read-only pause review reconciles 16 starts, 15 positive and one negative status.
No assertion, source fence or compatibility baseline is waived, no failure is
relabeled flaky, and no case is retried during pause. The old API26 Demo,
navigation/history failures and original shutdown warning remain unresolved.

Private pause-review SHA-256:
e3088911a796da1faebaca58fd24c85a178f5d9b514552688d308c5af1b4be40.
Investigation build snapshot:
1621114d106ecb40a4fab31c54ef5385e444b0b992d45d4004ca6fffbb3853d3.
API26 raw log:
b0b78c2002ee14770f903493457d704b96570c14498c4fac07929869703f0952.
API37 failed raw log:
fc2754c7ea4d4266d2957a82ebb7849bd6e46ccdc6d62ddff29811b2330356c2.

## Incomplete tasks retained in the goal

| Area | Still required |
| --- | --- |
| UI and exact-source gates | Diagnose/fix original API26 Demo/navigation/history causes and API37 phone-draft Cancel/keyboard viewport failure; preserve original gestures/approvals/assertions, then complete focused and full standard coverage on final source |
| Hermes compatibility/shutdown | Semantic/transitive review of changed used handlers on supported stable/prior and inspected main; resolve original owner cleanup warning through actual lifecycle behavior; preserve immutable sources and failures |
| Corrected SDK/deployment readiness | Actual corrected gateway-wire/unbound original-journal drain, concurrent snapshots/unprofiled behavior, final declared-source PM generation, marker/source/owner races, all-profile/worker admission, unified execution, authenticated health/runtime recovery, canonical old-stop/new-start and full live/private-backup acceptance |
| Projects/workers/files | Explicit authenticated chat/workspace attachment/adoption; worker identity, registered roots, repository/folder/file/NAS access; reviewed dispatch/reassignment with actual outputs/receipts on owning host |
| Kanban | Real-server status/model override acceptance; worker controls, events, attachment contents, pins and transfers |
| Automation/rooms | Advanced job editing/execution/results/logs; supported goal/heartbeat/loop controls; owner-room forwarding/paging, membership, grants and exact delivery |
| Integrations/configuration | Remaining plugin update policies/bundles, channel/webhook pairing, typed memory/persona/context/reset, representative MCP/provider/skill integration and doctor/ContextGraph acceptance |
| Remote end-to-end | Real auth renewal, approvals/clarifications, reconnect/recovery, images, multi-connection/profile isolation and integrations against supported Hermes versions and owner setup |
| Offline models/helper | Complete twelve-method external-model/prerequisite gate; final-production import/download/resume/Stop/recovery/history binding/helper behavior, compatible user-model/template limits and full lifecycle acceptance |
| Phone experience | Exact notification targeting, background/reboot/battery/OEM behavior, sustained thermal/endurance, Bluetooth/audio, multi-connection, accessibility/scalable layout and performance; retain installed-recognizer dictation limit |
| Production upgrade | Exact higher-version original-key APK in-place upgrade; signing/UID/schema/settings/data retention; current Pi chats/projects and applicable local models/history/live/offline behavior on normal package |
| Release/maintenance | Consolidate qualified branches, final-source complete CI/compatibility/device gates, refreshed dependency/advisory/SBOM/provenance review, support/community docs, Mermaid/math assessment, version/notes/README reconciliation and signed public-download/in-app update verification |

All four acceptance priorities remain: API26 diagnosis; upstream/shutdown; real
workspace/worker/automation/integrations; final production upgrade/release.
The [full audit ledger](../../AUDIT_EXECUTION_PLAN.md) and
[on-device plan](../../ON_DEVICE_AI_PLAN.md) retain detailed requirements and
evidence. Optional future ideas such as Wear OS are not added to current scope.

## Continuation boundary

Wait for explicit resume. Read [PROJECT_STATE](../../../PROJECT_STATE.md),
[HANDOVER](../../../HANDOVER.md), [GOAL](../../GOAL.md), this report and the feature
checkpoint; inspect exact failed/unknown receipts before changing or retrying
anything. Do not merge the investigation as a proven fix.

No final local build/instrumentation remains active. Automatically triggered
GitHub jobs may continue independently; pending results are not acceptance.
The owner's phone, models, credentials, live Hermes and published assets are
unchanged by wrap-up. Isolated Pi staging approval does not authorize live
cutover, new grants, canonical PM/policy/launcher changes, private GitHub/live-home
backup or service restart. None occurs here.
