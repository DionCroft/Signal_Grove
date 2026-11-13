# IME and caption diagnostics; separate native failures retained

13 November 2026. All four [resumed priorities](../GOAL.md) and the full original
audit remain active. This is a test-only diagnostic chunk. It adds no test
method and changes no app main source, release APK, live service or owner data.
The connected API26 scope fails; this chunk is **not an all-pass acceptance**.

## Exact original API37 trial and font scopes

The original b995a28 debug/test pair is taken from its same-run immutable build
artifact, checked against GitHub's archive digest, verified for package/signer,
and installed only on a new disposable API37/x86_64 AVD. Original APK hashes:

| Original b995 artifact | SHA-256 |
| --- | --- |
| Debug APK | `18ec11aa48fb24a57caf702a7e5d5c69ed1373d92184f0f9a59328b1152c897c` |
| Test APK | `7edab59ed7901aa16b773c413e976cbf44aa881fdf9298e2dfffdf6405c8f6e4` |

CI explicitly sets API37 font scale to **1.0**, and API26 to **1.3**. An initial
local original-APK trial used 1.3 on API37. It failed earlier at the caption
outline assertion, before a Review tap/consent. That large-text result is kept
separately; it is not reproduction of the hosted 1.0-font viewport failure.
Its raw log hash is `d0c9d74631cd09341286c1c684f3fa0e51dec386d1e10741a66593d39b5cf747`.

A new predeclared trial at CI's 1.0 font setting runs the original failing
method at most five times, stopping at the first failure. All five complete
unchanged, with three distinct reviewed iterations/receipts per invocation.
Raw command metadata is retained per trial. Windows/WHPX differs from hosted
Linux/KVM. These five positives do not establish the original cause, fix the
hosted failure or replace its failed partition. The original hosted cohort and
its no-tap/no-consent receipt remain failed and preserved.

The new owned image is official API37 Google APIs x86_64 r08; the failed hosted
SDK log names the same r08 archive. Installed package metadata says revision 8,
while source.properties says revision 7; both are retained unmodified. Emulator
37.3.8.0 boots Android 17/API37 with 4KiB pages. All image/AVD/tooling data remains
on the designated temporary drive. A wrapper returning zero without installing
and one AVD-creation failure are retained as setup failures, not successful
installs. The actual native CLI install and corrected AVD creation verify.

## Diagnostics preserve the existing guards

The optional viewport observer records only the existing native-window/inset
disagreement branch. It reuses those two existing observations, adds no native
window query, wait, input action or retry, and returns the same null result.
It records direction, bounded geometry and bounded elapsed time between the
sequential observations. They are explicitly **not an atomic keyboard state**.
Observer failure cannot turn the mismatch into readiness. Review receipts have
schema 2, at most 16 samples and a capped count; they distinguish readiness from
the final pre-tap read. Caption failures now include glyph/action/visible and
supplied-outline bounds, without caption strings. The same inside-shape result,
one-pixel tolerance and assertion are preserved.

Exact reverse comparison restores all three prior files: DialogKeyboard,
CanonicalViewerUiTest and DialogCaptionAssertions. Existing single-tap,
coherence, containment, caption, consent and no-replay rules remain unchanged.
No baseline reset, dropped assertion or failure waiver is used. Timing effects
of added instrumentation are not ruled out by source equivalence.

## Fresh source-bound native results

The verified diagnostic pair uses the original local debug key. API37 uses the
failed method plus both original shape counterexamples; API26 uses the full
CanonicalViewerUiTest plus those two counterexamples. All run with animations
off; the scopes stay separate:

| Scope | Positive / started | Host command time | Outcome |
| --- | ---: | ---: | --- |
| API37, font 1.0 | 3 / 3 | 12.021s | Focused pass |
| API37, font 1.3 | 3 / 3 | 13.021s | Focused pass; earlier large-text failure remains open |
| API26, font 1.3 | 21 / 22 | 53.556s | One single-tap WARNING; failed scope |

Both outline counterexamples pass in all three scopes, including actual unsafe
clipping rejection. The 14 workspace-review receipts have schema 2 and zero
observed IME disagreements. This does not prove the mismatch branch is absent
under the original hosted conditions; it was not observed in these trials.

