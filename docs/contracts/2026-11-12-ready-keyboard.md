# Already-clear API26 keyboard preparation - 12 November 2026

Hosted run43232288243 API26 shard0 fails before inventory because its default
keyboard is already clear: native top=keyTop1053,37 keys, no tip/close nodes and
ready=true. The old setup required a first-use tip and rejected that valid state.
Three meaningful regressions reproduce the policy errors before the fix; all14
keyboard-policy methods pass afterward. The earlier unexplained no-tip gap remains
rejected. The newest pre-fix main run43242603615 likewise lacks a successful
preparation receipt: all other device shards pass, but the complete union fails.
Neither run is relabelled as a full pass.

## Narrow change

The emulator-only AndroidTest runner now accepts either its reviewed exact
Gboard tip or an already-unobscured window. The first path acknowledges exactly
one tip dismissal; the second records zero dismissal attempts. Both hide/reopen
the keyboard, require stable complete native top=keyTop geometry, preserve the
default IME and return Home with the keyboard hidden. The host validates each
distinct state, exact action list/acknowledgements, nonce and bounded unique-key
receipt. An initial no-tip geometry gap, unsupported IME/API/hardware, coercion,
partial or failed receipt and repeated setup action remain refused.

Native app visibility, exact single DOWN/UP touch, caption, consent/cancellation
and no-write assertions are unchanged. The standard suite's external inventory
stays five classes/eleven separately declared model methods. This changes CI
preparation; it adds no production feature or permission.

## Current frozen build

Parent `dd473a5f6aacd9cc1e4595e8fa95d10af4766a9d`; source context `4873d78b3816471ed0de5c9ca3b7ab2ab1308ac1d5886b09f1f14bdb270612fb`
with839 frozen files. Required tasks, signed assembleRelease and production graph
export pass:1006 distinct JVM cases /96 reports without failures/errors/skips;
lint0 errors,51 debug/50 release warnings;422 portable and15 runtime-model policy
tests;495 checksum-policy components/1071 artifacts with no blanket trust.
Both original signing identities, package names, Android8 minimum/API37 target,
beta.9/code112, four native ABIs,64-bit16KiB load alignment and all32 library checks
verify. Every DEX is uncompressed/four-byte-aligned. No model weights are bundled.

| APK | Bytes | SHA-256 |
| --- | ---: | --- |
| Production, not published | 70993990 | `ea328c7649f7061839e283d32431c35b54484f7f92d917ea582f60f099b39962` |
| Debug | 92542534 | `e738657ec685a44a5c8b6afc3d096c11f8a5f1154b2ec0126dca0d2e69785814` |
| Test companion | 7865099 | `a5b8ccf64ded5d2fff4710afeec48766eb698f07b8ca90160f392eafc3c14b26` |

## Exact native checks

A new disposable Google APIs API26 x86_64 revision16 Pixel2 fixture uses130%
font scale and disabled animations. Its first-use setup passes with exactly one
dismissal, then all four unchanged focused methods pass (26.415s JUnit):

- CanonicalViewerUiTest.largeBilingualWorkspaceFormKeepsKeyboardActionsReachableAndCancelHasNoEffect
- ExistingSessionHistoryFlowTest.keyboardOpenStillAllowsNativePagination
- HostKanbanStatusFlowTest.bilingualKeyboardLeavesReadAndCancelReachableWithoutGrantingStatusWrite
- PhoneDraftRoutingTest.cancelReviewKeepsTheRemoteDraftAndPhoneFilesUnchanged

Six new native reports prove acknowledged DOWN/UP with SUCCESS/no retry/error:
workspace Cancel, history More/Close, Kanban Read/Cancel and phone-draft Cancel.
A separate nonce then verifies the already-clear preparation on that same
known-success fixture: zero dismissals, top=keyTop1053 before/after reopening,
37 keys and no tip. It is a distinct reviewed stage, not a retry of a failed
command. Both paths retain exact raw receipts; neither substitutes for a full
standard-suite result.

A separate older owned API26 fixture's APK installation times out at120s; one
read-only observation also times out. No preparation or native suite is attempted
there, the outcome remains unknown, and installation is not repeated. Its data
and failed receipts remain. The physical phone is not touched by keyboard tests.

Public beta.8 remains unchanged. Fresh complete hosted coverage, production-key
phone upgrade/live connection, compatibility review, final SBOM/provenance and
public download verification remain. [Physical restart](2026-11-12-physical-restart-retention.md)
closes only its stated lifecycle scope; [full original audit](../AUDIT_EXECUTION_PLAN.md)
continues.
