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

## Real LFM3 trial and preset adjustment

The verified Android 17 app loaded the pinned LFM3-1.7B QAD Q4_0 file
while Airplane mode was on, Wi-Fi was off and there was no default
network. The initial Fast choice saved and reopened after an app restart.
It loaded with 1,024 context tokens and four CPU threads. Two fresh chats
sent the same short hello request. Both recorded their effective settings
in the saved exchange.

| Output cap | Visible reply | Terminal result |
| --- | ---: | --- |
| 256 tokens | 0 UTF-8 bytes | Token limit |
| 384 tokens | 34 UTF-8 bytes | Token limit |

Saving the 384-token choice left the active 256-token load in use until
an explicit reload. Both original chat files and the initial capped turn
retained their hashes. The model file matched the pinned SHA-256, and the
emulator's original Wi-Fi and Airplane mode settings were restored.

The tested result uses APK `8952635fb9e7303bb15025a71ebcb4a18005d917789904a5927c34d3853357d0`. The 256-token transcript
SHA-256 is `0a747cd3b2d5cfcd8d6e6b45ea9ee8b49877524cfe61a469b3673a1f5b141fbb`. The 384-token transcript
SHA-256 is `1d3052680253b89ca4905de6a289c1475e2928cb334a21fbe122b8abd33fdb59`.

All three default presets now retain the established 384-token reply
budget. Fast reduces context capacity. Battery saver reduces context
and the CPU thread cap. Smaller output caps remain available under
Advanced settings. This comparison measured one request and supports
retaining the original budget. Comparative speed, energy use, and
physical performance remain open.

## Preset adjustment verification

The final fixed-snapshot build passed all 1,155 JVM tests and lint with
zero errors. Both Android emulators passed all 82 focused cases on the
exact amended APKs. The first build's source check held its APKs after
the documentation changed during compilation. A fixed-snapshot rerun
verified the same application inputs, published the APKs, and supplied
the emulator runs below.

- Build receipt `20261119-181823-2490ce32e21a42ed8a0114878814f2e0`
- Source snapshot SHA-256 `700add59f8d9d896ac175d941e7427f42cd4e678380d877f3392a482cc8ecaf9`
- Debug APK SHA-256 `e98b635f64f870d252aec4532550180bde688ecd4d9c824d7ff4265232d3aee4`
- Test companion SHA-256 `adbcef5ffa5383ad15c4f295b3c09cde468f1363112a8685469859c248177660`
- API 26 instrumentation SHA-256 `9e7a6abb6e2d2de6dd630d1886741fa1474755dda92a0bb45e8ccb38848c2846`
- API 37 instrumentation SHA-256 `b6749db7e6f5dcceb764b34f0527e78a6d224c53d535f8f5373451995c2674f4`
