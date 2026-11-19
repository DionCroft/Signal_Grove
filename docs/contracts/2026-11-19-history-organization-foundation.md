# History organization storage foundation — 19 November 2026

A separate display-metadata index is implemented and tested for future rename,
pin and archive controls. It is deliberately not wired into the app yet. It adds
no organization button, changes no phone history and does not complete OF-02.

The index keeps conversation IDs, model fingerprints, optional titles and two
flags outside the transcript directory. Organization cannot rewrite a question,
reply, activity timestamp, generation settings or branch source hash. Titles
allow up to 80 Unicode codepoints/320 UTF-8 bytes, with no blank/padded/control
or unpaired-surrogate text. The versioned typed index has a 64 KiB/50-entry bound;
unknown schema/extensions and invalid fields are refused without replacement.

An update verifies the current conversation/model and exact reviewed metadata.
A stale review cannot overwrite a newer title/flags; a reused ID bound to another
model is refused. Default preferences remove the association. Explicit reset can
remove orphan metadata after deletion; it never deletes a transcript or silently
prunes records. Publication syncs a unique partial before an atomic replacement.
Cancellation before publication preserves the prior index and cleans only the
current partial, keeping old interrupted evidence. This is one serialized
controller's storage foundation, not multi-process transaction, power-loss,
Android lifecycle or protected-backup qualification. Titles are private display
data; no code adds them to a model prompt, remote history or export.

## Validation and exact scope

Required tasks testDebugUnitTest, lint, lintRelease, assembleDebug and
assembleDebugAndroidTest pass. Independent XML review counts 1,082 distinct JVM
cases/103 reports, no failures/errors/skips. Eleven new cases exercise Unicode
and branch-hash preservation, stale reviews, model identity, invalid/path-like
input, corrupt/oversized/non-file indexes, cancellation during read and before
publication, explicit reset after deletion and a full orphan index.

The same source-bound app/test pair passes 40 selected native regression methods
on each API26/API37 without skips, including the existing reader/actions/search,
filesystem and seven draft-routing methods. These retain the existing UI checks;
there is no organization-specific native method or physical organization pass
in this increment. Zero-error lint retains 51 debug/50 release warnings. The
native engine, runtime-policy fingerprint and dependencies are unchanged.

Source snapshot: `baab5a3efe1009dd351d8228bdedd86b86f1df5d3098526823cbd088408d4a4e`.

Build receipt: `20261119-000202-84fd32efa6834cafa19eccd6bb04984d`.

App SHA-256: `89a8e08a930eb084c714f28d144145fd46be65920948b7cf29a1a10e3a352636`.

Companion SHA-256: `3594d52ad09626c109ded1576d81723a5d67759af05ebd5488e4b37f1cd8908f`.

Next: wire explicit reviews into the controller and history UI, join titles and
archive filters into read-only search, retain the complete inventory for limits
and parent navigation, and verify real Android index behavior/keyboard/cancel/
retention. Markdown export, versioned/protected backup/import and every original
remote audit/security/release gate remain. [Ordered backlog](../ON_DEVICE_AI_BACKLOG.md).

The earlier search build has its own [physical checkpoint](2026-11-19-phone-history-search-physical.md), including a retained stalled instrumentation run. It is not a physical acceptance of this newer APK. No new APK is published by this commit.
