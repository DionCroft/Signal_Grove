# Native gateway control observation: verified component, open regression

15 November 2026. The same full goal remains active. This is a read-only SDK
compatibility component under the approved isolated staging/native-check scope.

## Implemented boundary

`native_gateway_control.py` accepts only an already-connected Unix stream and an
explicit process incarnation selected by its caller. Linux SO_PEERCRED and real
procfs must agree on PID, start ticks, boot and effective UID before any request
and again before accepting a reply. Production manager selection remains the
caller's separate responsibility; the compatibility peers are owned children.

Only `identify` and `status` are permitted. Each supplied connection is one-use:
one correlated SDK v1 JSON line, one reply and EOF, at most 512 KiB and two
seconds. Strict UTF-8/envelope/types/correlation/depth/finite-number/duplicate-
field checks reject malformed replies. Mutation verbs refuse before send.
Timeout, peer loss and ambiguous replies stay unknown without discovery,
fallback, reconnect, replay, process signal or SDK timeout changes.

The pure status projector requires live timestamps within its bounded sample,
the selected PID, exact home digest, all 17 known live counters and all four
known ledger counters, strict fence metadata and explicit registered-only
coverage. Missing, unsupported, stale, busy-inconsistent, unenrolled or unknown
sources cannot become idle permission. Every accepted projection still returns
`fleet_ready_to_switch: false`. A drain marker, lifecycle label, ACK, legacy
zero counter or a single frozen generated ledger cannot authorize fleet work.

## Actual SDK qualification and retained first failure

The fixture uses the unchanged maintained SDK's Unix listener, dispatcher,
default identify, loop-owned status handler, real task registry type and
read-only SQLite status observer. Four private homes provide synthetic runner
state and generated SQLite ledgers: idle, busy, unavailable work and unenrolled
admission. No SDK function is patched. No model/inference, real credential,
live home, real ledger freeze, gateway drain or normal GatewayRunner CLI is used.
The Python audit guard is a source-specific fixture check, not an OS sandbox.

Eight real read-only requests complete. Every peer loads **228 SDK module
origins** from the verified **23,628-file** source set and ends cleanly, with its
own pointer/socket removed and no guard refusal. Current strict projections are
independently recomputed from the retained raw replies; the wire implementation
is AST-identical to the SDK-tested version. The only subsequent observer change
rejects unsupported ledger fields, with a matching regression assertion.

The first fixture completed identify, then its status request became unknown.
Its original operation, owner, trace, samples and failed unit are preserved;
the peer ended cleanly and no status request was replayed to it. New fixture
incarnations explicitly initialize their work owners before listening. Measured
cold preparation is 3.518–3.561 seconds, versus the unchanged two-second
wire budget; prepared status replies take 6.2–8.9 ms. This supports a cold-initialization
diagnosis; the failed call's internal duration was not instrumented. Real
GatewayRunner initialization/registration remains a required qualification.

## Regression evidence and immediate blocker

The new read-only gateway observer has **26 focused cases: 18 portable and
eight actual Linux peer/framing cases**. The final Windows suite passes **824 of
1,073 cases, with 249 platform skips**. Four fresh maintained-SDK peers verify
eight real Unix requests, the native PID/start/boot/UID binding and idle/busy/
unknown/unenrolled projections. The runner and ledger are generated fixtures;
normal GatewayRunner startup, real admission freeze, drain and fleet switching
are not accepted by this component.

The final broad native rerun is **held: 99 methods, 58 pass, one failure and 40
errors, no skips**. All eight gateway cases pass. The first existing backup case
encrypts successfully, then holds before proving its decryption child; no upload
occurs. Later cases refuse the extra maintenance-unit process. The original
receipt and failed unit are preserved; the cause is unproven. The earlier
99-method positive cohort has its own source scope and does not erase this run.

Previous source `09306e6` independently passes **1,047 hosted Linux policy
methods/no skips**. Source8d retains its full **1,142 API26 / 1,143 API37** gate.
The current source's hosted/full-native acceptance, original UI failure causes,
37 stable / 171 inspected-main findings, real project/worker/integration and
physical-phone/original-key release gates remain. Live services and beta.9 are
unchanged. [Exact implementation, failed checks and evidence](2026-11-15-prelogin-source-witness.md).


