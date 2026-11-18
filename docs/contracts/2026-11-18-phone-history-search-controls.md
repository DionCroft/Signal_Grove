# Phone history search controls — 18 November 2026

The unreleased offline feature branch now exposes explicit local text, model and
date search. It passes the required build/JVM/lint tasks and 40 selected native
methods on each API26/API37, including four new search UI cases. This is a tested
OF-02 increment, not a completed OF-02, phone search qualification or release.

## Behavior and boundaries

Phone history retains its complete inventory for parent navigation and storage
limits. Search opens a review with a literal title/question/reply query, model
choices from stored/installed fingerprints, and Any date/24 hours/7 days/30 days
presets. Dates use the phone clock when Search is tapped; applied bounds and
match/total counts remain visible. Clear restores the unfiltered list without
opening another chat or altering an unsent draft. No-match results explain how
to broaden the filters. Search never loads weights, generates text or contacts
Hermes. Existing open/delete controls retain their explicit behavior.

Reads run on the controller's serialized IO path, through the strict bounded
history store, one transcript at a time. Cancellation and failures preserve the
last applied query/results and existing files; no deferred search or inference
is replayed. Refresh recomputes an applied filter after explicit changes. Text
is limited to 1,024 UTF-8 bytes and rejects NUL/unpaired surrogates. Matching is
literal and case-insensitive, not a regular expression. The unfiltered inventory
and currently open/generated branch remain independent of search results.

The review uses a scrollable body and separate Search/Cancel footer above the
IME. English/Chinese/pinyin use the existing Words layer. Model selections use
fingerprints rather than manually entered IDs. This increment adds no rename,
pin, archive, Markdown export, encrypted backup or import; those remain in OF-02.

## Source-bound checks

All required tasks pass: testDebugUnitTest, lint, lintRelease, assembleDebug and
assembleDebugAndroidTest. Independent actual XML review counts 1,071 distinct
JVM cases in 102 reports, with no failures/errors/skips. Six new controller cases
cover weights-free search, preserved unfiltered inventory, clear without file or
chat changes, queued cancellation/no replay, malformed-file preservation and
refresh after explicit deletion. Query tests include invalid Unicode.

The same app/test APK pair passes exactly 40 selected native methods on each
API26/API37, with no skips. Four new search cases verify review cancellation,
one exact text/model/date dispatch, no-match/clear behavior and bilingual text
at 1.3 font scale with complete actions above the actual native IME. The earlier
36-method local reader/actions/filesystem cohort, including seven draft-routing
cases, remains in this run. These use synthetic UI data and real filesystem
fixtures; they do not establish real model or physical search acceptance.

Both lint variants have zero errors, with 51 debug and 50 release warnings
retained. No native engine, dependency or runtime policy changes are included.

Source snapshot: `1ce13080e320cd5b3875d1f0e36a91e27ebd25ee228cb63ccb809996bf7bcf50`.

Build receipt: `20261118-224800-42987822e4bd4e15ada92dc9fea581ca`.

App SHA-256: `d9a0fc372eba6136ca62d1cb67fa0334ae4a0156d52ccc6d3216ed3d80711611`.

Companion SHA-256: `3594d52ad09626c109ded1576d81723a5d67759af05ebd5488e4b37f1cd8908f`.

## Separate physical phone checkpoint and remaining gates

The earlier original-key app/companion pairing has one positive actual offline
native branch method, followed by owner-operated real app UI fork, edit and
regenerate checks. Each real UI confirmation adds exactly one separate chat;
all earlier chat JSON bytes remain unchanged. The copied fork needs no loaded
weights. Edit/regenerate use the explicitly selected 4,096 context, four threads
and 384 output cap; settings and native source identity are stored. Actual UI
generation is performed with normal networking restored, independently of the
separate offline native proof. It does not establish this newer search pair.
[Phone source/pairing and retained failures](2026-11-18-offline-phone-branches.md).

The search state/callback changes require a matching newer app and companion;
never install its test companion against the retained older phone app. Physical
search, final-source upgrade/retention, TalkBack/large-font/lifecycle/security,
full final-head standard CI and original-key release gates remain. No new APK
is published by this commit. The full Hermes/upstream/shutdown/project/worker/
automation/integration audit stays open. [Ordered backlog](../ON_DEVICE_AI_BACKLOG.md).
