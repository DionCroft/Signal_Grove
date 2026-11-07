# Task status operations

Reviewed 6 November 2026 against official Hermes stable
`2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` and main
`90d6b98fd86d20981ee06ddd5b7d93e4320f26a4`. This is a public-source contract
review, not certification of a custom deployed plugin. No live task was changed.
The later main canary `26874afd73ea4f81c5ac5a5098e222750a337a82` was also
checked through the official GitHub connector: all five complete blob identities
in the table below still match. These are pinned observations, not a claim about
future moving refs.

The original typed-plan checkpoint had an internal status executor and one-use approval value.
That checkpoint exposed no native form; current controls are described below. Its required build passed575 JVM
cases, but its first native fixture attempt failed and ended with a startup ANR;
acceptance remains open. This does not close the audit row.

## One status phase

The authenticated dashboard plugin's `PATCH /tasks/{task_id}?board=<slug>` accepts
status alongside other fields, but applies multiple phases. Assignment can write
before status; override/priority/text phases follow it. A status operation must
send only `status` and its documented handoff/reason field. Do not combine it
with assignment, metadata, priority or overrides. No guessed `expected_run_id`,
revision, If-Match or idempotency field is supported by this PATCH.

| Target | Supported observed origin | Dedicated field / material effects |
| --- | --- | --- |
| `done` | running, ready, blocked, review | Explicit replacement `result`; human force override, completion/evidence/parent/PR gates, handoff file staging, run closure, child readiness, managed and deferred-parent workspace cleanup, lifecycle hook. |
| `review` | running, ready | Optional `summary`; human force override, parent/reviewer provenance gates, artifact staging and possible reviewer inheritance/reassignment. |
| `blocked` | running, ready | Required mobile `block_reason`; closes or synthesizes an attempt, releases recorded claim/PID and may invoke a hook. The richer kernel block-kind field is not exposed by this PATCH. |
| `scheduled` | todo, ready, running, blocked | Optional `block_reason`; parks the task and releases recorded claim/PID. No wake time is accepted; actual scheduling is separate. |
| `ready` | another known status | May unblock or resume review, return todo under parent gates, or make work dispatchable. |
| `todo` | another known status | From review, reopens through the implementer/restored-review path and can actually land ready. Todo can become ready on a later dispatcher readiness pass. |
| `triage` | another known status | Direct transition; reopening done/archived still invalidates descendants. |
| `archived` | another known status | Closes a recorded attempt, may request worker termination, recomputes child readiness and attempts managed/deferred-parent workspace cleanup. |

Unknown origins and no-op transitions are refused by the mobile plan. `running`
is deliberately absent: claiming/dispatch is a different operation. Locally
eligible does not mean the server will accept the operation.

The host completion handler writes `result` even when the field was omitted.
The mobile foundation therefore requires a nonblank explicit replacement result
for every completion, including review approval. This is a deliberate client
constraint, stronger than the host's review exemption; it prevents an unnoticed
clear of an earlier stored result. Statements retain their exact Unicode text,
subject to bounded length and valid control/surrogate checks.

Prose-discovered handoff files are considered only for a managed scratch
workspace; the staging helper resolves containment, bounds individual files and
uses exclusive bounded copies into task attachments. Copy/preservation failures
can roll back the transition. The plan supplies no arbitrary artifact metadata.
PR acceptance can still require an exact persisted contract and an authenticated
assignee's GitHub checks. Those host gates are not satisfied by the mobile result
text; the plan does not claim otherwise.

## Approval and outcome requirements before execution

The reviewed PATCH always acts on the task current at host write time. It has no
atomic run/revision fence. A fresh client preflight cannot eliminate the race.
The approval contract is explicitly **task scoped at write time**, including the
task's latest connected graph. It requires the exact typed task ID and separate
consent to that scope; the approval is consumed once, including after a refused
preflight. It never labels the operation as bound to the earlier observed worker.
The Task projection exposes a claim/run reference, but not the kernel's process
start fingerprint. A claim hash is not verified process identity. Private native controls and translated effects are now implemented on the
development branch; their current acceptance scope is recorded below.

Read-only preparation collects the complete connected component, with128-task,
8MiB total and per-response/depth bounds. Missing/asymmetric/cyclic/unsupported
graphs are refused. Fresh preflight compares canonical wire metadata, including
undisplayed policy fields; heartbeat/lease clocks and derived diagnostics/age
are excluded. A board's200-character summary preview is not mistaken for the
full drawer handoff. Required nullable run/claim/workspace metadata must actually
be present. This is still preflight, not an atomic fence.

