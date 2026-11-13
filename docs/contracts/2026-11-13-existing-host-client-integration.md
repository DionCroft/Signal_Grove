# Existing-host client integration review - 13 November 2026

## Reviewed change and actual scope

The previous [readiness helper proof](2026-11-13-fresh-workspace-readiness.md)
used a disposable client substitution. The complete installable wrapper now lives
in [host_integration.py](../../server_extensions/worker_workspaces/host_integration.py).
The integrator installs it beside the existing trusted worker as
`workspace_reads.py`, through the owner's reviewed maintenance procedure. It is
not a phone-facing authentication API or an automatically installed service.

The full existing-broker wrapper is now versioned and source-tested, replacing
the trial-only call substitution with a reproducible installation template.
Each fresh projection observation first revalidates the exact protected caller
policy; changed grants, foreign frame scope/project and malformed input refuse
before native admission. Uncertain submitted outcomes remain submitted, with no
retry. The SSH client reads only fixed protected local control metadata.

Frozen checks pass **481 policy / 15 runtime-model / 72 Windows native cases**.
Twelve new portable cases cover the full wrapper and pinned package validation;
one new native case loads the actual immutable package and completes prepare,
list and preview, then refuses a duplicate and a foreign project.

A fresh full-wrapper Pi trial passes all five calls against the unchanged
installed daemon, retaining registry/policy, owner/fixture, maintained sources,
eight receipt rows and Pi services/stamp. AST comparison preserves the old host,
policy/loader, server class, projection/parser and adapter. This supports a
client-only integration plan without restarting the existing daemon. It is
**not deployed** and does not establish native HTTP/PKCE/WebSocket operator
binding, Android project/file access, NAS or final phone/release acceptance.

## Package and validation binding

The template pins the manifest's exact seven module hashes, then verifies all
files on every runtime load, even if their Python modules are already cached.
Use Git/LF-normalized module bytes when building this immutable package; reject
symlinks, unexpected manifests and byte drift. The standalone wrapper is outside
that package and must be reviewed and hash-bound separately. Never point it at
an arbitrary package, loader or user-supplied filesystem path.

| Item | SHA-256 / result |
| --- | --- |
| Pinned component digest | `ec08e8677e071a8b62723f3b5ac6702651f683dd8c1095b9065faf09faacc4ab` |
| Full template bytes | `aeab53948ff29a4dbbe06008702dce80afccd4be90f1315d7b5df65b48c4215c` |
| Local package archive, 21,197 bytes | `929b2b36d68fed44e3cf4e9eecfbad19002d7b29bfd8f2d4df51110f279c70f7` |
| Frozen portable log, 481 positives | `281156ee0afb64977acbfd61c7e955003e9519e0307990a3ad9638a599d489dd` |
| Frozen runtime-model log, 15 positives | `36ac0182aac6390f8de2d4a4dcbb011b3deaa72df7d0bc473f541785b55cba87` |
| Strict native log, 72 positives | `704f7d95bb3799ee1d9a4a5e189978a000481180814688525947bf9cc835ecbf` |
| Full-wrapper Pi trial record | `1fe7499b01b2850766212ed2b270fc6f4c37aaa7f81c8385a0c644ba31d16316` |

The local frozen gate binds 906 source inputs with zero failures/errors/skips
and no changes during execution. The Pi proof uses the exact complete template
and package; its staging entry selects the existing protected control-file root
after the template verifies that package. It replaces no package policy,
projection, native I/O or RPC function. Exactly five calls comprise status,
prepare, list, preview and deliberate duplicate refusal, with three fresh IDs.
The duplicate returns no cached content. Foreign subject and profile are denied
before SSH. This is an explicit administration context, not real app login.

The public coordinator modules are unchanged from parent `7a0c53c6`.
The daemon's existing immutable package stays loaded; no job, source, grant,
credential, model, gateway, project file or Pi service is modified. The fixture
returns seven entries and a 302-byte preview with its original verified hash.

## Concrete component-only integration and recovery plan

