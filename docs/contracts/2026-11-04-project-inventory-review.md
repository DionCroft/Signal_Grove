# Authenticated stored project inventory — 4 November 2026

Signal Grove reads stored project metadata through the existing dashboard-native management bridge. This foundation implements `projects.list` and `projects.get`; reviewed project/folder/archive/activation mutations, Kanban and rooms remain subsequent integration work. Selecting a host project is not presented as changing a mobile conversation's working directory.

## Verified primary contracts

Official source was read as text/AST only and verified against fresh Git-tree/blob identities. The following three files have the same blobs at the original **f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab**, stable **b200f5bc60e31588ba2cb60e288029c4a54ba5fa**, and the inspected main sources:

| Source | Git blob |
| --- | --- |
| [contracts/projects_pets.py](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/tui_gateway/contracts/projects_pets.py) | `2f17d6b9cdc6fae78875454a9971f64f2f50ad67` |
| [methods_projects.py](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/tui_gateway/methods_projects.py) | `ca073cfeab027f41fe7d81cba503d9b18c1767dd` |
| [projects_db.py](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/hermes_cli/projects_db.py) | `39bfc043e85ce96ee74a27370d03daf5914424c6` |

The runtime profile wrapper is in [server.py](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/tui_gateway/server.py); deferred handler scoping/rebinding is in [method_ctx.py](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/tui_gateway/method_ctx.py). Native ticket minting is `POST /api/auth/ws-ticket` in [dashboard_auth/routes.py](https://github.com/NousResearch/hermes-agent/blob/b200f5bc60e31588ba2cb60e288029c4a54ba5fa/hermes_cli/dashboard_auth/routes.py). The browser's `api/webapp/socket-ticket` is a separate route and is not substituted here.

## Scope and effects

Every read requires an explicit review of the selected dashboard URL, management actor and profile. The inference bearer is separate. The private profile-home fingerprint is frozen from the verified profile catalog, checked before a fresh gateway ticket/socket and checked again after the result. Missing/duplicate/remapped profile locations fail before opening the gateway or prevent returning the read result. URL prefixes, ordinary TLS validation, native HTTP bearer authentication, one-use purpose subprotocol tickets and the existing bounded/no-reconnect socket behavior remain in use. Socket URLs contain neither an inference bearer nor a ticket query parameter.

`_projects_method` marks handlers with `HandlerRegistry.profile_scoped`; installation rebinds closures and wraps them through the server's complete profile runtime scope. Explicit missing profiles produce the host's typed error; no session/ticket/launch fallback is requested by the client. Project results do not echo a profile scope, so no synthetic `success/scoped` proof is invented. Current-source scope behavior, the selected profile/home mapping and configured authenticated host remain the trust boundary, rather than a cryptographic proof of each row's origin.

`projects.list {profile}` returns all stored `projects` and optional `active_id`, including archived entries. `projects.get {profile,id}` returns one `project`. Opening the host store can initialize/add legacy database columns; these actions do not issue a project CRUD mutation or request repository scanning/tree hydration. Host folder values are displayed as private host paths, never resolved as Android paths. Icon/color/board references remain plain selectable metadata; a board reference does not prove a board exists.

The store's timestamps are Unix seconds. Delete/archive do not automatically clear the active-project pointer, so an unmatched or archived active ID remains an explicit observation. Legacy primary-folder flags can exist without a top-level primary path; those values are preserved independently. The native client neither repairs these metadata states nor guesses another active project.

`get_project` resolves an exact ID first and then a lowercase slug. Detail reads therefore require the returned canonical ID to equal the requested ID. A slug fallback cannot silently display another project. Future writes need collision preflight, exact fresh reviews and explicit residual no-revision race limits; this read foundation is not a substitute for that work.

## Private projection and bounds

Decoded metadata stays in the current management view model. There is no raw JSON/history cache or project database on the phone. Errors and string rendering omit private field values. Lists/folders are immutable; names/descriptions/paths are cleared on cancellation, close, authentication failure and scope changes. Rendering checks the live URL/actor/profile/home and the exact current snapshot references, so an old raw state cannot revive a closed or foreign private view. Late cancelled responses cannot repopulate it.

Client resource limits are distinct from upstream schema limits: up to **500 projects**, **128 folders per project**, **512-character IDs/names**, **8192-character descriptions** and **4096-character host paths**, within the existing socket frame/aggregate limits. Oversized, duplicate or wrong-typed payloads fail as a whole; no truncated list is labelled complete. Unsupported/missing management profile-location metadata produces an explicit disabled-read explanation. These are client support boundaries, not advertised host limits.

## Compatibility coverage

The original baseline is extended using the original commit's verified source, preserving all pre-existing hashes and reviewed variants. Coverage now includes **136 source modules / 1791 selected handlers / 256 fields**, including distinct `_projects_method` list/get identities, response schemas, database lookup/home/time semantics, registry/profile binding and actual server module registration. In the existing source map, **gateway_runtime** is `tui_gateway/server.py`; **gateway_rpc** is the cron/tool handler module `tui_gateway/methods_tools.py`. The registry coverage is deliberately attached to the former.

Seven project-specific offline regressions cover repeated-underscore RPC identities, profile marker/rebinding loss, field/result binding drift, ID/slug/home/time changes, lost module registration, source-key provenance and missing/AST-only source inspection. Real transport/Compose tests and their exact device/build results are recorded in [validation](../VALIDATION.md#stored-host-project-inventory-and-canonical-details-2026-11-04). Static/fixture checks do not certify a live Hermes deployment or physical/OEM behavior.
