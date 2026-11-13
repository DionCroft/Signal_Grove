# Rendered UI failure diagnostics

13 November 2026. The full audit/on-device AI goal remains active with all four
[explicit priorities](../GOAL.md). This chunk changes instrumentation only.
The original Demo and scrolling failures remain unresolved.

## What changed

`RuntimeSelectionTest` records a safe snapshot only after its original message
list lookup throws AssertionError. It retains that assertion as the cause and
never retries a lookup/click or treats failure as success. All original waits,
navigation actions, method identities and scroll/review/submission assertions
are source-identical outside that failure wrapper. An exact reverse-diff check
verifies this, rather than inferring it from passing tests.

The existing Demo failure snapshot adds the same rendered-state observation;
its original successful path remains byte-identical. The new test-only helper
records fixed-selector bounded counts, selected navigation, lifecycle/IME and
boolean fixture comparisons. Observations are explicitly non-atomic: collecting
semantics may let Compose advance after the original failure. The report does
not prove that those later observations were the state at the failed lookup.

It copies no message/draft/title/URL/token/session ID or arbitrary exception
text into JSON. The original AssertionError remains the cause. Only fixed
schema fields and booleans/integers/lifecycle enum values are emitted. Saved
JSON is limited to 16 KiB. No full semantics tree is logged. File-write failure
does not replace the original assertion. Existing CI already retains qa JSON.

The pinned Compose 1.12.1 Android AAR matches its declared dependency digest.
Inspection of its actual bytecode confirms performClickImpl dispatches through
performTouchInput. This supports observing layout/IME/route state; no wrong
click target has been observed in the original failed run. Private binary
review and the corrected namespace-aware metadata check are retained separately.

## Controlled native counterexample

The new standard method creates 16 synthetic stored messages, selects their
conversation while Home is displayed and satisfies the original scroll test's
ViewModel readiness predicates. It then verifies that the message-list node
is absent and the diagnostic reports Home selected, Chat unselected and all
16 messages ready. Thus data readiness alone does not establish a rendered
Chat route. This is a controlled counterexample, **not reproduction of the
original hosted failure or proof of its cause**.

The controlled Home snapshot also contains five exact Chat-description matches.
That proves the selector is not unique, without identifying which node the
original onLast click selected. The captured activity is resumed/focused and
IME is hidden. A controlled keyboard-dismissal/navigation trial is the next
way to test the off-route hypothesis rather than waiting blindly for recurrence.

The method passes a synthetic AssertionError through the same retention helper,
verifies the original cause identity and reads back its saved JSON. Private canaries in the fixture
key, API ID, title, message IDs/content, draft and original error are absent
from both wrapper text and saved JSON. All values pass an explicit type/enum
check. Prior diagnostic files and appearance settings are preserved.

It then navigates using the stable nav-chat tag and verifies the opposite
rendered state with the same selected messages: Chat selected, Home unselected,
one message list. English/Chinese/pinyin are enabled. These extra readiness
checks belong only to the new counterexample; no wait was added to an original
failing test.

All 13 focused native methods pass on the owned API26/x86_64 emulator at 130%
font scale with prepared Gboard and animations disabled: the two original
Milestone02 predecessors, all three Milestone03 methods, all seven
RuntimeSelection methods and the new counterexample. Native elapsed time is
27.042 s. There are no skipped/failed cases,
one planned counterexample JSON and zero actual failure snapshots. Raw log
SHA-256: `938dab817d6bb3c0ae011b5df9038946b8f2e94cfa09e3158bac60d223cc09ba`. Counterexample JSON SHA-256:
`944e6de201344470f639ebf5cf24b59fb8719989d0f677fac96456fbae3f1859`.

The exact old hosted pair is verified before replacement. Its synthetic private
fixture data is backed up, then only the owned emulator's two debug packages
are replaced because their hosted signer differs from the local debug signer.
No production package exists on that fixture. No owner phone or data is reset.
The phone is absent from the current USB inventory. Active instrumentation is
checked before and after; no UI observer steals its automation connection.

## Build and coverage scope

The fresh local build freezes parent 3f2388a plus exactly four test-source files,
with 889 source inputs. Required testDebugUnitTest/lint/lintRelease/assembleDebug/
assembleDebugAndroidTest pass: 1,011 JVM cases/97 reports, zero failures/errors/
skips and lint 0 errors (51 debug/50 release warnings). Original debug signing,
four ABIs/32 native libraries, DEX alignment, 16KiB 64-bit ELF/RELRO, native
extraction and actual SDK zipalign verify. A first driver preflight omitted the
two new files from its expected count and refused before any build; the failed
attempt remains recorded. A subsequent instrumentation compilation caught
a missing localization extension import in the new helper. That failed build
is retained; the import was corrected before this successful build. Original
assertions and interactions were not changed to pass either attempt.

Frozen source digest: `c49a0dc44d37cb054ffd16839c71a15dafa03f5167da24199a2f6f22815a2d5b`.

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `283a7a94ad3b70bcd6e4e9bca2ef690d13c2873c8c8973c72e46f64a23275fd6` |
| Test APK | `e456ef5349abd6f2a0f2c5c0cfa229fd683ab107cd48dc7e647414a4c74d0356` |

The new standard API26 inventory is 1,136 cases, including the new method.
Thirteen focused positives do not accept the full new-source device gate.
The last independently verified complete standard gate remains source c9c0718:
1,135 API26/1,136 API37. Do not transfer that gate to this new source, merge
different source/APK cohorts, hide failures or blindly retry hosted jobs.

## Remaining work

Inspect exact-source hosted outcomes and any retained failure snapshots next.
Both original failures still need a measured cause and correction; this chunk
improves diagnosis. The [prior replay/native-candidate evidence](2026-11-13-native-owner-candidate-and-ui-replay.md)
remains scoped to its original source/trials. Default pristine Hermes retains
its shutdown warning; the experimental patch is not deployed. Wider upstream
semantic/transitive and real project/workspace/worker, automation/integration,
phone/lifecycle/security and final production upgrade/release acceptance remain.
Every original audit row and all four goal priorities are preserved.

App main source, live gateway/models/credentials/jobs and public beta.9 assets
are unchanged. Source stays on its authoritative share; all scratch/builds/
AVDs/private evidence stay on E:. Private records: ui-diagnostic-source1362,
ui-diagnostic-build1371, ui-diagnostic-diff1373, ui-diagnostics1374 and compose-click1377. Preserve
unknown worker receipts and never replay them, restart a worker daemon or repeat
the owner's earlier phone reboot as part of these checks.
