# Dashboard authentication registry refactor — review in progress

The exact development source `d45a3e57fab98b80b2c16fff2f25eb3e82c06cbd`
passed hosted build/lint/JVM verification. Its full API26/API37 suites are still
running in [Android run 42274455789](https://github.com/DionCroft/Signal_Grove/actions/runs/42274455789).
The separate [compatibility run 42274564644](https://github.com/DionCroft/Signal_Grove/actions/runs/42274564644)
failed with exit 2 for five real used-handler changes. Stable remains clean at
`2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19`. Current main was
`e36d4e57359074d0f9e8ccc8981bca16b893ad7f`; this is a source-review result,
not evidence that the owner's deployed gateway has this change or an auth bug.

The main artifact ZIP digest is
`e94a9e2eaff4935b2c56350c83bb35d1d3190197289f238c7642d321a3597bad`;
the stable artifact ZIP digest is
`b8b7ef2a4bb53af8d842094aaee88919060d15b82d94b07a00781f837f99366a`.
Both archives and full reports are retained. No failed gate is waived.

## What moved

The [auth registry](https://github.com/NousResearch/hermes-agent/blob/e36d4e57359074d0f9e8ccc8981bca16b893ad7f/hermes_cli/dashboard_auth/registry.py)
now stores maps/lock and delegates target/snapshot/restore operations through the
[shared engine](https://github.com/NousResearch/hermes-agent/blob/e36d4e57359074d0f9e8ccc8981bca16b893ad7f/agent/provider_registry.py).
The detected changes are removal of the local `_target` function and `threading`
import, plus new `_lock`, `_providers` and `_scoped_providers` bindings. `_target`
exists as a bound-method assignment. Local registration/lookup/listing still
implement the auth-specific exact-name, duplicate and registration-order rules;
they do not use the shared engine's general overwrite/sorted-list APIs.

| Source | Verified complete Git blob |
| --- | --- |
| Current auth registry | `082f3731e315bc20f5331aec175af8bac34d17c5` |
| Current shared engine | `54d892ff2a6414915b2fff5a469b114c0c906770` |
| Shared engine at original F0 and stable 2bd | `af6280935e1ac3921c749afb243e8c4b67312d85` |
| Current constants | `32dbd53c9737545edea778df17eaec42c2581b56` |
| Current auth protocol | `b8e71461adf30b4814634cb3fbcfdf9b49488e33` |

The shared source already exists at F0; do not treat it as a missing-source
introduction. The retained old engine bytes match that original/stable blob.
Constants/protocol bytes retained from the earlier pinned checkout match current
main's complete Git blobs, and were used with the actual new registry/engine.

## Reproducible bounded behavior check

[scripts/compatibility_probe_dashboard_registry.py](../../scripts/compatibility_probe_dashboard_registry.py)
accepts a directory containing those four exact current files at their original
paths and a fresh separate output directory. It checks all Git blobs before
loading code, executes the already verified bytes, isolates home/appdata/temp
and denies network/process operations. It refuses optimized Python before
creating a fixture, because its assertions are acceptance checks.

```powershell
python -I -B scripts/compatibility_probe_dashboard_registry.py `
  --source-root E:\Temp\signal-grove\registry-source `
  --work-root E:\Temp\signal-grove\registry-check-1
```

The final canonical harness passed eight distinct cases: profile separation and
global fallback; exact names/duplicate rejection; global collisions; stale global
handles after rotation; exact-slot snapshot/restore; copied views and normalized
home identity; concurrent duplicate registration; and capability filters.
An explicit optimized-mode invocation was refused before fixture creation.
The code SHA-256 was
`a8ba4129a6a674a3a50b914d15c7cdd0c1d8d87b215ce76c5a1eb998346e5145`.
The final result SHA-256 was
`07f2680672a0d32d81d0a16b10d08285bf7fe67c7b97a6d1488cb44ab8eb4271`.
Earlier E-only/canonical harness iterations are retained as separate cohorts;
they are not summed into additional unique cases.

These are real modules loaded in isolated package containers. Parent startup
and authentication methods are deliberately not executed. Python audit hooks
are not an OS sandbox. No HTTP/login/native-session, actual provider, deployment,
owner-phone or full runtime acceptance is claimed.

## Required next work

The watcher still does not collect the shared engine. Extend its transitive
coverage and binding fingerprints, retaining the actual F0 source contract.
A retired local function/import may be accepted only under an explicitly
reviewed exact replacement source identity with mandatory replacement guards;
never make missing handlers generally acceptable. Add negative cases for missing
or changed replacement code, aliases, scope selection, snapshots and locking,
then compare the exact stable/main pins and run a fresh compatibility gate.
The original E36 failure remains failed. Continue private native Kanban status
forms/receipts, actual-server transitions and all other audit rows afterward.
