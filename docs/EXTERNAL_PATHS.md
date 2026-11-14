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
