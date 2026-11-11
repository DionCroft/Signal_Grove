# Reviewed new questions can move to phone chat

Chat offers **Use unsent draft on phone** for an eligible text-only question.
Review and edit its exact text, then choose **Open as new phone draft**. This
copies the reviewed question into a fresh local draft and keeps the Hermes draft
unchanged. Choose/load a phone model and send separately. No model is loaded,
question generated or Hermes request submitted by copying. Phone and Hermes
history, model choices, settings, images, credentials and approvals remain separate.
Existing phone conversations stay saved when the displayed selection is cleared.

The source draft has controlled mutation and submission provenance. A review is
bound to its owner, revision, connection partition and selected conversation.
Confirmation rechecks current state and the authoritative stored receipt. Active,
unknown, response-only-ended and read-only conversations cannot route a draft.
Images and staged retries of earlier failed requests are ineligible. Explicit
editing creates a new question; selecting another conversation invalidates the
old review even when the text is unchanged. A readiness-only failure before a
frozen turn submission leaves an unsent question eligible. Frozen submissions
retain their original receipt/body/key and existing recovery path.

Android intents carry only an opaque one-use token. The in-process inbox holds
one bounded UTF-8 question, refuses overwrite, expires delivery after five
minutes and retains no delivery across process death. Missing, wrong, consumed
or expired tokens do not load/send anything. Rotation preserves the destination
view model without taking the token again. A blocked destination leaves the
original Hermes draft intact and reports that the handoff could not be used.

The review uses a full-window surface with safe drawing/IME insets, a scrolling
body and fixed actions. The title shares the scrollable body so multilingual
text cannot push actions outside the keyboard-free viewport. Delivery status
uses the existing header info slot; its complete explanation opens separately
without removing space from the conversation.

## Validation and limitations

The frozen snapshot passes 931 JVM cases in 91 reports, 229 policies, both APK
assemblies and zero-error lint (50 debug / 49 release warnings). Original debug
signatures, four packaged ABIs and native alignment are verified.

Fresh Android executions: 16 on API 26 and 16 on API 37. Both run
all six routing cases and ten local UI cases. Eleven additional API 37 model-
readiness, frozen-receipt, image-share and profile
regressions passed on the earlier snapshot with byte-identical view-model, draft-
provenance and fixture sources; they are retained separately from these fresh runs.
Both emulators use font scale 1.3. The review check independently observes the
native IME viewport, complete translated caption/control bounds and an actual
Android-input Cancel tap. Original exact font/IME settings are restored. Four
captured review screenshots were visually inspected. Both settled keyboard
frames show complete controls; one API 26 transition frame is retained and is
not used as steady visual acceptance. The new header status opens
its full translated explanation while preserving at least 72 dp for messages
on the shorter API 26 display; no extra status row consumes conversation space.
These are selected method sets, not a full instrumentation or release pass.

Frozen source digest: `7691e17e554f1033854d985da41d4369f7ffd1ce0e7c9c6096c287a1fd5b359e`.

- app APK SHA-256: `e724265b6e31f73dd82d25f7f722affe0210ff83243567eb18029c713a739d57`
- test APK SHA-256: `cc918d190f22fbde75a11ddbc9c3bdd740180f448eb52d338f75f236c9c70897`

The retained earlier Android runs contain fixture failures: empty editable text
was compared with its placeholder; a direct ActivityScenario launch destroyed
the source task; retry setup did not await the complete receipt; and an English-
only equality expectation did not match the translated notice. These assertions
were corrected without dropping empty-draft/no-work/receipt checks. Visual
inspection also exposed real wrapping-dialog clipping/keyboard occlusion that
semantic visibility missed. The fullscreen correction and native viewport checks
address that finding. A subsequent status row reduced the API 26 conversation
area below the unchanged 72 dp gate; moving it into the existing header slot
corrected that regression. The earlier screenshots and logs are retained.

This is opt-in routing and UI/component acceptance, not an automatic fallback or
physical current-source release. The production APK and pending phone helper
installer files are unchanged. Exact-source phone upgrade/voice/helper/model
checks, transfer process-death and guest reboot, broader accessibility/lifecycle,
the existing history input finding, the original Hermes/workspace/worker/
automation/integration audit and signed-release gates remain open.
