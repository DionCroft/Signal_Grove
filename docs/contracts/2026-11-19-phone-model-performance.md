# Phone model performance settings

19 November 2026. Development branch.

Signal Grove saves context, CPU thread cap and reply token cap per installed
model SHA-256. Presets choose the settings shown in the
[phone model guide](../ON_DEVICE_AI.md#choose-settings-for-a-phone-model).
Advanced choices stay within the model and native engine limits. During each
explicit load the effective thread count is limited by available processors.
The existing minimum-free-RAM test runs after releasing previously loaded
weights, and the model card shows that threshold.

Saving settings writes a private atomic sidecar. Saving does not unload the
model, turn on generation or change settings for an active load. Returning
to the screen restores previous choices for the exact installed model. An
older library without settings uses Balanced, and deleting a model removes
its settings. Opening an old chat or restarting the app sends no message
and leaves the weights unloaded. The exact loaded settings drive the live
context meter and reply reservation. A saved reply records those settings
with its existing request metadata. Summary drafting and the setup helper
retain their own bounded generation budgets.

## Verified behavior

The exact E-drive build passed 1,155 JVM cases across 109 reports and both
lint variants with zero errors. Original-signer debug and instrumentation
APKs ran all 82 focused Android methods on each owned API 26 and API 37
emulator with zero skips. The new checks cover per-model isolation,
persistence across reopening, context and processor limits, invalid saves,
model deletion, Cancel, large-font bilingual review, and explicit Load
with active and saved settings shown separately. The prompt reservation
and saved request settings use the same effective limits as generation.

- Build receipt `20261119-174849-6a45c177788f427ea1df0ee6507e3622`
- Source snapshot SHA-256 `fc572d20a7bbf7f460edd3d9728d9e871179cad4ad9055ec03a88fc505cfacf5`
- Debug APK SHA-256 `8952635fb9e7303bb15025a71ebcb4a18005d917789904a5927c34d3853357d0`
- Test companion SHA-256 `adbcef5ffa5383ad15c4f295b3c09cde468f1363112a8685469859c248177660`
- API 26 instrumentation SHA-256 `cd924b8faf17b6cd2181c8c4d86235ef1ea02fe0dc68950fbd60aecb3e4f6754`
- API 37 instrumentation SHA-256 `414b63a6ef6c9a229b139c7c2b975c526053307714a54bc6a7121967d38e05f1`

Native sampling stays on the reviewed greedy policy. Measured memory and
generation timing, the offline benchmark, physical sustained performance,
and overall release acceptance remain open.
