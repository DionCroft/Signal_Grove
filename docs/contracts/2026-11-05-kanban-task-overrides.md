# Independent Kanban task model/reasoning phases — 5 November 2026

Validated development foundation; native user-facing forms are a subsequent chunk. Existing project, task, dependency and approval controls remain separate.

## Primary sources

Original `f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab` and stable `b200f5bc60e31588ba2cb60e288029c4a54ba5fa` were fetched via GitHub, verified as Git blobs and read/parsed without execution. Selected API/database/transaction helpers match at both pins; no historical baseline hash or reviewed variant was replaced.

- [PATCH schema/handler and independent phase helpers](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/plugins/kanban/dashboard/plugin_api.py).
- [Model/reasoning normalization, storage, event and post-commit observer](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/hermes_cli/kanban_db.py).
- [Write transaction](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/hermes_cli/kanban_db_connect.py).

## One phase per request

The existing authenticated, explicitly profiled/board-routed `PATCH /api/plugins/kanban/tasks/{task_id}` supports:

| Client choice | Exact body | Effect |
| --- | --- | --- |
| Set model/provider | `model_override` and optional `provider_override` | Trimmed nonblank model; blank/omitted provider becomes NULL in this phase. |
| Clear model/provider | `clear_model_override: true` | Clears both, preserving reasoning. |
| Set reasoning | `reasoning_effort` | Normalized supported vocabulary; literal `none` pins thinking off. |
| Clear reasoning | `clear_reasoning_effort: true` | Inherits profile reasoning, preserving model/provider. |

No request combines either override phase with assignment, status, text, priority or the other override phase. Clear flags are separate explicit builders, not silent consequences of an empty model/effort editor. Model/provider are bounded to 512/256 characters; unsupported reasoning, controls/unpaired surrogates and blank models refuse locally. These are client bounds, not a claim that an ID exists or a provider/model supports the configured effort.

The host allows these overrides while running and applies them on the **next dispatch**; archived tasks refuse. This changes stored configuration, not loaded model weights, a current worker, the project's execution host, or the phone's active provider. Host races remain possible after observed preflight.

The helper commits the override and its event in one transaction, then calls `notify_task_updated` after commit. A failure, 400/404 or disconnected response after attempting cannot prove no effect. No request is automatically repeated.

## Nullable-field and acknowledgement boundary

The existing optional-string map discarded both missing and explicit-NULL fields. A separate private set now records which of the three override keys the host reported. Their values remain in the existing bounded private field map; only field-presence names are added. Other task consumers retain their normal optional projection.

An override request requires all three keys in the approved subject (including explicit NULLs). A valid exact-task ACK may still lack matching-effect evidence. Matching requires all three reported keys, the requested override values and the unchanged independent override phase. Thus a missing provider is not proof it was cleared; empty string is not NULL; `none` is not inherited reasoning. Changed/omitted readback fields never upgrade an ACK to confirmation. Matching state is an observation, not exclusive causation or future/runtime adoption.

Fresh catalog/default-anchor/task/home checks, shared pending barriers, one atomic Room claim, one attempted send and noncancellable ACK persistence before independent readback reuse the existing task repository. The generic receipt action registry now recognizes both phases. Durable records contain hashes, field names and booleans, not model/provider/reasoning values, task text or raw host errors. Runtime adoption, worker stop and filesystem proof flags remain false. No Room migration, new endpoint, dependency, signing or permission change is introduced.

## Source watch and acceptance

Seven newly selected helper/transaction fingerprints and five PATCH fields are additive. Previously watched override dispatch bindings, validators, transaction alias, observer and vocabulary remain intact. Synthetic AST drift tests cover clear/default/type changes, mixed phases, archived-guard removal, transaction redirection, post-commit observer ordering and removed helpers. Source-watch success does not certify a deployed runtime.

Twelve JVM regressions and ten new literal HTTP/Room test methods cover normalization, clear/none distinction, missing/NULL shape, independent races, exact identities, readback, privacy, cancellation/unknown/no replay and foreign pending work. Existing exhaustive task-action tests also include both new phases. Required strict build/lint, 511 JVM tests and 133 Python checks pass. All ten new and 89 related native methods pass on each owned API26/36/37, 297 same-artifact focused passes with no skips. Initial failures and their corrections remain recorded in [VALIDATION.md](../VALIDATION.md). Exact-head hosted gates and user-facing forms remain separate.
