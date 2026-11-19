# Exact on-device prompt context

19 November 2026. Development branch.

The Native LLM counts the same template fragments and special tokens as
generation. It treats user content as plain text, includes the selected earlier
completed and output-limited turns, and reports both occupied prompt and remaining
output room. The existing generation limit reserves 384 tokens for reasoning and
answer text together. The byte, turn and context limits still apply.

The composer measures an eligible draft after 300 ms. A submitted turn or
generated branch rechecks its exact prompt before publishing a pending exchange.
An over-capacity draft keeps the existing history, and a user can explicitly
copy it into a new unsent chat. The branch review measures its selected prefix.
An unsupported exact measurement is labelled as unavailable.

## Verified evidence

The Windows build passed 1,148 JVM tests and both lint variants with zero errors.
The two owned Android emulators passed 73 focused tests each without skips.
The native Android 17 test used the exact previously approved LFM3
1.7B QAD Q4_0 GGUF. Its tokenizer measured 489 prompt tokens for the boundary
fixture. Generation accepted a reservation of 23 in a loaded context of 512.
One additional output token was rejected with no streaming callback. A subsequent
count also succeeded. The unchanged garden-answer case timed out with two virtual CPUs and passed with four. Both runs are kept.
The first native compile stopped
on a stale helper-variable reference. The corrected build and both device results
are bound below.

Physical context acceptance, explicit earlier-turn selection and editable reviewed
summaries remain open. The separate physical phone backup and restore remains
tracked in the [offline backlog](../ON_DEVICE_AI_BACKLOG.md).

- Build receipt `20261119-143254-c1ee28b0a8664e03b6e4d5dd671dca2d`
- Source snapshot SHA-256 `cf6e03515051155ac9994b8b99c9632560b73e7713b7b53371ba888c19f99e9d`
- Debug APK SHA-256 `6f36d482611453739c66981da1ae771f7c8925b87da7e7fd16aeb536bf1ae4eb`
- Test companion SHA-256 `3bc505b715a626b54fad871928dbcd9ba57b7f0372a2962ede00a8353a53a187`
- Android 17 LFM3 instrumentation SHA-256 `be2c96b0c9d3f20507e9fef1fd14363f6734cd7b4a58fc7706327ccdf7c5b9a7`
- Four-vCPU garden instrumentation SHA-256 `ba0c10fd1f1ec194f8d86bb140a423284704a3971a10d556a6b64265ec6475a7`
- API 26 focused instrumentation SHA-256 `f937c8f0a9b7828cf5d21cee5c9bce4bc971c0a29826f180d3209e22afcf9239`
- API 37 focused instrumentation SHA-256 `35855cb5ecdd7e7153f6c030e6c4377cad72618df72c91fa57ea44f5c7c9b071`
