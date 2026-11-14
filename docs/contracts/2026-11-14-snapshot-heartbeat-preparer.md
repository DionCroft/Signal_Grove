# Whole-snapshot lifecycle derivative preparer

14 November 2026. The full goal and all four priorities remain active. This
developer source-preparation tool is default-off and performs no SDK execution,
dependency install or live activation.

## Exact admitted inputs and outputs

`scripts/hermes_snapshot_heartbeat.py` admits only two explicitly reviewed
whole-source maps. A caller-supplied hash cannot authorize another snapshot.

| Explicit base kind | Files | Canonical source-map SHA-256 |
| --- | ---: | --- |
| pristine-stable3b | 23,586 | `52a2dddf97a3fa39251a666fa8819f05b3185d249f9e14005f66247ba217fbf2` |
| maintained-stable3b-read-v1 | 23,628 | `1bc98a59131cdcc7b30d5d520cc88bffa27d0fbc22f6c4d6026738c0b9e05644` |

Pristine input additionally retains exact HEAD/index/working-byte/mode proof.
Maintained input retains every custom file through its accepted full map.
Both patch targets must equal their exact stable3b inputs. The existing reviewed
two-file patch has SHA-256
`21e42e06ebf1f1c7c2c6eda122a64894b76485d7b3a95c8640e164f057091190`.
The old pristine preparer and probes are unchanged.

The new SDK source is a fresh sibling of its base. That preserves relative
runtime/vendor meaning while every file outside gateway/run_shutdown.py and
tui_gateway/session_reaper.py remains byte-identical. Output/evidence roots
must be fresh, separate and ordinary; unsafe paths, duplicate map keys, extra
files, empty shadow directories, reparse points, a changed base and reused
partial roots refuse. Failed roots/receipts remain rather than being reset.

Actual POSIX preparation preserves every original file mode and all 124 exact
Git executable identities. Git's two replacement files are explicitly restored
to their original modes before verification, including under umask 077. The
strict mode comparison remains intact. The maintained database implementation,
pyproject, lock and provenance stamp remain unchanged.

## Source-preparation evidence and retained failures

The corrected actual Pi derivative verifies all 23,628 files and all modes.
Its complete source map is SHA-256
`e1af8f93ea6c094da8e6eb593926d3410568c333a874f7fb283a773f93e046a6`;
its full native source proof is SHA-256
`f54ecda040b80fe62c49400c0e8c7322885550adbe113dd79cced556d81b5879`.
Independent calculation from the frozen base and exact two output hashes agrees.
The relative tool-store target, package inputs, all maintained bytes, original
source, live services/stamp/facts and ancestor permissions remain unchanged.

The first native copy is retained: Git applied the correct two byte changes,
but restrictive umask made those files 0600 instead of their original 0644.
The guard refused it before SDK execution. Read-only reconciliation verifies
all its bytes and exactly those two mode differences; no old root is repaired
or reused. The corrected fresh copy preserves the original modes explicitly.

Twelve source-integrity counterexamples pass, including actual Git application
under a restrictive POSIX umask where available. The complete portable suite
passes **513 tests** (previous 501 plus these twelve). The first full-suite call
from outside the repo is retained: ten imports failed to resolve server_extensions.
The corrected invocation uses the documented repository root and passes; no
test/import assertion was removed.

Small policy fixtures do not establish SDK acceptance. The source preparer
executes no SDK payload. Separate native helper/owner/held-writer gates remain.
The initial Pi helper run completed eight assertions suites without test errors
but recorded 11 caught reads, so its whole gate remains rejected. A fresh run
records exact permitted native metadata and still refuses unknown paths.
It does not erase that prior failure or suppress the base's cleanup warning.

## Reproduction and remaining acceptance

Use existing absolute source/map roots from the reviewed commissioning record;
Windows source/tools/scratch stay on E:. These placeholders identify developer
inputs, not app settings or credentials:

```text
python -I -B scripts/hermes_snapshot_heartbeat.py prepare --base-kind maintained-stable3b-read-v1 --base-root BASE_SDK --source-map REVIEWED_MAP_JSON --source-root FRESH_SIBLING_SDK --evidence-root FRESH_EVIDENCE
python -I -B scripts/hermes_snapshot_heartbeat.py verify --base-kind maintained-stable3b-read-v1 --base-root BASE_SDK --source-map REVIEWED_MAP_JSON --source-root FRESH_SIBLING_SDK
```

The preparer rejects a different source map. Its printed proof is experimental
source provenance, never unmodified-upstream or deployed acceptance. No new PM
resolution, source default, policy/grant, permission, model, credential or APK
is authorized by running it.

Next: finish the actual Pi eight-helper gate, full native/HTTP finite exit and
actual held-SQLite close fence against this derivative. Keep all source hashes,
assertions, old failures and independent native exit/SQLite evidence. Applicable
canonical Linux regression coverage also remains; the earlier pristine six-RPC
positive and Windows fifty-case cohorts retain their exact independent scopes.
The frozen base's original heartbeat warning stays open until a supported fix
is adopted and verified. Live activation still needs its own exact review.

[Existing two-file lifecycle review](2026-11-13-heartbeat-lifecycle-candidate.md)
and [actual base Pi HTTP result](2026-11-14-actual-pi-owner-http.md).

## Subsequent native acceptance

The corrected actual Pi helper and normal/held owner gates are now independently
accepted within their finite experimental scopes. The earlier held/red entries
above remain historical. [Exact native results and remaining canonical/app-environment gates](2026-11-14-maintained-heartbeat-native.md). Live activation remains unapproved.
