# Exact-pin heartbeat lifecycle candidate

13 November 2026. The existing full goal remains active. This is an experimental
upstream SDK correction and regression-tool chunk, not an Android feature,
live deployment, compatibility waiver or completed release gate.

## Correction and safe failure behavior

The reviewed patch targets only Hermes
`3b421578588243f920886e6fe79a6faa87ea026e`. It changes
`tui_gateway/session_reaper.py` and `gateway/run_shutdown.py`.
Patch SHA-256: `21e42e06ebf1f1c7c2c6eda122a64894b76485d7b3a95c8640e164f057091190`.
[Source, MIT attribution and reproduction commands](../../upstream_patches/README.md).

Heartbeat admission closes atomically; admitted synchronous and periodic writes
drain before the single cleanup attempt. Cleanup clears the captured DB/identity
registrations without reacquiring a handle. The gateway waits within its existing
shutdown budget before any DB close. A stuck writer, blocked cleanup or cleanup
failure keeps session DBs open. Later exit callbacks observe the same result;
they neither reopen a DB nor replay a failed/unknown cleanup write. A nonpositive
refresh interval still registers once without a periodic thread.

The bounded wait covers cleanup itself, not just the periodic thread: cleanup
runs in one owned daemon so a blocked SQLite call cannot make the caller ignore
its deadline. The close gate remains closed until that attempt is known done.
This deliberately preserves failure and recovery rather than hiding a warning.

## Canonical tools and exact provenance

The new source preparer reads immutable Git blobs, applies only the hash-pinned
patch into a fresh E-drive test snapshot and verifies all **23,586** files.
Every other file must match the base Git object; extra/missing/changed files,
empty shadow namespaces, unsafe paths, reparse points and reused output roots
are refused. No base checkout, live installation, model, credential or job
record is changed. The patch has an explicit LF Git attribute.

The canonical snapshot matches every source byte in the separate native trials.
Its eight real-helper/SQLite regressions pass with zero boundary denials:
periodic and register-once behavior; repeated exit after DB close; held initial
and periodic writes; bounded held cleanup; failed cleanup with no retry; stop
before startup; invalid timeout. The official SDK binding mechanism uses an
explicit synthetic server/callback registry and controlled fixture failures.
**This helper suite does not establish full owner-runtime/deployed acceptance.**

Sixteen new policy counterexamples pass within the full **438** policy tests;
the **15** runtime-model cases also pass. They cover exact patch identity,
wrong/dirty bases, unsafe names, missing/extra/changed files and namespaces,
reparse attributes, output retention and malformed blob streams. Fixtures do
not execute their source payloads or initialize a user project/repository.

## Separate actual native owner-runtime evidence

Three private native-Windows trials use the exact same candidate bytes and
existing dependency environment. They preserve actual owner bootstrap/DACL/SID,
one-use tickets, profile isolation, real sockets, source fences and actual
process exit. No runtime SDK function replacement, callback removal, warning
suppression, model/tool turn or live-fleet action is used.

| Trial | Verified finite scope | Exit | Modules | Trace SHA-256 |
| --- | --- | ---: | ---: | --- |
| Empty | Native bootstrap/auth/read refusals/owner loss | 0 | 1,202 | `ca14e10ffb5a7e4b6bbf45fb765c19d28b9b26ac3f56c8ee09f508e665eed57a` |
| Seeded | Same plus 20 owner-room checks in two synthetic profiles | 0 | 1,203 | `e84f640501ba1a3e65aca8c92a3ae40fd071b8cd65fe261a24fadc280d8b0c0c` |
| Held SQLite writer | Same 20 checks plus actual close-gate contention | 0 | 1,203 | `c71c43ba35643447b9ea2d778784d0cd54c5ca4e32920ec1d2a3fa9eff4c6a2f` |

