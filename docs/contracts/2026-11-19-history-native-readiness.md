# History keyboard readiness and physical checkpoint — 19 November 2026

## Retained failures

The full [c46 Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/44436729536)
failed on API26 shard0: 561 methods, one failure. The exact organization Save
assertion received `pinned=false` while expecting `pinned=true`; title and archive
matched. The other three device jobs succeeded. The artifact's raw failure is
preserved; no full-suite acceptance or product root cause is inferred from the
job summaries. Artifact14491569146 ZIP SHA-256:
`58feedc9f33faf26ac9383d7d6489cae6bdc29bb48481fb59e72845d555fe491`.

On the owner's phone, instrumentation restarts the debug process and MIUI rejects
the test-host activity start with result102. Pre-opening MainActivity does not
prevent this. A bounded diagnostic stops with no accepted case. A separate
reviewed diagnostic observes the same denial, then supplies one matching
MAIN/LAUNCHER foreground intent to the exact test host. This unblocks startup;
the identification method executes and fails because scrolling to a timestamp
does not ensure its adjacent branch label is visible. Both failed diagnostics
and the earlier stalled search run remain preserved. No device security policy,
networking, model or real conversation is changed by these diagnostics.

## Test correction and fresh local evidence

Only two Android-test source files change. Identification now scrolls explicitly
to each branch label while retaining all three exact timestamp/label assertions.
The organization tests explicitly focus the title editor, retain the actual IME,
observe coherent native-window/inset state and complete caption/row geometry,
wait for a stable target and reobserve it before a single native tap. Each toggle
has one five-second readiness budget. There is no additional DOWN or click
retry. The subsequent checked state and exact Save title/pin/archive/model/
identity assertion remain. Cancellation still publishes no labels or generation.
The bilingual 1.3-font-scale case also exercises both toggles above the real IME.

Required build/JVM/lint/APK tasks pass: 1,093 distinct JVM cases/103 reports,
no failures/errors/skips, zero-error lint with 51 debug/50 release warnings.
The same freshly built app/companion passes exactly 56 selected methods on each
API26/API37 with no skips: all 50 earlier cases plus six existing scripted
single-dispatch contract cases. Those six scripted cases exercise the input
boundary; they do not establish Android input acceptance.

Separately, each API records six actual history-toggle inputs (three pin and
three archive). Every recorded attempt reports SUCCESS, exactly one accepted
DOWN/UP, no retry, a focused/attached/shown window, and the IME visible before
and after. The observed input lies within the actual visible frame. Exact
case inventories, raw terminals, APKs and source manifests are independently
reconciled. A stopped earlier build receipt is retained and never treated as
success; a fresh wrapper completed the required tasks and publication.

Source snapshot: `6b5fb3a2406c4cc3161377443029ef0576b2ec2d21937c301ab9e9e737900b52`.

Build receipt: `20261119-091056-36e4fca55731459f8123da3860fd2691`.

Fresh emulator app SHA-256: `f3b9d98b4df979e80c1e19f49b2e9a2639c58c44ae3359476574f07b366ad24b`.

Updated companion SHA-256: `960fac43d1de4cba540906e90dbde71773fa8dd6518114c29942176b3cc40035`.

API26 actual raw SHA-256: `0799bc2d5f87e570f4aec6800a06464e59aa8a09981dfa57230e867eb2dc88fc`.

API37 actual raw SHA-256: `26258fd1557d75fbe293cd5041b2d412bf27907d36b01ff5835edccdcb97a900`.

## Physical upgrade and remaining acceptance

The owner installed the previously consolidated original-key app8bdaf53ebc3f/
companion2dfc9fc16f89. That in-place upgrade retains all three package UIDs,
15 protected cold metadata files, both full model files and all four earlier
real-UI conversation files. The normal code112 production app remains unchanged.
No model is mapped and no chat is replayed on opening the debug app. This upgrade
is now verified; the earlier document's pending-install statement is historical.

Every application/build input matches that installed app's source. The delta
contains these two Android-test files and seven already committed evidence
Markdown files. The companion may therefore be updated alone after verifying
the connected device and installed subject. Any physical result must identify
the retained app8bda and new companion separately; it must not be labeled as a
fresh-pair or production result.

The requested real-UI title/pin/archive Save has no observed outcome yet; the
phone disconnected. Preserve and inspect its current organization index before
another action. Physical organization/cancel/unloaded archive reads, current
phone native cases, fresh complete hosted coverage, accessibility/lifecycle/
resource/security and original-key production upgrade/release remain open.
Neither selected local coverage nor a green PR compatibility job that skips
upstream scanning closes those gates. No new public APK, main merge or Pi change
is made in this checkpoint.

Next: run fresh complete hosted coverage and finish the bounded physical
organization/read/cancel check with the companion-only correction. Continue
OF-01 accessibility/lifecycle and OF-02 selected Markdown export/versioned
protected backup/import afterward. Every original Hermes/upstream/shutdown/
workspace/worker/automation/integration requirement remains in the active goal.
