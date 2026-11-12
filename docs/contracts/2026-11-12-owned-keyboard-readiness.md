# Owned API26 default-keyboard readiness - 12 November 2026

A fresh Google APIs API26 emulator reproduced Gboard's first-use Google Search
coachmark in Android Settings search, independently of Signal Grove. The hint
began at y=827 while normal keyboard keys began at y=1053. Hosted diagnostic
2581's workspace action extended to y=1011 and was covered by that first-use tip.
The original native visibility assertion correctly failed. No application layout
fix or retrospective acceptance is claimed.

## Guarded preparation

The explicit AndroidTest instrumentation targets only the debug package on API26
ranchu/goldfish x86_64. It verifies the selected Gboard component and reviewed
6.2.34.154174348-preload-x86_64 / versionCode `25623411` before UI actions. A canonical
per-attempt UUID binds the bounded receipt. It opens only Settings search, finds
one exact reviewed tip and close control, dismisses it once, verifies hint removal,
hides the keyboard, reopens the editor, and requires complete native window
geometry to align with the visible keys. It then returns Home and proves the
keyboard hidden, with the default IME unchanged. Contacts, voice, IME replacement,
model downloads, production connections and existing user devices are untouched.

The intermediate afterDismiss sample is explicitly a hint-removal observation;
it does not assert unobscured geometry. Attempt 507 acknowledged dismissal and
removed both hint nodes, yet its accessibility window retained top 827. A separate
post-attempt observation and native touch region were top 1053. The final sequence
requires reopened.ready=true and top=keyTop; no app visibility oracle is relaxed.

The CI host requires one exact successful terminal receipt, bounded unique-key
JSON, matching identity/nonce, exact action acknowledgements, one dismissal and
reopened geometry. It retains failed/partial output and refuses reuse. Unsupported
API37 does not invoke preparation. Unknown Gboard versions fail closed for review.
The standard suite still runs once, with its existing strict no-skip/partial-run
checks and the unchanged five-class/eleven-method external-model inventory.

## Exact validation scope

Frozen source 511 is based on parent daa3ec81737e56586975aeaa3c7258434fdb178f.
Its full source context digest is `98ff71fe31fc9492c71d3918540e1fdba799401e2922f49943aa21452af65d6b`. Required build tasks,
1004 JVM tests in 96 reports, both lint variants (zero errors; 51/50 warnings),
320 policies, original debug signing and all 32 native-library ABI/alignment
checks pass. No model is packaged. Final commit documentation is added after
this source freeze; all frozen non-document inputs are checked unchanged.

Debug application SHA-256: `3b5faf2be9c537a3b4a692d05e3ab452e75548a86f121b6ed5583cd9c99c7656`.
Test companion SHA-256: `56309d5b81768249ef7ffbab97edad5f828e4d6155a8b766cd6b5c49b695b352`.

Fresh disposable fixture 512 uses API26 Google APIs x86_64 revision 16, Pixel2,
font scale 1.3 and disabled system animations. Preparation 515 passes once. Native
batch 516 then passes all four exact unchanged methods:

- CanonicalViewerUiTest.largeBilingualWorkspaceFormKeepsKeyboardActionsReachableAndCancelHasNoEffect
- ExistingSessionHistoryFlowTest.keyboardOpenStillAllowsNativePagination
- HostKanbanStatusFlowTest.bilingualKeyboardLeavesReadAndCancelReachableWithoutGrantingStatusWrite
- PhoneDraftRoutingTest.cancelReviewKeepsTheRemoteDraftAndPhoneFilesUnchanged

All six retained native reports require SUCCESS, exactly DOWN/UP, acknowledged
both events, no error and no retry: workspace Cancel, history More/Close, Kanban
Read/Cancel and phone-draft Cancel. Existing assertions verify complete native
captions/controls and consent/cancellation effects. This focused API26 result is
not a complete hosted suite, phone/model acceptance or signed release.

## Preserved failures and remaining gates

Preparation 500 failed before diagnostic acknowledgement metadata existed.
Preparation 507 records true open/dismiss acknowledgements, absent hint nodes and
stale accessibility extent, then fails before hiding/reopening. Both remain
failed; neither is retried or relabelled. Read-only observation 501 and distinct
existing-keyboard verification 502 do not count as fresh first-use acceptance.
Probe 492 used a mixed exploratory APK pair and is not acceptance evidence.

Hosted diagnostic 2581 API26 has 1124 positive / 1125 started and the preserved
occlusion failure. Its API37 run timed out at 6000.018 seconds: 991 started,
990 positive, last started PluginSourceFlowTest.changedCatalogMetadataRefusesBeforeSourceInspection.
No complete-suite pass or root cause is inferred. Previous full success 2fde17d
is scoped only to that source. Parent daa3ec8 hosted jobs were still running at
this checkpoint and remain independently scoped.

Private evidence retains source/build/signature receipts 511, keyboard policy 513,
fixture 512 launch/config, preparation 515 raw/parsed receipt, native 516 exact
case/result/screenshot/tap files and original failed receipts 500/507. No private
device data or generated APKs are committed. The full audit ledger, worker and
root/file authority, production interoperability, physical/model/lifecycle,
source/advisory coverage and signed release remain open. Beta.8 remains published.
