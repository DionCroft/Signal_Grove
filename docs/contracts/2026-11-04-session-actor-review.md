# API control actor rename review — 2026-11-04

Reviewed main commit [`07274ac65438a0b62bb9fd64bf284e58b2fd8fc6`](https://github.com/NousResearch/hermes-agent/commit/07274ac65438a0b62bb9fd64bf284e58b2fd8fc6) against stable source `b200f5bc60e31588ba2cb60e288029c4a54ba5fa`. The active mobile baseline remains pinned to v0.22.6 commit `f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab`; its fingerprints and used-route/field lists are unchanged. Existing two reviewed variants remain intact.

## Exact API entrypoint change

[`gateway/platforms/api_server_authority_runs.py`](https://github.com/NousResearch/hermes-agent/blob/07274ac65438a0b62bb9fd64bf284e58b2fd8fc6/gateway/platforms/api_server_authority_runs.py) replaces `Principal` with `SessionActor` in its import and three actor constructors: `respond_run`, `stop_run` and `resolve_unknown_run`. A non-executing AST comparison proved that substituting only this identifier in the complete new module reproduces the old module AST. No executable statement, argument, scope, ownership/generation/prompt check or returned acknowledgement changed in those entrypoints.

[`gateway/session_contract.py`](https://github.com/NousResearch/hermes-agent/blob/07274ac65438a0b62bb9fd64bf284e58b2fd8fc6/gateway/session_contract.py) performs the same rename in the frozen dataclass and Protocol annotations. The complete module also has an identical normalized AST. Fields remain `subject`, `profile_id`, `capabilities` and `transport_id`; the protocol identifier and serialized receipt/session types remain unchanged. The native HTTP client does not import this Python type.

| Watched handler | Exact reviewed fingerprint |
| --- | --- |
| `authority:respond_run` | `4184a34230ffc7f0f80535fe18363db7d5b282a49f22046f1ecd5984c0bc3ca2` |
| `authority:stop_run` | `96dd2e811391dc13582abe7de6386e772b55fd44d98ccf7ee73e437c616ca860` |
| `authority:resolve_unknown_run` | `f944fb77ca52397d51ec4f0d65d1dd6f3d79b550df3b46305abbe9d730e44c5d` |

Only these three exact fingerprints are accepted, each with this commit and review reason. The checker does not normalize actor names, accept wildcards, rewrite baseline fingerprints or automatically approve later variants. An offline regression checks all three baseline/variant pairs and requires another review for a subsequent change to any one handler.

## Surrounding runtime changes and limits

The [source diff](https://github.com/NousResearch/hermes-agent/compare/b200f5bc60e31588ba2cb60e288029c4a54ba5fa...07274ac65438a0b62bb9fd64bf284e58b2fd8fc6) also moves settlement/delivery into separately tracked, shielded work and defers terminal media collection until delivery. `_publish_completion` itself has an unchanged AST, but surrounding recovery, retirement and delivery timing changed. Constructor normalization does not establish equivalent server runtime behavior.

The compatibility judgment is that these API entrypoint variants require no mobile request/ACK change: the app continues to await explicit durable run status/receipts, fence generations and preserve unknown outcomes without replay. Live official-host recovery, images and cancellation remain part of the active interoperability audit. Static fingerprints and fixture tests do not certify every transitive helper or deployed host. No upstream source was imported or executed for this review.
