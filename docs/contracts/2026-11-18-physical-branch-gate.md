# Explicit physical LFM3 branch gate — 18 November 2026

Adds one separately invoked external-model method:
`LocalModelPhysicalTest.verifiedLfm3BranchesRetainParentsSettingsAndNeverReplay`.
This source checkpoint establishes compilation/policy/build acceptance. Its
actual phone result is still pending; no model/device/OF-01 completion is claimed.

The gate uses the actual native controller, the already reviewed pinned LFM3
file and isolated QA histories. It requires an actual 64-bit phone, a verified
app-private fixture and explicit `localModelLicenceReviewed=true`. It imports
the existing model into the debug library, loads at 2,048 then 1,024 tokens and
checks original generation, reviewed regeneration, a no-generation fork,
edited generation, Stop on a visible native chunk, unloaded history reads and
cold no-replay recovery. It checks immutable parent/earlier-child bytes,
model/request/runtime identities, actual load/output arguments and exactly four
native generation calls. User chat files are compared before/after and never
used as fixture histories. Native output/cancellation failures fail the method.

Five external classes now contain **13** methods (previously 12). All previous
methods and standard-suite assertions remain; no newly failing standard case
is excluded. Exact-source inventory tests and strict negative/assumption checks
still apply. The resource/licence gate is not standard CI or a skipped positive.

Required app/test builds, **1,053 JVM cases / 101 reports** and zero-error lint
(51 debug/50 release warnings) pass. The scripts policy suite runs **1,158**
distinct methods: **885 positive / 273 explicit Windows platform skips**.
Independent parsing checks actual method identities, not just the final count.
Some runner warnings/async diagnostics separate headers from their positive
status; the raw log and initial overly restrictive verifier rejection remain.

Original-key debug APKs are staged in the owner's phone Downloads with exact
hashes. Xiaomi requires normal installer interaction. The normal app remains
code112 with unchanged APK bytes; this is a new debug install, not debug upgrade
retention. No Pi service, gateway, credential/model default or worker grant changes.
Native/controller checks do not alone establish real app UI or accessibility;
full audit, release and OF-01 phone acceptance remain open.

## Exact source and artifact bindings

Immutable source snapshot: `638e73f7cb33810196d98524ead1d1bbe1eb57b7d90f9e5cc42a1a232b42d0bc`. Build receipt:
`20261118-212158-8bbd1c537e4c42f594a355eaeda9e0dc`.

- app APK SHA-256: `c4f612caaa3e6c9506d9c47125758b5ae866dea63d403c338ddb41ff073f629e`.
- tests APK SHA-256: `fc90671844e79088d98c90a1d9113df2049a7c1f52675ab0828e546fabde9fdb`.

Raw policy log SHA-256: `121f86a32233a4cc1294cc70f237f71c6150c6f98b0d23d988f59be2d600e5e8`.
