# Unique Chat routing after a measured Home-state failure

13 November 2026. The full audit and all four [priorities](../GOAL.md) remain
active. This chunk fixes test navigation preconditions; app main source, actual
native tap dispatch, live services, public APKs and owner-phone data are unchanged.

## The controlled transition now fails in hosted evidence

The [source 4f27df1 cohort](https://github.com/DionCroft/Signal_Grove/actions/runs/43467846366)
API26 shard 1 starts 599 distinct methods, records 598 distinct positives and one
failure in homePickerKeyboardDismissalKeepsTheOriginalChatSelectorUsable. The
hash-verified archive has 15,416,384 bytes and native command time 2,039.8 s. This
is an assertion failure, not a timeout or a rerun of the original failed cohort.

Its retained trace ends at the first Apply transition. The actual dialog keyboard
had been verified, selection was accepted and all 16 expected messages loaded.
After the original description/onLast click, the failed list lookup and later
snapshot show Home selected, Chat unselected and zero chat-message lists. There
are six matching Chat descriptions, one resumed rule activity, no picker and no
visible IME at that later observation. Observations are sequential, not atomic.
The exact last matched node/coordinates were not captured: neither a particular
Recent arrow nor an IME-swallowed click is claimed as the measured target.

This proves the transition's data readiness did not establish Chat navigation.
The app already supplies unique nav-chat tags on both navigation layouts; its
Home Recent arrows can also have Chat descriptions. Choosing the last description
is an invalid target contract. Both affected test files exactly match the failed
source before this change; source/app identity is not inferred from a later APK.

The original clarification method is **positive in this failed partition**. The
original RuntimeSelectionTest scroll method is not in this partition and gains
no positive result from it. Neither original API26 failure is claimed reproduced
or fixed. The failed cohort stays failed, including its separate shard 0 keyboard
preparation failure. No passing partition/replay is merged into it.

| Original evidence | SHA-256 |
| --- | --- |
| Failed shard ZIP | `04b0212adfb33d3726820656a8175dda6706e750b0f35ab33ab2f7bb696dba0c` |
| Raw instrumentation | `c47d6792a13a3fffefcc7a8bb46114b4a3b0859f451894858fb5d112a0151eb5` |
| Exact failed transition JSON | `b92c0a08d6dc043ac4283ac8a9607b930a9856fb7a133be5253e7af50204b57d` |

## One targeted action plus a route postcondition

The shared test helper waits at most 5 s for exactly one nav-chat node with positive
bounds, requires a displayed clickable control, clicks **once**, then requires
the route's selected state. It has no description fallback, effect retry or
success condition based on ViewModel data alone. All five RuntimeSelectionTest
Chat navigation calls use it. The data waits, swipe and scroll-preservation
assertions remain unchanged; the Demo test source is unchanged.

The real Home-picker fixture is renamed to describe the corrected contract.
Each Apply/Cancel case adds four synthetic decoys beside its target conversation,
ensuring at least five recent conversations before testing duplicate descriptions.
It still verifies the real dialog IME, selected data, displayed message list,
runtime choice and cleanup. Its bounded version 2 trace names the fixed route
target and afterRouteClick, without conversation/model/provider/message values.
The original failed trace remains separately retained.

Two new controlled Android component methods prove duplicate descriptions do
not redirect the targeted click, and an accepted click without route selection
fails with exactly one route click and zero decoy clicks. These component cases
are separate from actual app/keyboard input. Exact reverse comparison restores
both prior affected files after removing the explicit changes. App navigation
implementation, original Demo file and native tap helper are byte-unchanged.

## Fresh required and native checks

The completed build freezes f0e999e plus four test-source changes across 896 inputs.
Required instrumentation/debug assembly, JVM tests and both lint variants pass:
**1,011 JVM cases / 97 reports**, no failures/errors/skips, lint 0 errors with 51
debug/50 release warnings. Original debug signing, four ABIs/32 native libraries,
uncompressed aligned DEX, 16KiB 64-bit ELF/RELRO and SDK zipalign checks verify.
All Python inputs are identical to the earlier 446-policy/15-runtime-model build;
those earlier results are not described as a new run.

Frozen source digest: `b08785d9662ee0ac2d473872c4cf1f6fbdad03b0bff8b6a39a03f3870d49ad5b`.

| Artifact | SHA-256 |
| --- | --- |
| Debug APK | `dbaa7c3a5efc72a32d3b323b9d3bd2bcf4db2fefa5d05d15c933dae3975657cc` |
| Test APK | `cdd173460d3a532bfd9ec0d1446731456636af03d384d88554993564b2a09ca9` |

Each owned API26/API37 emulator independently passes the exact 16 focused method
set: the 14 original Demo/runtime/predecessor and rendered-state methods, plus
two new route counterexamples. Each has two completed real-keyboard Apply/Cancel
transitions, multiple Home Chat-description matches, selected Chat and one
16-message list afterward. Runtime choice/selection and trace privacy/size
checks pass; there is one controlled snapshot and zero actual failure JSONs.
Commands are terminal with no active instrumentation afterward.

| Native scope | Command time | Raw log SHA-256 | Transition JSON SHA-256 |
| --- | ---: | --- | --- |
| API26/font 1.3, 16/16 | 36.548s | `c6d47ffe1e91898fcb0b8a7198cdd72deb2097ca69c0b8d544e3dfa033599b5b` | `95826911bab4782161c415f2484911686fd92d6d33cb6a65b1a8741180744d6d` |
| API37/font 1.0, 16/16 | 56.564s | `f158dfeb0896558f4fcff6527a7f74cf5ff3108d975bb169bd28b5c366c936b7` | `c776ec6a58f26495c26c829952f5c328edd4b3acda034a9cc3a289921951446f` |

Prior installed pair hashes and owned AVD identities are checked; synthetic
data/QA is backed up before the same-key debug upgrades and explicit fixture
clears. Only the owned API37 font is set to CI's 1.0 for this scope; its earlier
1.3 caption failure stays open. No owner phone is reset, updated or rebooted.
Standard native inventories are 1,141 API26/1,142 API37. Focused results are not
a complete final-source standard gate. Last independently reproduced complete
coverage remains c9c0718's 1,135/1,136 cases. Earlier failed cohorts remain failed.

## Remaining acceptance

The observed controlled routing failure is addressed in these local scopes.
Full clean-source hosted coverage and first-run keyboard gap acceptance remain
required. The original Demo failure and original missing-list failure retain
their evidence/unknown original causes. Delayed native tap WARNING and the old
caption/viewport findings are separate and remain open. The CI workflow keeps
active main checks across chunk pushes; no running cohort is cancelled or
relabelled to make the final head green.

Full upstream semantic/transitive/shutdown review, real project/workspace/worker,
automation/integration, device/security and final production upgrade/live/offline
release acceptance remain. Inspected stable/main review and the experimental SDK
candidate keep their prior scopes; no compatibility variant is waived or SDK
deployed here. Beta.9/source 0ee and stable 1.0.0 are unchanged. Source stays on
its authoritative share; scratch/builds/AVDs/private evidence stay on E:.

Private evidence: api26-shard1-failure1433/review1434; chat-route-source1434,
build1435/proof1436/diff1437 and native1438. Every original audit row is retained.
