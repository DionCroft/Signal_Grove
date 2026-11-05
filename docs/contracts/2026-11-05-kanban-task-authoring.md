# Kanban task authoring contract and implementation boundary

Reviewed against stable **v0.22.7 / b200f5bc60e31588ba2cb60e288029c4a54ba5fa**: [dashboard API](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/plugins/kanban/dashboard/plugin_api.py), [task database](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/hermes_cli/kanban_db.py), [initial dependency state](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/hermes_cli/kanban_db_graph.py), and [profile normalization](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/hermes_cli/profiles.py). Source is parsed/read, never executed. Git blob identities for the two additional source modules are verified at both the original F0 and stable B200 pins.

The foundation implements all eight typed data/repository/transport operations. A subsequent development chunk adds private task text, priority, assignment, comment and deletion forms with generation/scope-fenced view-model integration and localized exact-value reviews. Task creation and dependency forms still follow separately. Status/override/worker/dispatch/auxiliary actions, task file bytes, events, pinning and transfer remain further work. These development controls are not in the published beta.4 APK and do not complete the wider audit.

| Reviewed operation | Literal method and path under the configured dashboard prefix | Payload or query |
| --- | --- | --- |
| Create | POST `/api/plugins/kanban/tasks` | Typed `CreateTaskBody` fields; private frozen generated nonce |
| Text edit | PATCH `/api/plugins/kanban/tasks/{task_id}` | Only explicitly selected `title` and/or `body` |
| Priority | PATCH `/api/plugins/kanban/tasks/{task_id}` | Only signed integer `priority` |
| Assign/unassign | PATCH `/api/plugins/kanban/tasks/{task_id}` | Only `assignee`; empty string explicitly clears |
| Comment | POST `/api/plugins/kanban/tasks/{task_id}/comments` | Nonblank `body`, declared `author` |
| Add dependency | POST `/api/plugins/kanban/links` | Exact `parent_id` and `child_id` |
| Remove dependency | DELETE `/api/plugins/kanban/links` | Exact parent/child query, no body |
| Delete task | DELETE `/api/plugins/kanban/tasks/{task_id}` | No body |

Every request carries explicit selected access `profile` and board slug. Existing URL-prefix, segment encoding, TLS, bearer/cookie/origin isolation, authenticated identity and profile-home verification are reused. Task storage is shared across access profiles at the exact canonical dashboard address; the access profile is not an independent Kanban namespace.

## Frozen values and preflight

Create supports title/body, assignment/tenant/priority, scratch/dir/worktree selection and optional path, reviewed parent IDs, triage, positive runtime/goal turn caps, skills, goal mode, model/provider/reasoning overrides, and canonical project selection or explicit empty project opt-out. `completion_contract`, result/handoff metadata, branch, initial status and worker claims are not in `CreateTaskBody` and are never smuggled into the request. The private generated UUID is frozen once. The host checks its idempotency key before the transaction without a uniqueness constraint; neither that key nor a new-looking returned ID proves this request exclusively created a new row. No automatic replay or replacement key is offered.

The mobile bounds are explicit: title 4,096 characters, body/comment 262,144, path 4,096, tenant/skill/model 512, provider/profile 256/128, at most 128 distinct reviewed parents and 256 normalized skills, and positive signed-integer runtime/turn caps. Text rejects controls except newline/tab in multiline fields and rejects unpaired surrogates. Skills are trimmed/deduplicated and commas refused; the host remains authoritative about skill versus toolset names. Provider requires model. Reasoning preserves `none` as an explicit value, using the reviewed effort vocabulary. Assignee normalization trims and lowercases; the upstream helper does not itself enforce a filesystem-name grammar. Existing mobile profile/control-character bounds still apply.

The frozen board definition and default-board database anchor, fresh authenticated access/home, selected task's complete private detail and both endpoints of a dependency must still match before claiming. Creation parent cards must still match fresh details. Canonical project metadata/home and collision checks are repeated for explicit or inherited project links. Counts, observation timestamps and shared-current pointer are not revisions for an explicitly routed board. These are observations, not an atomic host CAS or physical-path identity proof; a host/client/worker can still race after preflight.

The host PATCH can commit assignment, status, override, priority and text phases separately. The client exposes one phase per request, with text/title sharing the host's one transaction. It never converts an attempted 400/409/404 into proof that nothing happened. A post-commit observer can fail after a write.

## Effects that approvals must state

Creation may produce ready work that an already running dispatcher can immediately pick up, including inherited parent tenant/notification subscriptions. Triage postpones dispatch without proving no worker can act later. Missing/open parents affect initial status; creation's initial-state rule requires `done`, while later dependency gating treats archived parents as terminal. A warning is a host report, not a dispatcher-health or execution proof.

A project-linked board can supply the project's canonical repository and default worktree. An explicit scratch kind or empty project ID opts out of inherited board linkage; an explicitly supplied project can still promote scratch to worktree when that project has a primary repository. The host may normalize or derive paths and branches. Exact path/runtime adoption is not proved by Android configuration readback.

Assignment can clear a failure streak, emit an observer event and make ready work dispatchable; a running claimed task can refuse. Priority/text edits record events and can invoke lifecycle observers after commit. Declared comment authors are metadata, not proof of the management actor's identity.

Linking refuses a running child through this route, self/cyclic/missing edges, may demote ready to todo, and inherits parent delivery subscriptions. Unlinking can recompute ready and release work to an existing dispatcher. Task deletion removes the task, links, comments, events, attempts and subscriptions in its transaction, then recomputes ready. It does not use task archival's worker-termination path. The selected helper does not establish erasure of attachment metadata/files or an active process. Neither task deletion nor dependency/status observation is worker-stop proof.

## Receipt and acknowledgement boundary

All eight actions join the exact-address shared pending barrier and verified-home project barrier, including the final Room transaction. Foreign pending history is projected as a value-free shared marker; settled foreign history stays private. One local claim permits one attempted send. Restart/cancellation/transport/HTTP/malformed-ACK uncertainty remains unknown and blocks another mutation until explicit local review. A cancellation before sending is distinguished from an attempted operation.

Durable records contain action, identity scopes, hashes, field names, counts and booleans. Task titles/body/comments, paths, skill/model/tenant/project values, warnings, returned task IDs, private nonce and host error text remain transient. ACK is persisted in a noncancellable section before independent readback. Readback failures or changed mapping/home preserve that ACK.

Task-returning ACKs must have a valid exact task ID for edits, with requested-value agreement assessed separately. Create always leaves `created_identity_verified=false`. Dependency removal with `ok=false` remains an acknowledged no-effect result. Delete must acknowledge the exact frozen task ID; later absence is configuration observation only. Comment ACK is only `{ok:true}` with no created comment ID: even a later identical comment with a new ID cannot identify this operation's specific record, so it is never upgraded to confirmed. Worker stop, file erasure, runtime adoption and path mapping remain unverified flags.

## Maintenance and validation

The additive source watch selects six write routes, 46 schema/helper/binding fingerprints and 26 request fields, plus original graph/profile-normalizer sources. All newly selected definitions match original F0 and stable B200. Existing baseline hashes, source identities and exact variants are preserved; no broad rebaseline or missing-source exception is added. Negative tests cover route/schema/default/nonce changes, PATCH ordering/error aliases, graph/deletion readiness effects and assignee normalization. Current validation and its retained initial failures are recorded separately in `docs/VALIDATION.md`.
