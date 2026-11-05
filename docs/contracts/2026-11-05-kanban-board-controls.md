# Reviewed host Kanban board controls

This development beta.5 chunk implements board create, display/default/project edits, directory archive/delete and shared current-pointer writes. Task writes, worker/dispatcher controls, event streaming, attachment bytes, pinning and board transfer remain subsequent work. Published beta.4 APKs are unchanged.

## Pinned primary contract

Reviewed official stable `v0.22.7` at `b200f5bc60e31588ba2cb60e288029c4a54ba5fa`: [dashboard plugin API](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/plugins/kanban/dashboard/plugin_api.py), [board filesystem/metadata helpers](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/hermes_cli/kanban_db_boards.py) and [shared storage/current-pointer helpers](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/hermes_cli/kanban_db.py). Sources are read/AST-parsed, never imported or executed. Existing plugin mount/profile/home gates remain covered by the [inspection contract](2026-11-05-kanban-inspection.md).

| Native action | Literal host request | Relevant effects |
| --- | --- | --- |
| Create | `POST /api/plugins/kanban/boards?profile=…` | Canonical slug, six explicit default fields and `switch:false`; metadata write and database initialization |
| Edit | `PATCH /api/plugins/kanban/boards/{slug}?profile=…` | Checked fields only; immutable slug; project resolution can seed the default directory |
| Archive | `DELETE /api/plugins/kanban/boards/{slug}?delete=false&profile=…` | Moves the board directory, leaves an archived metadata tombstone, may clear current pointer |
| Delete | `DELETE /api/plugins/kanban/boards/{slug}?delete=true&profile=…` | Removes the current board directory/tombstone, may clear current pointer |
| Set shared current | `POST /api/plugins/kanban/boards/{slug}/switch?profile=…` with `{}` | Persists shared host pointer; effective selection can still be overridden by host context/environment |

All requests retain the configured listener prefix, encoded selectors, header bearer credential, explicit profile, existing TLS checks and disabled redirects/retries. There is one mutation attempt after one atomic durable claim. No arbitrary RPC, provider call, task dispatch, local-task mutation or automatic chat-workspace adoption is added.

## Review and effect boundaries

Drafts, exact values, canonical project choices, paths and returned archive paths remain private in memory. Create needs a known non-archived default anchor and rejects every known live/archived slug. This is a preflight check, not compare-and-swap: upstream `create_board` can reuse a raced slug, overwrite metadata and clear an archived flag. A matching ACK/readback never proves a new board was created. There is no restore endpoint or create-as-restore control.

Edits distinguish omitted values from explicit empty values. Omission keeps metadata; empty name uses the host's derived display name; empty description/icon/color saves empty text; empty project/default directory clears the reference. On create, a blank directory can inherit a chosen project's primary path. On edit, setting a project with the directory omitted can seed its primary path; explicitly empty directory prevents that seeding. Clearing only the project keeps the existing directory. Host directory expansion/normalization remains unverified by Android.

Project selection requires its own reviewed full `projects.list` read, including archived records for namespace collision detection. Only live, unambiguous canonical IDs are offered. Immediately before the board write, the client rereads the full project inventory and checks the selected metadata/primary path/namespace and reported access home. It does not scan repositories or assume a changed project has been adopted by running work.

Default-board removal is disabled. Archived board metadata can be edited and its tombstone deleted; archived open/switch/rearchive controls are disabled with explanations. Deletion additionally requires the exact typed slug and a fresh unchecked approval. Every action reviews exact owner/profile/actor/slug/reported database path, affected fields and effects.

Archive moves contained databases/workspaces/logs/attachments; delete removes the current board directory. Neither stops a worker. Host path overrides may put actual board data elsewhere. Deleting an archived tombstone does not delete the earlier archived copy. Upstream can clear the current pointer before a later filesystem error, and metadata annotation can fail after an edit. Attempted HTTP failures therefore remain unknown; a status code alone is not evidence that nothing changed.

## Shared ledger and independent readback

Kanban storage is shared across profiles. New board action types participate in the exact-dashboard-URL instance barrier, receipt visibility and explicit local-review query. The access-home hash is stored as a value-free routing fence in the summary, while `projectHome` remains empty. Same-home project alias work blocks a new board claim; board pending work blocks affected management writes across profiles. Atomic Room transactions admit one winner for competing board/project claims. Unrelated ordinary work on another profile retains existing independence. Foreign pending rows are redacted again before rendering and do not expose actor, profile, private target or summary.

Preflight compares the complete reviewed board definitions/current pointer and default database anchor, allowing dynamic counts to advance. The host supplies no revision/CAS, so a later concurrent write remains possible. A parsed ACK is durably stored in a non-cancellable section before separately cancellable home/catalog readback. Unknown results never auto-replay. Cancellation before attempt is locally refused; cancellation after attempt keeps the unknown barrier. A valid ACK survives failed/remapped readback. A matching catalog yields only `configuration_observed`; receipts always leave `new_board_verified`, `file_erasure_verified`, `runtime_stop_verified` and `path_mapping_verified` false.

Settled summaries persist hashes, field names and structural booleans, without private board names, slugs, project IDs, descriptions, working directories or returned archive paths. Unknown outcomes require explicit local review and a separate instance approval; reading current state never silently clears that barrier. Closing/changing scope/cancelling drops private drafts and reports and invalidates frozen reviews, including stale raw-state rendering.

## Watch and validation

The additive AST watch selects four write routes, eighteen write/schema/helper handlers and fourteen request fields. Only two whole-schema fingerprints are new to the baseline; all earlier fingerprints, variants and the original baseline commit are retained. Every newly selected definition matches original F0 and stable B200. Five new mutation-based negative regressions cover route removal, request defaults/schema additions, partial-effect order, archive direction, current-pointer validation and canonical project resolution.

Required strict build/lint, 478 JVM tests, 111 Python checks and 162 focused native cases on each API 26/36/37 plus one same-pair larger-text landscape tablet case passed. Actual phone/tablet review/result captures were inspected; source hashes, original signers and restored geometry were verified. See [VALIDATION.md](../VALIDATION.md). The initial receipt-label Compose compilation failure is retained; it is not a passing build. Static/source/fixture checks do not certify a live deployment, filesystem erasure, runtime worker health or physical/OEM behavior.
