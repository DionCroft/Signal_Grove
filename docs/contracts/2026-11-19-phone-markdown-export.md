# Reviewed phone chat Markdown export

The Phone history action prepares a frozen snapshot of the selected saved chat.
The controller checks its model, timestamp, branch origin, last state and labels
before presenting the review. The review gives the title, saved activity, size,
exchange count and suggested filename. The Android picker creates the document
at the user's chosen destination. A local folder keeps the file on the device.
A cloud document provider may sync the plaintext file.

The ViewModel consumes one picker result for the reviewed snapshot and retains
the selection in memory during rotation. Android saved state carries a boolean
interruption marker. A recreated process can report an interrupted export.
Choosing again requires a fresh review. The update guard stays held through
picker selection and output close. Explicit cancellation requests that the
provider stop opening the file and checks for cancellation between output chunks.
Stop or an unconfirmed provider result can leave a partial file.

## Export verification

Build receipt 20261119-103706-bacf3b82cdc54807a4e126fa2fd673d2 captured source
`e0955397eccf5646a1c7e6f6aa76d36ce946da64fe74da0c6c05e19875f21eba`. The complete local JVM suite passed 1,107
methods in 104 reports. Both lint variants passed with zero errors
and 51 debug and 50 release warnings.
The matching debug app and companion passed 61 selected cases
on API 26 and 61 on API 37. Three new cases cover review and
picker cancellation, exact resolver output and duplicate callback handling,
plus interruption and invalid destination handling. They supply the picker
result to exercise the real Android resolver and keep the full suite repeatable.

The separate real system picker check navigated from Settings into Phone
history on API 26 and saved a synthetic Unicode chat into Downloads. The
document was 502 bytes and matched the independently constructed
expected content byte for byte. The source hash matched before and after.
The source snapshot for that check was `f784b9532c441d5b4e7ba1f94d7ad7976f1ee9bfafb2ed50b8dbd8cc43279946`. Only Android test
files changed between that snapshot and the latest local build.

- App SHA-256 `4d180ba57b85f94b9f6b2a6a61e20a3976a304deb729932032e20a4fe6452358`
- Test companion SHA-256 `1766d5f33d433772662b9cd149f13da225e2880ab7e39c48299d9e954583fe91`
- Native API 26 log SHA-256 `ac5494ea67fd7da52ebd3a40a5049cb893e5f3fe876359987e8a0e1671c2e2a8`
- Native API 37 log SHA-256 `d839a1fda17ca5a5ec25d735e110e842dcebbcc77de9129ae0af0c87f19c2356`
- API 26 system picker output SHA-256 `ec40e6b1a6f73325966d61e863f5ef6312484ca2cb7d5fc85b7a99649682154c`

## Retained history checkpoints

The first new APK pair passed the three new export cases. The wider API 26
cohort failed when the All filter tap left Active selected. The next run
exposed a hidden-keyboard assumption in the corrected filter fixture, and a
late keyboard fixture tapped before its keyboard appeared. The test changes
now observe the actual coherent viewport, require the All control to be
selected before Search, and require an actual visible keyboard after the
controlled reopen signal. Both controls still receive one native tap and
their original result assertions. The earlier raw failures remain preserved.

- First failed API 26 log SHA-256 `6c3e6abeb1d12e044f73e11b1046b791ff7150c9db1154af2641106a9ba17757`
- Second failed API 26 log SHA-256 `aec90b2e13c43cc1811ac518656af906d90174fee95607298a60a907fd5910ae`

The previous history branch passed the full [Android CI run](https://github.com/DionCroft/Signal_Grove/actions/runs/44496075372).
Independent verification matched all four partitions to their actual test
inventories and APK pair. API 26 passed 1,182 and API 37 passed
1,183 standard methods with zero skips. The six pin and
archive inputs per API each had one accepted DOWN and UP with real IME visible.
The CI checkout was `0cbdd82900a1a933fd52d44d6b1e1b3fe737a907`, the same tree as
`88a8d050c16d8c2637af5379d7007966d6af2e66`. This covers the previous history
source. Full hosted coverage for the export branch is pending.

The owner's companion-only upgrade retained all three UIDs, 13
checked metadata files, both models, the checked database bytes and all
four existing chats. The corrected branch identification test passed once
on the real phone, after observing MIUI deny the test host and supplying one
matching foreground activity. The actual phone log SHA-256 is
`59f545eeec53a93d5d22985da89a992c5ade565f0953f75a899251ce6f5efd42`. Physical organization Save, Cancel and unloaded archived
reads remain open, as do the physical export, encrypted backup/import and
final release checks. See the [underlying format](2026-11-19-selected-markdown-foundation.md)
and [earlier UI failures](2026-11-19-history-native-readiness.md).
