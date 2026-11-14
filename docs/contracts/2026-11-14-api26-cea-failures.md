# Retained API26 navigation failures at reviewed source cea0cbf

14 November 2026. This is a failed gate, not an accepted device cohort or a
reproduction of the original source1180 Demo failure. No job is restarted and
no test/assertion is weakened.

[Android run 43585004016](https://github.com/DionCroft/Signal_Grove/actions/runs/43585004016),
API26 shard 0 job 132428348245, ends failed. The original GitHub artifact
14112486121 is downloaded through the connector, and its 11,466,516 bytes and
SHA-256 match the GitHub artifact metadata. Archive names/duplicates are checked
before reading original logs. The run metadata names source cea0cbf; APK/build
identity and complete disjoint-cohort reconciliation still need separate review.

The unfiltered instrumentation result declares/starts 540 tests, records 538
named positive outcomes and two -2 failures, and ends `FAILURES!!!`, tests 540,
failures 2. Its adb command returns 0; that is not a passing instrumentation
result. The emulator is still alive at capture and the command did not time out.

| Test | Actual failure |
| --- | --- |
| `PhoneDraftRoutingTest.cancelReviewKeepsTheRemoteDraftAndPhoneFilesUnchanged` | Could not find the `nav-chat` semantics node at line 96 before touch injection. |
| `SetupGuideFlowTest.newHostChoiceIsExplicitPersistsAndDoesNotChangeConnection` | Could not find the Settings content-description node in `open()` at line 22. |

The retained runner log places the phone-draft cancellation failure immediately
after `editedReviewOpensOnlyANewPhoneDraftWithoutSubmittingOrLosingHistory`.
That ordering is evidence to investigate, not proof of a leaked activity or an
application defect. Neither per-failure screen/lifecycle/Compose state nor a
verified cause is supplied by these missing-node errors alone. The original
`Milestone03Test.demoClarificationChoiceIsReviewedBeforeSubmission` cause stays
open separately; this shard does not contain that case.

At the observation, API37 shard 0 and API26 shard 1 jobs report success; API37
shard 1 is still running. Those status summaries are not independent raw/APK/full
cohort acceptance. Do not use successful sibling shards to waive the failures.

The next action is to capture native activity lifecycle/window and Compose state
before fixture cleanup on these failure paths, then reproduce the actual
transition without retries. Preserve original failures, case selection, review,
submission, privacy and retained-data assertions. Fix only a demonstrated cause
and complete standard final-source device gates afterward. The wider Hermes,
project/worker/integration and signed-production requirements remain active.

## Evidence identities

| Evidence | SHA-256 |
| --- | --- |
| Original GitHub ZIP | 49ef8ad7282f239f847f2b7ff632daef3f5f1c36cf8e6fbdb3a2ff10c73ea2f5 |
| Raw instrumentation log | e7a039f5701f9b64e33a8781721ece76c5209cb2d986cbc5150c17ee0faeb91e |
| Original instrumentation result | 7fe512c57f3fbe8eb5e49fa5d3e3cdd6f2327674fda2c323ad5294348d9a1ade |
| Retained runner log | d414867e40ca1bad21e86f78c07874179b373126826db5bf72e7842b7f82611f |
| Original device state | afae7ba484b614fc5afd640c5b6112570dfe9fc5ee69fe72fd25f3d4d7eebed9 |
