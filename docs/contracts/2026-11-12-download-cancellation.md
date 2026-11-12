# Download cancellation under I/O saturation and beta.9 checks

A subsequent [foreground phone run passes full HTTPS resume](2026-11-12-foreground-model-download.md).
The failed outcomes below remain historical evidence.

## Reproduced defect and bounded fix

Two JVM regressions occupy the downloader's only I/O thread while headers or a
response body are blocked. Both fail against the unchanged downloader: cancelling
the job does not call OkHttp cancel within two seconds. The cancellation watcher
was queued on the same occupied dispatcher. The fixtures independently release
their blocking call in cleanup and verify one request, cancellation identity and
retention of the original partial prefix.

Only the watcher's thread-safe OkHttp cancel uses an unconfined coroutine now;
blocking network/file operations stay on I/O. See [Kotlin dispatcher semantics](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-dispatchers/-unconfined.html)
and [the pinned OkHttp call implementation](https://github.com/square/okhttp/blob/parent-5.5.1/okhttp/src/commonJvmAndroid/kotlin/okhttp3/internal/connection/RealCall.kt).
There is no change to TLS, request headers, range/hash/size validation, atomic
installation, retries, original memory guards or native timeout assertions.

The complete fixed build passes **1,006 distinct JVM tests /96 reports**, no
failures/errors/skips, and zero-error lint (51 debug /50 release warnings).
Both added regressions pass (10 and 7 ms). Local build OOM and configuration
failures are retained separately; reducing host build concurrency yields a full
successful build without excluding tests or ABIs. The resolved production graph
is exactly unchanged: 150 Maven versions /95 artifacts. Earlier known-indexed
advisory review retains its stated limits.

## Exact candidate and device scope

Frozen build-input digest:
`47ceba3875b9479fcfb7958a885ebe3e06a0f5fdad5f780bbc7356eb0a4f8a8c`.
Beta.9/code112 retains both original signing identities, minAPI26/target37, all
native ABI/page checks and uncompressed, four-byte-aligned DEX. No model is bundled.

| APK | Bytes | SHA-256 |
| --- | ---: | --- |
| Production | 70,993,978 | `ea68dc391086d7422d497c7e26a6252a7e67cda8c1269bcc1ab9d929a7137781` |
| Original-key debug | 92,543,466 | `9dc7267f6cfcc7756de918e6854d186a96da8e58d2c64d366456066b5e7f9243` |
| Test | 7,865,091 | `0262e95d6194fe7e42e570f623c47374ee8342c527e1aa3eb7e9df9ceec1cce5` |

The API37 emulator upgrade retains both UIDs and351 scoped core hashes; its
already-exact test APK is not reinstalled. The owner's ARM64 API37 debug upgrade
retains all three UIDs, both original model hashes and local chat/preferences.
Before/after encrypted snapshots verify; all three protected metadata files are
identical across the new phone tests. A prior updater-timestamp-only difference
is separately reconciled. Production remains beta.8 and is not changed.

## Native HTTPS outcomes remain partial acceptance

Both original methods execute once per device against isolated roots and actual
pinned public HTTPS, without changing 10s cancellation or 300s resume assertions.

| Device | Cancellation observations | Explicit discard | Explicit resume |
| --- | --- | --- | --- |
| API37 emulator | 34 and18 ms | Pass | Timeout;668,174,446-byte partial retained |
| Owner ARM64 API37 phone | 3 and3 ms | Pass | Timeout;23,246,323-byte partial retained after freezer recovery |

The phone kernel reports its test cgroup frozen despite the activity manager's
unfrozen report. One resolved debug-launcher foreground action unfreezes the
same process (kernel1 ->0), allowing the existing run to report its timeout.
No test is restarted, partial replayed or freezer control written. A temporary
USB-awake setting is restored to its exact original value. Java traces require
root and are not collected. The phone recovery is not clean resume acceptance.
Both native runs finish two cases with one pass and one failure. Strict success
verification correctly rejects those batches. The earlier two timeout failures
and their partials remain [historical evidence](2026-11-12-uncompressed-dex-candidate.md).

Full download/hash/atomic-install acceptance still needs a correctly observed
foreground run and investigation of slow transfer. The latest hosted API26
shard1 also fails keyboard preparation before test inventory; no standard cases
from that shard are accepted. Earlier complete device/UI/offline results retain
their own source scope. Process/reboot, final standard union, original-key
production upgrade, compatibility, SBOM/provenance and publication gates remain.
The full audit remains active; no beta.9 public APK or release is claimed.
