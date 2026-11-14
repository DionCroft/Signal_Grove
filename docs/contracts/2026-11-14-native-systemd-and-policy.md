# Native systemd helpers and private policy proposal

14 November 2026. Approved isolated staging now has independently verified
positive native stop-helper and policy-schema evidence. The live gateway, serve
process, policies, grants, directory modes, models, credentials and APK remain
unchanged. The complete audit and its four explicit priorities stay active.

## Actual systemd-owned stop and cleanup

A fresh SDK-PM-prepared home selects the reviewed bound source and its own
application environment on actual Linux/aarch64 Python 3.14.9. The real user
manager starts one uniquely named transient unit in its own cgroup. Readiness
verifies its systemd parent, native PID/start-tick identities and exactly three
members: one disposable main and two sentinel children. This is not parent/OS
simulation or a cleanup-helper call inside an agent/shared cgroup.

The unit invokes the unchanged SDK-published launcher for ExecStop
`gateway.systemd_stop_mark`, followed by ExecStopPost `gateway.cgroup_cleanup`
and a separate observer. The observer reads the actual manager's command arrays:
both SDK helpers have CLD_EXITED/status 0, exact argv and no ignored failure.
The main receives SIGTERM and captures the matching planned-stop PID and integer
Linux start ticks. The sentinels receive no SIGTERM and do not survive cleanup.
The observer is the only remaining member of that cgroup when it records success.

The controller makes exactly one start and one stop request. Both complete.
The transient definition, unit, cgroup and all exact owned process identities
are gone. No fixture credentials, config or session DB are created. An independent
read-only recovery authenticates the eleven original evidence files, all three
fixture programs and all five runtime-verifier modules. It recomputes the whole
23,628-input source/native-mode map and separately verifies 41 native bytecode
outputs. Existing services, PM facts, selected source and directory modes remain.
The [exact fixture sources](../../scripts/native/systemd_stop_fixture/README.md)
are committed; private runtime paths and routing identities are not published.

## Earlier failures retained

The initial busctl invocation rejected the payload's `-I` before creating a unit.
A later controller reused a source-map variable for fixture expectations and
failed before stopping its already identified unit. Its first recovery reader
misread the manager variant's JSON array and also made no stop request. That
unit's first actual stop eventually completed and removed its processes/cgroup;
its observer then failed by comparing native Linux start ticks to a psutil epoch.
Those failed receipts remain failed. No SDK source, callback or assertion is
weakened to pass. The corrected fresh check uses the actual D-Bus array shape
and the pinned SDK's integer Linux identity contract; its raw command arrays are
saved before assertions. The positive result belongs to that fresh check only.

## Private policy is validated, not activated

The exact candidate contains one already enrolled subject/profile/device and one
existing local validation-project grant. Four actual reviewed extension modules
load in isolated staging; native schema validation and the fixed SSH route match
fresh non-credential enrollment metadata. It makes zero worker/model requests.
Independent recovery verifies the candidate's complete bytes, single regular-file
link, owner and 0600 mode, plus the producer report and loaded source hashes.
The strict anchored reader still rejects the real unsafe ancestor permissions.
No bypass or weaker reader is introduced; the canonical policy path stays absent.
This is not a live HTTP owner authentication or phone workspace acceptance result.

## CI and remaining boundary

At source cea0cbf, [compatibility CI](https://github.com/DionCroft/Signal_Grove/actions/runs/43585003896)
verifies all 576 portable checks without skips and 15 runtime-model checks;
pinned registry/sidecar and the
[Windows worker run](https://github.com/DionCroft/Signal_Grove/actions/runs/43585003917)
pass. The upstream contract job remains failed and is not waived or rebaselined.
The [Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/43585004016)
passes build/unit/lint/instrumentation compilation; its API26/API37 device jobs
were still running at observation. No current-head complete device result or
new release is claimed.

The [review-only cutover packet](2026-11-14-runtime-cutover-packet.md) remains
uninstalled. Its full bounded executor/rollback and authenticated work-fence
review must be finished before asking for live approval. Canonical-home PM,
seven exact named mode changes, policy installation, fresh verified encrypted
backups and idle-only two-service cutover remain separate live actions. No such
action follows from isolated staging approval. Preserve unknown work and new
conversation data; runtime rollback must never restore old DBs or replay tasks.

Original API26 failure cause, full upstream used/transitive review, actual
project/workspace/worker/NAS/adoption, jobs/Kanban/rooms/integrations, phone/model
lifecycle and exact-source signed-production acceptance remain in the full ledger.
The successful isolated fixture does not close them.

## Evidence identities

| Evidence | SHA-256 |
| --- | --- |
| Independent native systemd review | b9434d51d9c45a011f928d5a4d71c4bdd016ac47ba14f34849a557d80faa196b |
| Native controller operation | 522c2c414cdb2af372f1d8b6148c1517c363e3a2a4b695e07879a2fa31dd53bd |
| Real manager raw command arrays | a3d9c93c149ef88991d4103c4404e05a07cc9482db6cdbe1bdf1b6b032b35b51 |
| Positive final observer | f08e748b7e6ac85a69c58a7221be665f03090761ac8fd4f771b30ffc997009f3 |
| Complete runtime inventory | a8f9ea9b72247474713bd970646a309558319b02a35f5779288a7a72784808f4 |
| Main fixture source | e47fc91cc1443d2fbdf352091d3f29df8de8e461f5bded3c1d47ad1bcb997222 |
| Sentinel fixture source | 9069d1ef767a41b1631f008325d1bef97bca22b4e811c623dbbbd5284ff305c8 |
| Final observer source | 471521a11d1ae8662ae952cf91d13d255907b09bb92372b01e9a8f0688370bb1 |
| Independent policy review | d07de85fc0e65b883bda0bdb7474af86d91ada08c75ebd51367c40ed605987cf |
| Native policy validation report | cea98b147cf7acd19073d305827ffd32957483a9f2701cbf87a2eb9ff88785c0 |
