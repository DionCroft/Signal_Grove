# Optional native coordinator workspace reads

## Optional native timing edits for paused jobs

The edit methods use the authenticated owner connection and the
non-routing `expected_profile` assertion. All selectors require an
exact canonical `job_id`.

| Method | Additional fields | Result |
| --- | --- | --- |
| `signal_grove.cron.edit_prepare_v1` | None | Opaque catalogue `version`, current `timing` and `repeat` |
| `signal_grove.cron.edit_review_v1` | `expected_version`, `changes` | Current and proposed `timing` and `repeat`, reported profile time zone and `review_digest` |
| `signal_grove.cron.edit_apply_v1` | `expected_version`, `request_id`, `changes`, `review_digest` | Original ID, reviewed version, review digest and committed timing/repeat |
| `signal_grove.cron.edit_observe_v1` | Original `request_id` | The retained original edit receipt |

The changes object accepts schedule, timezone, catch-up and grace
minutes. One-time edits require an explicit UTC offset so the instant
cannot drift between review and commit. The job must already be
paused, disabled and free of a fire claim. The read-only review
uses the native schedule projection, including repeat-rule changes
when switching cadence. A client shows these effects and binds its
confirmation to the returned review digest. Apply recomputes the
same digest inside the lock with the current scope. An inherited time
zone is the zone reported by the selected profile, or null for server-local
time. The edit cannot activate or dispatch a job and cannot change its
prompt or workspace.

