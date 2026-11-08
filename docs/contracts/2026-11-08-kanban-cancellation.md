# Kanban cancellation visibility and outcomes

## Interaction

While host-Kanban management is busy, Cancel occupies the AlertDialog dismiss
footer. Close remains in the confirm footer. Both use minimum 48 dp targets and
the existing small pixel-theme shape. The body scrolls independently; receipt
insertion and body position do not control the availability of cancellation.
English, Chinese and pinyin labels reuse structured Words.

The existing `ManagementViewModel.cancel()` remains the sole action. It stops
foreground waiting and invalidates late foreground responses. It does not prove
that a remote write or worker was stopped. A write that may have been sent keeps
its durable unknown receipt; the app neither replays it nor presents a late reply
as a new successful operation. Existing actor/profile/home checks and approvals
are unchanged. No transport, repository, schema, dependency or server change is
part of this UI correction.

## Regression

The new native case uses the existing authenticated, prefixed fake management
server and its original ten-second held-write gate. After observing a sending
receipt, it scrolls a trilingual body to the beginning. The unchanged application
fails because Cancel is not displayed, while its Close footer stays visible.
The exact failure and PNG are retained. This proves the scrolling-layout problem;
it does not establish the precise timing cause of an earlier full hosted failure.

With the footer fix, both full controls stay within their dialog at the top and
bottom of a body that exceeds its viewport. The test then cancels and retains the
original requirements: one write only, an unknown receipt and no late result
report. Six existing cancellation call sites address the fixed footer directly;
their read/write, approval and receipt assertions are retained. No test or fixture
deadline was extended.

## Evidence and scope

All required builds pass 836 unique JVM cases in 82 raw XML reports and both lint
variants without errors. The selected native cohort has 25 executions: 12 on
API 26, 12 on API 37 and the new case on compact API 37. The full-profile selection
contains six existing held-operation cancellations, five existing bilingual
workflows and the new regression. Six top/bottom busy-footer PNGs were reviewed.
Preferences, source bytes, original keys, display/font/AVD configuration, reverse
bindings, 28 legacy cache files and five staged beta.8 assets retain their hashes.
All 657 frozen inputs matched before documentation edits; tested code is unchanged.

Earlier observer mistakes are retained in validation. The unchanged single-case
probe passed but its wrapper demanded a plural summary. The expected old-UI
regression failed, then its recorder requested absent history-only sidecars.
Independent read-only reviews reconciled those terminal results without reruns.
The baseline record lacks raw before-config/reverse values and does not claim
their equality. The final cohort records and compares complete before/after
metadata and collects the actual Kanban PNG format.

The separate receipt-return caption clipped at its rounded edge in the original
footer checkpoint. Its subsequent correction is documented below; the original
six footer images did not accept every dialog label.

Full exact-head hosted acceptance and the wider integration/room/worker audit
remain open. This is an unreleased development change; no owner phone, Pi, live
worker or publication acceptance is implied. See [validation](../VALIDATION.md).

## Receipt-caption follow-up

The Return to management receipts body button now uses the same existing small
pixel shape. Before/after inspection confirms intact English/Chinese/pinyin
captions, including the first English R at API 26 font scale 1.3. Only this shape
property changes; action, text, tests and controller/request behavior are unchanged.
Required builds pass 836 JVM / 82 XML and both lint variants without errors. The
test APK is byte-identical to `b583162`. Three fresh executions of the existing
pending-scroll case, one per preserved emulator profile, pass with six new PNGs
inspected. The parent's 25-case proof stays separate. All 658 frozen inputs,
keys/preferences/display/configuration/reverse bindings, 28 legacy caches and five
staged beta.8 assets retain their recorded proofs. The correction is unreleased;
full hosted and broader integration acceptance remain open.
