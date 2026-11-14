# Native systemd stop-helper fixture

These are the exact three standalone fixture programs exercised on the actual
Linux/aarch64 user manager in the [accepted native check](../../../docs/contracts/2026-11-14-native-systemd-and-policy.md).
They are acceptance companions, not an installer or a service runner. They do
not invoke the SDK cleanup helper themselves. Do not run them from a shared
agent cgroup or point them at a live Hermes home.

A reviewed controller must create a unique transient user unit, a fresh private
credential-free PM-prepared home and an empty absolute evidence directory.
It must verify the complete SDK source/store identity, absent unit, real systemd
parent and dedicated cgroup before admitting exactly the fixture main and its
two children. In this directory the sibling filenames must stay `main.py`,
`sentinel.py` and `observer.py`.

The disposable unit uses `KillMode=process` so its two sentinels survive the
main process until the unchanged SDK's own cleanup command kills them. This
fixture setting is not a proposed production unit change. Its ExecStop is the
SDK-published launcher with `--run-module gateway.systemd_stop_mark`. Its two
ExecStopPost commands are that same launcher with
`--run-module gateway.cgroup_cleanup`, then the observer. Do not invoke the
reaper directly in a shell/shared cgroup. The controller must stop only its exact
owned unit once, reconcile any uncertain result and preserve evidence before
retrying anything.

`main.py` records PID, native Linux start ticks, real parent/cgroup and children;
on SIGTERM it captures the planned-stop marker. `sentinel.py` records its identity
and any SIGTERM, then waits. `observer.py` reads the real manager's command arrays,
requires CLD_EXITED/status 0 from both SDK helpers and checks exact marker start
ticks, manager parentage, exclusive cgroup membership and no surviving children.
It saves raw command arrays before assertions. Linux `/proc` field 22 is not an
epoch timestamp; its type and exact value are checked without conversion between
different clock domains.

Final acceptance also requires independently recovered raw output, reverified
source/native modes/PM facts, disappearance of the exact unit/cgroup/processes,
and conserved live services and existing permissions. See
[external runtime paths](../../../docs/EXTERNAL_PATHS.md) for the limited OS
runtime-metadata exception. A positive fixture does not authorize a live cutover.