1. Refresh host/daemon PID and full creation time, scheduled task configuration,
   exact maintained worker/helper hashes, active owner, grant/registry hashes,
   read instance and receipt states. Abort on unexpected drift, maintenance,
   uncertain work or changed authority. Do not replay earlier unknown requests.
2. Create and decrypt-verify the required fresh encrypted recovery backup and
   bind it to those exact references. Include the current wrapper, manifests and
   required runtime bytes; keep state and credentials out of public source.
3. Stage and independently verify the immutable package on the execution owner,
   preserving its protected ACLs. Retain the old package. Review the exact
   wrapper diff and replace only that file atomically. Do not stop/restart the
   daemon, change its task or restore an older maintained worker snapshot.
4. Verify a new ordinary RPC client loads the new pinned package while the same
   existing daemon PID/start time/read instance remains ready. Complete a newly
   identified read-only Pi trial through the normal helper, preserving each
   failed or uncertain outcome. Verify adjacent queue/health/source/ACL checks
   and the required final encrypted backup.
5. If the client update fails, atomically restore only the original wrapper's
   verified bytes. Retain both packages and exact receipts. Never restart to
   erase uncertainty or blindly replay an admitted read. A later deliberately
   reviewed normal daemon restart would use the new package and retire old
   process-local leases; it is not part of this client-only plan.

No live installation or new backup is claimed by this checkpoint. Static AST
and source review plus the actual staged-wrapper/old-server trial support the
plan. They do not guarantee future concurrency, filesystem availability or a
third party's deployment behavior.

## Remaining acceptance

The full [goal](../GOAL.md) and [audit ledger](../AUDIT_EXECUTION_PLAN.md) remain
active. Production native actor/profile/operator binding and translated Android
workspace adoption, worker/root/repository/file/NAS access still need actual
acceptance. Automation/integrations, upstream semantic/shutdown review and the
original API26-cause questions remain. Current-head CI and physical production
upgrade/live/offline checks remain separate from the complete c814 standard
gate (1,141 API26 / 1,142 API37 cases, excluding 12 external-model methods).
Beta.9 and its public APK, signing identity and assets are unchanged.

## Hosted portable-fixture correction

Hosted Windows-workspace workflow43516959720 at source9d1844 failed its Linux
protocol prerequisite: the new package fixture accessed an unset `TEMP` and
raised KeyError before the native job could run. Its raw failure is preserved;
110 of 111 protocol cases passed. The correction uses the platform temporary
directory on non-Windows and retains mandatory explicit TEMP/non-C enforcement
on Windows. No wrapper, runtime package, policy, source guard or native assertion
changes. All 481 portable cases pass again on 907 frozen inputs; every input of
the previously passing 72-case native gate is byte-identical. That native suite
was not rerun. At the initial correction checkpoint, hosted acceptance was pending. The
subsequent corrected-source workflow43517488808 now passes 111 Linux protocol
and 72 distinct native Windows cases at source356d57c. Its raw native artifact
matches the exact commit and complete local native inventory, with zero failures,
errors or skips; every recorded native source hash matches its Git checkout.
The failed source9d job remains failed and preserved. This adds hosted bridge
acceptance, not app-auth/phone/NAS or full Hermes compatibility acceptance. No live component or release update is performed.

The corrected portable log SHA-256 is `f70dc72542e13ee303708698ed9375c28b970c9ec48613070add162d0d1f576e`.
The original [failed protocol job](https://github.com/DionCroft/Signal_Grove/actions/runs/43516959720/job/132201251136)
is retained as failed, with the native job skipped by its prerequisite.

Corrected-source hosted evidence: [workflow43517488808](https://github.com/DionCroft/Signal_Grove/actions/runs/43517488808),
artifact14081860601 (5,944 bytes), archive SHA-256
`ff4fa18b6bbde1d8d0bc9d4338b0bc709d1768f4183cb8f37ce99661f86fda86`,
raw native JSON SHA-256
`5e87c87a16343e1031562f8881e2dc275a0eb3628f9a812f542a1ed5713c578f`.
This follow-up changes documentation only; code inputs remain source356d57c.
