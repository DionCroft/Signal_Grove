# Android owner profile retirement and cache isolation

## Observed behavior

The real Android client signs in with the native PKCE exchange, refreshes its
bearer and verifies the same owner and two distinct profile homes. It explicitly
reviews a live session before heartbeat/resume/replay. A generic launch ticket
continues to serve default. On Webapp, a valid foreign-home describe triggers the
existing single profile-bound negotiation for field. Gateway-only addresses
refuse that named profile rather than borrowing default's live state.

Each confirmed receipt records its applied-view cursor in a unique disk Room
database. Android closes that database; the host independently copies and checks
it before requesting genuine `GatewayRunner._unserve_profile` on field. The
fixture verifies the field authority is retired and removed from the registry,
with only default still serving. Android reopens the database and checks exact
receipt/cursor equality and wrong-home isolation. A new field inventory and a
held, unconsumed field adoption review cannot reconnect or create another receipt.
Default remains readable. A second independent database copy must have exactly
the same logical rows, including all scope fields and summaries.

This is receipt/applied-cursor persistence, not a new claim about chat-message
cache contents, active/unknown work, worker execution or production login.
Earlier closed-history and unknown-write evidence stays separately scoped.

## Bounded actual-owner fixture

Both immutable upstream checkouts remain unchanged and all imported source
modules are checked against their exact Git blobs. The disposable owners run on
the workstation in fresh private test roots. Only a synthetic upstream IdP is
used; HTTP/PKCE/refresh, bearer verification, tickets, WebSocket routing and
session authorities are real. The Webapp mount uses a renderer placeholder; no
browser-renderer acceptance is inferred. The prefix adapter forwards the actual
ASGI app under `/dashboard/`, including native callback prefixes.

The fixture creates one local session per profile through the genuine native
owner route, with no inference or tools. The Android bridge permits only empty
`runtime.describe` before actual binding, live inventory at limit 200, one empty
heartbeat, one reviewed exact-session resume per profile and replay from the
exact acknowledged cursor. Work submission, mutation, arbitrary sessions,
foreign profiles, repeated resume/replay and altered cursors are refused. Only
reviewed auth/profile/ticket routes pass HTTP; bodies, frame counts and sizes
are bounded. The out-of-band retirement request follows the quiescent phone
snapshot. Its read-only status route reports only the fresh case ID and phase.

Backend-only and Android modes are mutually exclusive, require their respective
explicit inputs and cannot claim one another's results. Default CI executes the
new method against a clearly labelled mock gateway. Real acceptance requires the
external case descriptor, actual-owner result, exact terminal instrumentation and
independent host/phone database agreement. The host rejects a mock substitute.

## Final measured source and matrix

Source410 freezes 751 files at source digest
`ad67dedde0ea51da555dfad019b35345db005637e3a504fed9493ff022c49410`. Required build tasks pass 987 JVM tests /
95 reports, both lint variants with zero errors (51 debug / 50
release warnings), and app/test APK assembly. All 293 policies, 15 existing
model-status policies and checksum policy pass (495 components / 1071 artifacts,
verified metadata, no blanket trust). Dependencies and production/build inputs
are unchanged by this chunk.

The original debug signer is retained. All 32 packaged native
libraries pass ABI/load alignment checks, including the 16-KiB requirement for
arm64/x86_64; no GGUF is included. Earlier summaries understated the packaged-library count as 20; the retained
source362/source384 proofs each list 32. Those two summary sentences are
corrected without changing the historical measurements.

- App SHA-256: `4afd6db937f3d89106312578c393158a1af6f76dc5cf2af409de8010c5e3e07f`.
- Companion SHA-256: `39ccd7ecf4a742d4cdb984d69ab8c998e791850f6bc864daae7e822e0d93551f`.
- Fixture SHA-256: `7c9046f230a0180ab149ef5aaf4b74277282bc9b538ce27ae649b9d9ed61ebb8`.

Run411 executes one actual Android method for each matrix cell. All eight pass
the Android/core transport assertions, with independent healthy final SQLite
stores, unchanged session IDs/cwd/revision/generation, zero messages, admissions,
worker executions, mutation receipts and held leases/compression locks. Each
before/after phone copy is quiescent and preserves its exact confirmed rows.

