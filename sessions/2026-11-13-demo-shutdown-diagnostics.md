# Demo and shutdown diagnostic checkpoint

The existing goal stays active. Original source 1180 shard1/2 passes 598 cases;
the hosted failure remains unreproduced/unfixed. Safe failure-only snapshots
preserve original interactions and assertions. Fresh exact-key diagnostic pair:
5 focused native passes,1,011 JVM/97 reports,422 policy + 15 runtime-model passes,
zero lint errors (51/50 warnings), all 4 ABIs/packaging verified.

Native owner default and trace modes retain the shutdown warning and exit 2;
20 seeded room checks still pass. Trace confirms DB close before late heartbeat
clear/reopen. Current static stable/main findings are 40/104;3 new handlers
triaged without waivers. No live SDK/server, model, credential, worker receipt,
physical phone or public APK changed.

Next: reconcile clean diagnostic-head hosted coverage/failure snapshots, then
test a real heartbeat lifecycle correction and resume remaining live/phone gates.
[Exact evidence and scope](../docs/contracts/2026-11-13-demo-shutdown-diagnostics.md).
