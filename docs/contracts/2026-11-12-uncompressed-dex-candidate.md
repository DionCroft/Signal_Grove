# Uncompressed DEX candidate and retained-data emulator checks

An API37 emulator inventory process fails during Android startup, before test
bodies run. Its stored ANR shows the main thread in libz/ART ZipEntry extraction,
with no application classes initialized. The original APK stores all DEX entries
compressed. The candidate explicitly sets packaging.dex.useLegacyPackaging=false,
using the documented [Android DEX packaging option](https://developer.android.com/reference/tools/gradle-api/9.4/com/android/build/api/dsl/DexPackaging).
All DEX entries in app, test and production APKs are now uncompressed and four-byte
aligned. This removes that decompression path, not every possible startup ANR.
Minimum API26, package names, signing keys and native/runtime application code
remain; planned beta.9 increments versionCode111 ->112. No model is bundled.

## Frozen candidate checks

The complete build succeeds: 1004 distinct JVM tests /96 reports, zero-error
lint (51 debug /50 release warnings), both debug APKs and the signed production
APK. All source inputs are frozen/verified, native LOAD alignments are checked
(16 KiB for 64-bit), and signatures match the original production/debug keys.
The first artifact parser expects an obsolete aapt2 sdkVersion label; its failure
is retained. Correct verification requires minSdkVersion26 and target37 without
changing artifacts. Sensitive-file/pattern review also passes.

- Production: 70,993,762 bytes, SHA-256
  `963e5cca5e3dcda4cff8998bef723c0499ffc2fb286e51867ed27081c3bb3c51`.
- Original-key debug: 92,543,658 bytes, SHA-256
  `6c567118b8dee66a5503b1c17c11467889c0b50a7c8502768ba563a8796449af`.
- Test: 7,865,091 bytes, SHA-256
  `0262e95d6194fe7e42e570f623c47374ee8342c527e1aa3eb7e9df9ceec1cce5`.

Larger APKs trade download size for avoiding DEX inflation at cold startup.
Production has not been installed or published; the original phone app is intact.

## Actual emulator acceptance and retained failures

API37 in-place debug/test upgrades retain both UIDs and all351 scoped core file
hashes before opening. The cold two-case inventory completes (19.279s total;
JUnit0.307s). Inventory is not functional model-transfer acceptance.

API26's app install exceeds the host's120-second observation timeout. Before any
retry, Android's historical session records native success; the exact installed
APK hash, original UID and66 core hashes reconcile it. Only the not-yet-installed
test companion is then installed once. Both UIDs and all66 core hashes remain.
No app install is replayed, package data reset or emulator reboot occurs.
The exact candidate passes all10 native LocalAiFlowTest cases, no skips/failures:
licence/import consent, disabled/explained controls, model mismatch/stop, clipboard
review, grounded helper/diagnostic opt-in and bilingual large-text layout. Its
synthetic screenshot is visually inspected. Native run127.970s; inventory4.261s.
No real model, download, Hermes call or owner phone is involved in that UI batch.

The API37 real-HTTPS batch fails both original bounds: cancellation10s and explicit
resume300s. A mid-run foreground helper returns an unexpected result; an observer
reattaches to the exact still-running ADB process by PID/creation time and captures
its terminal outcome without restarting it. Native reports2 failures despite ADB
exit0. The second case first cancels in152ms and preserves an8,390,311-byte partial
without automatic resume, then explicitly resumes. Final isolated partial sizes
are8,447,628 and384,018,487 bytes. The latter demonstrates progress within an
insufficient five-minute download window; it does not establish the first
cancellation delay's cause. Both failures/partials are preserved. No assertion,
timeout or memory guard is weakened; complete transfer acceptance remains open.

The six earlier physical offline passes remain valid for their recorded original-
debug candidate, not an installation of beta.9. Original-production-key physical
upgrade, process/reboot recovery and the full standard candidate CI/device union
remain release gates. No completed or unknown remote request is replayed.

## Fresh compatibility and advisory scope

Verified source c55 GitHub compatibility artifacts report stable v0.23.1 commit
3b421578:40 changed used handlers; current upstream main5f17ff9:100, compared with
97 in the older saved main report. Other mobile-relevant drift categories are
empty in these reports. Review is still required; no handler finding is waived.
[Hosted report](https://github.com/DionCroft/Signal_Grove/actions/runs/43194783793).

The exact150 Maven modules/95 input artifacts are queried against OSV with a
known-vulnerable positive control and complete pagination: zero matching known
runtime advisories on12 November. An additional exact llama.cpp940a30e commit
query returns none; coverage is limited to known indexed advisories, not a full
native/toolchain/server/plugin security audit. Final release SBOM/provenance and
published-asset verification remain due.

Private evidence: inventory840/ANR841-844, frozen build846/artifact854, upgrade857,
failed transfer858/observer861/reconciliation865, API26 timeout859/native session862/
reconciliation863, native UI864/proof868/screenshot869, advisory860/866 and fresh
compatibility855/867. Reproduce with the ordinary Windows build wrapper and the
explicit isolated Android classes. Do not reuse terminal fixture/cutover journals.