| Android API | Hermes pin | Surface | Confirmed phone rows | Imported modules | Shutdown warnings |
| --- | --- | --- | ---: | ---: | ---: |
| 26 | `2bd378e` | gateway | 1 | 1384 | 1 |
| 26 | `2bd378e` | webapp | 2 | 1386 | 1 |
| 26 | `3b42157` | gateway | 1 | 1417 | 1 |
| 26 | `3b42157` | webapp | 2 | 1420 | 1 |
| 37 | `2bd378e` | gateway | 1 | 1384 | 1 |
| 37 | `2bd378e` | webapp | 2 | 1386 | 1 |
| 37 | `3b42157` | gateway | 1 | 1417 | 1 |
| 37 | `3b42157` | webapp | 2 | 1420 | 1 |

The 12 confirmed receipt rows are in eight separate case databases; before/after
copies are not counted as extra receipts. Run412 additionally passes 40 focused
methods per API: 18 shared profile-ticket cases, one native gateway contract,
14 auth/attachment cases (including the default mock path), five Room storage and
two closed-history cases. Strict parsing independently verifies all starts and
positive terminals, with no skips or partial results. Installed APK hashes,
existing app/test UIDs, font scale and native-IME settings are retained. No UI
native Cancel rerun is included in this batch.

Every actual-owner case retains one observed upstream atexit session-reaper
warning about reopening a closed state database for a heartbeat write. The
supervisor returns 2 and `CORE_PASS_SHUTDOWN_FINDING_OPEN`; this is explicitly
not clean lifecycle acceptance. The warning is neither suppressed nor promoted
to a passing shutdown. The phone model/helper, real Pi/production IdP, physical
upgrade, active work, complete UI/hosted CI, upstream drift/advisories and signed
release remain separate gates in the full audit.

## Retained preliminary failures

Source406 passed required build/signing checks after correcting a temporary
verifier copy that accidentally substituted 16,406 for 16,384. The original
threshold is restored; byte-identical prebuilt libraries were independently
inspected and the failed partial verifier output is retained. An earlier verifier
invocation stopped at its precondition before wrapper publication; it produced
no accepted proof.

The first real matrix407 attempt failed before retirement: API26's host harness
tried to execute `test` directly under `run-as`, which Android denied; after the
host error its test timed out. API37 timed out during a read-only APK-hash
preflight and never started instrumentation. Both owner attempts are terminal,
failed and retained. Neither received a retirement request. Independent final
stores match their created session fences and contain no work or mutations.
The harness now uses the shell builtin, waits for terminal instrumentation before
removing its owned port, and never reuses an uncertain case root. The four fresh
API26 matrix409 core proofs pass on source406 but are not substituted for the
final-source eight-case matrix above.

Run408's auth batch has 13 positive methods / one failed new mock method on
each API: the existing viewer-mode refresh route rejected its newly exercised
caller with HTTP 500. Its wrapper stopped before storage/history, so those
unexecuted classes are not counted. Source410 adds only that explicit mock mode
to the existing refresh guard; production code and real-owner assertions remain.
Earlier native Cancel/IME non-success, ADB-loss, timeout and compatibility findings
remain recorded and unresolved. No failed batch is relabelled as a pass.

## Private evidence and next gate

Artifacts remain in the designated private temporary storage, outside Git:
`local-helper-source410.json`, build/proof410, original-signer APKs,
`profile-owner-matrix411-api26.json`, `profile-owner-matrix411-api37.json`,
case-specific raw instrumentation and before/after SQLite copies,
`profile-validation412-api26.json`, `profile-validation412-api37.json`,
`profile-final-proof413.json`, policy410 logs and
`profile-preliminary-failures410.json`. No fixture credentials, local databases,
model weights or generated outputs are committed.

Next instrument the retained single native Cancel result with bounded diagnostic
metadata and no retry, then continue worker/project/file ownership and reviewed
dispatch. The broad audit and optional local-AI goal remain active. This chunk
publishes source to main; it does not publish or accept a new signed release.
