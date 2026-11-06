# Task status operations

Reviewed 6 November 2026 against official Hermes stable
`2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` and main
`90d6b98fd86d20981ee06ddd5b7d93e4320f26a4`. This is a public-source contract
review, not certification of a custom deployed plugin. No live task was changed.
The later main canary `26874afd73ea4f81c5ac5a5098e222750a337a82` was also
checked through the official GitHub connector: all five complete blob identities
in the table below still match. These are pinned observations, not a claim about
future moving refs.

The typed plan now has an internal status executor and one-use approval value.
It has no native form or user entry point yet. The required build passes575 JVM
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
start fingerprint. A claim hash is not verified process identity. Native controls
and their translated explanations still need implementation before exposure.

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

Still required: finish transitive/deployment review; native private
form/review/receipt routing; actual isolated
server transitions; full hosted acceptance and separately authorized live checks.
The first strict fixture incorrectly omitted the board GET's documented
`include_archived=true` argument. Its16 negative methods, zero positives and
startup ANR remain recorded; no native acceptance is claimed for that cohort.
The plan is not a substitute for any remaining check or complete feature parity.


The corrected internal executor now has 575 JVM and 67 API37 local HTTP/Room
regressions, including 23 status methods. Original failed cohorts remain recorded
in VALIDATION.md. Twenty-two status lifecycle/helper fingerprints plus four
request fields are watched against the preserved F0 baseline; all match reviewed
stable 2bd378e and main b2e4ded. This does not cover every workspace/dispatch helper
or prove deployed custom code. No native status control is exposed at this stage.
