# Optional native coordinator workspace reads

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
