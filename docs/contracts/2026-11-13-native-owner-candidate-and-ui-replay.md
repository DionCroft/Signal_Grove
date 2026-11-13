# Native owner candidate and exact API26 replay

13 November 2026. The full goal remains active with its four explicit
[acceptance priorities](../GOAL.md). This is a tested maintenance-tool chunk;
Android source, live gateways and public release assets are unchanged.

## Explicit native shutdown candidate

The existing owner-runtime probe now accepts default-off
`--heartbeat-candidate-base`. Its source root must be a separately prepared,
fully verified snapshot of exact Hermes
`3b421578588243f920886e6fe79a6faa87ea026e` plus the existing two-file patch.
Verification covers all 23,586 files, refuses arbitrary/extra/missing/modified
source and preserves import, authentication, socket and profile fences.
Git replacement refs are disabled; file validation uses bounded reads.
Candidate reports explicitly refuse unmodified-upstream acceptance.

Four native negative trials refuse an unsupported pin, held-writer mode without
candidate opt-in, pristine source presented as patched, and unreviewed source.
No SDK code executes in those rejected attempts. The fresh pristine traced
empty trial still exits 2 with the original warning: 1,206 source modules and
38 trace events. Its warning has not been suppressed or reclassified as success.

Three separate explicit candidate trials exit 0:

| Trial | Source modules | Trace events | Additional result |
| --- | ---: | ---: | --- |
| Empty owner bootstrap | 1,202 | 44 | Actual process exit, no cleanup warning |
| Seeded room reads | 1,203 | 44 | All 20 native owner room checks |
| Held SQLite heartbeat writer | 1,203 | 58 | 20 room checks; close fence returns in 1.219 s with both DB connections open |

The held trial uses actual SQLite contention, the production writer admission
helper and the real GatewayRunner close gate. It replaces no SDK function.
After releasing the owned lock, the same cleanup drains and normal shutdown
physically closes the registered databases. All three actual process exits
retain zero heartbeat rows, successful read-only SQLite quick checks, zero
warnings and no late connection reopen. These are finite synthetic profiles,
not model/tool turns, Android/Pi interoperability or deployment acceptance.

The canonical eight actual-helper/SQLite regressions also pass. The full local
policy suite passes 440 cases, with 15 separate runtime-model cases. New policy
counterexamples verify the bounded read and disabled Git replacement objects.
See [reproduction commands](../../upstream_patches/README.md) and the
[earlier candidate design and retained failures](2026-11-13-heartbeat-lifecycle-candidate.md).

## Exact hosted API26 pair replay