The first broad native cohort passed 99 methods/no skips before the added strict
ledger-field assertion. The final source's 18 portable gateway methods and eight
native gateway methods all pass. Its broader native failure is retained and is
not waived as unrelated or replaced by the earlier pass. The unchanged backup
code records encryption completion and a decryption intent, but no positive
decryption-child observation. The initial cause requires bounded diagnostics;
the later single-process refusals are consistent with a retained child. Do not
retry that original operation, reset its failed unit or delete its evidence.

The next action is to **instrument and diagnose the native backup decryption
child hold**, preserving its artifacts and strict single-process admission.
Then complete journal-bound gateway drain, normal GatewayRunner qualification,
all-profile/worker work coverage and the unified executor, reload/authenticated
health and bounded runtime-only recovery. Both canonical PM contexts and the
seven real modes remain unactivated. Prepare the full separate live approval
packet only after these gates; retain every original audit and release task.


## Reproducibility and private evidence digests

Portable checks use `python -B -m unittest discover -s scripts/tests -v`.
The controlled Linux cohort uses `scripts/ci_native_policy.py --native-only`
inside its exact finite user-manager unit with generated tools/keys/state.
The actual SDK fixture is `scripts/native/sdk_gateway_control_fixture.py` and
requires a separately verified SDK source and lexical selected PM interpreter;
it is not a deployment command. Private raw logs/state remain outside Git.

Consolidated review SHA-256: `63b95d58b86b8bbcc8157fb50723bab8000e70fbc75d86d0b573bd67a5d0175b`.

| Evidence | SHA-256 |
| --- | --- |
| `gateway-scope-review2224/review.json` | `7814dafe4604247bda76ee4578f6bdd5b73c4c699bf3da7ea7cd598249877c04` |
| `sdk-control-review2221/review.json` | `99ddae83e39426a68eac89c4c551a075147c424c21b4da41b6ef3b83f7a4cc10` |
| `gateway-native-review2215/review.json` | `fc6066c03cc6d9186c397e178b4a0d47559786906b05e9cfd9807a0613f3f5a1` |
| `native-failure2223/review.json` | `57aa20b66a8e166ace49d9cbfb31fccb1c5b46bf137c2e580a84a6734fc11e3a` |
| `inventory-windows2220.log` | `d84f2945c657d31579ffbf32fb0ded2052e96976354294c20465677d7c29bb53` |
| `hosted093062203/review.json` | `d8550f0d5e7c5afc3f5f76f341ccdf64df0dbb1c044b23274e869690e7534146` |
| `standard8d2188/review.json` | `1ec4c600690ce79b3e1b6657520b46cda0d7c296fd269a4993b0eefd0fa37df7` |
| `prepared-retirement-review2199.json` | `933e5b9f8bcbd420316d8b56c35c2c0622ec8366a86d8932a593de397a1096c8` |

| Current source | SHA-256 |
| --- | --- |
| `scripts/native_gateway_control.py` | `40e16dd65c344dd98e046e5ec4ad8a9d24d7e44591e1ce022fb7a0eaf5eb2f8f` |
| `scripts/native/gateway_control_server.py` | `18ddbea1aa05c49c2b09005eadd61530819dc343e57056aa32e0a10a9248242a` |
| `scripts/tests/test_native_gateway_control.py` | `a7ccd83637ad7c423dd7deeb2411643dad732b3c7e0854e077f7ec3a8ea4321c` |
| `scripts/ci_native_policy.py` | `680a040e0d785564ef4aa64d4e8b13861d9ddc5348b3d165950f177b61911b3b` |
| `scripts/native/sdk_gateway_control_fixture.py` | `7617d550f2ae6f768f0f88cb783adc28386a11ce50db016ab8b501ccf8be6ba4` |

No live service, launcher, PM fact, real mode, policy, grant, model or credential
is changed. This component does not close the full goal or authorize deployment,
private GitHub/live-home backup use, a production phone upgrade or a new APK.
