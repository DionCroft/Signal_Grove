# Keyboard transition trial and retained API37 review guard

13 November 2026. The full audit/on-device AI goal remains active with all four
[priorities](../GOAL.md). This chunk adds one controlled test method; app main
source, live services and public APKs are unchanged. No original failure is
claimed fixed and no existing action, wait or assertion is changed.

## Two actual keyboard transitions

The added method opens the model picker from Home, enables manual IDs, focuses
the provider field and verifies the actual native IME window plus delivered
dialog insets. It exercises Apply and Cancel once each. After dismissal it uses
the original onLast Chat-description touch, selection, five-second data wait,
idle synchronization and message-list lookup. It adds no wait or snapshot
between picker dismissal and that original Chat action.

Both cases pass on the owned API26/x86_64 emulator with prepared Gboard, 130%
text and animations off. Each Home state contains five exact Chat-description
matches. The picker has two Compose roots and main IME visibility true. After
the original action, selection is accepted, Chat is selected, one message list
renders 16 messages and main IME visibility is false. Apply saves the expected
synthetic runtime choice; Cancel preserves the default. Neither case reproduces
the original missing-list failure. These two observations do not establish the
original cause or make the selector unique.

The pinned Compose 1.12.1 AAR matches its declared dependency checksum. Bytecode
inspection confirms performClick goes through performTouchInput and the Android
dispatcher calls View.dispatchTouchEvent. This trial checks the original Compose
test path, **not physical input routing around the IME**. It does not replace
the separate native-tap or owner-phone acceptance gates.

Fourteen focused native methods pass in 35.057 s:
the 12 original Demo/runtime/predecessor methods, the existing route/privacy
counterexample and the new two-case transition method. There is one controlled
counterexample JSON, one completed transition trace, zero actual failure JSON
files and no active instrumentation after exit. The trace contains fixed
stage/boolean/count observations and excludes fixture IDs, model/provider IDs,
messages and titles. It is capped at 32 KiB; diagnostic write failure cannot
replace an earlier failed transition assertion. Appearance settings are restored and the synthetic Demo runtime is reset to
default, with no message or uncertain click replay.

| Native evidence | SHA-256 |
| --- | --- |
| Raw focused log | `11f29b11f7ab3be63b674ac2be98f9214cb64359d1426105c737f699c0b102c2` |
| Two-case transition JSON | `05cbfc21978dd0142aebbd1441db1c15ba21a6c8dab61b1fe8b9801c36803cb4` |
| Existing controlled snapshot | `944e6de201344470f639ebf5cf24b59fb8719989d0f677fac96456fbae3f1859` |

The old installed pair is hash-verified, its synthetic data is backed up, and
only the owned emulator's debug pair is updated with its original key. Its
synthetic app data is explicitly cleared for the prepared fixture. No owner
phone or production package is changed. New API26 standard inventory is 1,137.
This focused run is not a complete new-source standard gate.

## A separate complete-cohort failure is retained

The [b995a28 hosted run](https://github.com/DionCroft/Signal_Grove/actions/runs/43444941468)
is terminal: build and both API26 shards pass; API37 shard 1 passes; API37 shard 0
and complete coverage fail. The failed partition starts all 538 cases, records
537 positives and one negative result in
`CanonicalViewerUiTest.workspaceRefusalAdviceDoesNotClaimAcknowledgementOrRetry`.
Its native elapsed time is 2,216.292 s; this is an actual assertion failure, not
a timeout. Keep the failed cohort failed; do not merge a later passing replay
or another source's partitions into it.

The assertion is “Review geometry changed before its only tap”: a coherent
no-keyboard viewport had been observed, then the final viewport read returns
null. The exact retained failure receipt reports nativeTapAttempted=false and
consentPresent=false. Thus the **failing Review attempt was not dispatched**.
Earlier synthetic iterations have separate receipts; this does not claim the
whole test had no earlier fixture effects. No worker action was replayed.

Both the review helper and DialogKeyboard source match the exact failed commit.
For requireKeyboard=false, the inspected helper returns null when native IME
window presence disagrees with delivered dialog IME visibility. This is a
**source-based inference about the observed null**, not a measurement of which
signal arrived or disappeared first. The old receipt lacks that direction.
Keep the one-tap/coherence/containment/caption/consent guards intact while
gathering stronger API37 observations. No guard is waived or blindly retried.
No common cause with either API26 failure is established.

| Retained API37 evidence | SHA-256 |
| --- | --- |
| Original artifact ZIP | `59e06429e27dd4923bd45dd041713b631efad530689d49b47b8361d0c9a67e7f` |
| Original raw instrumentation | `657395661cbdb9bfb5bf4ae7396d5cfe2f17905a455adfb803d48eaef851a081` |
| Failing review receipt | `f2f5c8152c203b76ecbf02698761ddd82a07fd23a73028854c4419f4027eb726` |

The archive has 13,238,874 bytes and matches GitHub's digest. The initial private
reader used an invalid UTF-8 BOM codec alias after download; review resumed from
the same retained archive with the correct decoder. No native job was restarted
and the signed download URL was removed. Last independently verified complete
standard gate remains source c9c0718's 1,135 API26/1,136 API37 methods.

## Required build and next work

The local build freezes 9744caa plus exactly the added test method, with 890
source inputs. Removing that method restores the prior file exactly. Required
assembleDebugAndroidTest/testDebugUnitTest/lint/lintRelease/assembleDebug pass:
1,011 JVM/97 reports, no failures/errors/skips, lint 0 errors (51/50 warnings),
original debug signing, 4 ABIs/32 native libraries, DEX alignment, 16KiB 64-bit
ELF/RELRO, native extraction and actual SDK zipalign.

Frozen source digest: `bb56489b5c4794518420e78365116a829b0357e9c10c58e11c5ecf16c7a9927d`.

| Test artifact | SHA-256 |
| --- | --- |
| Debug APK | `9da0ea00b4c525a065b2dbbe42d99ae72e6cba5f372e21957db0a1953ee0fe71` |
| Test APK | `5c2ff45c50e77b12e4de753780312f4b81e4210edc03afc09aba05ef040142cc` |

Next reproduce/diagnose the API37 IME-window/inset disagreement with exact
source/APK identity and pre-dispatch receipts, without changing the one-tap
guard or replaying uncertain work. The local SDK has only an API26 image;
API37 requires a verified new owned image or a source-bound hosted trial.
The original API26 failures remain open while exact-source diagnostic CI runs.
Default pristine Hermes still retains its shutdown warning; the experimental
candidate is not deployed. Wider upstream semantic/transitive, real project/
workspace/worker, automation/integration, phone/lifecycle/security and final
production upgrade/release gates all remain required. Every original audit row
is preserved. Public beta.9/source 0ee and stable 1.0.0 remain unchanged.

Source stays on its authoritative share; all scratch/builds/AVDs/private
evidence remain on E:. Records: selector-transition-source1379/build1380,
selector-transition1382/diff1383, api37-shard0-failure1384, receipts1386 and
source-review1387. No live model/credential/gateway/job change, daemon restart,
owner-phone reset or repeat of its earlier reboot occurs in this chunk.
