# OF-01 reviewed chat actions — 18 November 2026

Unreleased work on `feat/offline-chat-actions`; the full goal remains active.
This increment builds on [branch storage](2026-11-18-local-branch-storage.md).
OF-01 remains incomplete and the public beta.9 APK is unchanged.

Local messages now offer Edit and send, Regenerate reply and Branch here.
Every action opens a review with Cancel. The controller accepts the exact
selected conversation snapshot, checks model identity for generation, publishes
one child and invokes inference once. Repeated input while busy is ignored;
changed selections, wrong models and invalid text do not publish or generate.
A plain branch copies through a completed/token-limited reply without loading
weights or generating. Parent navigation uses recorded parent identity.

The child pending question is durable before native dispatch. Cancellation
between the IO publication and main-thread continuation records CANCELLED in
that child; failure records FAILED. Parent bytes stay unchanged. Refresh,
reopening and cold recovery do not replay either request. The update-work guard
remains held until reconciliation. These are local actions only.

Unsent drafts follow phone-chat identity in memory. Returning to a parent
restores its own draft; creating a child does not carry it into a different
conversation. Send clears the exact submitted draft after actual publication,
including a cancelled post-publication action; a pre-dispatch cancellation or
failed save retains it. Later edited text is not cleared by an earlier snapshot.
Drafts are not written to Android saved-state bundles or Hermes history.

Validation of source snapshot `9ab440b0ed2ab6e74fbe2dd78518f07b6cb3d863d9e96da2e35f2dd6a545e1da`:

- 1,036 distinct JVM cases / 99 XML reports, zero skips/errors/failures. Seven
  controller regressions cover request/context identity, exactly one invocation,
  failure and cancellation after atomic publication; six draft-bank regressions
  cover scoped text and publication. The 12 storage regressions remain positive.
- Required app/test APK tasks and debug/release lint pass, with zero errors/fatal
  findings and 51/50 existing warnings. ZIP CRCs and published hashes match.
- The same pair passes 21/21 selected actual methods, zero skips, on API26 and
  API37: eight synthetic action UI cases, three real Android-filesystem cases
  and ten existing synthetic local-AI flow cases. The real runner's dry/actual
  inventories match exactly. These tests do not load model weights or contact
  Hermes. UI cases include edited Unicode text with an IME, review cancellation,
  stale selection, wrong model, unloaded branching, parent navigation and
  English/Chinese/pinyin at 1.3 font scale.

Build receipt `20261118-190215-144faa1034cd49c8be2fdf752436b8ad`, SHA-256
`13719b4f81d4b1e344e63f064a3c828514c8a960007a0892c7c148d11c5e6813`. Debug APK SHA-256
`8cdf928cb562b7e5d939808c92bac1fddc4f29c00d4b9cb9358e4eab9d17d45a`; test APK SHA-256
`27eb401fa58df7cec304b878b8848851070357e7c59c028b8852cf46b1996bef`. Raw method transcripts and XML bindings remain in private E: evidence.

Remaining for OF-01: actual generation settings/runtime provenance, passive
Markdown/code copying, long-reply ergonomics, further branch/large-font/TalkBack
coverage and final native-model/physical-phone/lifecycle acceptance. The source
retains original model/template output handling, cancellation and size bounds;
this increment does not requalify all external models or release upgrades.
PR #1 holds the separate UI-guard full standard hosted gates. Preserve all wider
Hermes, worker, automation, integration, security and signed-release requirements.