In the held-write trial, an explicit fixture call admits a real SDK heartbeat
writer while another SQLite connection holds `BEGIN IMMEDIATE`. The actual
gateway close gate returns after **1.224 s** with both original profile DB
connections still open. Releasing the lock lets the admitted write/cleanup
finish without replay; normal shutdown then physically closes the DBs. Every
trial clears heartbeat rows before shared DB closure, has no late reopen,
preserves stored room/session/admission expectations and passes read-only
post-exit SQLite integrity checks. These are bounded fixture trials, not a
whole-backend, sustained-load, Pi or deployed-version acceptance claim.

The public unmodified owner probe is byte-unchanged and still retains the
original warning/exit 2. The private experimental harnesses are separately
identified by SHA-256 in the retained records. A supported public candidate
mode for that native probe remains the next integration step; the new public
helper runner is not a substitute for it. No new upstream pin/handler variant
or broad source exception has been accepted.

## Failures and limitations retained

Early helper-fixture attempts retained boundary failures on absent Windows
Linux-probe paths; final canonical execution allows only those exact absent
read probes and retains all real outside-read/write/network refusals. All
eight test assertions were unchanged. An initial archive-size bound refused
before SDK execution. A subsequent source fence caught 39 PowerShell files
converted to CRLF by Git archive before any SDK code loaded. Exact Git objects
reconciled the private snapshot; the canonical preparer avoids those filters
entirely. These failures remain on E: and are not relabelled as accepted runs.

The exact reviewed base remains an upstream cleanup finding until a supported
correction is adopted and validated. Other semantic/transitive compatibility
findings, worker/project/automation/integration and physical release gates are
still required. The last inspected stable/main cohorts retain 40/104 findings;
this patch changes none of the compatibility baseline/waiver data.

## New retained API26 failure

The preceding [b5433027 Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/43426665735)
passes API26 shard 1 but fails shard 0 in
`RuntimeSelectionTest.scrollingBackKeepsTheReadersPositionWhenMoreReplyTextArrives`.
The initial `position()` lookup cannot find `chat-messages` despite the preceding
VM message/conversation wait. The raw command completes 537 starts with 536
positive methods and one failure; it did not time out. Its artifact digest is
`ca901db62dfae9a47b8281f6830ab5a448ddd6c79a8489a37225d2e8e605c0b3` and raw log digest is
`d65998486b047cfbb9eda9a721e091eb2f84e0ede4d4371e8f395b26a4e2ecc9`.
This differs from the retained source1180 Demo-question failure. A shared cause
is unproven. Neither failure was waived or blindly retried, and no full hosted
gate is claimed. Reproduce the exact scroll/navigation context next; retain the
original Demo failure and its diagnostic follow-up as a separate obligation.

## Android and delivery boundary

The fresh required build is c9c0718 plus the seven reviewed maintenance-tool
changes, with **884** exact frozen inputs. It passes **1,011 JVM tests / 97
reports**, both lint variants with **0 errors / 51 and 50 warnings**, original
debug signing, all four ABIs/32 native libraries, uncompressed aligned DEX,
64-bit 16KiB ELF/RELRO requirements, native extraction and SDK zipalign checks.
No Android main/test source or production behavior changed in this chunk.

| Built maintenance-check artifact | SHA-256 |
| --- | --- |
| Debug APK | `321f04832a76c3db960457366c1988058681a447c19e2a2708642caee0aab6be` |
| Test companion | `a9e4f284e94e179fd5a60f4940cbb46caa8bcb324e2260832fa3ec1339761526` |

The public beta.9 APK/source 0ee08e3 and stable 1.0.0 are unchanged. Exact clean-head
hosted coverage, real integration acceptance and final production-phone upgrade/
live/offline evidence remain open. No phone data or saved connection is changed.

Private records: heartbeat-candidate1320; heartbeat-unit1321 through1325;
heartbeat-runtime-candidate1326/1327 and blob reconciliation1329;
heartbeat-owner1330/1331; heartbeat-canonical1336; heartbeat-policy1337;
heartbeat-build1338/artifact-proof1340; api26-shard0-failure1339/review1340.
They retain exact source/harness hashes, raw logs, failed attempts and receipts.
