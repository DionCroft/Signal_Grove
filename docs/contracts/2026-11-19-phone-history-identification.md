# Distinguishable phone history branches — 19 November 2026

Unreleased phone history rows now show the recorded activity time with seconds,
using the device's date/time locale, and a copied/edited/regenerated branch label
when origin metadata exists. English/Chinese/pinyin labels use Words. This makes
the owner's identical-question/identical-reply branches distinguishable before
opening Organize chat. The timestamp is the stored conversation activity, not a
new observation or label-save time. These display additions write nothing and
do not change pin ordering, archive/model/date filters, prompts or model state.

Required build/JVM/lint/APK tasks pass: 1,093 distinct JVM cases/103 reports, no
failures/errors/skips; zero-error lint retains 51 debug/50 release warnings.
The same app/test APK pair passes exactly 50 selected native methods on each
API26/API37 without skips. All 49 earlier cases remain, including the new index/
review controls, real Android atomic-cancellation/storage cases and seven original
draft-routing cases. The eighth organization UI method checks three identical
question/model rows with different branch kinds and distinct recorded whole-
second times, comparing actual rendered times/labels and no action callbacks.

The first compiled fixture used 1/2/3 milliseconds, all formatted to the same
second. It is superseded before this new native method is ever executed. The
final fixture uses 1/2/3 seconds to detect incorrect timestamp binding. Its
required build and exact two-API native evidence are separate from that earlier
compiled fixture; no case pass is inferred from the weaker setup.

Source snapshot: `15e2611ae73f9deffb90bd4416c382bf2f0789e30a4433725e6d5238d607b7c5`.

Build receipt: `20261119-010918-0096638a7cea4c3e8e93675a9e48298c`.

App SHA-256: `8bdaf53ebc3f769edb7fe2d43f306003d7650df91b495c21138f3a30cebef198`.

Companion SHA-256: `2dfc9fc16f8941a0e268266aaa3cd958f0eaf662864c47445828bc1b7e361cb6`.

The [organization behavior and limits](2026-11-19-phone-history-organization-controls.md) remain. These original-key debug APKs still need the current phone upgrade/organization/read/cancel checks; its installed earlier search pair is not replaced by this commit. Phone instrumentation startup diagnosis, final-head standard CI, accessibility/lifecycle/resource/security and original-key production upgrade/release remain open. No new public APK or Pi deployment is made.

Next: stage this matching pair and verify retained data, then reviewed label/
pin/archive changes and unloaded reads on the owner phone. Preserve all prior
failures. Continue selected Markdown export and versioned/protected backup/import
in OF-02. The original Hermes/upstream/shutdown/workspace/worker/automation/
integration audit remains in the active goal.
