# Reviewed phone history organization — 19 November 2026

Phone history now exposes Organize chat: optional title, pin and archive choices,
with one Save labels confirmation. Blank titles restore the first-question title.
Review/cancel and explicit Active/Archived/All search views work without weights.
Archived chats remain stored/readable and count toward the existing 50-chat
limit; an Archived chats shortcut and empty-active explanation keep them reachable.
Pins change display order; original activity timestamps stay unchanged. Searches
match custom titles as well as original question/reply text, with existing model
and date filters. Clearing search returns the active view.

Labels remain in a bounded private sidecar and do not enter generation context.
Parent transcripts, branch source hashes, model bindings and open chat identity
stay intact. New branches get their own default labels. The unfiltered catalog
is retained for capacity and parent navigation, including archived conversations.
No read/open/send silently changes an archive flag or starts a model. Exact
reviewed metadata/model guards refuse stale saves before publication. A typed
conflict distinguishes that refusal from a later IO/read error; UI does not
promise a write failed merely because a post-publication refresh failed.

Cancellation after atomic publication reconciles the actual stored catalog
before idle. Deletion reads metadata before changing a transcript, then finishes
its bounded explicit deletion/label cleanup with an acknowledgement once the
chat deletion is known; the row/current selection updates immediately. A bad
index refuses deletion before its transcript changes. Unknown/failed outcomes
are not automatically retried. Existing index schema/size/Unicode guards remain.

The full-screen organization review has a scrollable body, bounded editable title,
labeled toggleable rows and separate Save/Cancel above the IME. Invalid titles
remain visible with Save disabled. English/Chinese/pinyin use Words. Ordinary
TalkBack navigation and final physical-device acceptance remain required.

## Verified source and cases

Required tasks testDebugUnitTest, lint, lintRelease, assembleDebug and
assembleDebugAndroidTest pass. Independent XML review counts 1,093 distinct JVM
cases/103 reports, no failures/errors/skips. Eight new controller cases cover
unloaded organization, unfiltered/archive reads, stale reviews, queued cancellation,
cancellation after publication, labels excluded from model prompts, exact explicit
deletion, corrupt-index refusal and independent branch labels. Three additional
search cases cover custom titles, pin/view/date semantics and model replacement.

The same APK pair passes exactly 49 selected native methods on each API26/API37,
with zero skips. Seven new actual UI cases cover Cancel, one exact reviewed save,
invalid-title refusal, restoring a default title, archived-only access, explicit
view choices and bilingual 1.3-font complete actions above the actual native IME.
Two new real Android filesystem cases verify round-trip/search/branch hashes and
cancellation before atomic publication while retaining an older partial. All
40 earlier reader/actions/search/storage/draft cases remain in these runs.
Both lint variants have zero errors, with 51 debug/50 release warnings retained.

Source snapshot: `31e0145d7cae8b2ca62d48a9e3e0cd8de514e0b1d1177ccc404b57a2bdde00d1`.

Build receipt: `20261119-003131-6b06174669114b71ada560b8d2974f53`.

App SHA-256: `eeb31acfa0fa9907737458d451c8eb23fd701db440014fd413bc00119a54af2b`.

Companion SHA-256: `8391d372efc31b889a073eba74e5180e490652d413b5f01538c7acd88421abdc`.

## Delivery limits and next step

These original-key debug APKs are not installed on the owner phone or publicly
released by this commit. The earlier [actual phone search checkpoint](2026-11-19-phone-history-search-physical.md) retains its exact earlier app pairing and stalled synthetic-run failure. Current physical organization, startup diagnosis, final-head standard CI, accessibility/lifecycle/resource/security and original-key production upgrade/release remain open. The native engine/runtime policy and dependencies are unchanged.

The four phone test chats now include identical-looking generated branches.
Add last-activity/branch-kind row details before the next consolidated phone
review so the owner can distinguish them before organizing. Then verify the
matching app/test upgrade and explicit label/save/cancel/archive/read behavior.
Finish OF-02 selected Markdown export and versioned/protected backup/import.
Every original Hermes/upstream/shutdown/project/worker/automation/integration
audit requirement remains in the active goal. [Ordered backlog](../ON_DEVICE_AI_BACKLOG.md).
