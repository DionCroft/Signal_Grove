# One-use native dispatch claims and current hosted evidence

This retains the earlier one-use claim checkpoint. The
[current owned error-path evidence](2026-11-14-native-service-error-paths.md)
adds immutable attempt observations, 124 Pi methods and five actual native
error/replay cases. The earlier source/receipts remain unchanged.

14 November 2026. Failure-path review identifies a no-replay gap in the initial
service adapter: its own in-memory spent set cannot exclude a newly constructed
adapter, while `assert_current` proves journal bytes but does not grant an
external action. A caller reopening a pending intent could pass that check.
No live action uses the initial component. This checkpoint fixes the boundary.

## Claim the intent once, on its original live owner

`CutoverJournal.claim_dispatch` rechecks actual journal names/bytes and requires
the original native PID and live operation owner, exact pending operation/step
and durable authorization digest. A nonblocking lock protects the claim, and
its spent set belongs to the journal rather than an individual adapter. The
service adapter must claim before RefUnit or StartUnit/StopUnit effects.

Replacing the adapter with the same live journal cannot release a claim. A
forked process cannot use its parent's authority. Reopening pending bytes does
not own the operation and refuses even if it is unknown whether dispatch ever
occurred. The original durable intent precedes this in-memory claim; a crash
requires inspection and separately approved recovery, never re-execution.
Claim failures do not rewrite or reset history. This is not an approval issuer,
work fence, source preimage verifier or automatic compensation mechanism.

Four new actual POSIX regressions verify spent-claim sharing, reopened pending
refusal, exact authorization/operation/step and a real forked child's refusal.
The combined Pi cohort passes **all 122 named methods with no skips**: 99
coordinator/journal methods and 23 service-boundary methods. The fresh actual
owned lifecycle also completes six distinct native jobs, refuses six spent
actions, ends eight payload incarnations and verifies eight stop/post helpers.
Independent recovery checks raw names, journal/snapshot/source hashes, process/
cgroup/unit absence, package inputs/caches and conserved live state. Non-service
lifecycle prerequisites remain explicitly synthetic. Actual manager fault/late
job recovery, PM/backups/admission/publication/reload/live-user and full cutover
acceptance remain open.

The first combined unit run fails because its private controller omits the
reviewed lock-subprocess code hash from child metadata. All four new methods
pass in that failed run, but no overall acceptance follows it. Read-only
recovery proves parent/child terminal and that the service phase never starts.
The failed logs remain; the metadata omission is corrected, then a new fixture
uses fresh operation/root/unit identities with the exact original assertions.
No failed/unknown native service action is replayed.

The complete Windows Python suite runs **764 cases: 697 pass, 67 POSIX skips**.
Sixty-four coordinator/journal/watchdog platform methods pass on the actual Pi;
three older platform skips retain separate scope. No Android/build inputs or
published APK change. Original security/on-device/upstream/project/worker/NAS/
integration/phone/release requirements remain active.

## Independently recovered hosted Android matrix

The already-started source `654d839e99eaa4079661a3125f747738a9f672b4`
[run 43609260527](https://github.com/DionCroft/Signal_Grove/actions/runs/43609260527)
finishes verify, all four native shards and final exact coverage successfully.
No rerun/cancel is issued. Six evidence archives match GitHub byte counts and
SHA-256. Raw start/success identities independently prove disjoint exact unions:

| API | Executed distinct standard methods | Shards | Failures / skips |
| --- | ---: | --- | --- |
| 26 | 1,142 | 541 + 601 | 0 / 0 |
| 37 | 1,143 | 542 + 601 | 0 / 0 |

The actual extracted app/test APK bytes match all partition/aggregate receipts.
All 97 JVM reports total 1,011 cases, zero failures/errors/skips; both lint
variants have zero errors, with **52 debug / 51 release warnings** retained.
Android/build inputs remain unchanged between this source and this checkpoint.
Twelve external-model methods, the physical phone, original-key production
upgrade and full live interoperability are separate gates. Passing old failure
cases does not demonstrate the original Demo/navigation causes. Compatibility
review still needs the previously observed 40 stable / 152 main used-handler
differences and transitive behavior; no baseline is reset or waiver introduced.
The public beta.9 production APK is unchanged.

| Hosted APK | SHA-256 |
| --- | --- |
| Debug app | `b818812dfb5f3ae3676066801768839e9700db36280b20228cb1aa2414d3d04e` |
| Debug test companion | `4db236e83d1494bfcd512f32049a71089f7eadf2c474894b08dfda4eb0cfe39d` |

Next exercise actual service failure/unknown completion and replay refusals in
owned native fixtures, then finish all remaining concrete adapters and the
fresh backed-up runtime-only forward/recovery packet before separate live
approval. Preserve live data/credentials/models/grants and unknown work.
[Initial service checkpoint](2026-11-14-native-service-manager.md) ·
[Runtime packet](2026-11-14-runtime-cutover-packet.md).

| Evidence | SHA-256 |
| --- | --- |
| Current journal | `18168257a6f795094c5ef529796592886885c9b15d14ebea31ee471a8017eae6` |
| Current service adapter | `38102fb48b6ff0027fc709132e8dcae8af95e277bc1eb0fd8e9c67915c6c7455` |
| Journal regressions | `0c147a82f054556b9bb126cf31580d10b5aab23dedf09204c6020e643caa92b3` |
| Native terminal receipt | `7266c44f0fdee99095835b33487bd252685ee4065f159a4d3a9e91773efb07de` |
| Independent native review | `c7cb7a1e5b878240fb47f8c521113d05bea86de1e7a5c2686a5abb3531ea7c54` |
| Preserved failed unit receipt | `e43d9da40f553090e7a6683f8fd179109adb0ec44258b80c646b3bd13d6dfee0` |
| Failed unit-only recovery | `da0682dacf14ae85aac06bfe60c7cb61cb530494cc71da80f3a9a1756c7a24ae` |
| Full Windows suite | `7ac88af980cafe7e9ece5d3e24e1f36f157ce875f6e2d4959517f01c38571710` |
| Independent raw Android matrix | `7542741333ec0a80e5f202e55015ef329475df6274200f54b778ea70ab944e48` |
| Independent actual APK/JVM/lint | `27062635defaf98302d663a463191d6195c614f5f425b33b7a09c1b414f773d4` |
