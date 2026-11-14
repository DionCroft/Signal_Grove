# Actual native user-manager service adapter

This retains the initial source/evidence checkpoint. Failure-path review finds
and fixes a journal-owner replay gap in the [current one-use dispatch source](2026-11-14-one-use-native-dispatch.md).
Use that source and its 122-method Pi/764-case Windows evidence for further
acceptance; the initial happy lifecycle alone did not expose this boundary.
No live action uses the initial component.

14 November 2026. `scripts/native_service_manager.py` implements the concrete
Linux user-D-Bus start/stop component for the fixed cutover coordinator. Its
actual owned-service trial passes on the Pi. It has no live CLI/default approval,
and does not implement the remaining production PM, encrypted backup, native
admission, artifact publication, reload or live-user verification adapters.
No live Hermes service, launcher, policy, permission, credential or model changes.

## Native connection, authorization and outcomes

The adapter uses one persistent connection to the exact native user-bus socket,
checking its private parent, inode/ctime/UID, kernel SO_PEERCRED peer and manager
PID/start-ticks/boot identity. The user-manager's unique D-Bus name is pinned;
no replaceable well-known-name fallback, reconnect or environment bus lookup is
allowed. Only the two exact Hermes role names or a same-nonce owned acceptance
pair can bind. No arbitrary service, restart, kill, cancellation or reset API.

Local bounded filters and server subscriptions for each exact unit's JobRemoved
are established before dispatch on the same connection. Absolute owned-socket
watchdogs bound authentication/Hello and I/O; request/message/queue byte limits
hold on overflow instead of silently discarding evidence. Loaded command arrays
retain path, complete argv and ignore-failure flag; only runtime counters are
excluded from configuration identity. Environment/secret properties are never
requested, and errors do not echo arguments. Full unit/source-file preimages,
actual work fences and approval issuance remain the production caller's duty.

Every action requires the real exclusive journal's current pending stage,
durable authorization digest, exact fixed verified prefix, packet/authority/
scope/operation and unexpired deadline. Native snapshots double-check current
PID/incarnation/invocation/job, loaded configuration, executable and cgroup.
Forward stops require the reviewed old configuration; forward starts require
the new configuration already loaded before dispatch. Separately approved
recovery starts require old configuration; recovery stops bind exact reviewed
old/new executable pairings. Drift refuses before a service command is sent.

Each action becomes spent before dispatch; mode `fail` refuses job conflicts.
RefUnit leases preserve an inactive unit's loaded evidence until the connection
closes. An asynchronous job ACK never grants success: the exact job-path/ID,
unit and `done` JobRemoved must correlate. A stop also needs inactive/PID0,
successful stop/post-stop helpers, ended original incarnation and empty/absent
cgroup. A start needs a fresh invocation/owner and reviewed configuration/exe.
Uncertainty closes only the owned connection and holds without retries,
cancelling the job or automatically rolling back.

The native D-Bus behavior follows the documented
[systemd manager contract](https://www.freedesktop.org/software/systemd/man/latest/org.freedesktop.systemd1.html)
and [Jeepney blocking connection API](https://jeepney.readthedocs.io/en/latest/api/blocking.html).

## Supported isolated side environment

Supported Hermes PM builds a separate hash-pinned Jeepney 0.9.0 environment.
It installs only Jeepney, without keyring/pytest or modifying the app/tool
selection. Independent comparison against the exact pinned 49,010-byte wheel
verifies all 30 installed package files and 29 PM-generated caches, including
cache headers and compiled-code/source identity. The wheel SHA-256 is
`97e5714520c16fc0a45695e5365a2e11b81ea79bba796e26f9f1d178cb182683`.

The first post-build wrapper incorrectly expected zero generated caches and
failed after PM succeeded. That failure is preserved; the build is not replayed.
Read-only recovery proves the original process ended, identifies the selected
generation and independently verifies actual source/cache content. Application
and tool facts and all live references remain unchanged. This is isolated side
readiness, not canonical-home old-stop/new-start PM acceptance.

## Actual verification and its limits

All **23 boundary methods run on the actual Pi, with no skips**. Two Linux
AF_UNIX watchdog cases retain their strict sub-second assertion. The initial
Windows socketpair experiment took 120.05 seconds and failed that assertion;
the source constructor is Linux-only, so the tests now require actual Linux
AF_UNIX behavior. The failure is retained and the assertion is not loosened.

`scripts/native/service_manager_fixture.py` refuses live Hermes names. It
connects/subscribes before a controller creates two exact owned transient
services with finite 90-second payloads and real ExecStop/ExecStopPost helpers.
The real adapter stops both, starts both with new incarnations, then performs a
new explicit owned cleanup operation to stop both. Six distinct job completions
and six refusals of a spent action verify. All eight main/child incarnations end,
all eight helper executions succeed, the original cgroups have no remaining
processes, both transient units unload and native files/journal hashes verify.

Non-service prerequisites in this fixture are explicitly synthetic journal
rows. It does not prove real PM/backups/admission fences, a full coordinator
cutover, reload/publication, actual live-user authentication or production
failure/recovery acceptance. The happy native lifecycle is not fault injection;
those additional actual adapter/error-path gates remain before live deployment.

Independent read-only recovery checks all 23 named results, actual job/snapshot
and journal digests, terminal controller/payload incarnations, helper records,
unloaded units/cgroups and exact source bytes. It reconserves 23,628 SDK inputs,
41 SDK caches, all package inputs/caches, app/tool facts, seven ancestor modes
and live references. No native action is replayed during review.

The complete Windows Python suite runs **760 cases: 697 pass, 63 POSIX skips**.
The 58 coordinator/journal and two new watchdog platform methods actually pass
in their separate Pi cohorts; three older skips retain their original scope.
No Android/build inputs or published APK change. All original audit/on-device/
upstream/project/worker/NAS/integration/phone/release requirements remain active.

Next test actual bounded failure/unknown outcomes in owned fixtures and build
the remaining concrete adapters. Finish fresh work fences/backups, both canonical
PM paths and exact runtime-only recovery before separately reviewing live action.
[Runtime packet](2026-11-14-runtime-cutover-packet.md) ·
[Fixed coordinator](2026-11-14-fixed-cutover-coordinator.md).

| Evidence | SHA-256 |
| --- | --- |
| Adapter | `853aed3a92fbf132a2aabbee401b75ea819af728d31e0b6fd3427ffcf9a6a401` |
| Boundary tests | `b144c4755a1f577c5b45d36229ae557a988ab86715e40f2864739c7492bb4379` |
| Owned fixture | `6a91d001cd7497466537e1955a1418e673d328c779ee18d40ebc5078acbf87df` |
| Native terminal receipt | `707397517b6644e31bd281f6d91031e5ed6bcab11c825f9b89e9bd1f950fa90c` |
| Independent native review | `b5ab0c8037bda3ffb17c763f654267f1464c4628c86c656b833a79113ef44703` |
| Side-environment independent review | `d2ffd0610c1a75b7b1c3dd8d663ddf2e61ad881d10cb38658fb984f98719f74c` |
| Actual copied journal | `4fc7ec35045acc81d193272d90d81475e4757f722e9a76fd29419ce4005fbd35` |
| Full Windows suite | `f7e1eeca6c4c276cd4fc9aa639262116f1c9fc21f2e8dd3ced8892232785c979` |
