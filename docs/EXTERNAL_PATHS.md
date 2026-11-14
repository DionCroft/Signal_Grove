# External runtime paths

Signal Grove source stays in its authoritative checkout. Owner-host tools,
cache, builds, ordinary temporary material and private evidence use the owner's
chosen temporary drive. Device and server runtime paths follow their platform.

## Isolated Hermes native transport acceptance

Linux AF_UNIX sockets have a short native pathname limit. A deeply nested
approved Hermes candidate uses the SDK's existing XDG runtime fallback:
`/run/user/<uid>/hermes-gw-<fixture-home-hash>/control.sock`.
The current UID owns the existing nonsymlink XDG directory (0700), a new private
fixture directory (0700) and its socket (0600). Only IPC lives there. All fixture
homes, synthetic SQLite/state, source, caches, ordinary scratch, logs and
receipts remain inside the approved staged control-plane generation.

Source: exact reviewed Hermes `gateway/control_socket.py`; the owner-workspace
and coordinator-auth fixtures preserve actual native kernel UID, PKCE, refresh,
one-use tickets and WebSocket behavior. No platform is faked and no socket/auth
implementation is substituted. Worker routes and test login are explicitly
synthetic; no user-project content, real model or live worker is invoked.

To recreate: verify isolated-stage authorization, the whole source map and
unchanged live services/facts, then select a fresh fixture root. Derive the
path through the SDK's home hash and verify the entire private path is absent.
Admit only that exact Unix listener and the fixture's own ephemeral loopback
ports. Keep all failed/unknown receipts; do not reuse or blindly replay them.
Normal SDK teardown removes its own socket/pointer. Preserve any unknown node;
an empty newly created private directory can remain for OS-session cleanup.

The maintained fleet path registry records the same exception. It authorizes
no live activation, service restart, ancestor permission change, policy/grant
change, worker/project action, credential transfer or model change.


## Planned isolated systemd helper acceptance

Positive ExecStop/ExecStopPost acceptance requires the real systemd parent and
a dedicated cgroup. A fixture may use one uniquely named user transient unit,
`signal-grove-acceptance-<unique-id>.service`; the OS user manager owns its
runtime definition under `/run/user/<uid>/systemd/transient/` and its corresponding
kernel cgroup below `/sys/fs/cgroup/`. These are OS control metadata, not project
files. Source, native fixture homes/PM facts, sentinel readiness/identities,
logs and ordinary temporary files stay inside approved control-plane staging.

Before use, verify the named unit is absent and capture the current live service
identities. Start only the fixture's fixed command with its isolated credential-
free home. Verify actual manager parent, exact new cgroup and owned PID/start
identities before requesting that unit's stop. Never invoke the cgroup reaper in
an agent/shared cgroup, fake systemd parentage, touch production unit membership,
or replay an unknown run. Retain terminal/unknown evidence. Completion requires
the fixture's unit/processes gone and unchanged live services/source/facts.

Source/template: pinned `gateway/systemd_stop_mark.py`, `gateway/cgroup_cleanup.py`
and the reviewed renderer packet. This records a planned native test boundary;
it does not claim that such a unit has run or authorize a production cutover,
policy/grant, credential, model or existing-directory permission change.

The planned systemd boundary above has now been exercised and independently
verified in the [native stop-helper check](contracts/2026-11-14-native-systemd-and-policy.md).
Its exact unit/cgroup/processes are gone; all ordinary files remain in approved
staging. The [committed fixture companions](../scripts/native/systemd_stop_fixture/README.md)
record the test scope. No production permissions or service actions are implied.
