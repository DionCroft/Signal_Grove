# Native owner room reads and complete Android code freeze - 12 November 2026

## Real authenticated runtime, synthetic stored data

The final `scripts/compatibility_probe_owner_runtime.py` adds optional
`--seeded-room-reads` for exact stable Hermes
`3b421578588243f920886e6fe79a6faa87ea026e`. Existing immutable targets and the
original empty default remain. The actual GatewayRunner/bootstrap/ready path,
Windows current-user DACL/peer SID, native control tickets, canonical WebSockets,
dispatcher and strict wire contracts execute. No trusted Actor, fake dispatcher,
auth shortcut, descriptor substitution or production credential is supplied.
The documented no-platform/no-turn-warmup fixture limits remain; this is not
complete `start_gateway`, HTTP login, Android/Pi or deployed-fleet acceptance.

Each fresh isolated profile has six fixture rooms with 18 management-only rename
events: three readable owned/legacy-login rows interleaved with three foreign
rows. Both profiles deliberately reuse room IDs while keeping different names
and event contents; each also has a sibling-only ID. The source's single-user
owner comparison is used: a stored interactive-login stamp names the same machine
owner, not a second tenant or a general shared-room grant. Stored ownership data
is synthetic; the caller identity/ticket/transport are real native owner paths.
No user event, model/tool turn, room task or session admission is created.

Twenty exact distinct checks cover:

- `default_native_owner_pages_skip_foreign_and_include_legacy_login_owner`.
- `default_larger_page_limit_keeps_actor_visible_continuation`.
- `default_same_room_id_reads_only_selected_profile_state`.
- `default_foreign_private_room_state_and_log_refused`.
- `default_sibling_only_room_state_and_log_refused`.
- `default_sibling_profile_selector_refused_with_data_present`.
- `default_room_log_pages_are_exact_monotonic_and_tail_is_empty`.
- `default_invalid_room_and_log_pagination_refused`.
- `default_room_tables_and_ownership_rows_unchanged_by_reads`.
- `field_native_owner_pages_skip_foreign_and_include_legacy_login_owner`.
- `field_larger_page_limit_keeps_actor_visible_continuation`.
- `field_same_room_id_reads_only_selected_profile_state`.
- `field_foreign_private_room_state_and_log_refused`.
- `field_sibling_only_room_state_and_log_refused`.
- `field_sibling_profile_selector_refused_with_data_present`.
- `field_room_log_pages_are_exact_monotonic_and_tail_is_empty`.
- `field_invalid_room_and_log_pagination_refused`.
- `field_room_tables_and_ownership_rows_unchanged_by_reads`.
- `seeded_reads_schedule_no_room_tasks_or_session_admissions`.
- `both_profile_stores_preserved_after_all_seeded_clients_close`.

The snapshots include all `hosted_room*` tables and ownership metadata. Exact
before/after snapshots match. Independent post-exit read-only SQLite inspection
reproduces both snapshots and SHA-256 identities, checks 6 rooms/18 events/six
ownership rows per profile, zero sessions/admissions and healthy quick_check.
All successful captured responses exclude foreign room canaries and sibling-only
IDs. No private raw corpus, machine SID or machine path is committed.
All 1,207 loaded source records match their immutable Git blob and SHA-256 hashes.

## Preserved default, source and shutdown results

The exact final repository source also runs the original empty current-stable
mode: 1,206 imported source records, both profiles empty, genuine description/
profile/one-use-ticket/owner-loss checks retained. Both parents are invoked
directly by the observer, so their actual Python return code is observed rather
than inferred from PowerShell's status conversion:

| Mode | Seeded checks | Source records | Actual parent exit | Shutdown finding |
| --- | ---: | ---: | ---: | --- |
| original empty |0|1,206|2|one DB-reopen warning|
| seeded room reads |20|1,207|2|one DB-reopen warning|

The warning remains `CORE_PASS_SHUTDOWN_FINDING_OPEN`. Source inspection confirms
`tui_gateway.session_reaper._atexit_clear` clears its heartbeat after runner stop
has closed shared SessionDB handles, causing a reopen warning. No warning,
reaper, thread or assertion is suppressed. Clean/full lifecycle is unaccepted;
room-read scope passes independently. No immutable SDK source is patched.

