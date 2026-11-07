# Existing history layout — 7 November 2026

The history dialog previously placed a large multilingual search/filter header outside a weighted chat list. On small screens the header could consume the list viewport. Filters, help and rows now share a single bounded scrollable body, with pagination and Close kept outside it. The paging fixture reaches an older Discord row, imports its actual messages and confirms zero writes.

A separate controlled API26 check showed that the long fixed read-only explanation plus disabled composer left too little chat space with Chinese, pinyin and 130% font enabled. A compact read-only bar now opens the full scrollable explanation; Send remains disabled. Existing VM/request/repository origin guards, source retention, profile isolation, draft data and Room schema are unchanged.

All four required Gradle tasks and 548 JVM cases passed. The same 17 origin/storage/history/title methods passed on API26 and API37, with original debug signer and installed APK/source checks. Strong UI checks require a readable message viewport before the deliberate write rejection and after explicit banner dismissal; they await the controls dialog and an observed metadata GET, then verify lock/fork stay disabled. The controlled pre-fix failure and the earlier incomplete/failed full hosted runs remain retained.

Five fresh small-screen screenshots were reviewed. The compact chat, full reason, older-history row and disabled controls are readable. The initial history screenshot also revealed an unresolved keyboard/footer overlap: Search can acquire focus automatically, and semantic visibility alone did not prove a real tap could reach the footer. Initial focus, IME-aware layout and an actual Android-input pagination regression are the exact next correction. This checkpoint is not a release verdict.

The API37 emulator stopped before compact-suite installation/test preflight. Its existing configuration and userdata were preserved on normal restart; the separate resumed 17-case run passed. No device reset, reseed, resize, owner-phone fixture, production server change or foreign-history write was performed. Private screenshots, transcripts, IDs, endpoints, source archives and receipts remain outside Git.

## Keyboard and Android-input follow-up — 7 November 2026

The history title, search, filters and rows now share the scrollable body. The short localized More footer retains the full Load more accessibility description. The dialog respects keyboard insets, initially focuses an explicitly eligible Close button after its window gains focus, and dismisses the keyboard when selecting a conversation or completing search.

All four required Gradle tasks passed with 548 JVM cases. All 18 origin/storage/history/title cases passed on each owned API26 and API37 emulator, including a new case that checks initial focus, observes the actual keyboard, and uses real Android input to page and close while it is open. The same original-key APK pair was verified installed with unchanged source inputs, display, density, font scale and AVD configuration. Fresh normal and keyboard-open screenshots were reviewed. Earlier focus/layout failures remain retained; no reset, resize, data clear, skipped case or owner-phone credential fixture was used.

These focused checks do not replace full exact-source hosted CI, production assembly/signature/manifest checks or retained-data phone upgrade acceptance. The broader audit and native worker project-file execution remain open.

## 7 November 2026 — legacy history navigation regression

The complete API26 run at `9711672` executed 936 methods: 935 passed and the Milestone02 search/pin/rename/local-delete case failed because Pin was absent from the current lazy viewport. The exact missing-node reason, complete log and digest-verified artifact are retained. It was not an Android input-permission rejection. API37 also completed its full 937-method run with 936 positives and the same sole legacy Pin failure. Both original cohorts remain failed and retained. No blind repeat or premature publication was performed.

The legacy test now dismisses its dialog keyboard, scrolls the actual history container to each requested row control, asserts displayed/enabled actions and keeps the original pin, exact renamed title and empty-after-delete database assertions. A fresh actual-device screenshot captures the reachable row. Application code, networking, origin/approval guards, version defaults and Room schema are unchanged.

The four required Gradle tasks and all 548 JVM cases passed. On both owned API26 and API37 emulators, all 21 origin/storage/history/title/Milestone02 cases passed on the same original-key QA pair, with exact installed APK and frozen source checks, unchanged configuration/display/density/font scale and no uninstall/reset/data clear. Fresh screenshots on both were inspected. The explicit local debug versionCode111 override preserves existing emulator installations; it is not a release APK or owner-phone update.

The accepted original-key production110 phone installation remains separately recorded. New exact-source full hosted gates and final signed-artifact provenance are still required before beta.7 publication; the old-source publisher is blocked. The independent status-form repair at `310ccc7` has its own 577 JVM and 53/96 focused evidence plus an open clipped form-caption defect; it is not part of this hotfix. Private logs, screenshots, IDs and source archives remain outside Git.

## 7 November 2026 — image-share wait and complete-suite budget

The b02 full run42336563273 passed API26 with936 unique methods. API37 recorded820 positives and one ImageShareTest failure: its finite wait predicate threw because no Compose root was registered immediately after requesting the activity handoff. The run later hit the75-minute instrumentation deadline while progressing at case822/937, leaving116 unfinished. Its digest-verified archive, full failure and partial output remain retained; no hung-test or application root-cause conclusion follows. No beta7 assets were published, and prior publishers were blocked.