The b5433027 [original hosted failure](https://github.com/DionCroft/Signal_Grove/actions/runs/43426665735)
is retained. The scroll-preservation method could not find its initial
`chat-messages` node after the ViewModel readiness wait; its cause is unproven.

The original hosted build archive is hash-verified before replay:

| Input | SHA-256 |
| --- | --- |
| Hosted build ZIP | `9fa6e9db06e54a94e8b6c4770f4dbfeaa48c500c221fde68cf72041ebc70d4fc` |
| Original debug APK | `edda6023e1530d6d8dcba682136147ff9aa395b8e5d5e9fbee24d3c7ba11ec8e` |
| Original test APK | `9b69642e79767b3baacbdb4f5b2a6ad9da20b24ee8d085022a808558c9cff91f` |

Both original packages, code114, instrumentation target and shared hosted
debug signer verify. This signer differs from the owner's local debug key;
the pair is installed only on the existing owned disposable API26 emulator.
Its previous synthetic fixture data is backed up before package replacement.
No owner phone/package data is reset. Settings match the original CI profile:
3 GiB, two cores, pixel_2, 130% text, animations off and prepared Gboard.

The unchanged five-method RuntimeSelection shard sequence passes in 17.507 s.
A separate clean synthetic full shard0 replay passes all 537 original cases in
1,188.104 s. Its full inventory is 1,135; external-model methods stay excluded.
Original assertions, waits and interactions are unchanged. No UI observer
steals automation during either run; the native SDK fixture ran independently
on the Windows host. Both failed hosted outcomes remain failures. Passing
replays do not establish a cause or fix, and do not merge with another source's
shards to manufacture a complete gate.

| Raw log | SHA-256 |
| --- | --- |
| Original five-method sequence replay | `00fc09f339325cfd7fa4fd684f27ca8fff80b8673165207fe08fe7f84ac214e2` |
| Original 537-case shard replay | `9aef66ac303ad51b9cad34c13b642f526bf0fb398f4c4ab49d4b4d8f1e5bdfdc` |

Next, capture sanitized route/render/lifecycle evidence if the missing-node
failure recurs, preserving successful test behavior and assertions. Readiness
of a ViewModel does not itself establish the rendered route. Compose documents
separate synchronization for external work in its
[testing guidance](https://developer.android.com/develop/ui/compose/testing/synchronization).
Neither this observation nor ambiguous navigation descriptions prove the cause.
The original source1180 Demo clarification failure also remains unresolved.

## Complete diagnostic-source hosted gate

The [c9c0718 hosted run](https://github.com/DionCroft/Signal_Grove/actions/runs/43433441594)
is complete. All five relevant ZIPs match GitHub SHA-256 digests. Independent
coverage verification reproduces the hosted summary exactly: 1,135 API26
methods (537/598) and 1,136 API37 methods (538/598), with disjoint complete raw
partitions, no missing/duplicate/failed/skipped cases and matching APK identities.
App SHA-256 is `e13c6f496125b9cc23c4ef415d77b978592c239619117b1b12b04ee4d7cf1b13`;
test SHA-256 is `51a3c5139f310e5170dce7e8edfaa69a639df7f9b35cdfb4499a5cf426df0235`.
This accepts that source's standard gate, not the separate external-model gate,
either old failure's cause, or the new candidate-tool commit's hosted checks.

## Compatibility and build boundaries

The [b995a28 compatibility run](https://github.com/DionCroft/Signal_Grove/actions/runs/43444941370)
passes parser, pinned registry and pinned sidecar jobs. Its upstream job fails
and both report archives match their published digests. Stable3b42157 still
flags 40 changed used handlers. Inspected main5c1abd2 flags 104. Its flagged
handler identities/hashes equal the previous inspected0e72e8a set; other static
source fields differ, so this is no whole-source equivalence or execution proof.
No pin, handler digest, baseline, warning or callback is waived.

The fresh local build freezes b995a28 plus exactly the three reviewed Python
changes, with 886 source inputs. Required checks pass 1,011 JVM cases/97 reports
and both lint variants with zero errors (51 debug/50 release warnings). Original
debug signing, four ABIs/32 native libraries, aligned uncompressed DEX, 16KiB
64-bit ELF/RELRO and SDK zipalign/native-extraction checks verify. The validation
driver's first attempt used the wrong helper-record field and refused before
any build; it is retained separately. The corrected attempt changes no tests.

Fresh source digest: `153a13eae7225b511d8d4dff044efc1641115362c7fafd6083d9b82813f601f8`.

| Test artifact | SHA-256 |
| --- | --- |
| Debug APK | `842f95b67b447945f0a6e71c20f358987cf16c4d9280855743e7c94547ead136` |
| Test companion | `a9e4f284e94e179fd5a60f4940cbb46caa8bcb324e2260832fa3ec1339761526` |

Public beta.9 remains the source0ee08e3 experimental APK with its original
production key. No release asset is replaced. The phone remains absent from
the last USB inventory; final production upgrade/live/offline acceptance,
real project/workspace/worker, automation/integration and every original audit
row remain open. No live gateway, credential, model, job or worker receipt was
changed. Keep source on its authoritative share and all scratch/private evidence
on E:. Private records: native-candidate1351, native-candidate-policy1352,
native-candidate-helper1353, native-candidate-build1356, hosted-acceptance1358,
runtime-sequence1348 and original-shard0-1349. No automatic retry or replay.
