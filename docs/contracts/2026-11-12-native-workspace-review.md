# Native workspace Review regression - 12 November 2026

## Hosted evidence and scope

[Android run 43256878684](https://github.com/DionCroft/Signal_Grove/actions/runs/43256878684)
is bound to f9e38350c6058457eb0c25f0ef2f8d8da98585e2. The three successful
partitions independently verify raw inventory/native logs, source and identical
APK hashes. API37's disjoint 536/597 partitions exactly cover its 1,133-case
inventory. API26 shard 1 passes 597 cases. Failed shard 0 has 535 starts,534 distinct
positive results and one negative result:
`CanonicalViewerUiTest.workspaceRefusalAdviceDoesNotClaimAcknowledgementOrRetry`.
Its independently parsed 1,132-case inventory equals the successful API26 inventory.
The standard verifier rejects its missing pass receipt. No receipt is fabricated.

The first refusal screenshot shows the expected busy advice. The next loop
cannot find workspace consent after Review; keyboard preparation had succeeded.
An unchanged original-key local class run passes 19/19. This is a distinct result,
not proof that the hosted failure was harmless or of its root cause.

## Test change

The Review helper independently observes native accessibility windows, delivered
IME insets and the focused dialog's visible frame. It accepts a hidden keyboard
only when native windows and insets agree. The existing keyboard-required default
is unchanged. Complete control geometry must settle for 250 ms within a bounded
wait; caption glyphs must fit their actual outline, and native caption bounds
must fit the visible frame. The observed viewport is checked again immediately
before dispatch. One acknowledged native DOWN/UP pair is allowed, with no retry.
The result must contain fresh unchecked consent and a disabled Confirm control.

Four existing Review sites use this helper without removing their assertions.
The added `nativeWorkspaceReviewRequiresFreshConsentWithAndWithoutKeyboard`
opens two large English/Chinese/pinyin forms, explicitly tests both IME states,
checks fresh consent, then cancels each through native input. No repository
workspace effect or acknowledgement is permitted; the attachment/installation
guard remains. Existing refusal, one-use acknowledgement and Cancel checks stay.

Workspace buttons use the theme's small cut-corner shape. An initial test-only
pilot wrongly applied the default capsule assertion, producing five failures
before Review dispatch; those failures and artifacts are retained. The corrected
helper accepts the measured rendered outline and preserves the original default
for other controls. Two actual rendered cases prove a valid small cut-corner
caption passes despite the wrong capsule assumption, and genuine large-cut-corner
glyph clipping still fails. Bounds, ellipsis and overflow assertions remain.

## Local native acceptance

API26 passes 31 exact unique methods: 20 canonical-viewer UI cases, two rendered
outline counterexamples, six scripted single-dispatch contract cases, and three
history/Kanban/phone-draft regressions. Each native start has its matching positive
terminal result; none is skipped. Six scripted cases are not hardware input.
The raw evidence also verifies 16 new native-controller receipts: eight Review,
three workspace Cancel, two history, two Kanban and one phone-draft Cancel.
Each has exactly one accepted DOWN and UP, SUCCESS and no retry or error. Eight
matching Review observations report contained stable geometry and present consent.
Only fixed action IDs, numeric geometry/timing and bounded states enter these
diagnostics; captions, paths, credentials and model output are excluded.

API26 reuses an owned, normally stopped disposable fixture without clearing data;
both package UIDs survive the exact debug/test upgrade. A new API37 fixture fails
its initial launch for a missing factory encryption initializer. A separate fresh
fixture is prepared with a verified blank official SDK initializer, without
copying a user key, disabling encryption or modifying shared SDK/old AVD data.
It remains live but ADB-offline, so no app install or instrumentation is submitted.
Boot failure/guard outcomes stay retained; this is not local API37 acceptance.

## Frozen build and identities

Parent d50518c77767427e5ad659139661c6463404ad62; 845 frozen source files,
context digest `851f2269c797fa86c0947e9239f0c367e2371f8a74730a6ac9394c612195e061`. All five changed files are Android
test source. No production source, backend, credentials, model/default, package
or signing identity changes. Required tasks pass1,006 distinct JVM cases across
96 reports, no errors/failures/skips; lint has zero errors, 51 debug/50 release
warnings. All four native ABIs, 64-bit 16 KiB load alignment and uncompressed,
four-byte-aligned DEX verify. No model weights are bundled.

| Unpublished artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| Debug | 92543246 | `2bc4f6867bb26616c63e252fbd6e52616310189b616d9a950d45b46ef83a1d5e` |
| Test companion | 7901783 | `bf55f3be92d2a9107100deafdc111c79fabc1406d3f7f2a84d1eaa4991227c48` |

Portable policies remain 422/422 from the earlier test-only pilot; unchanged
runtime-model policies 15 and stable auth 77 retain their own exact source scope.
Current compatibility run 43264707316 independently retains 40 changed used
handlers on stable 3b42157 and 100 on inspected main 2bfa6b0. Those failed upstream
findings are not waived by parser/registry/sidecar success.

## Next acceptance

Commit/push this tested chunk and require the new exact-head complete hosted
API26/API37 gate. Local passes do not relabel old failures or certify live Hermes,
workers, projects, transport or models. Then continue used-handler dependency
closure, original-production-key phone upgrade/live retention and public
publication/download verification. The prepared production APK remains explicitly
bound to its earlier f9e3835 source; these test-only artifacts are not a new signed
production build. Beta.8 remains public; beta.9 is unpublished. Every unresolved
row in [the original audit](../AUDIT_EXECUTION_PLAN.md) remains required.
