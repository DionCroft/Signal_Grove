# Backup child observation: strict diagnostics and intermittent hold

15 November 2026. The full goal remains active. This source chunk improves
failure evidence; it does not claim the intermittent backup issue is fixed.

## Changed behavior

The backup actor retains its own exact Popen handle before observing native
identity. It collects the same five mandatory facts: process incarnation,
effective UID, exact executable path, executable device/inode and exact finite
maintenance cgroup. Every fact must be true before the caller feeds input.

An unavailable or mismatching observation writes `child_observation_held` with
phase, PID, boolean/unknown check results, return code and reviewed program hash.
It emits no raw exception, command, key, token or private path. It grants no
permission to adopt, feed, signal, restart, retry or replay the child. The claimed
actor stays unknown; the journal and owned finite unit preserve the outcome.
No automatic cleanup of uncertain production work is added.

The native regression creates an actual Python child in the real fixture cgroup
while expecting the reviewed age image. Incarnation, user and cgroup pass;
executable and inode fail. The held receipt is independently verified and no
backup input, output or HTTP effect occurs. Fixture-only cleanup closes that
exact created child's input and waits for its normal exit.

## Actual observations and limits

Strict backup-child diagnostics now retain the exact Popen handle and a
credential-free per-check hold receipt. A wrong-executable regression verifies
that no input or backup/publication effect is permitted. The fresh complete
controlled cohort passes **100 native methods/no skips**; Windows passes **824
of 1,074 cases, with 250 platform skips**. The prior 99-method failed run remains.

A separate generated-data diagnostic reproduces **three holds in 100 fresh
decryptor starts**: incarnation, UID and cgroup pass, while executable path and
inode do not match the reviewed program. All three receive **zero ciphertext
bytes** and every child ends. The other 97 generated round trips verify. This
narrows the failure to executable observation; the exact cause and safe fix are
still unproven. No check is relaxed and no held backup is replayed.

Previous source `97db9f3` independently passes **1,073 hosted Linux policy
methods/no skips**, including all 26 gateway cases. Current-source hosted checks
remain separate. Source8d retains **1,142 API26 / 1,143 API37** standard methods;
all original UI, upstream (37 stable / 171 inspected-main findings), real project/
worker/integration, local-AI/physical-phone and original-key release gates remain.
Live services and beta.9 are unchanged. [Exact source, reproduction and scope](2026-11-15-native-gateway-observation.md).

The separate diagnostic exercises the actual unchanged `_owner` check on 100
fresh age v1.3.1 decryptor children with generated keys/data. Its surrounding
actor/receipt/scope carriers are explicitly diagnostic objects, not production
approval or manager acceptance. Unproven children get empty input solely to
finish the owned fixture; they receive no ciphertext. Each is waited/reaped.
This is neither a replay of the earlier stage nor production backup acceptance.
The three hold samples show mismatched image/inode, not permission/read failures;
the original failure did not record these individual fields. A fork/exec timing
boundary is a hypothesis, not a proved root cause. Capture the actual transition
before changing native trust or introducing a bounded observation policy.

The fresh 100-method complete controlled cohort passes; its source-specific pass
does not erase the earlier one-failure/40-error cohort or the three diagnostic
holds. All inspected native fixture owners/units are stopped. Source/dependency
facts, live incarnations and seven real modes conserve. The previous gateway
component's actual SDK control check retains its synthetic runner/ledger scope.
Normal GatewayRunner, drain, native admission and fleet switching remain open.

The next action is to **identify the executable transition in the same original
spawned backup child**, then implement and validate a bounded native handoff
without accepting an arbitrary image, widening trust or replaying the held
operation. Preserve its artifacts and failed unit. Continue journal-bound gateway
drain, normal GatewayRunner/all-profile/worker coverage, the unified executor,
reload/authenticated health and runtime-only recovery afterward. Both canonical
PM contexts, the seven real modes and the separate final live approval remain.
Every original audit, phone, integration and production-release requirement stays.

## Evidence

Run portable discovery with `python -B -m unittest discover -s scripts/tests -v`;
run the controlled Linux cohort with `scripts/ci_native_policy.py --native-only`
in its required exact finite user-manager fixture. The private diagnostic's
exact generated controller and 100 raw samples are retained outside Git; its
review digest below binds them. No model, real key or live home is used.

Consolidated review SHA-256: `041d0fb96b07fad20eee9d99437aabea8f18c2ac7b3407cfd2401335941a3e31`.

| Evidence | SHA-256 |
| --- | --- |
| `backup-child-native-review2234/review.json` | `ec33dc497ac962beb4e364aef78236d1f02f8697a8808a8dec48562d88013127` |
| `child-probe-review2236/review.json` | `4fdfe241de1f9f38f7950f3470ed2cee81477caa601008e3b20fdc780f4b3509` |
| `hosted97db2232/review.json` | `09d5641997a48facc3e816e58ee0be82eb0b55600064c3c9c39ab5cdc9919f17` |
| `inventory-windows2230.log` | `6d9aa84ce1a275a4f7fa5161ff9434a477e7e360f12fa5e79d1521a02d2dd9c7` |
| `gateway-scope-review2224/review.json` | `7814dafe4604247bda76ee4578f6bdd5b73c4c699bf3da7ea7cd598249877c04` |
| `native-failure2223/review.json` | `57aa20b66a8e166ace49d9cbfb31fccb1c5b46bf137c2e580a84a6734fc11e3a` |
| `sdk-control-review2221/review.json` | `99ddae83e39426a68eac89c4c551a075147c424c21b4da41b6ef3b83f7a4cc10` |
| `standard8d2188/review.json` | `1ec4c600690ce79b3e1b6657520b46cda0d7c296fd269a4993b0eefd0fa37df7` |

| Source | SHA-256 |
| --- | --- |
| `scripts/native_backup_transaction.py` | `3a3dbe1f25d5fb25f7694f704992fccda2e0f2f77852edf202ccb3469d08d89b` |
| `scripts/tests/test_native_backup_transaction.py` | `2150870922f9f0297ed46124d1324b2fd8b3085a9c6241af3c4d1cd4e2d3881e` |

Live deployment, private GitHub/live-home backup, production upgrade/release and
the full goal are not accepted by this diagnostic component.
