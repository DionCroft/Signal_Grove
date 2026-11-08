# Signal Grove project state

The full audit goal remains active. Keep implementing verified chunks and pushing
regularly. The complete remaining scope is in
[the audit ledger](docs/AUDIT_EXECUTION_PLAN.md); earlier checkpoints and retained
failures are in [validation](docs/VALIDATION.md) and Git history.

## Current development checkpoint

The read-only native room browser has exact connection/actor/profile/home checks,
ticketed list/detail reads, opaque paging, explicit stale/unknown observations and
foreground lifecycle fencing. Standalone rooms can be installation-shared; the UI
explains this. Membership/context/grant/delivery controls are not implemented.

- Browser1a889d6:831 JVM,39 targeted Android,222 policy and immutable205-source checks.
- Actual standalone2e15439:831 JVM,48 Android including9 actual stable/main/prior
  pairings across three emulator profiles,229 policy. Every imported source byte
  is Git-bound. Each actual pairing has87 wire records,27 read RPCs,9 sockets and
  exactly0 admissions/workers in both profile databases; chats/room rows unchanged.
- Current shape correction:831 JVM/81 XML,both lint0 and27 existing native UI cases.
  The largest-font Back-label clipping is visually resolved. Only the room UI file
  changed among non-document inputs; the test APK is byte-identical to2e15439.

All652 frozen build inputs, original keys/preferences/geometry,28 legacy cache
files and the separate beta.8 assets match. See
[the room contract and scope](docs/contracts/2026-11-08-hosted-rooms.md).

The finite actual-room fixture uses unmodified auth/ticket/profile/room handlers
with background ASGI startup omitted. Two initial startup probes caught policy
metadata changes; their failures and exact snapshots remain retained. No
unchanged-row or permission assertion was relaxed. Full deployed gateway startup,
profile-owned forwarding, Pi/phone/TLS/inference and worker readiness remain
separate acceptance boundaries.

## Release and protected state

Development chunks are pushed to feature/fix branches. Main6b809e6 and the held
release branch207c6b4 stay fixed; the privacy branch remains277c89b. Do not merge,
reset, force-push or advance these refs as part of unrelated audit work.

The owner's production phone retains version111/beta.8 and its saved connection.
Project details loading is owner-confirmed. The pending Automatic-mode new-chat
check, fresh history retention and exact-source publication gates remain open.
The existing beta.8 release is a private draft; do not create another draft or
reupload unchanged assets. Its source excludes the unreleased canonical viewer
and rooms. Original keys/data and the offline speech preference remain protected.

## One exact next action

Build the next bounded actual owner-forwarding fixture: distinct verified profile
owners with same-ID rooms, selected-owner discovery, actor-filtered opaque paging
and installation-shared/handed-root merges. Use immutable official components,
owned synthetic storage/listeners, exact traces and zero admission/worker checks.
Do not infer profile-private copies from the shared standalone store or substitute
stubbed forwarding and call it actual-server conformance.

Also reconcile existing exact-head hosted runs from their retained handles and
raw artifact digests. Browser1a889d6 has successful verify/compatibility metadata
and running full device suites; raw acceptance is pending. Privacyd57 API26 has
exact1043-method raw acceptance; API37 is still live. No extra blind reruns.
Continue every other audit row after each coherent verified checkpoint.