Bind private review to authenticated actor, exact URL/prefix, selected profile,
profile-home identity, board/database mapping, task, original state and requested
single-purpose payload. Re-read those identities and affected task/dependency
observations before any send. Reopening done/archived can demote transitive
ready/review/running/done descendants to todo, clear their attempts/claims,
request worker termination and reset failure counters. A direct-link count is
not a complete impact graph. Preserve bounded/cycle-aware traversal and explicit
incomplete-graph handling.

Use the existing shared-home uncertainty barrier and durable one-send receipt
lifecycle. A generic 400/409/500 is not proof of no write: refusal paths can write
audit/evidence events, and post-commit effects can fail separately. Lost responses
are unknown; never resend blindly. Store only metadata/hashes in public/exported
receipts, with transient exact values in the private review.

Separate requested status, returned effective status and independently observed
stored state. Ready/todo can resolve differently because of review and parent
gates. A task-row match or successful ACK never proves engineering completion,
worker termination, hook execution, runtime adoption or workspace cleanup.

## Worker and filesystem boundaries

Worker signaling checks host-prefixed claims. Current spawn fingerprints combine
boot/container identity and process start time; recycled or unreadable matching
identities are not signaled. An explicit unverified fingerprint refuses signaling
of a live process. **Legacy null fingerprints still use the older bare-PID
behavior.** Public source is not a guarantee that a deployed row has a current
fingerprint. Termination is best effort after transaction commit; some direct
status handlers discard its outcome. Completing or requesting review does not
itself prove that an old worker stopped executing.

Scratch cleanup requires strict lexical and resolved containment below a
managed root, never the root itself. Active children/shared use can defer it.
Unmanaged directories and `dir` workspaces are retained, but finishing a child
can enable deferred cleanup of its parents. Linked-worktree cleanup preserves
the main checkout, dirty/unpushed/unresolvable work and uses non-force removal.
An autogenerated `wt/` branch may then be deleted. Best-effort failures are
swallowed. The underlying unpushed check also has a local-trunk fallback when no
remote refs exist; do not claim every removal proves remote backup. These are
host guards, not a permission for the client to delete project files itself.

## Source evidence and remaining checks

The complete Git blob identities match both reviewed refs and the preserved
read-only source used for bounded inspection:

| File | Git blob |
| --- | --- |
| [plugin handlers](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/plugins/kanban/dashboard/plugin_api.py) | `c38f8e399bdca0d8cf7f5cb1c9ed8e383302e1c8` |
| [task lifecycle](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/hermes_cli/kanban_db.py) | `cd14dc277c31c18dd8e4abfbae4e4f11c09728d7` |
| [workspace cleanup](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/hermes_cli/kanban_db_workspace.py) | `bf5d878fc0ce46e6a36f676eba2c7c1d3fa8b1ec` |
| [worker identity/signaling](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/hermes_cli/kanban_db_dispatch.py) | `72a779c7c7a4359ce21476f86842869d39219d13` |
| [worktree retention](https://github.com/NousResearch/hermes-agent/blob/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19/hermes_cli/worktree_ops.py) | `bc932825b7330f6d06b5cfc1d50cbf4bc29de7fa` |

Still required: finish transitive/deployment review, actual isolated server
transitions, full new-head hosted acceptance and separately authorized live checks.
Private form/review/receipt routing now has the focused acceptance below.
The first strict fixture incorrectly omitted the board GET's documented
`include_archived=true` argument. Its16 negative methods, zero positives and
startup ANR remain recorded; no native acceptance is claimed for that cohort.
The plan is not a substitute for any remaining check or complete feature parity.


The corrected internal executor now has 575 JVM and 67 API37 local HTTP/Room
regressions, including 23 status methods. Original failed cohorts remain recorded
in VALIDATION.md. Twenty-two status lifecycle/helper fingerprints plus four
request fields are watched against the preserved F0 baseline; all match reviewed
stable 2bd378e and main b2e4ded. This does not cover every workspace/dispatch helper
or prove deployed custom code. That earlier internal-executor checkpoint exposed no native status control.


## Private native review and receipts — development source

Static review of the exact listed upstream blobs confirms `_task_dict` uses
`asdict(Task)`: all seven required nullable task/claim/workspace/result/contract
fields are exposed. The process-start fingerprint is not a Task field. This
source check does not certify actual server transitions or worker identity.

The task drawer now offers the eight supported targets; unavailable/same-state
choices explain their restriction and Running is not offered. Done requires an
exact replacement result; Blocked requires a reason. Review/Scheduled have an
explicit optional text choice, and Scheduled supplies no wake time. Preparing
impact is a separately initiated read, not a status write.

The private review displays the exact host/profile/actor, board/database path,
task and requested fields, full connected component and all applicable lifecycle
effects. It requires the exact typed task ID plus an explicit grant for latest
host task/graph effects after preflight. Cancel, close, profile/context changes
and shared pending work discard old drafts/grants. No atomic worker identity or
run lock is implied. One send retains a durable acknowledgement before optional
readback. Requested status, host-reported status and matching later row remain
separate; differing results never trigger a corrective replay.

Status receipts route back to Kanban instead of generic MCP inspection. A lost
reply remains unknown even after fresh reads. Cross-profile pending status work
shows only a redacted endpoint barrier; settled private history stays scoped to
its original actor/profile/credential context. Matching row text never proves
worker shutdown, descendant transitions, file cleanup, hooks, loaded models or
engineering completion.

The current pair passed 577 JVM and 25 status/22 override API37 native cases;
three fresh bilingual modal captures were inspected. The earlier executor's full
968/969 device passes are separate. See VALIDATION.md for retained failures,
hashes, full-gate requirements and the remaining real-server/worker boundaries.
This source is not included in public beta.6.

## 7 November 2026 — status/override review-generation repair

The unpublished status-forms branch inherited the plain private review counter. Observable StateFlow generation is now collected by both Kanban and project dialogs, so reviews can invalidate/rebuild even when visible ManagementState remains identical. Two explicit generation-only native cases accompany the correction. Existing request/preflight/profile/receipt fences are unchanged. Patch preflight initially refused the changed Kanban context before mutation; the clean file patches and exact collector line were then applied. The earlier failed full hosted source remains failed and retained.

All four required Gradle tasks passed with 577 JVM cases and zero failures/errors/skips. The same original-key QA app/test pair passed 53 forms/generation/lost-reply cases on API26 and 96 including related project/receipt cases on API37. Every expected unique method, installed APK hash, 563 frozen inputs, source archive, AVD configuration and original display/density/font scale was checked. Fresh controlled bilingual screenshots were reviewed and exposed a clipped long read-impact caption on API26. This remains an open visual defect; the existing semantic tests do not prove full caption visibility. Both existing AVDs upgraded in place without reset, reseed, resize, uninstall or data clear. No owner-phone credential fixture or production backend operation was used.

These local APKs deliberately use code 111 / `1.1.0-beta.8-qa` through explicit build overrides for owned-emulator upgrades; committed source still defaults to development code 108. They are not release assets and must never replace the retained original-key beta.7 code-110 artifacts. Correct the open footer defect and add stronger layout/native-input checks before dispatching the final feature head through full hosted Android/compatibility acceptance. Live worker identity, filesystem effects and actual isolated-server status transitions remain separate open audit work. Private source archives, logs and receipts stay outside Git.

## 7 November 2026 — readable status captions and safe dialog insets

The long multilingual read-impact caption was visibly clipped by the default tall rounded TextButton. A rectangular-bound probe passed unexpectedly; exact Material3 source and a stronger unchanged-application check confirmed glyph 0 lay outside the capsule outline. The form now uses a short structured multilingual caption with the full accessible label and explanatory body, full-width stacked actions, scrollable headings and safe-drawing insets for system bars/cutouts/IME. Focus/keyboard dismissal precedes read, confirm or cancel. Exact task ID, separate latest-impact grant, payloads, graph bounds, scope and durable one-send guards are unchanged.

The first corrected-layout build failed on a new test-only assertion chain; that failure is retained. The next required build passed577 JVM tests, but its full focused cohorts remain FAILED52/54 and95/97: two new bilingual checks each rejected a retained paragraph layout constraint. A bounded diagnostic measured a650px paragraph constraint,296px rendered caption and714px button. The final check tests actual glyphs against clipped caption bounds and the capsule, height overflow and line ellipsis. Its original rounded-glyph assertions are byte-identical to the check that caught the old defect. All other prior positive methods remain separately scoped evidence; they are not claimed as a clean final cohort.

The final safe-area source passed all four required Gradle tasks,577 JVM cases and the two affected bilingual/keyboard cases on each owned API26/API37 emulator. Real Android input reached Read with the IME open and Cancel after typing the exact task ID without granting a write; reading and cancelling sent no status change. The normal reviewed path retained its exact body, explicit grant, one write and receipt assertions. Ten fresh whole-device form/review/result/IME captures were inspected. Original signing keys, installed hashes,564 source inputs and original AVD geometry/configuration were checked; no owner-phone fixture, uninstall, data reset, reseed or resize was used. These code111/beta.8-qa APKs are local acceptance artifacts only. Full final feature-head hosted gates and hotfix reconciliation remain open.

Independent tooling commit f122a65 directs Kotlin daemon rendezvous files to the E work root and quotes paths with spaces. An actual new daemon path and explicit-JDK quoted-path probe passed; older default-directory use was discovered and is not retrospectively claimed to have been confined.
