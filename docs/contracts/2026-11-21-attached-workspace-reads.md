# Followed Hermes session project reads

The coordinator can associate one followed Hermes session with a separately
enrolled Windows project. The protected policy owns this association. The
Hermes session keeps its own valid working directory on the coordinator. The
worker opens the registered checkout on its own device.

## Source and enrollment

The optional adapter was exercised on Hermes v0.23.1 at
`3b421578588243f920886e6fe79a6faa87ea026e`. The dispatcher can restore a
session when it receives a `session_id` argument. Attachment reads use
`attached_session_id` instead and require the same connection's existing
subscription and the matching live subscriber. The selected session's cwd,
revision and generation are read from the same durable row. The cwd must also
match its live native policy. A history-only or archived session is refused.
[Native dispatcher and follow handlers](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/gateway/session_controls.py#L290-L379).

Hermes checks workspace moves against a directory on its authority host.
The native workspace transaction advances the generation and updates the
persisted cwd. Each attached request uses the current revision and execution
generation. A move committed while a worker read is in flight discards the
reply. [Workspace preparation](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/gateway/session_mutation_policy.py#L37-L55) ·
[Workspace commit](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/hermes_state_mutation_prepared.py#L85-L99).

The existing protected operator policy accepts schema 2 with an additional
`attachments` list. Each record has exactly these fields.

| Field | Required value |
| --- | --- |
| `subject`, `profile_id`, `session_id` | The verified host-owner principal, exact served profile home and canonical session ID. This combination is unique. |
| `coordinator_cwd` | The exact absolute cwd confirmed by Hermes. |
| `device`, `project_id` | The specific enrolled worker and project already present in the matching grant. |
| `worker_root` | The exact registered absolute Windows root as returned by the worker's prepare operation. |

The operator configures the association through the existing owner-protected
file and fixed route. The legacy schema 1 continues to support the existing
worker methods. It has no followed-session associations.

## Wire contract

Both methods use the existing operator-capable, authenticated authority socket.
Arguments contain `attached_session_id`, `expected_revision`, and
`expected_generation`. The read method also takes a closed `request` object.

| Method | Request and result |
| --- | --- |
| `signal_grove.workspaces.describe_attachment_v1` | Reports readiness, the worker instance, the single project and the registered root. |
| `signal_grove.workspaces.read_attachment_v1` | The request takes `operation`, `request_id` (32 lowercase hexadecimal characters), and `worker_instance`. List and preview also take `binding_token`, `relative_path`, and `entry_token`. Prepare checks the actual worker root and returns a short-lived coordinator binding token. |

List starts with the empty relative path and a null entry token. Opening an
entry uses the relative path and token from the preceding listing. The held
binding is tied to the session, live subscription, authority, transport,
revision, execution generation, protected policy and worker instance. It expires
at or before the worker's binding. The service keeps the worker binding token in
memory and never returns it to the client. After a stale or expired result, a
fresh user-reviewed prepare is required. The existing closed error envelope
retains whether a worker request may have been submitted.

The Windows broker continues to enforce its original root, file identity,
limits and read-only operations. These handlers create no project admission or
worker execution. Deployment and Android-to-Pi-to-worker acceptance are
separate from the source and native tests in this checkpoint.
