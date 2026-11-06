# Dashboard authentication registry refactor — guarded source review

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

## Guarded source acceptance

The watcher now collects the original shared-engine source as well as the auth
wrapper. Its original 29 extracted handler fingerprints come from the verified F0
blob, extending the F0 contract without replacing any previous fingerprint.
Nineteen additional dependencies are selected: nine shared-engine methods/imports
and ten auth ownership helpers, bindings/imports. Exact E36 variants are separately
reviewed. New helper/binding absences remain permitted only at the original or
explicitly reviewed older pins.

Four former local implementations/imports use `reviewed_handler_replacements`:
`_target`, `threading`, `snapshot_registration` and `restore_registration`.
Each requires the complete reviewed auth-module source SHA-256
`bdc657b049497103863c5c49c0ceb6d98789b3a699ffda22ea2509c043cfd492`
and its mandatory watched replacement dependencies. A new local binding selects
replacement mode, which must be complete. A hybrid of original functions and new
bindings is rejected even when those individual fingerprints are known. A later
commit can reuse the reviewed mode only while the exact source/dependency bytes
match. There is no general missing-handler or changed-source allowance.

Nine additional policy regressions cover source identity, every missing/changed
replacement guard, original/refactored modes, unsafe hybrids, reintroduced
functions, changed scope helpers, malformed review evidence and real transitive
selection. All 157 policy tests pass. Existing mixed-baseline unit fixtures now
construct a complete reviewed mode; every introduced dependency is still removed
and rejected individually, including the affected retirement dependencies.

Fresh complete 158-module pinned inspections pass for stable 2bd378e (original
mode, no replacements) and main E36 (all four guarded replacements), using fresh
Git tree/blob identities and checked cached bytes. The report digests are:

- Stable: `dec24b41425089c196ec951b630db1985362a57c3bfaa63bee95fd8bfd069624`.
- Main: `ccdf8bfb7693c2395deef53e029432155ed32881e8e721ee2e708887108ae677`.

The original failed hosted run remains failed. These local inspections and the
eight real-module cases are separate evidence; a fresh hosted compatibility gate
is required for the accepted source commit. No HTTP/deployment acceptance follows.

The compatibility workflow now includes a `pinned-registry-modules` job. It
retrieves only the four reviewed public E36 files, then invokes the canonical
harness, which verifies their complete Git blobs before execution. The download
step's token is not provided to the following fixture step, and the fixture
clears its own environment before loading modules. Only the bounded result JSON
is retained as an artifact. YAML/embedded-Python syntax and source LF bytes were
checked locally; hosted execution is pending the fresh exact-head gate.

## Remaining checks

Run the fresh exact-head hosted compatibility gate and finish the outstanding
D45 Android API26/API37 suites. Resume private native Kanban status forms/receipts,
workspace/dispatch transitive coverage, actual-server transitions and every other
audit row. The public APK and main branch remain separate from this development
review.