The caller's verified short dependency alias is retained across the supervised
child and Python import path. The audit read root still resolves to the identical
installed dependency directory. Versions/permissions are unchanged; no package
operation occurs. An explicit early guard now refuses `-O` disabled assertions.
Four final-source preflight checks refuse before a supervisor/runtime starts:
disabled assertions, mutable source target, seeded mode on the unreviewed original
target, and an already existing output root. The latter's sentinel stays exact.
Portable 422/runtime 15/checksum policies pass again.

Final probe SHA-256: `1c3ccbc99c0717043f8bc5d72815e5b44db2f19ba474c67d0ced25bccc6a6787`.
Use the [original isolated owner procedure](2026-11-08-owner-runtime-bootstrap.md)
with an existing compatible Windows interpreter/dependencies, `-I -S -B`, exact
clean source, `--source-commit 3b421578588243f920886e6fe79a6faa87ea026e` and the
optional `--seeded-room-reads`. Use a short existing alias for an overly long
dependency path; it must resolve to the same canonical site. Each deliberate
trial requires a fresh E output root. Inspect the exact result and handle before
retrying; exit 2 is not full-lifecycle acceptance.

Preserved pilots are separate: 1065 fails before runtime startup on an existing
dependency's long Windows DLL path; 1066 passes its 20 room checks but the new
mode's final child guard still expects the original empty-state label. Read-only
post-exit stores remain healthy. 1067 corrects that accounting and retains the
shutdown finding. Final repository-source seeded/empty runs have accurate scope
metadata, exact source hash and observed return codes above. No old failure is
relabeled as a successful run.

## Complete Android code-freeze evidence

[Run 43277441323](https://github.com/DionCroft/Signal_Grove/actions/runs/43277441323)
is bound to 2970ffb1c73b788c3d95f0a35ae630cb7ec3c34a. All four native artifact
ZIPs and its summary ZIP independently match GitHub byte counts/digests. The
unmodified full verifier re-parses raw inventories and every start/positive finish,
checks identical APK bindings, matching inventories/disjoint exact unions and
reproduces the hosted summary exactly:

| API | Complete unique inventory/execution | Disjoint partitions |
| --- | ---: | --- |
| 26 | 1,135 | 537 + 598 |
| 37 | 1,136 | 538 + 598 |

This includes all three newly added workspace/outline cases. External model
acceptance remains explicitly false; the five-class/eleven-method model gates
retain separate physical/fixture evidence. No case is skipped or counted twice.
CI pair: debug `b3aa98b1c522bf595f02e88ea4ee2f1120cb829a9d25f97a0b2eb4915ca2ce75`;
test `bc9e4ae84228578d9ffc54f06db455b500f2b6a80a625149ca1b2861dfaf704c`. These are CI artifact identities, not a
new original-key production release.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
|android-device-26-shard-1|15441304|`229e527cdf43f496eaf009619805672e4170b9ca1a18550f0a0aaa6363c76460`|
|android-standard-suite-summary|1166|`f94028914851368cd65b4c9d4d36dc9e5fc36d0e8086c9e7e41c02cecaac5b47`|
|android-device-37-shard-1|16476792|`332ec6ce18c38e6927e36bc2dded548e9e2ea7834110c5e74d8f63290d7a4d2c`|
|android-device-37-shard-0|13743272|`ddd100cba22613831ea98dcb2b406f003f823d158314bb86cb052bbfbab529a8`|
|android-device-26-shard-0|11855837|`2fe91c8eff504ce51c105816da8342c5d75aad811b96ee95a9643d9182e698a9`|

The later auth/authority/owner Python probes and documentation leave all 543
frozen Android/native/build inputs unchanged. This extends relevant input
coverage, not the declared source identity of a build/artifact. The prepared
original-key production asset remains explicitly bound to f9e3835; no later
source label is retroactively attached. The old f9 failure stays retained.

## Remaining work

Prepare a fresh original-key beta.9 candidate from reviewed main and compare its
relevant inputs/signing/package/version/alignment/provenance before the physical
production upgrade/live connection/history checks and public APK delivery.
Continue the actual Pi/worker read path and current stable/main source-dependency
review while builds run. The 40/100 inspected used-handler findings are unwaived.
The SDK shutdown finding, actual HTTP/API actor versus owner, real Pi/TLS,
shared-room grants/forwarding/membership, actual project/root/NAS/worker effects
and every original [audit row](../AUDIT_EXECUTION_PLAN.md) remain required.
The phone, production app, models/history/settings, Pi/Spark and credentials are
unchanged. No new APK is published by this conformance checkpoint. Goal stays active.