The API26 failure is
`CanonicalViewerUiTest.nativeWorkspaceReviewRequiresFreshConsentWithAndWithoutKeyboard`.
Its **Cancel** dispatch reports WARNING: one DOWN and one UP are accepted,
1,264 ms apart, against a 400ms long-press timeout. No second DOWN, retry or
replacement tap occurs. The attempt is retained as uncertain; accepted input
alone does not prove the intended cancellation. This is separate from the
earlier hosted Review-viewport and both original API26 Demo/missing-list failures;
no common cause is established. Ten other native tap receipts report SUCCESS.
All three instrumentation runs are terminal with no active instrumentation.
No real worker is used by these synthetic UI fixtures.

| Fresh native evidence | SHA-256 |
| --- | --- |
| API37/font 1.0 raw log | `86e90a34f32bce2db6d1304e5f139b2ee0ba7544a393ad8ca30d51575d4fd980` |
| API37/font 1.3 raw log | `5a43bf02cbcb5c4e145130d42773a7644c542f911445fcb98efe61fc3ce1c690` |
| API26/font 1.3 raw log | `d2e6b8d67034c935061bdc742ac1a96efda0016c4daec9764a078e2901548445` |
| Exact Cancel WARNING receipt | `389f17cd83fbfc003de6190e6dc703756358f7ba121f1a4d28350e094fe47323` |

The private driver initially indexed API37's intentionally absent API26-only
keyboard report and stopped **before** native UI execution. Completed installs
were reconciled by exact device hashes, not repeated. The corrected driver
continued once from that checkpoint. Only owned emulator debug packages were
updated, with synthetic data/QA backed up before signer replacement or reset.

## Required build and acceptance limits

The completed build freezes 4f27df1 plus exactly three test-source changes across
891 inputs. Required instrumentation assembly, JVM tests, both lint variants
and debug assembly pass: **1,011 JVM cases / 97 reports**, no failures/errors/
skips, lint 0 errors (51 debug / 50 release warnings), original debug signing,
four ABIs/32 native libraries, uncompressed aligned DEX, 16KiB 64-bit ELF/RELRO,
native extraction and SDK zipalign verification. These successful checks do
not override the separate failed connected scope.

Frozen source digest: `66c21957593f978ef2083ac5dd7690dcbf3ff9700ecf5eec25a800fd34a1c025`.

| Diagnostic artifact | SHA-256 |
| --- | --- |
| Debug APK | `9b3450c59b44a57f9904db205f5b5dd3e8cef77efe69ae4038cf97e29bd2abf4` |
| Test APK | `7129e4a50cc228fc3ac95d3f91e5540d1a9559e37ad1abce4ab213092829b8e8` |

No methods are added: the prior API26 inventory stays 1,137. A complete standard
gate for the final clean source remains required. Last independently verified
complete standard coverage remains c9c0718's 1,135 API26/1,136 API37 cases.
The later 9744caa hosted cohort now reports failed API26 shard 0 / API37 shard 0;
their native artifact review is pending. Do not merge its passing shards with
another cohort. The 4f27df1 Android gate was still building at this review;
its upstream compatibility job fails while parser/pinned helper jobs pass.
Fresh compatibility artifacts need inspection; no compatibility success or
unchanged upstream drift count is inferred from the job conclusion.

Next reconcile those exact failed artifacts and the retained Cancel receipt,
then diagnose the guard/caption transitions without replay or relaxed input
rules. Both original API26 failures stay open. Pristine Hermes shutdown review,
real workspace/worker/automation/integration and final production upgrade/live/
offline release acceptance remain. The experimental SDK candidate is not
deployed. Beta.9/source 0ee and stable 1.0.0 remain unchanged. No owner phone
reset/reboot, daemon restart, live model/credential/gateway/job change or replay
is performed. All scratch, builds, AVDs and private evidence stay on E:.

Private evidence navigation: ime-caption-source1404/diff1405/build1406,
artifact-proof1407, native1408/resume1411; original-b995-case1402 and
original-b995-ci100-1410; sdk1391-1393, review-api37-1396/1397 and launch1400.
