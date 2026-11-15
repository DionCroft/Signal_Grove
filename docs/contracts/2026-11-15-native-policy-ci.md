# Native policy CI fixture setup

Subsequent checkpoint: source8d01e9's full 1,015-method hosted policy suite
passes without skips; source7e1bd25's complete standard API26/API37 gate is now
independently verified at 1,142 / 1,143 methods. The original checkpoint below
retains its then-pending status. [Later raw/source proof and remaining scope](2026-11-15-native-serve-retirement.md).

15 November 2026. Hosted Ubuntu discovery was running controlled native tests
without their required finite user-manager unit and generated fixture inputs.
At source `59f43b3`, the parser/Android prerequisite fails before Android checks;
the original [parser run](https://github.com/DionCroft/Signal_Grove/actions/runs/43663463026)
and [Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/43663463033)
remain. The worker workflow passes. No failed run is relabelled successful.

## Source change

Both workflows now use `.github/actions/native-policy`. On a disposable GitHub-
hosted Linux runner only, it installs age/user-session support, starts that
runner account's user manager and creates a copied private Python interpreter
under `tools/`. Jeepney 0.9.0 is installed from its hash-pinned wheel. The local
driver itself uses an already prepared manager and never invokes sudo, installs
packages, repairs live permissions or changes Hermes services.

`scripts/ci_native_policy.py` creates a fresh private fixture root and exactly
one `signal-grove-acceptance-<operation>-pm.service`. It uses Type=exec,
RuntimeMaxSec=900, TimeoutStopSec=5, KillMode=control-group, Restart=no and
UMask=0077. The unit replaces its `env -i` process with isolated Python. Only
the explicit fixture environment is passed; runner/GitHub/Hermes credentials do
not enter test context. Tests retain all existing native owner, manager, cgroup,
program, journal and no-replay assertions. No new platform skip is introduced.

The driver independently observes the user-bus socket/peer, manager unique name
and native process incarnations. It supplies private PM/operational/transaction
roots and a generated age-encrypted ZIP after an exact decrypt/hash/ZIP proof.
The transport case is named for the operation it actually asserts: verified
ciphertext publication. Its provenance is explicit (`sdk-age` for the earlier
reviewed SDK fixture, `generated-age-protocol` for CI); reports cannot relabel
the generic fixture as SDK output. All previous byte/hash/native identity,
private-target, download, receipt and retained-source assertions remain. Actual
SDK backup creation retains its separate [97-method and real SDK trial](2026-11-15-backup-transaction.md).

The default mode discovers the complete policy suite. Before execution it saves
every method ID. Acceptance requires the exact planned/started/stopped/positive
method sequence with no duplicates, missing results, failures, errors, skips,
expected failures or unexpected successes. A missing/import-failed inventory
cannot pass. Six portable regressions include incomplete events, duplicate/empty
inventories, subtest failures and inherited-credential exclusion. `--native-only`
is an explicit 64-method controlled cohort for isolated component qualification;
its result is labelled and never establishes complete hosted policy acceptance.

Full stdout/stderr, inventory, result, manager observation and summary are retained
as explicit CI artifacts. Fixture private keys and homes are excluded. The parent
requires a zero unit exit, no restart, a stopped main process, an empty/absent
cgroup and exact positive results. A failure remains with its original directory,
unit and logs. There is no automatic rerun, replacement unit, reset or cleanup.

## Verified scope and preserved failures

The full Windows run passes **1,008 cases: 794 positive / 214 platform skips**.
The affected controlled native cohort passes all **64 methods without skips**
on Pi using the final driver, existing manager and generated keys/data. An
independent read-only review verifies every raw positive method, exact source,
manager authority, generated ciphertext/hash/ZIP and stopped unit. The SDK's
23,628 immutable source inputs, 41 caches, app/tool/support facts, seven real
modes and live service incarnations remain unchanged. This is control-plane
qualification, not a mobile-project working copy or live home on Pi.

The first Windows command used the wrong working directory: its ten workspace
import failures stay recorded; the corrected full run passes. The first native
driver exposed the same missing repository root in isolated Python: 14 PM cases
pass and 50 cases fail importing the policy package. Its failed unit is stopped,
its generated fixture is retained and all raw failures remain. The correction
adds the repository root explicitly; it changes no test assertion or production
adapter. A fresh operation then passes all 64 controlled methods. The private
syntax check initially found no installed YAML parser/pip; a SHA-256-verified
PyYAML 6.0.3 source archive supplies only its Python parser files in scratch.
All three changed YAML files parse. No package setup script runs.

Subsequent source `7e1bd25` passes the actual hosted composite action and all
1,008 policy methods without skips. Independent raw/artifact verification binds
the exact method union and stopped unit; the 15 runtime-model cases pass too.
Android build/JVM/lint passes, with four device shards still live. Upstream
compatibility and final release remain open. [Exact hosted proof and later policy
work](2026-11-15-auth-pm-source-fence.md). The full goal remains active.

## Exact evidence

| Artifact | SHA-256 |
| --- | --- |
| Native fixture driver | 3892d9cffa41e27b244bdbc74e72825c8e57266150fcb3cc17dabd5e0ffb659d |
| Positive-result and environment regressions | d08ba49f6df365d7dd798a31d15c4e412e57d810b39814663c4261d10c5bdc36 |
| Hosted runner preparation | 662d1f6537a963e7b564615cf3741ee3d2b2e156cf540f7a42bd8eb04afe0d34 |
| Explicit ciphertext provenance | 0ff568bd36fd53d5e53ac48f57de771ce2bc7b43ddee6f1c93be6a8e6e4e9978 |
| .github/workflows/android-ci.yml | e784968d44d5cc7ae3a690f1fa2fa77096342425d0b97a179d4508074972f0f6 |
| .github/workflows/hermes-compatibility.yml | 9ba14cf54275f880bc9a54046dd83f003bf9db0153e2e5be7b88bac5b03a01e8 |
| Independent native/raw/conservation review | e92defa51ae49f262e6826d547f61d0c13c55a73f10c6dafc6a63752ec236327 |
| Full Windows policy log | ab1417b2688d409500809a1812db68e1453ee65c8accb8a057b796507e14170c |
| Pinned-parser YAML review | 42ffca078e52e4c2020de29bd1a7b23932cb1671be9d1e10460143a432bd6a1d |

Private absolute paths, generated keys, fixture contents and full logs remain
outside Git. Android app code/build inputs and the published beta.9 APK are
unchanged. This CI repair closes no historical API26 cause, upstream/transitive
or shutdown review, live backup/cutover, project/workspace/worker/NAS, automation,
integration, phone/local-AI, security or original-key release requirement.

[Full goal](../GOAL.md) · [Audit ledger](../AUDIT_EXECUTION_PLAN.md).
