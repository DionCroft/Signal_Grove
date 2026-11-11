# Reviewed Hermes-host workspace changes

In **Hermes management → Live sessions**, explicitly review/open a session,
reveal **Session workspace**, then choose **Change host directory**. Enter a
different absolute directory, review the exact host/profile/actor/session,
previous/requested paths and revision/generation, tick consent and confirm.
Cancel submits nothing. Known-running, missing/unusable-directory and unresolved-
receipt cases explain why the control is unavailable.

The directory belongs to the Hermes authority host. For a Pi-backed connection,
choose a directory on that Pi. This does not bind a chat to a Windows/NAS project,
verify a worker, browse its files or grant execution permissions. Host project
metadata and active-project pointers remain separate. Hermes verifies actor
control rights, idle state, revision and execution generation, and may normalize
the path and probe host-side Git. A running=false observation grants no authority.

## One approval, one send, durable outcome

The one-use approval binds the authenticated target, home/profile, resolved
session, instance, authority epoch, both fences and the previous/exact proposed
directory. A separately authenticated canonical socket verifies the same binding.
Identity/home checks precede an atomic Room claim. The request ID is the durable
local receipt key, and that claim exists before the single session.mutate send.
The change opens no conversation and submits no prompt, tool turn or worker work.

Only a correctly bound workspace ACK with both fences advanced once and a usable
absolute returned directory is projected. Its hashed-directory summary is saved
before later identity/home verification. It remains **acknowledged**: these checks
do not independently read the directory back or prove execution. The stale viewer
closes after a successful change under the already-reviewed last-viewer policy; a fresh attachment needs another explicit
review. A known ACK survives later verification failure. Missing/malformed/lost
ACKs and cancellation after a possible durable claim preserve uncertainty.

Uncertain operations block further writes in the existing exact-resource/shared-
home barrier, including aliases. There is no reconnect/retry/new-key replay.
Management receipts show the exact session and selectable mutation request ID.
Inspect that session, its backend receipt and directory before the separately
approved local uncertainty review. That review marks only the local barrier; it
never repeats, undoes or certifies execution. Paths, prompts, model/client secrets
and transcripts stay out of receipt summaries, logs and the helper's context.

## Review layout

The full-window form has a scrolling body, fixed actions, drawing/IME insets and
large translated captions. Pixel shapes prevent multiline action text from being
clipped. Status/navigation icon contrast follows the dialog surface. Reviewing
clears input focus and hides the keyboard before the read-only decision. The
native test waits for keyboard/decision bounds to settle before one Android
Cancel tap; it never retries that tap.

## Validation

The final application/test snapshot passes 950 unique JVM cases in 93 reports,
both APK assemblies and zero-error lint (50 debug / 49 release warnings). The
232 policy cases pass; their script and baseline inputs are byte-identical to
this final snapshot. Historical golden digests are preserved.

Fresh selected Android execution sets pass 30 methods on API 26 and 30 on API 37:
15 viewer/form/receipt cases, 10 native-auth/ticket/Room transport cases and five
checkpoint/storage cases per emulator. The large English/Chinese/pinyin form at
font scale 1.8 is checked against the actual native IME window and complete native
caption bounds, followed by one real Android Cancel input. The keyboard setting
is restored. Four final native captures are visually reviewed for complete
captions, host/session/exact-directory review and accessible actions. This is not
a full Android suite or physical-phone release proof.

The original debug signature, four packaged ABIs and native load alignment are
verified. No GGUF bytes are packaged. Frozen build digest: `557d8bd8ddea66e0d64cb210373984892c51c768f158f0838f646416893a1aaa`.
Application SHA-256: `ea3ccf85f406177bb70f70f508a8843718a6a855b1d3eba23d863779ce976280`.
Test SHA-256: `e529b0008ab2aad09b48abf267828970f819dde060b68a069dfbe9fd06e5ec52`.
Checkpoint documentation is recorded after these checks; runtime and fixture
inputs retain their measured hashes. No production APK publication is claimed.


## Real-source component proof

The tracked compatibility_probe_workspace.py supervises one fresh isolated E:
fixture using a clean immutable Hermes checkout at
`2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` and existing dependencies. It refuses
other source, a reused root and non-isolated Python startup. An observation
timeout preserves the exact child PID/root as unknown without killing/replaying.
A process-exit observer and later read-only SQLite checks retain late boundary
failures and verify store health and the one terminal bounded callback admission.

Eleven cases pass against the actual canonical dispatcher, authority, SQLite
mutation ledger and frozen policy: read-only actor refusal, active-admission busy
refusal, exact revision/generation advancement, agreement of live/saved terminal
CWD and stored row, session-info observation, exact receipt replay with no second
mutation, changed-payload conflict, stale revision, stale generation, missing
directory refusal and preserved system prompt. All 1,049 imported source modules
match their immutable Git blobs. The final exit boundary has zero denied events.
Only exact fixture Git probes are permitted; known metadata probes stay explicitly
blocked. Read/write/socket boundaries are Python audit hooks, not an OS sandbox.

The runner and actors are trusted synthetic fixtures and execution is a bounded
callback. This does not prove HTTP/native authentication to that real owner,
inference/tool execution, worker permissions, Pi deployment or full lifecycle.
The Android auth/ticket/Room checks use a separate mock backend; do not combine
these two proofs into an authenticated real-source end-to-end acceptance claim.
The earlier full-owner shutdown cleanup finding remains open.

Run with an existing Hermes interpreter using -I -S -B and the script's required
--source-root (clean E: checkout at the pin), --dependency-site (existing packages)
and --work-root (fresh E: directory) arguments. No installation or production
credentials are needed. The construction fixture's upstream MIT licence is
included under docs/licenses/hermes-workspace-fixture-MIT.txt.

## Source guards and retained findings

Ten newly consumed modules are Git-bound and statically parsed at the original
F0 pin plus stable 0.23.0/0.23.1. The contract checker now reads 215 sources and
watches their full ASTs plus the mutation request/ACK identity fields. Original
historical contract/scope/variant golden digests remain unchanged after excluding
only the new additive watches. No new handler variant or blanket acceptance is
added. Changed implementations still require review and runtime acceptance.

Retained attempts include the missing coroutine import and historical-projection
scope assertions; the receipt test initially expected English while enabling bilingual text, then explicitly fixed its locale; three debug HTTP fixtures stopped at the HTTPS guard before
being explicitly corrected; visual review found clipped rounded-button letters;
and the API 26 native Cancel check exposed the keyboard transition. Probe151
stopped on absent pytest before session work. Probe152/153 completed their 11
behavior cases but failed incidental metadata boundaries and remain failures;
read-only reconciliation preserves the terminal mutation state. Fresh-root probe156
and the final tracked/supervised probe157 pass. No failed/unknown root is replayed
or relabelled as a pass.

## Remaining acceptance

Complete authenticated real-source workspace transport and lost-result/permission/
busy/unknown-worker/profile recovery, fresh independent directory readback and the
supported/deployed version matrix. Then finish actual worker/project binding,
identity/scope discovery, repository/file access and reviewed dispatch. A Pi
workspace must not substitute for the Windows authoritative working copy.
Current-source physical-phone, native/background/reboot/helper, integration,
automation, all original audit and exact-source signed-release gates remain open.
No phone production APK, Pi/Spark service, server model/default or worker changes
are made by this chunk. This is development source, not an APK publication.
