# UI readiness and native phone-draft viewport - 18 November 2026

The owner resumed the full goal: address UI blockers before implementing the
offline feature backlog. This chunk qualifies bounded navigation/readiness
guards and actual native Cancel geometry on API26 and API37. It adds no new
offline feature, server capability or public APK release.

## Change and why

The original Demo fixture assumed a Home click meant Chat was rendered.
The Home action now has a stable semantic tag; its fixture observes a unique,
enabled, nonzero, stable target before one original scroll/click sequence and
requires Chat selection afterward. A delayed enable, duplicate caption,
callback without navigation and permanently disabled action are covered.
There is no second click to obtain a successful result.

The phone-draft fixture previously reused a viewport across later native/
Compose reads and a screenshot. It now captures screenshots before final
readiness, checks both complete controls and their native captions, rejects
changed/torn geometry, and re-observes immediately before one native Cancel.
Readiness stays within one five-second deadline. Native tap intent and returned
completion are distinct; precise DOWN/UP receipts remain separate.

An actual late-keyboard case starts from an explicitly verified show/hide,
opens the editor at the pre-input boundary, and proves the original Cancel
location is now covered. It requires resampling before its sole native Cancel,
then verifies the draft/history remain unchanged and no model was loaded.

| Run | Old Cancel bottom (screen px) | Current visible bottom | Pre-input resamples | Native Cancel |
| --- | ---: | ---: | ---: | --- |
| API26 | 1752 | 1053 | 1 | One accepted DOWN/UP pair |
| API37 | 1815 | 1048 | 1 | One accepted DOWN/UP pair |

The original already-visible-keyboard case independently passes with zero
resamples on each API. Every helper record is matched to a successful single
native DOWN/UP pair with accepted events and no retry. Normal and late cases
still require both whole controls and full native caption bounds above the IME.

## Final source-bound checks

| Check | Result |
| --- | --- |
| Required JVM/lint/debug application/test-APK build | Successful |
| JVM | 1,011 distinct cases /97 XML reports, no failures/errors/skips |
| Lint | Zero errors/fatals; 51 debug /50 release warnings retained |
| API26 | 27/27 selected methods, no skips; fresh owned image, enlarged font and strictly prepared default keyboard |
| API37 | Same 27/27 methods, no skips; fresh owned image |
| Selected classes | Milestone03Test, PhoneDraftRoutingTest, UiRouteNavigationContractTest, UiFixtureDiagnosticsTest, RuntimeSelectionTest |

This cohort covers the original Demo actions, phone draft isolation, actual
history/scrolling/model choices and four new regressions. It is not the earlier
workspace27-method cohort or complete standard/physical/model acceptance.

Build snapshot: 1f46186eb7b8baddc5629f61cc5db9a262990196b7e6f000bc0a9ffcc15416c4.
Build parent: 228fa116fc973a56b3040fadc06b029db19d8818 with reviewed working changes.
Application APK: 413e2ea1cfea628bbdbc3db96b4dab4a6afe43a383ede592deb6c1cff6378679.
Companion APK: 37377d0561e667d3ea2ee55d39668d88bf04f952ac9dec280886463923aad7f7.
Original debug signer:
cab8b5521a7bc7d6f4c7a7a3d093f4bc74549a54785152c69d1b364881fa4f2b.

Private independent review: 5b4c2c255e1b3c7d8f7621a5825f4237985f05df49df7d39b8ba34b4a3193c60.
API26 raw log: b2c549f7d8c21ea8939d5b6a0fa7bd2c7ce4d54b6b37eb86cf8b867edca23d5b.
API37 raw log: 92fc00d8b18a3e16b95ba87aa656cadedac91366590a26d712b56b1c61946893.

The build snapshot precedes the documentation-only evidence updates. Code
postimages are independently bound to the tested context; a later commit is
not relabeled as the original APK source. Exact-head hosted standard CI is
the subsequent consolidation gate.

## Preserved failures and operating evidence

- The unchanged old-source API26 shard starts602 cases:601 positive, one failed
  native-keyboard preparation in the picker-transition case. Google Search
  crashes with an over100-jobs error; its system crash dialog overlaps the
  failure window and app native focus is absent. The run remains failed.
  Original Demo/history cases in that sequence pass; that does not establish
  the exact historical CI failure's cause.
- The first new late-keyboard regression on fresh API26 passes26/27: its initial
  hidden reading precedes an asynchronous editor opening, so its required
  firstCandidate-hidden assertion correctly refuses. Setup is then changed
  to an actual show/hide; all original assertions remain and both final runs pass.
- A reused API37 image loses ActivityManager before dry inventory; both APK
  installs have already returned Success and their exact bytes verify. The
  failure occurs before the service call and no native case is dispatched.
  The framework log records an ApkAssets finalizer timeout in system_server.
- After automatic service recovery, the existing installed pair is verified
  without reinstalling. That run passes24/27; three native-dialog cases lack
  focus. The focused system window is Application Not Responding for System UI.
  Sleep/keyguard hypotheses are disproven: display is on and device unlocked.
  Logs, window state and screenshot are retained.
- Completed old AVDs are normally stopped after evidence capture, without data
  reset, app suppression or replay. Fresh API26/API37 images run the final
  unchanged APK pair; all prior failures and receipts remain separate.

Original old-sequence raw log:
7d8c3545dc17c2a498231adf6a1519dff8c7207217bb3d6aa356dd6e5782068b.
Its system/crash log: 4dc1bb2e450d00fafbd44aad7eac57372a984efa55515701125ebcf10ada45a1.
No old failed run is waived, relabeled flaky or made positive by a retry.
No forced framework restart, unrelated-app disabling or error-dialog hiding
is used. Historical exact causes are not all proven by this guard qualification.

## Next scope

Finish the exact-source hosted gate and preserve the original audit findings.
Then implement OF-01 (edit/regenerate/branches/formatting) and the rest of
[the backlog](../ON_DEVICE_AI_BACKLOG.md) in priority order. All wider Hermes
compatibility/shutdown, corrected SDK, workspace/worker/integration, physical
phone, external-model, security and original-key release requirements remain.
This stage sends no physical-phone commands, changes no live Pi/model/credential
and publishes no APK.
