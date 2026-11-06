# Authentication, plugin and profile guard review — 6 November 2026

This is a static compatibility review for Signal Grove beta.5. It accepts exact
normalized AST variants, not arbitrary new upstream behavior. The original
baseline and all previous fingerprints/variants remain intact. No upstream code
was imported or executed. Public sources were fetched from the official
`NousResearch/hermes-agent` repository, checked against complete pinned Git trees,
Git blob identity and local SHA-256; cached bytes were verified again.

## Sources

- original: [`f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab`](https://github.com/NousResearch/hermes-agent/tree/f0d94882e89ab8d4098b5bb2f166adfb9ee0a0ab).
- stable: [`2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19`](https://github.com/NousResearch/hermes-agent/tree/2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19).
- main: [`90d6b98fd86d20981ee06ddd5b7d93e4320f26a4`](https://github.com/NousResearch/hermes-agent/tree/90d6b98fd86d20981ee06ddd5b7d93e4320f26a4).

The original pin remains authoritative for original hashes. Helpers already
present at that pin gain their actual original fingerprints. Helpers introduced
later have only exact reviewed hashes; absence is allowed only at specifically
inspected older commits. Future disappearance or any unreviewed body still fails.

## Reviewed behavior

The new access helper accepts a listener-issued token or a deployment token
matched by the registered `session-token` provider. That provider rejects empty
tokens and uses a constant-time comparison against its provisioned startup token.
It requires explicit configuration to register, has no interactive login and
cannot refresh. Native refresh retains the older listener-issued-token rule.
Bearer authentication retains precedence. Cookie writes retain method/origin
refusal. The new session-token header is considered only without bearer/cookies,
still requires the registered provider and calls its verification method.
WebSocket verification keeps its provider check; the legacy in-process token
is not made a blanket credential. Provider registration, token loading, cookie
origin checking and their imports/bindings are now watched explicitly.

Plugin API loading extracts the existing import routine, preserving module
registration before execution and cleanup on exception. The caller still checks
enable/disable state, refuses project Python and routes applicable host-mode
plugins through their isolated host. The shared importer itself supplies no trust
policy. Host forwarding keeps the requesting-profile and secret-scope gates.

Plugin recall is checked before activation and before installation dependencies.
Enablement uses the exact discovered entry and directory rather than guessing an
ambiguous leaf name. Bundled exemptions remain explicit. Only a saved literal
`allow_removed is True` permits the update/reclone exception, whose default stays
false. Catalog matching and cache helpers are selected too. Cache-only enable
checks and stale-cache fallback are not a guarantee of fresh online revocation.

Secondary-profile listener mirrors require live listener states and scoped URLs.
Relay can mirror other runtime states but retains the profile's enablement and
configuration; another profile's shared runtime cannot override an opt-out.
Default/empty profiles and malformed runtime maps yield no borrowed mirror.

## Newly watched source modules

| Module | Official path | Original SHA-256 | Main SHA-256 |
| --- | --- | --- | --- |
| `web_signin` | `hermes_cli/webapp_sign_in.py` | `4c6065bd2d991d9506b505330cee9c1f70b7c0da2af618e4493d002b671cc020` | `b97f4ce71d5e9c8104026dd09071ae2a1e3b6645e1918d74ec38ab7a05d20008` |
| `plugin_isolation` | `hermes_cli/plugin_isolation.py` | `6c6a7efae92fb84918f8ffc07b561eafe0cd2b76b233be60d1dae984b5ba6377` | `34fba20ffed2a0e05763ba9f97b2b80873e0e3fa7e3786344c2bede5a593d829` |
| `signin_entry` | `hermes_cli/dashboard_auth/__init__.py` | `533c0dc638f74d2e149743d487e0b9c664980901f7b41a0565d6fc9bce29ea08` | `533c0dc638f74d2e149743d487e0b9c664980901f7b41a0565d6fc9bce29ea08` |
| `signin_registry` | `hermes_cli/dashboard_auth/registry.py` | `64f95fceb17febdbf34b75e7143419f9ffe2b5259a2aee8c8c23ae5fc4c73f33` | `64f95fceb17febdbf34b75e7143419f9ffe2b5259a2aee8c8c23ae5fc4c73f33` |
| `session_token_provider` | `plugins/dashboard_auth/session_token/__init__.py` | `349fb13221534682da8f39c8e16d2f0952e2b11e045f95d0e4d5787e1facb7cf` | `2c16eecbdade0838f98a8d1048a9ca40138aa8928a3bbcce918d1a8135693674` |
| `session_token_loader` | `hermes_cli/web_server_session_token.py` | `860fc60d3c2ed276ee259e7931261b190e6ac21ef8720986d6eb9b0e7c082b3a` | `860fc60d3c2ed276ee259e7931261b190e6ac21ef8720986d6eb9b0e7c082b3a` |
| `auth_request_utils` | `hermes_cli/dashboard_auth/request_utils.py` | `c1081abe0f960d274084d5c64c5a92c389cdade1c1a186239588ee4bdfb7fe88` | `29390aca8217e6003a78df933e8628ee2b27d6f33e3d84614b4a7eb475ebc809` |
| `signin_shared` | `plugins/dashboard_auth/_shared.py` | `fbd1dd5eae897717abc99762ab8b7b998355a2907114790eaf4a8d7ccd33d95a` | `fbd1dd5eae897717abc99762ab8b7b998355a2907114790eaf4a8d7ccd33d95a` |

## Exact accepted variants

| Handler/binding | Pin | Normalized AST SHA-256 |
| --- | --- | --- |
| `auth_middleware:_OTHER_PAGE_WRITE_DETAIL` | `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` | `13e06f8a9e819f2527c8e9b4bbc552ca046573848289daa68b81030b46b4a8d4` |
| `auth_middleware:_SESSION_TOKEN_HEADER` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `7222fbde134a11187ae93291cfd22b9e1fcfd13c21548a241efbe242386f34a1` |
| `auth_middleware:_SESSION_TOKEN_PROVIDER` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `6ff1350aae8cbcf0c878dd795a78019a6d00c14256a4f8179ca22844540bc160` |
| `auth_middleware:_cookie_write_refusal` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `c012a272955d4c62f09acd03afe6362aef05d215679880cead62540ed185b67a` |
| `auth_middleware:_session_token_header_session` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `fc1bbcb78b5a50e29d52cae99d03ec039c6c72620f77e7e5c8a2ca9153f332e6` |
| `auth_middleware:gated_auth_middleware` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `00a67c6a9723167a1da2c54ab067ce85a75d1990270aa089695435e56711ef34` |
| `auth_middleware:peek_session` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `11f01c71b8a48ceff94698b2858331fd40c98c3444df9c203535e09cab7aaf4e` |
| `channel_config:binding:SERVED_PROFILE_MIRROR_PLATFORMS` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `eb24f65ed1a26193115f53020afc4cee99464b34c1d73a34e4bdb26508df33ff` |
| `channel_served:served_profile_mirror_platforms` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `7d8582d109cadc3199cdde3411a2677852fc5b0e85bf20af180cd871d15ebf82` |
| `channel_status:shared_listener_mirror_platforms` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `76c18358af948afb740b76fb604aee7519c6399eee6c3fe78807156a05e3c7c8` |
| `channels:_messaging_platform_payload` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `b302b32b009f990061c5cf14ae093b2e1006e2a1acb284fd31bb3ed3d6b9c17c` |
| `channels:import:_LISTENER_MIRRORS` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `7cf6f8122bd582ca9012b2ce1937451b219c33405aac7a696714c8606befbe98` |
| `chat_auth:_ws_auth_reason` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `a7f627111bbd6aaa3f621dd1c1b50962fefe48e50dcce35691c8c1afe5cca129` |
| `kanban_plugin_mount:_mount_one_plugin_api` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `c335397c7af6de98161ab6bf63411b9581f836d194d8b9242883a7e2caa6bd98` |
| `plugin_core:_refuse_recalled_entry` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `c929857e7f1f600af89ae77d80c9a07acbc28f6ac6bb2dbebb9f4dee6552c9c6` |
| `plugin_core:_set_plugin_enabled` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `24e50b6a0f797541b619eb5b2d36f8fec3d6d5e50c8f52fedae39478ac40bcbf` |
| `plugin_core:dashboard_set_agent_plugin_enabled` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `2e2f8240fc5a10eda72f5f24c123ba9d8a840dfd44bd6ca7618e48ac011f0bc1` |
| `plugin_install:_enable_placed` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `f5a2b04d44395a37df36f859c5bf590f95647bb49edb7dcfcff564bc709a6554` |
| `plugin_install:_install_plugin_core` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `0a8d8d65662c255bf20a7a9de1dc628556fa7623057bf12b24f8297a3b5dd4bf` |
| `plugin_isolation:import:ModuleType` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `b343da3cbee5fdc20b13884c8a91391bd41328254a5e91998e459868b1b0cd1c` |
| `plugin_isolation:import:importlib.util` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `6586fdc68f0fb27b16fc9a680b00a5ace1246e314af6c74c90b8186d4b024162` |
| `plugin_isolation:import:sys` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `1947f6c361a4c14f35d9226aa71b6a657f31cb02298118a273d8f89b6cfc5097` |
| `plugin_isolation:import_user_code` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `4966a49ce4112153c161cb155cc4daf07f9ca9a71232110854f7a6c8b10358ee` |
| `plugin_update:_pull_plugin_update` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `120e2bacd7b19953ea2b0f370029f613ba47b0cb05817558343dcb99db424c89` |
| `plugin_update:_reclone_plugin_update` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `3b3c2cade6ea96be96868c0337bdc5238e2dd7079205552edcf72a5a9598d107` |
| `session_token_provider:SessionTokenProvider.matches_token` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `9406fef0f3992ef4fc93f6685676ebbdd0f41f749f663dcb040edc30c8f23d05` |
| `session_token_provider:SessionTokenProvider.verify_session` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `e80dc4c731c59b3f40374e2bef346310c32010752ce3bfd4ad4babce07b58e14` |
| `web_signin:_is_deployment_token` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `8ef6245d787b80c73ecacc532c6f7cf83dc051df857591f30daf994e895fae7a` |
| `web_signin:access_token_works_alone` | `90d6b98fd86d20981ee06ddd5b7d93e4320f26a4` | `5ea56862e4e9a91bfa9fea18c89896d91d9a591d5e8f7283625901e0ea148030` |

The review adds 182 selected dependencies and
29 exact variants. Synthetic negative tests cover
authentication precedence, constant-time/empty checks, provider identity, cookie
origins, importer cleanup, project/host policy, recall ordering and exemptions,
profile mirror scope, and exact introduced-helper absence. Both inspected pins
have no remaining used-contract drift. New API routes still require independent
feature triage. Static checks do not certify full transitive security, deployment
configuration, physical devices or interoperability with a live provider.
