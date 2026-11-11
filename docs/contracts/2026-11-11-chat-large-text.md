# Large-text chat space and hosted UI findings

## Observed defect and behavior

The prior-main hosted API26 run recorded three UI failures, then lost ADB before
finishing its full suite. A bounded local run325 uses the same 1080x1920 / 420 dpi
and system font scale 1.3. It reproduces both HistoryReadFeedbackTest failures:
the message viewport is 77 px, or 29.33 dp, and the saved native reply is clipped.
The workspace/native-IME case passes locally, so its hosted failure remains open.

The three-line on-device AI entry occupied its own row above messages. Chat now
places that action in its existing conversation-control row as an explicit 48 dp
phone icon. Its full English/Chinese/pinyin accessibility description and same
LocalAiActivity action remain. The Settings entry retains its translated text.
This recovers message space without shrinking the configured font, error details,
read-only explanation or native reply. No model, server, history, consent or
handoff behavior changes.

History feedback tests retain their original minimum viewport and fully-visible
native TextView checks, cached-message equality, translated error, scrollable
full details and read-only controls. They also check the phone action's 48 dp
bounds and exact translated description. The first compact candidate (source326,
run328) fails that new check: its visual IconButton bounds are 40 dp. It already
measures a 134.48 dp message viewport, but that observation is not a passed
reply test. The final candidate explicitly sizes the control to 48 dp. Failed
receipts/screenshots remain separate; no assertion was lowered or removed. Source331
then passes all 61 selected methods on API26. On API37, 45 methods pass plus five
draft cases, while one draft case stops because the input window is absent at
the instant after its insets became visible; its ten local-AI methods are not run.
Those failed/incomplete run332 records remain distinct from final checks.

The workspace test retains its strict action/caption-versus-real-IME assertions.
Failures now include exact Compose/window/visible-frame coordinates and attempt
a screenshot while rethrowing the original failure. No product workspace-layout
or mutation logic is changed. A local pass does not explain or waive the hosted
failure.

The test viewport observer now returns an unready result only while the actual
input-method accessibility window is absent. All three stable-frame waiters reset
their stability timer on absence, keep the existing five-second timeout and require
a non-null native viewport. Actual frame/inset/caption/Cancel assertions remain.
No invented keyboard boundary or extra click substitutes for the native window.
See Android's [inset phase documentation](https://developer.android.com/develop/ui/compose/system/insets-ui#insets-and-jetpack-compose-phases)
for inset-update timing; the tests still require the separate native window.

## Final-source validation

Source336 freezes 743 files under digest
`01eaa9666227c14f9d81ae8c5bdf891c1eee5458612f7212817b2acd0b1ac60c`. The required build passes
968 JVM cases / 94 XML reports and both lint variants without errors
(50 debug / 49 release warnings). All 263 policy cases pass; their inputs remain
unchanged across the final test-wait/48 dp adjustments, independently checked.

- Final app SHA-256: `9d559c69fa5c4671cc9e6854d7681f2a6d59b47fa4444a6e28c465fde29851be`.
- Final companion SHA-256: `f1837562d2591c7bf32ec36d9a66beb782bb2dc3d9cdb4f7310b7e2f5cb1a43d`.

Fresh run337 passes **69 distinct native methods on each API**: 17 viewer UI,
13 authentication/workspace, 5 storage, 2 history-contract, 8 history-feedback,
6 phone-draft, 10 local-AI UI and 8 existing-history UI methods. API26 runs at
system font scale 1.3; API37 at 1.0. Exact starts, positive named completions and
no skips are independently parsed from every invocation. This is a selected
suite, not the full standard device suite or physical model gate.

The final API26 missing-history viewport measures 134.48 dp, compared with the
29.33 dp reproduction; API37 measures 428.95 dp. The phone action measures exactly
48 dp on both. Existing cached replies are fully visible through the native
TextView checks. Installed APK hashes and original UIDs match; font and native
keyboard settings restore exactly. Four screenshots were inspected: replies and
large translated workspace actions are readable. The API26 history capture
retains post-dialog dimming; it is not settled color/animation acceptance.

The original debug signer, four native ABIs, ELF alignment, no bundled weights
and exact frozen files are checked before documentation is added. Failed run328
stops before draft/local UI methods; those unexecuted cases are not counted as
passes. The prior real-owner history/protocol measurements stay bound to their
original APKs. This UI-only patch is not fresh Pi, real-owner, model-generation,
physical-phone, complete accessibility or full Android-suite acceptance.

## Evidence and remaining work

Private E-only receipts retain run325, compact-chat-red329.json, the superseded
source326 build/run328 and compact-chat-candidate333/result.json, plus final
source/build/device/geometry proofs in compact-chat-final339/result.json and
compact-chat-visual341.json. The test-only native-window readiness correction is
measured with final source336, without replacing the earlier failure record. No APK, model, credential, raw DB or personal chat is committed.

The guide's stale beta.6/current-beta.4 download directions are refreshed to
[the existing beta.8 release](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.8),
verified as the published preview with Android code 111. This does not publish
the development changes or change the application version.

At the pre-push observation, main `152e52b` Android run `43061820253` has a passing
verify job; device jobs `130674003394` (API26) and `130674002690` (API37) are running.
Compatibility run `43061820267` passes parser/registry/sidecar jobs and fails used-
contract review. These are not full passes or waivers. Exact final-head workflow
handles are recorded after pushing.

Next inspect the full hosted device results and reproduce the outstanding
workspace/native-IME failure from its coordinate evidence. The prior ADB loss
also remains unclassified. Keep the complete audit/local-AI requirements in
[the ledger](../AUDIT_EXECUTION_PLAN.md): real permission/actor/profile/owner loss,
active/unknown recovery, Windows worker/project/working-copy files and reviewed
binding/dispatch, current-source phone/deployed checks and signed-release gates.
No new APK release or full-goal completion is claimed.