The image-share predicate now allows zero roots only during its original5000ms bounded poll, then requires a displayed actual review. All VM identity, draft text, no-auto-send, import and recreation assertions remain. The95-minute complete-suite budget is copied exactly from the reviewed development runner; one invocation, finite timeout, partial-output retention and failure validation are unchanged. The reviewed Windows daemon fix keeps compiler rendezvous files in the configured temporary root. Application inputs, networking, version defaults and Room schema are unchanged.

A clean build with task-output reuse disabled passed all four required Gradle tasks and548 JVM cases;14 CI-runner checks passed. The same original-key QA pair passed all24 existing-history/title/Milestone02/image-share cases on each owned API26/API37 emulator, with exact source/APK/configuration/display/density/font checks and no reset, resize, uninstall or data clear. Production input hashes for199 files match b02. Source snapshots and reports stay private. Local code111/beta.8-qa is an explicit emulator-upgrade override, not a public or phone-update artifact. New exact-source complete Android and compatibility gates, final original-key public artifacts and publication verification are still required. Main and the stable release remain unchanged.

## 7 November — common history layout release backport

The four common app/test files from featurea7dcfa0 are backported byte-for-byte:
external structured Search caption, existing-tag editable search locator,
native Android IME/caption/glyph assertions and SDK-compatible dialog fitting.
The feature source passed all four tasks with593 JVM/lint0 and all30 affected
cases on each owned API26/API37. Four actual normal/keyboard captures were
inspected. Its earlier27px/43px red failures, ineffective coordinate diagnostic
and failed all-platform-fitting cohorts remain retained; the source freeze is
closed and the feature chunk is committed/pushed. Raw JVM/lint reports were
retained separately before clean release packaging.

This is a release backport, not final release acceptance. The release stays at
110/beta.7 and excludes unpublished status forms. Its required clean build,
552-case JVM/lint checks and exact-source full hosted Android/compatibility
gates remain pending. The four common files have identical Git blob bytes to
the tested feature; that narrow equivalence does not replace the release's
complete native gate. Existing local QA devices remain at111; no110 debug APK
will be installed over them. Fresh hosted API26/API37 devices will exercise the
complete exact-source suite, preserving the no-downgrade constraint.

The predecessor e5 already passed full552 JVM/936 API26/937 API37 and all four
compatibility jobs with independently verified artifact hashes. Those remain
predecessor evidence only. Its exact production8605 owner-phone update retains
both earlier test replies, completed status, connected state and empty draft,
confirmed by the owner and read-only screenshot/XML. No new turn, reset or owner
instrumentation was used. New final-source original-key assets/provenance,
upgrade checks and public download verification are still required. Stable1.0
and main6b809e6 remain unchanged. Old publishers remain blocked.

## 7 November — clean release backport and fresh variant reports

The required clean build passed all91 tasks in10m7s. Its552 unique JVM cases
have no failure/error/skip. The first verifier correctly stopped when the clean
tree had no release lint XML: the standard lint task had generated only debug
lint. No final proof was accepted from that partial verification, and the
partial reports/error record remain retained.

Explicit lintRelease then passed in7m33s on the same566 frozen inputs. Both
debug and release XML reports now have fresh timestamps tied to their own
invocations and zero error/fatal findings. The original CAB signer, public-debug
version110/beta.7, all source hashes and all four common-file Git bytes identical
to featurea7dcfa0 were verified. Retained clean validation pair SHA-256 values:
app ab70c795593dab574abce81c73ab347b387d517ca87b289b8995b5f3e26fcbac;
companion b37cca3171dd6393444ae0550ae6525c613855b8c9b03df6a8689bbda3331931.
Raw input ZIP eae2c81d092d4ed22d375f2c3940d14e88f06179ec8e3068d44bc01e515e8db0.
These uncommitted-backport validation artifacts are not final signed release
assets. No local device was installed/downgraded; current-release native
acceptance remains the pending full exact-source hosted gate.

Scope correction: featurea7dcfa0's593 JVM/30 native per API and fresh debug lint
remain verified. Its retained release lint XML was timestamped06:56UTC, before
the11:06 feature build. Earlier statements treating that cached XML as a fresh
release-variant check must be read as debug-variant acceptance only. This
backport's explicit fresh release check is separate evidence, not a retroactive
feature release pass. Required commands will be documented explicitly by variant.

Fresh official-ref inspection identifies stablev0.23.0 at the previously
reviewed2bd source. The source watcher passes at mainaf5c2014, retaining the F0
baseline, seven reviewed guarded variants and four handler replacements, with
no known findings or changed consumed mobile contracts. This is source-watch
evidence, not live feature parity or a replacement for full hosted gates.
Read-only owner-Pi API/health and model-observer checks also returned200, with
credentials kept on the Pi and no model/default/service/agent-work changes.
Neither observation certifies the phone's current request choice or worker
project execution. The566-input local source freeze is now closed.
