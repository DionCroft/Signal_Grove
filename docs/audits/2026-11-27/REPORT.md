# Signal Grove product and release audit

27 November 2026 · Audited source 972542abffec86c9fd79475b829c2abec9addc8f on feat/offline-workspace.
Public users can download beta.16. The offline workspace remains on the
feature branch, with beta.17 used for a private upgrade test.
[Public beta.16](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.16) ·
[Development branch](https://github.com/DionCroft/Signal_Grove/tree/feat/offline-workspace)

## What was verified

| Area | Result |
| --- | --- |
| Build | 1,266 JVM cases passed with zero failures, errors or skips. Debug and release lint and both debug APK assemblies succeeded. The app and Gradle inputs match the private signed upgrade build. |
| Portable policies | 74 focused cases passed across shutdown, workspace attachment, scheduler receipts, dependency policy and device coverage. Strict dependency verification covered 524 components and 1,123 artifacts. |
| Focused device checks | Android 17 passed 33 cases. Android 8 passed the same 33 cases. The subset covers capture/task storage, notebook isolation and review/send, Today receipt refresh, denied microphone permission, shared gateway admission and updater storage. |
| Existing physical evidence | The Xiaomi ran OCR and transcription on synthetic inputs in Airplane mode and saved edited results. The private, original-key beta.17 emulator upgrade preserved a beta.16 task and opened Quick Capture. |
| Live reachability | The configured Pi responded over private Tailscale. The API health route returned HTTP 200 and the dashboard responded with HTTP 302. These checks establish reachability. |
| Hosted release gate | Failed in the policy job before Android 8 or 17 ran. The local reproduction failed at the same inventory assertion. The exact failure and the prerequisite repairs are recorded below. |

[Build and prior device evidence](../../VALIDATION.md) ·
[Hosted Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/45972818779)

## Confirmed blockers and product gaps

### P0 · The Android release gate stops before device tests

The external test inventory omits
`LocalModelFamilyIntegrationTest.verifiedQwenExtractsReviewedCaptureTasks`.
Its exact source equality check fails, with 1 failure across 1,255 policy cases.
Both API levels were skipped. The coverage job then failed because no shard
artifacts existed. Release readiness depends on a new exact-source run.

Two more methods have explicit opt-in assumptions for language-pack or
Airplane-mode fixtures and currently sit in standard test classes.
Classify `MediaCaptureNativeTest.modelPacksRecognizeSyntheticInputsAndSaveOnlyEditedTextOffline`
and `TodayConnectionTest.localTodayIsAvailableInAirplaneMode` for their
prepared fixtures. Keep their runnable microphone-denial and receipt-refresh
cases in the standard matrix. Keep assertions and exact coverage intact.
[Gate inventory](../../../scripts/ci_android_device.py#L24) ·
[Inventory assertion](../../../scripts/tests/test_ci_device_setup.py#L305)

### P0 · Hermes compatibility is still open

The stable source check completed on Hermes v0.23.4
(8fc0374447313e276f6b9a2764bfc750190c966a). Relative to the tracked
baseline it found zero removed used routes, 220 changed watched handlers
and 29 changed watched persistence sources. It flagged mandatory semantic
review. Relevant surfaces include run admission and recovery, session
history, approvals, scheduler behavior, workspace authority and rooms.
Prioritize authenticated runtime tests for replay, profile isolation,
reviewed scheduling and related network interoperability.

Current upstream main (1d15aad1dfa8e9e5fcbd2082cad5666a0907a07d) could
not be inspected completely because ten required source paths have moved
or disappeared. Confirmed tree changes include the plugin settings package
and `cron/job_args.py`. The persistence layer now has dedicated modules.
Map and review the replacement sources before updating any baseline.
[Stable release](https://github.com/NousResearch/hermes-agent/releases/tag/v0.23.4) ·
[Exact upstream main](https://github.com/NousResearch/hermes-agent/tree/1d15aad1dfa8e9e5fcbd2082cad5666a0907a07d) ·
[Hosted compatibility evidence](https://github.com/DionCroft/Signal_Grove/actions/runs/45717692263)

The hosted isolated shutdown workflow passed on its reviewed cohorts,
including v0.23.4. A live shutdown and recovery proof for the maintained Pi
remains open. Verify in-flight work, durable interruption or uncertainty,
late completion fencing, reconnect and recovery without duplicate admission.
[Shutdown evidence](https://github.com/DionCroft/Signal_Grove/actions/runs/45717692260)

### P1 · Today can omit remote approvals and open the wrong run

Source inspection confirms that Today queries the local Room tables and
refreshes at most eight locally known conversations with run IDs. A pending
approval created on another client can be absent from this list. An empty
receipt list therefore has limited meaning, even while connected.

Tapping a completed-run row passes only the session key to Chat. After a newer
turn in the same session, the row opens the conversation without locating the
displayed run. The Refresh button can also be enabled when the server lacks
Runs capability, and the handler returns without feedback. The current fixture
checks receipt scope and freshness. It lacks an older-run tap test.

Add an authenticated and capability-gated approval inventory for the active
profile. Show which scope and time range were checked. Link rows by exact
connection, profile, session, run and request when applicable. Explain
unsupported or busy Refresh states. Test older runs, approvals originating on
a second client and reconnect within the freshness window.
[Today storage](../../../app/src/main/java/org/hermesmobile/app/data/TodayDao.kt#L10) ·
[Today UI](../../../app/src/main/java/org/hermesmobile/app/ui/TodayOverview.kt#L79) ·
[Refresh guard](../../../app/src/main/java/org/hermesmobile/app/ui/HermesViewModel.kt#L62)

### P1 · Saved captures and notebooks lack data management

The capture DAO supports creation, search and reviewed task linking.
The notebook DAO can detach items. Users still need controls to edit, archive
and delete saved captures, rename and delete notebooks, and export or
restore a selected offline workspace. Existing encrypted backups cover phone
chat history. Define how local task links and citations survive an edit,
deletion or restore. Keep Hermes replies and their server retention independent.
[Capture storage](../../../app/src/main/java/org/hermesmobile/app/data/PhoneCaptures.kt) ·
[Notebook storage](../../../app/src/main/java/org/hermesmobile/app/data/PhoneNotebooks.kt) ·
[Phone backup scope](../../ON_DEVICE_AI.md)

## Acceptance still required

| Area | What needs to be demonstrated or added |
| --- | --- |
| Live Pi to Windows | The last authenticated admission sample was unobserved. The protected project-control ledger and enrollment prerequisites need review on the real deployment. Verify the phone attaches to the authorized workspace, discovers an allowed file, shows the exact worker and checkout, and handles both Allow once and Deny. Verify one approved scheduled occurrence executes. Recover the same original receipt after disconnect. |
| Hermes chat | Continue a shared conversation across clients. Verify session attachment, profile switching, language and image payloads, pending approval renewal and reconnect without duplicate work on supported real versions. Verify notebook handoff and link the actual reply on the live Pi. |
| Automation | Build on jobs, schedule editing and scoped receipt fixtures. Prove timezone and daylight saving transitions, paused edits, catch-up, one reviewed Run once, results and bounded logs on the real scheduler. Add a clear unknown outcome and original-receipt recovery on the phone. |
| Kanban, rooms and integrations | Complete real-server Kanban transitions and provider overrides before worker and event controls, attachment contents, pins and transfers. Complete room paging, grants, membership and delivery. Prioritize real onboarding and reconnect for an MCP and a messaging channel before broad plugin update and webhook UI. |
| Notifications and accessibility | Verify deep links against the exact connection, profile, conversation and run, plus multi-connection preferences, denied permissions, reboot/Doze and OEM battery behavior. Test TalkBack, 200% font, compact width, keyboard, contrast and reduced motion through Today, capture, notebook review and approval. |
| Offline media and local models | Run the real Xiaomi microphone flow with consent and verify permission recovery, cancellation, backgrounding and storage pressure. Add pack status and removal, then consider more languages. Keep the 4K production LFM3 limit until representative results justify raising it. Existing near-window 8K and 16K trials exceeded 180 seconds. Improve context budgeting and earlier-turn selection within the supported limit. |
| Release and support | Repair the exact-source matrix and validate Hermes compatibility. Verify a signed Xiaomi upgrade retains the connection, history, tasks and existing offline data. Test updater discovery and installation from the actual public assets. Refresh production dependency advisory review, SBOM, signatures, release notes, screenshots and support docs. The Play plan separately needs a Play-specific bundle and private reviewer setup. |

[Full acceptance ledger](../../AUDIT_EXECUTION_PLAN.md) ·
[Live admission evidence](../../VALIDATION.md#live-gateway-admission-diagnosis) ·
[LFM3 context measurements](../../VALIDATION.md#lfm3-context-capacity-probe-on-the-xiaomi) ·
[Play release plan](../../GOOGLE_PLAY.md)

## Recommended implementation order

1. Restore the standard Android matrix and refresh the Hermes compatibility
   checks. Preserve failed runs and rerun both API levels on
   the exact proposed release source.
2. Make Today a reliable action inbox. Fix run navigation and Refresh
   feedback, then discover server-reported pending items in the active
   profile. Add reviewed capture and notebook edits and deletion.
3. Prove the bounded Pi-to-Windows workflow and recover the receipt for
   one approved scheduled occurrence. Verify the signed Xiaomi upgrade
   and GitHub updater after the release candidate is ready.
4. Extend offline data portability and capture ergonomics. Add selected
   workspace export and restore, a direct screenshot-share action into
   Quick Capture, and clearer context budgets for long notes and chats.
5. Expand automation, Kanban, rooms and supported integrations one
   complete workflow at a time, with exact attribution and recovery.

This audit used synthetic data for Android fixtures and read-only health
requests against the owner's Pi. It records release blockers and observed
behavior alongside the acceptance work still owed on the live system.
