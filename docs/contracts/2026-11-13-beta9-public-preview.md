# Published beta.9 phone preview - 13 November 2026

At the owner's request, [Signal Grove 1.1.0-beta.9](https://github.com/DionCroft/Signal_Grove/releases/tag/v1.1.0-beta.9)
is public as an experimental prerelease (Android code 114). Publication time is
13 November 2026 at 11:48:08 UTC; GitHub release ID 444164989. The wider audit
remains paused. This makes the tested candidate downloadable before completion
of all release-acceptance rows; those incomplete checks are disclosed rather
than reported as passing or waived.

## Download and update

[Download the production APK](https://github.com/DionCroft/Signal_Grove/releases/download/v1.1.0-beta.9/hermes-agent-mobile-1.1.0-beta.9.apk)
on the phone, then install over the normal Signal Grove app. Do not uninstall.
The package and original production signer are retained. The optional debug
asset updates a separate package; no test companion is needed for ordinary use.
The in-app updater discovers this prerelease with Include preview releases
enabled and still requires download/install approval. Stable 1.0.0 is unchanged.

## Exact source and public assets

The tag resolves to `0ee08e37c90e009a70bb290301c81433a11517a5`, a commit in
main history and the actual clean signed APK build source. The 676 other code
inputs outside the physical test fixture match reviewed main 31f05c7. Later
documentation/test commits are not relabelled as the binary source.

| Asset | Bytes | SHA-256 |
| --- | ---: | --- |
| hermes-agent-mobile-1.1.0-beta.9.apk | 71,052,938 | `1837ea8f2ea94abcee63c38f630e14e72f92902fab9963d5c98756dff501f832` |
| SHA256SUMS | 519 | `fea58bfe819225b22dcf0b2237cca10db617a1446739db4cddcaade637812594` |
| signal-grove-1.1.0-beta.9-provenance.json | 2,777 | `e59f7bead7cf6dd69d61b4a16cc5942903b1c2a62565a9a50083aa8e3b1e0213` |
| signal-grove-1.1.0-beta.9-runtime.cdx.json | 106,776 | `fdbcdb5e59a25d908ac4277d17327e2283559321c67943fb37cd2fc97040984d` |
| signing-certificate.pem | 1,874 | `1edcbbd9dafe0dd454efd6c5222dc21c3c6664d0e9b8434b8290f394addb6f54` |
| hermes-agent-mobile-1.1.0-beta.9-debug.apk | 92,608,818 | `5e1f970a985c5ee51a6a51826e46d2a107bcbaacbbf143b58cc33c7203a8af85` |

All six files were fetched anonymously from their public GitHub download URLs
and checked against expected byte counts/SHA-256 values and GitHub asset
digests. SHA256SUMS matches the five other assets. The runtime SBOM is
CycloneDX 1.6 with 183 components; public provenance names the actual source,
separate physical companion scopes and incomplete compatibility/production
acceptance. No signing secret, model weight or private evidence is uploaded.

Original production signer:
`bbffacc53cc2dfecd78c598a41a44caf89623827b315bb873f62129da771e40f`.
Both APK signatures and version/package manifests are rechecked immediately
before publication. All 32 packaged native library files are byte-identical
between the debug and production variants; that does not replace final
production UI/upgrade acceptance.

## Included features and validation scope

The preview includes optional local text chat/history, native streaming/Stop,
verified model download/resume and GGUF import/licence/storage/context controls,
plus static setup guidance and a source-backed offline troubleshooting helper.
Local and Hermes authority/history remain separate. LFM3 and Qwen have physical
native evidence; arbitrary files/phones are not certified and weights are not
bundled. Existing canonical/history/project navigation and packaging improvements
in the same product source are included.

The actual build passes 1,011 JVM cases/97 reports and zero-error lint with
51 debug/50 release warnings. Independently verified source0ee standard Android
coverage is 1,135 API26/1,136 API37, with exact raw inventories, disjoint shards,
APK/source bindings and hosted-summary reproduction. Seven selected physical
native methods have positive evidence on the unchanged debug APK across their
identified companions, including helper/Chinese and thinking/visible-stream
cancellation, recovery and reload. The complete twelve-method external-model
gate and final exact production-package phone upgrade remain open. All prior
failed/unknown outcomes retain their scope.

## Explicit remaining checks

- Upstream compatibility inspection still fails pending semantic/transitive
  review of changed used handlers (last fully inspected 40 stable/101 main
  findings, not confirmed defect counts). SDK shutdown cleanup remains open.
- The exact final production APK's in-place upgrade/live/offline acceptance is
  not yet complete. Earlier production 112 reads and current debug-native tests
  are separate evidence. Existing data is not reset and no live model is changed.
- The retained source1180 API26 Demo clarification test failure still requires
  diagnosis. Later main 31f05c7 Android CI passes; that does not erase the old
  failure or replace the binary's actual source.
- Full project/workspace/worker/files/NAS, approvals/recovery/integration,
  automation/rooms/config, background/notifications/audio/accessibility/
  performance and community-release acceptance remain in the original audit.
- Natural reasoning uses the total token cap; complex prompts can reach the
  limit before a visible answer. Installed recognizer support is still needed
  for dictation and is independent of local text inference.

Publication is limited to the owner-requested preview availability. No upstream
baseline, source guard, failed CI result or unresolved audit row is changed to
claim completion. Future completed releases retain their normal full acceptance
requirements. The full goal remains paused and stable 1.0.0 remains unchanged.

[Paused evaluation](../audits/2026-11-13/PAUSED_REVIEW.md) and the
[full audit ledger](../AUDIT_EXECUTION_PLAN.md) retain the remaining work.
