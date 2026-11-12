# Current stable auth and clean release inventory - 12 November 2026

## Reproducible native authentication scope

The repository's `scripts/compatibility_probe_native_auth_history.py` adds exact
stable revision3b421578588243f920886e6fe79a6faa87ea026e. All earlier revision
definitions and their original cases remain.48 explicit Git-blob checks cover
critical app assembly, auth/provider/cookie/sign-in, ticket, routing, owner-scope,
terminal/secret scope and idle-client dependencies. The full source checkout must
also have that exact HEAD and be clean. The final77-case run records946 actually
loaded project-module SHA-256 identities and no unexpected audit denial.

The original52 cases still check native PKCE sign-in/refresh, scoped inventories/
messages with shared IDs in two profiles, foreign/unknown-profile refusal without
creating a home, bearer/code/verifier replay boundaries, native HTTP ticket
purpose/peer/profile/instance/drain restrictions, remote socket delegation and
saved-gateway callback/state/issuer binding. Twenty further cases exercise the
real deployment-session-token provider, exact header/provider matching, invalid
bearer and cookie precedence, no header fallback across an existing session,
same-site/cross-site/duplicate Fetch Metadata write refusal, safe reads and
normal same-origin/non-browser logout behavior. Webapp provider-token binding
and the deployment provider's lack of refresh remain enforced.

Five cases target the actual changed `GatewayRuntimeAPI._serve` sequence. A
redeemed local interactive ticket counts as an attached client before the handler
fetch yields. Both default and secondary identities enter their actual owner
scope. A drain or descriptor change while loading refuses before invoking the
handler; load failure releases the client count. Tickets stay consumed in every
case. The downstream handler is an explicit synthetic adapter: this proves the
admission boundary, not the complete canonical WebSocket handler or model/tools.

The fixture's two synthetic authorities are linked to their synthetic multiplexed
owner, as real served authorities are. Profile-home assertions are retained.
The verified short E dependency alias is kept for Windows DLL loading; all audit
reads still resolve to its same canonical dependency root. This changes no
dependency versions and broadens no audit read/write/network/subprocess boundary.
Outputs/home/SQLite remain in a fresh E fixture; owner credentials are absent.
Python audit hooks are defense in depth, not an OS security sandbox.

Final probe source SHA-256: `e18e4efb1a604bba4774acff2acea2835f4d5d8b807e8054408cf5b3436033a7`.
Separate wrong-source and unknown-revision controls refuse before output-home
creation or upstream execution. Full portable422/runtime15/checksum policies pass.
Use the original [probe procedure](2026-11-07-native-auth-history-matrix.md), an
existing compatible Windows interpreter/dependencies and `--revision stable-3b42157`.
Use a fresh output root for each deliberate trial; inspect uncertain outcomes.

## Actual source review and preserved failures

Compare the newest reviewed dependency cohort, not only the original baseline.
Both middleware handler bodies are already reviewed; their source fence changes
because `gateway/run_api.py` and `hermes_cli/web_server.py` changed. The run API
adds off-loop handler loading/preload, counts the client before waiting and
rechecks drain/readiness afterward. The dashboard start entry wraps its runtime
in PM interactive-install-prompt suppression. That start entry and a real handler
import/startup timing path are not executed by this ASGI/admission fixture.
Complete closure review remains required before admitting any source cohort.

Retained pilot failures are distinct: an existing dependency's canonical Windows
path exceeds the DLL loader limit before any case; a new-case fixture references
a callback dictionary instead of the preserved native bearer; and an admitted
secondary fixture lacks its multiplexed owner/config link. The corrections keep
all assertions. Intermediate52/72-case passes do not relabel failed77-case runs.
The exact final repository77-case result passes after faithful fixture setup.

No compatibility baseline hash, handler entry, absence or waiver changes. Fresh
hash-verified report artifacts from run43256878415 retain40 changed used-handler
findings on stable3b42157 and100 on inspected main2bfa6b07a14a9675cc6bc85e6bf254c23072fb5f.
Successful parser/registry/sidecar jobs do not erase that failed upstream job.
Native daemon/control-channel, real IdP, TCP/TLS/proxy capture, Android callback,
owner `api` actor binding, live project/worker effects and full canonical socket
acceptance remain separate requirements.

## Clean-main signed build and local assets

Exact clean source commit: `f9e38350c6058457eb0c25f0ef2f8d8da98585e2`.
Frozen842-file context: `f395fc8562afd88bf4a6d4c139da4590bee3b96835cd92dc34b93e22e600ab8f`.
Required tasks plus signed release/export pass:1006 distinct JVM cases/96 reports,
no failures/errors/skips, zero-error lint51 debug/50 release warnings. Original
production/debug keys, package identities, Android8 minimum/API37 target,
beta.9/code112 and every native/DEX alignment check verify.32 packaged native
files include the four ABI shims/engines; all64-bit load segments meet16KiB.
DEX is uncompressed/four-byte-aligned. No model weights are bundled.

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Production, unpublished | 70993838 | `3ca88f71d46948e4ef0c33cbeee299f0212da8e5f22a531609986f252d5059c6` |
| Debug | 92543466 | `2a250626cd7887922029366c10409431cc3452e0e6f07d146f470e06966f43f8` |
| Test companion | 7865099 | `a5b8ccf64ded5d2fff4710afeec48766eb698f07b8ca90160f392eafc3c14b26` |

The later probe/documentation change does not alter any Android/native/build
input. This exact APK remains bound to f9e3835; no newer source identity is
retroactively attached to it. It is not yet installed on the owner phone.

Fresh OSV queries complete at16:39 UTC:150 exact Maven versions and native commit
940a30ee3b5593e1a66fe0aadc3b731989022552 have no matching known indexed advisory.
The unrelated known-vulnerable positive control returns findings and pagination
is exhausted. This is not a full security certification or future guarantee.

Local-only assets contain production/debug APKs with updater-compatible names,
the original public certificate, SHA256SUMS, a schema-valid CycloneDX1.6 runtime
inventory and explicit local provenance.95 original Maven inputs match strict
verification metadata;150 Maven components,32 native-file hashes and one pinned
native-source component total183. Exact shipped licence notices match source.
The official schema is bound to immutable specification commit55343ba19dee1785acf1ce9191540d5fd7b590db;
its main and SPDX Git identities are verified. No private paths/credentials are
included. Graph edges, build tools, server/plugins/models and an external build
attestation are outside this inventory's claim.

## Remaining gates

Hosted Android run43256878684 passes build verification; its four native device
shards were still running at this checkpoint. No complete union is claimed.
Preserve that run across new main pushes. Complete semantic/dependency review,
production-key physical upgrade and live connection/history/projects, final-source
gate reconciliation and public asset/digest/download/updater checks remain.
Public beta.8 and the phone's production installation remain unchanged. All
prior model/restart evidence, old emulator unknowns and full original audit rows
remain. [Full ledger](../AUDIT_EXECUTION_PLAN.md) Â· [Release process](../RELEASING.md).
