# Closed saved-history projection and real bearer API reads

## Behavior and source review

The regular Chat history reader now excludes rows explicitly marked `display_kind:
hidden`, for both user and assistant roles. Those rows still count toward the
raw offset, page size and memory budget, so a full hidden page does not hide the
visible reply on a later page. Reasoning sidecars are not imported as messages.
This corrects an observed privacy defect: the actual API can return nonempty
content together with its hidden marker.

A first-page resolved compression continuation remains supported. A later page
reporting a different conversation fails before repository cache replacement.
Malformed JSON, absent/non-object message rows and contradictory supplied paging
metadata produce closed English/Chinese/pinyin advice, without parser snippets
or a raw cause. The exact cached conversation and messages survive a failed read.
Legacy responses omitting optional identity/paging metadata remain supported.
The existing bounded page-size reduction for oversized reads is unchanged.

Review covers the official [API reference](https://hermes-agent.nousresearch.com/docs/user-guide/features/api-server/)
and pinned [API implementation](https://github.com/NousResearch/hermes-agent/blob/3b421578588243f920886e6fe79a6faa87ea026e/gateway/platforms/api_server.py).
The inventory API uses an optimistic full-page `has_more`; two records read with
limit one require a third, empty read. The app's normal Chat bearer API already
supports closed Desktop histories. No additional dashboard login/browser, native
Stored adoption, grants, endpoint, schema or automatic retry is introduced.

## Actual-owner Android matrix

`compatibility_fixture_owner_workspace_tcp.py --read-api-history` starts the
actual Git-bound GatewayRunner, profile authorities, native control, dashboard
lifespan and real bearer API adapter, using the existing dependencies and fresh
disposable homes. Only the history mode creates random per-profile fixture API
keys. A bounded outer proxy forwards complete, size-limited response bodies for
specific read-only GETs. It cannot forward a turn, mutation or arbitrary URL.
No dispatcher, authorization, projection, model or database result is simulated.

The dataset is synthetic: each profile has two closed Desktop conversations,
503 messages total. A shared remote ID has a user message, 499 hidden rows with
nonempty sentinel content, then a visible reply with a reasoning sidecar. A
profile-only conversation tests isolation. Fixture seeding is direct local DB
setup, not a product write or inference acceptance claim.

| Execution pin | API | Fixture | Git-bound imported modules |
| --- | --- | --- | --- |
| `3b421578588243f920886e6fe79a6faa87ea026e` | 37 | 305 | 1,093 |
| `3b421578588243f920886e6fe79a6faa87ea026e` | 26 | 306 | 1,094 |
| `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` | 37 | 307 | 1,059 |
| `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19` | 26 | 308 | 1,059 |

Each case passes one native method with seven checks and twelve actual GETs:
ten HTTP 200, one wrong-profile-key 401 and one foreign-ID 404. Both histories
contain exactly the two visible messages in separate scoped Room caches after
real database close/reopen. Foreign-origin reply/control remains read-only;
credential preferences are unchanged. The test checks paged inventory and the
normal message repository; it explicitly seeds matching Conversation cache rows.
It does not exercise the normal inventory-merging UI flow.

Independent read-only host SQLite checks cover every session/message column,
including losslessly typed BLOBs. All before/after digests match. Both stores
retain two closed sessions and 503 messages, zero admissions, workers, rooms,
mutation receipts and transcript locks. Independent copies of each exact Android
fixture DB pass quick_check: two scoped conversations, four visible messages,
zero server/management operations or pending controls. All four child processes
exit cleanly with no shutdown warning. This does not close the earlier native
workspace/owner shutdown finding or full lifecycle acceptance.

Cases 305/307/308 also pass the original parent supervisor. Case 306's child and
Android checks passed, but its parent exited nonzero because the snapshot helper
was defined below the parent entry point. The original failure is retained;
independent check 313 verifies its exact exited PID, HTTP result, host snapshots
and copied Android DB without rerunning it. The helper was moved before entry
and a regression check added. Case 306 is independently reconciled, not relabelled
as an original supervisor pass.

## Reproduction, failed attempts and artifacts

A controlled old source267 app with the current companion reproduces the defect
against the actual API in case304: its cached messages contain the 499 hidden
rows, failing the exact two-message assertion. The host remains unchanged. The
four fixed cases use source301, app SHA-256
`328b388c3f5aa6c81d3afaab969321174d7e8fea5e328c8b822fee59e8ac2412`,
test SHA-256 `ec119d8a9b65414e374a1fd98f9134ac8d246322e7f0a57ac4b1a0771b4d0f88`.
Their source digest is `e4cc0243aace1f995bbabec2dc79a30bc8a92b6b3c42acf39db750b1c06ef8ae`.

Earlier attempts remain separate: prototype275 used the wrong seed helper;
prototype276 proved bearer/profile reads but ended on an unrelated workspace
exit oracle. Cases281/282 stopped on BLOB snapshot encoding; 284/286 stopped on
the mistaken exact-total paging oracle. Cases296/298 received incomplete JSON
because the outer proxy used one network read instead of all chunks. None is a
valid hidden-row red/green result. The full-body proxy fix has bounded chunk tests.
An early verifier invocation before build301's terminal receipt failed its
state precondition; it did not validate an unfinished build.

Run303 passed API37 UI/auth/storage (35 methods) and its malformed-cache method,
but its new mock history case rejected MockWebServer's localhost hostname. API26
ran 17 UI methods with one consent checkbox remaining Off after a tap; its
physical cause remains unattributed. The localhost fixture URL now uses
explicit loopback; the advice-state test uses one semantics activation and still
asserts Off to On before Confirm. Other consent tests retain physical tap checks. The semantics-state check does
not settle that physical input finding.
No repeated tap or removed assertion obtains acceptance. Those failed receipts
remain retained. Final-source results and artifact comparison follow below.

## Additive compatibility watches and remaining scope

The baseline adds only two consumed helper watches: `_project_client_message`
and `APIServerAdapter._message_response`. Original f0 fingerprints are extracted
without executing upstream from API SHA-256
`73175d668c0c683209ad047191921fe899e84b8a429470081a718ae7d163d3f6`.
The two reviewed execution variants require their exact whole API-module hash.
Every original contract value, mobile scope and review mode remains unchanged.
Historical digest tests explicitly exclude only these two additive keys from
their old projections; their golden hashes and fourteen room guards are retained.
Separate tests reject changed/missing helpers or an unreviewed source context.
The initial policy309 failures exposed this missing historical projection split;
no golden hash was replaced. The existing canary review findings remain open.

Same-ID history edits during paging have no snapshot guarantee. Missing legacy
identity metadata cannot establish an unchanged continuation. Already cached
hidden content cannot be identified retroactively; a successful fresh read
replaces it. Image-placeholder/large image acceptance, server compression/races,
normal inventory merge/UI, current-source physical/deployed checks, permissions,
actor/profile/owner loss, active/unknown turns, Windows worker/project/files and
all original audit/local-AI/release requirements remain required. No APK release
is made. Next close real permission/identity/recovery cases and the authoritative
Windows project/working-copy binding, retaining the separate phone history gate.

## Final frozen checks

Source315 freezes 742 files under digest
`9e2152535bbc5f19e3e64feb1cdc84a11bef10a0bca90cfa40684aa39826262f`.
The required build passes 968 JVM cases / 94 reports, debug and release lint
with zero errors (50 / 49 warnings), both APKs, original debug signing identity,
four native ABIs, ELF alignment and no bundled model weights. Policies pass 263
cases. Every frozen file matches before documentation is added.

Final app SHA-256: `c9d6af4ce5e2fae29100bbb786073943846deb6e19ece5c7882cb242d99a23a3`.
Final companion SHA-256: `ead19f2eeeaaf1801949d6b68a8058331dc89cb30d23e79e4aabbdc493a75b36`.

Fresh source315 selected suites pass **37 methods on each API**: 17 viewer UI,
13 authentication/workspace, 5 storage and 2 history methods. The standard
history case uses a clearly labelled mock; the four actual-owner cases above
remain separate. Native keyboard settings are restored exactly. These are
selected suites, not the full standard Android or physical-device release gate.

The 253 product/debug/native/Gradle inputs are unchanged from the actual-owner
source301 app; twelve DEX files, resources and manifest are byte-identical.
Twenty native libraries rebuilt under the new build context, so raw APK hashes
differ. The four actual-owner cases stay attached to their measured source301
artifacts. They are not relabelled as final raw-APK or native-inference acceptance.

Private E-only receipts retain full source/build/APK bindings, instrumentation,
Git-bound runtime imports and independent SQLite verification. Key indexes are
`history-artifact-equivalence318.json`, `closed-history-attempts319.json`,
`closed-history-independent313/result.json` (SHA-256
`ca6ce10105cb19b32f501e060e0ee1d7c7928463bf0dbf1f062dd8778beb8a72`)
and `closed-history-old-red317/result.json`. No token, model, DB or raw personal
history is committed.

## Prior-main hosted CI finding

Before this commit, Android run `43048244021` at `a46901e2c09f027f0b121462466a402d424db889`
had a passing build/verify job. API26 job `130628885319` failed after 653 starts
and 649 named positive completions out of 1,105 declared tests; the ADB command
exited 255 and post-failure device state was unavailable. It is an incomplete
failed suite, not a timeout or full pass. The retained artifact reports three
prior failures: the bilingual workspace action/native-IME boundary assertion and
two HistoryReadFeedbackTest reply-height assertions (29.33 dp versus 48 dp).
These remain open; selected local passes do not waive hosted layout evidence.
API37 job `130628885309` was still running at this pre-push observation.
Compatibility run `43048244141` had passing parser, pinned registry and sidecar
jobs but stable/main used-contract review failures. No baseline waiver is made.
The next bounded UI work must reproduce and resolve these exact findings while
retaining the full audit, deployed/physical and release requirements.
