# Retired Skill Hub auxiliary-model slot — 4 November 2026

Fresh main inspection at **ed1afb082eb3e46cf2edae9d8f68fb67b09ee4df** flagged exactly two existing used definitions: `_AUX_TASK_SLOTS` in [web_server_config.py](https://github.com/NousResearch/hermes-agent/blob/ed1afb082eb3e46cf2edae9d8f68fb67b09ee4df/hermes_cli/web_server_config.py), and the `auxiliary` defaults in [config_defaults.py](https://github.com/NousResearch/hermes-agent/blob/ed1afb082eb3e46cf2edae9d8f68fb67b09ee4df/hermes_cli/config_defaults.py). Stable B200 and this pinned main were read through fresh official Git-tree/blob identities, without importing or executing upstream code.

Main removes **skills_hub** from the built-in auxiliary task tuple and removes its `_aux(30)` default. Its explanatory comment says legacy `web_extract`, `session_search` and `skills_hub` auxiliary blocks are ignored. This retires a dedicated auxiliary LLM route; it does not remove Skill Hub discovery, scanning, installation or maintenance APIs. Other auxiliary routes and their field shapes are unchanged by these two definitions.

Signal Grove already reads task IDs from the authenticated host's task inventory, including declared plugin tasks. It has no static built-in task union that resurrects a removed slot. A frozen request compares the complete visible auxiliary snapshot during fresh preflight; retirement invalidates single-slot, bulk and global-reset reviews. Constructing/rebasing a request for a missing task is refused. A fresh remaining/plugin task list still supports reviewed bulk acknowledgment and separate configuration readback. Existing global-reset effects and unversioned concurrent-registration limitations remain explicit; no exact affected-list guarantee is invented.

The two `RetiredAuxiliarySlotTest` regressions pass inside the **429-case JVM suite**, with zero failures/errors/skips. They cover removed-slot invalidation and unsupported rebasing/selection, then remaining/plugin route acknowledgment and readback. No Android behavior or server configuration was changed to accept the retirement. In particular, this review does not delete legacy user configuration.

Only these exact normalized variants are admitted, pinned to that full commit:

| Definition | SHA-256 |
| --- | --- |
| `model_config:_AUX_TASK_SLOTS` | `d131792cd006022ed9005a1e8dd68ae5e338907f51e66fb38d0d5b71a52a37ab` |
| `model_defaults:auxiliary` | `980c2ffababb18766dcc49e20d5e857a99a455a5c06c0427fe88556a1b8b39c9` |

All prior baseline hashes and reviewed variants are preserved. An unrecognized next fingerprint still requests review. Static/fixture evidence does not certify an installed Hermes deployment, provider behavior or physical/OEM hardware.