Only staged generations with the reviewed jobs hook support edits.
Stage it with `--expected-cron-jobs-sha256` and the existing exact
controls hash. The separate scheduler hash enables the archive hook.
A cross-process lock refusal stops before the timing edit. The native
job save stores the last 20 edit receipts per job. The same request
and expected version return the original retained receipt, and
conflicting reuse refuses. Use observation after a lost or uncertain
reply. An absent or expired receipt alone does not prove that the
edit was never made. A change to any job expires an earlier
review of the shared catalogue.
[Validation](../../docs/VALIDATION.md#strict-paused-schedule-editing-source).

## Optional native Run once receipts

The original request is addressed with `signal_grove.cron.run_once_observe_v1` and
`{expected_profile, job_id, request_id}`. The authenticated owner profile
must match `expected_profile`. The profile, job and request derive
one opaque native execution ID. The protected ledger lookup requires that
ID, the canonical job and the reserved Run once source. It returns the
profile, job, original request and exact execution status while the row
is retained. It can still read a receipt after the job is removed.

The optional `--expected-cron-executions-sha256` stages the reviewed native
execution-ID hook. The hook uses the existing SQLite transaction and
primary key. It reserves an out-of-schedule claim for the reviewed source.
A missing or expired receipt is insufficient to determine the outcome.
With both the jobs and executions hashes supplied, staging adds the
internal reviewed manual claim. It checks the job and receipt share the
profile, revalidates the caller's approval under the fire fence and jobs
lock, and reserves the original execution before saving the job claim.
A failed save leaves the original receipt available for reconciliation.
The normal tick defers the pending occurrence while the manual claim is live.
The manual settlement preserves pause, repeat, next occurrence and
automatic retry state.

The network surface currently exposes receipt observation. Callers of the
internal claim still need exact approval consumption and verified dispatch.
[Native claim validation](../../docs/VALIDATION.md#native-pause-preserving-run-once).
[Validation](../../docs/VALIDATION.md#native-original-run-once-receipts).

## Optional native scheduled output previews

`signal_grove.cron.output_v1` takes `{expected_profile, job_id, execution_id}`.
Use the same verified native owner socket as for execution receipts.
The execution must still exist under that canonical job ID in the
current profile's Linux ledger. The response returns the exact profile,
job and execution IDs, recorded status, `archive_state`, `limited` and
optional `text`. Archive states are `available`, `unlinked`, `missing`
and `changed`. Only available previews contain text.

On a reviewed, staged generation, provide
`--expected-scheduler-sha256` in addition to the controls hash when
previewing or applying the adapter. This adds an association under
Hermes' existing output writer fence **after** its normal archive save.
The controls-only installer still works. This extra hook checks the
archive bytes, writes owner-only identity and digest metadata, and
keeps up to 50 associations per job. The manifest lives under the
job's ordinary output directory and follows native removal. The reader
verifies the same file again before returning up to 16 KiB of UTF-8
from a linked archive of up to 512 KiB. Older, replaced, oversized or
removed archives can be unavailable. Unknown execution status remains
unknown even when output exists. Existing owner-only file permissions
and stock archive names and pruning are used. Arbitrary filesystem
paths, older timestamp guesses and unrelated gateway logs are outside
this method.

The installer validates both hashes and source anchors before writing
any module to the candidate. Apply to a staged runtime to review the
complete patched generation before activation. The installer has no
live transaction spanning the two source files. A partial staged
generation requires inspection before use.
[Validation](../../docs/VALIDATION.md#exact-scheduler-archive-association).

## Optional native scheduled execution receipts

`signal_grove.cron.executions_v1` accepts `{expected_profile, job_id, limit?}`
for one current job's latest attempts, or `{expected_profile, job_id, execution_id}`
for one exact receipt. Limits are 1 through 20. The execution ID is the
32-character lowercase hexadecimal value from the native ledger. The requested
profile must match the verified owner connection's current home. The field is
an assertion of scope. Generic Hermes profile routing is outside this contract.

On Linux the reader opens the protected current profile's scheduler catalogue
and SQLite ledger with bounded read-only access. A page requires an exact
unique catalogue job ID. An exact execution receipt remains addressable after
one-shot job removal while the ledger retains the matching job and execution ID.
The payload includes `profile`, `job_id`, `ledger`, `attempts` and `limited`.
Each attempt contains the durable ID, reported status and timestamps, delivery
code and an error-presence flag. A missing exact receipt returns NOT_FOUND.
An absent ledger on a valid job page is reported as absent. The read preserves
the scheduler's reported unknown outcomes. Stock output and log files need
their own exact execution association before they can be displayed for a run.

The source uses the same optional authority installation as the workspace
methods below. Linux ledger validation is tracked in
[Validation](../../docs/VALIDATION.md#authenticated-scheduled-execution-ledger-source).

**Native source gate accepted, 14 November:** exact-source 493 portable, 8 Linux
policy, 72 Windows provider and 119 CI protocol cases are verified; the protocol
cases are a portable subset. All native source hashes match committed LF bytes.
The optional binding remains undeployed and creates no policy/grant/service.
[Accepted evidence and next deployment boundary](../../docs/contracts/2026-11-14-protected-operator-policy.md).
The preparation records below retain their original pending states.

**Protected operator binding, 14 November:** operator_policy.py accepts only
explicit fresh grants and fixed server-owned routes. policy_file.py supplies
native no-follow owner-protected POSIX observations; Windows returns UNSUPPORTED.
All 493 portable cases pass. Eight native Linux methods and their strict gate are
prepared; hosted native acceptance remains pending. No operator module, live
policy/grant or deployment is created. See the
[configuration and acceptance contract](../../docs/contracts/2026-11-14-protected-operator-policy.md).

**Native deployment preflight, 14 November:** ten selected running Pi source
modules match immutable 3b; extension and operator modules are absent. Supported
plugin RPC supplies no native owner/transport context, so it cannot replace this
explicitly maintained source integration by trusting phone params. No deployment,
policy/grant change or restart occurred. See the
[implementation and recovery plan](../../docs/contracts/2026-11-14-plugin-refusal-and-native-plan.md).

**14 November update:** the owner approved the exact source fix; it is now applied
and passes 481 portable, 16 credential/socket and 18 authority functional checks.
The pristine SDK shutdown warning and separate Pi/Android acceptance remain open.
[Current source status and unchanged deployment boundary](../../docs/contracts/2026-11-14-approved-http-owner-binding.md).

The checkpoint below records the earlier proposal and its original approval hold.

This is an opt-in extension of Hermes' existing authenticated authority socket,
not another agent backend or public service. It forwards only registered-worker
prepare/list/text-preview reads. Windows handles and file contents remain on the
execution owner; the coordinator holds only the explicit reply in memory.

`native_adapter.py` declares two closed, versioned native method contracts:

- `signal_grove.workspaces.describe_v1`: `{device, profile?}`. A fresh private
  worker status checks this native actor/profile's enrollment. Only a ready
  response offers prepare/list/preview and its exact worker instance.
- `signal_grove.workspaces.read_v1`: `{device, request, profile?}`. Request has
  schema1, a 32-hex request_id, the reviewed worker_instance UUID, operation and
  registered project_id. List/preview additionally carry binding_token,
  relative_path and entry_token. The root directory uses empty path/null token.

Neither method accepts scope, principal, authority, root, host, command, key or
registration claims. The native dispatcher resolves the selected served profile;
the adapter then requires its authenticated host-owner subject, verified operator
gate/capability, matching authority instance and still-registered transport. It
never translates the ordinary API-key subject `api` into the machine owner.
Authority retirement, caller/profile/transport changes and current grant-revision
changes drop in-flight results. A host-scoped sign-in does not imply a worker
grant for every served profile.

The outer result is `{schema:1,state:"ok",payload:{...}}`, or a fixed
`{schema:1,state:"error",code,request_submitted}`. No exception text or contents
are logged by this extension. A missing/unavailable explicit operator module
answers UNAVAILABLE, with no worker call or ready capability. An unextended
Hermes runtime answers unknown_method. No broad upstream baseline waiver or
automatic fallback to coordinator storage is appropriate.

## Operator binding

The separately maintained `gateway.signal_grove_workspace_operator` module must
explicitly export a `CoordinatorReadService` named `service`, importing its types
from `gateway.signal_grove_workspaces.coordinator`. It supplies the enrolled
authority identifier, a fixed device-to-private-transport map and a fresh grant
callback. The callback must match the actual NativeReadContext subject and exact
profile home against protected enrollment policy, returning a ReadGrant with the
current policy revision and explicit registered project IDs. Missing, changed or
foreign policy refuses. The scope hash uses the same domain-separated canonical
JSON as the reviewed Windows enrollment; it is a context fence, not a credential.

`SingleCallSsh` accepts operator-created argv only. Reuse the existing verified
SSH alias/config, BatchMode, StrictHostKeyChecking and fixed worker RPC command;
no phone field may choose those values. Its stdout must use the existing helper
`{ok:true,result:{...}}` envelope exactly; raw/failed/extra-field wrappers refuse
without exposing helper details. Do not change the generic execution-job
transport's limits. This read-specific transport bounds input to 16 KiB, output
to 512 KiB (including the escaped broker envelope), stderr to 8 KiB and deadline
to at most 30 seconds. It handles replies above the old 64 KiB metadata limit.
Four in-flight reads maximum are admitted, without queuing a fifth. Failed or
cancelled SSH cleanup does not prove that remote work never ran. No implicit
retry, execution submission, project-content file, receipt cache or model call
is added. The Windows broker still owns request deduplication and lease expiry.

## Reviewed installation and acceptance

`install_adapter.py --runtime-root <reviewed-runtime>
--expected-controls-sha256 <exact-live-hash>` previews the two guarded source
anchors and module hashes. `--apply` is explicit; it stages the complete module
set before the source switch and preserves an existing extension for review.
It does not create operator policy, copy credentials, open a listener or restart
the gateway. Use the existing encrypted-recovery, idle-check and staged runtime
maintenance process before any production source change. Never reuse an
uncertain/terminal deployment receipt or blindly replay a request.

Portable tests run with `python -B -m unittest discover -s scripts/tests -v`.
With the reviewed Hermes v0.23.1 checkout and its dependency environment:

```text
python -I -B scripts/probe_workspace_coordinator.py --source-root <immutable-checkout> --work-root <fresh-approved-scratch>
```

The probe checks the exact clean commit, creates isolated profile databases and
an in-memory source adapter, and exercises the actual native authority and
contract classes. External network/execution and writes outside its new fixture
are denied. The worker reply and trusted constructor sign-in are synthetic;
this does not certify HTTP/PKCE authentication, production Pi policy, live worker
content, Android or full owner-runtime lifecycle. Actual production/native socket
and default/foreign-profile checks remain required before app integration.

The independently deployed Windows pipe and real Pi SSH proof are documented
in `docs/contracts/2026-11-12-persistent-owner-read-bridge.md`. NAS full 128-bit
file-identity support remains unresolved; UNSUPPORTED must remain explicit.
Phone files, the broader audit, physical/model gates and the signed downloadable
GitHub APK remain open. This source chunk does not publish a new application.

## Actual HTTP-owner investigation — 13 November 2026

The new opt-in --coordinator-http-reads mode on the owner-workspace probe uses
actual PKCE/refresh, issued tickets, TCP WebSocket and the official runner. With
the current unchanged adapter it reproduces a legitimate signed-in HTTP identity
that lacks native instance fields and is refused before worker access. A tested
proposal binds that identity to the identical current registry authority while
retaining every owner/operator/profile/transport and supplied-instance fence.
It is not applied: automatic approval review requires explicit authorization of
the persistent authentication change. No Pi policy/grant/deployment is included.

The disposable candidate passes 16 credential/socket cases and18 native authority cases; the
actual worker remains synthetic, and the original SDK exit warning remains
nonzero. The original12 constructor outcomes and all earlier failures are retained.
See the [exact review and proposed patch](../../docs/contracts/2026-11-13-http-operator-binding-review.md).
