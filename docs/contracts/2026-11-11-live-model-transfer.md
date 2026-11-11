# Live Android model transfer and cancellation recovery

The production LocalAiController and its unmodified default HTTPS-client policy
now pass real public-model transfer checks. The fixture supplies no substitute
HTTP responses, credentials, trust manager, model bytes or inferred completion.
It uses unique no-backup model/history roots and an explicit model/licence opt-in.

| Live scope | API 26 | API 37 |
| --- | --- | --- |
| HTTPS body received, explicit stop, retained partial, bounded return to idle | Pass | Pass |
| Explicit discard removes only the partial and retains unrelated test data | Pass | Not run |
| New controller retains the same partial without automatic download | Not run | Pass |
| Explicit resume, exact full checksum, metadata checks and atomic install | Not run | Pass |

API 37 resumed the actual pinned LFM3-1.7B QAD Q4_0 to 1,049,615,680 bytes with
publisher SHA-256 43a7d09a0e9cba91b63eb7052488b9564569bddfd65754d2e426465e572a7452.
The complete private file is independently rehashed and its lfm3 metadata checked.
No model is automatically loaded and no chat is created. Explicit cleanup deletes
only that test model. API 26 deliberately stops after cancellation/discard; it is
not counted as a full resumed-transfer result.

The stopped byte count is visible in controller state. Tests require the work
guard to remain active during transfer, then clear only after bounded stop and
partial reconciliation. On API 37 the new controller sees the retained size;
a short idle interval leaves its byte count/hash unchanged before explicit resume.
The existing response validator requires the exact resumed 206 range before any
append. TLS, identity encoding, no-Hermes-credential client isolation, no transport
retry, pinned revision/size/checksum and atomic publication rules are unchanged.

## Retained failure and implementation correction

The first live API 37 run received a valid 8,393,915-byte publisher prefix and
installed nothing, but Stop surfaced operation_failed. Its terminal failure and
partial remain retained; there was no blind replay. A paired JVM regression then
proved that a cancelled blocking body read can escape as IOException. The active
failure control remains an IOException with its original cause preserved. Its
first oracle required object identity; that was corrected for coroutine exception
wrapping before verifying the baseline had only the genuine cancellation failure.

The downloader now checks coroutine cancellation when catching a blocking I/O
failure, before forwarding a still-active I/O failure. This lets the existing
controller cancellation path reconcile the partial and show its resumable state.
It does not swallow active connection failures or change network policy.
[OkHttp 5.5.1 documents cancellation as a possible I/O failure](https://raw.githubusercontent.com/square/okhttp/parent-5.5.1/okhttp/src/commonJvmAndroid/kotlin/okhttp3/Call.kt).
The application retains strict append checks because
[HTTP permits a server to ignore Range](https://www.rfc-editor.org/rfc/rfc9110.html#section-14.2).

The corrected application passes the same live assertions. Its Android test APK
is byte-identical to the one that exposed the API 37 failure. No success condition
was removed. Both fresh checks verify installed app/test hashes, exact source and
terminal method/status sets with no skips. Original debug signatures and package
IDs are retained. These are emulator component checks, not physical-phone,
normal download-dialog, process-death, reboot or full release acceptance.

Required builds pass 918 JVM cases in 90 reports, 229 policy cases, both APK
assemblies and zero-error lint (50 debug / 49 release warnings). The two added
JVM cases distinguish cancelled blocked reads from active I/O failures. Native
inference, local/remote authority, the pending phone-helper APKs and Pi/Spark
configuration are unchanged by this networking correction.

Frozen source digest: `7505b520388109d82a0665b0cf8c9bd9668b2fb6d2ca083b83ca2d65d6b955de`.

- app APK SHA-256: `f9a443bcbe121e45c277d453ddef9ba7ca676e79551600e6ffa3f4b4457e944b`
- test APK SHA-256: `4000dcb68999c86fb36767d8ffb580431750ee50bcbfcbe45cb899053a8a4c8f`

Reproduce by explicitly running LocalModelTransferIntegrationTest with
allowLiveModelDownload=pinned-lfm3-licence-reviewed. Select the resume method on
API 37 and the discard method on API 26. The resume method fetches approximately
one GiB from the public publisher; review its separate model licence first.
Without this explicit argument the live network cases are skipped. Routine CI
therefore does not establish this live-transfer acceptance.

Still required: physical-phone download/upgrade/helper checks, normal UI consent
and transfer recovery, interruption at installation boundaries, real Android
process/background/reboot behavior, second-model physical/cancellation coverage,
the full original Hermes audit and exact-source signed-release gates. No new
production APK was published by this chunk.
