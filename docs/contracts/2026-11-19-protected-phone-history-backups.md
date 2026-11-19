# Passphrase-protected phone history backups

A protected `.sgbackup` contains the same versioned phone-chat archive
used by the reviewed restore transaction. A protected read authenticates the
entire file into app-private staging, then validates the inner archive before
offering any data to the restore review. Closing the reviewed import releases
its plaintext file. Cancellation, invalid archives and incorrect passphrases remove
the incomplete stage.

## Envelope version 1

The file begins with the eight ASCII bytes `SGBKAE01` and a random
16-byte password salt. These 24 bytes are the associated data. The key is
derived with PBKDF2-HMAC-SHA256 and 600,000 iterations, producing 32 bytes. The
passphrase is passed to the platform as Unicode without trimming or
normalization. The caller owns and clears its passphrase buffer. The codec
clears its mutable derived key and internal password copy after use.
[Android key derivation API](https://developer.android.com/reference/javax/crypto/SecretKeyFactory).

The remainder is a Tink AES-GCM-HKDF-SHA256 ciphertext with 32-byte input and
derived keys and 1 MiB ciphertext segments. Tink creates its own random salt and
nonce prefix. The reader checks the version and supported archive size before
derivation. Segment overhead is included in the protected-file size bound.
Truncation, appended bytes, changes to the version 1 salt and changed segments
fail authentication. An authenticated container with an invalid inner archive also
fails validation.
[Tink streaming format](https://developers.google.com/tink/streaming-aead/aes_gcm_hkdf_streaming).

The protected writer keeps the supplied destination open for its caller.
A destination failure aborts output. The crypto stream closes locally without
publishing another segment. A restore still requires a fresh
collision review and explicit confirmation.

## Validation

The strict build passed 1,139 JVM tests in 108 reports,
including seven protected-backup regressions. Debug and release lint reported
zero errors. Both Android 8 and Android 17 passed 67 focused methods using
the same signed debug app and companion. The new native cases open an
independently generated Unicode-passphrase fixture, resolve a chat ID collision
without overwriting the existing chat, and reject late tampering after a
multi-segment backup has begun decrypting. A JVM-only follow-up moved the
synthetic output error to the first ciphertext segment. Its manifest differs
from the device build only in the JVM test source. Both lint variants and
the complete JVM suite passed again.

The initial strict build identified an AndroidX module descriptor that needed
a checksum even though a newer annotation artifact was selected. The descriptor
and new transitive artifacts were independently checked against Google Maven and
Maven Central. A scoped OSV query returned no records for the four checked Tink,
Gson, Error Prone and JSR-305 versions on the validation date.
Lint flagged the newer Tink 1.24.0. A scoped workstation probe passed with
its dedicated streaming configuration. Follow-up Android validation appears
below. [Tink 1.24.0 release](https://github.com/tink-crypto/tink-java/releases/tag/v1.24.0).

[Tink 1.23.0 release](https://github.com/tink-crypto/tink-java/releases/tag/v1.23.0) ·
[OSV query API](https://google.github.io/osv.dev/api/).

- Build receipt `20261119-123913-45c1de9efb8747af9ee0ca1a1ac98f82`
- Device source snapshot `a64c6c536e765b878438d34d05e247c055ade5f9d78945dcd3dc72d699edcffe`
- JVM follow-up source snapshot `f264da9c7e7fa361279f135ca7300677f153ffbc7092fe60a72be9f5e6181b15`
- JVM follow-up build receipt `20261119-125005-308b5f878951460cac956577f320c1c0`
- Debug app SHA-256 `2313ae46307961e9b119841d92e298141f2b37764675febc60a4a472afdd55e5`
- Test companion SHA-256 `6c829e00a152387317a083d3541c4bb0f9c4feb7b6f1e8f1ef06c7f20127bc9e`
- API 26 instrumentation SHA-256 `afe27be596ed88810d25a94956945c7133f3d2b52e8db128c2d25480520b1978`
- API 37 instrumentation SHA-256 `4ee4763ae478b061dea2d92683ebf1c2410bc9563729de9d93f9dfbef826562b`

The app-private format, authentication and storage restore are verified.
Android document-picker screens and a physical backup/restore remain open.
The private staging lifecycle is recorded below.

## Private document staging

The phone history controller owns a dedicated directory under the app's
no-backup storage. Preparations freeze the selected chat records and label
bindings into a bounded private file. The prepared file's digest is checked
before its bytes reach an output stream. Imports recognize the fixed header
before reading the body and apply the limit for that format. A restore
review reads the selected private copy even if the original document changes.

Closing a prepared document releases its staged copy. Closing a protected
review releases its decrypted file. At startup, phone history reclaims
recognized abandoned staging files before displaying the catalog. The
cleanup stays within the owned directory and preserves unrecognized entries,
chat transcripts, labels, models, and the restore journal.

Tink 1.24.0 uses its dedicated streaming configuration with the same v1
envelope. Both Android versions opened the independent earlier vector and
passed authenticated round trips. Maven Central checksums were verified and
a scoped OSV query returned no records for Tink 1.24.0 on the validation date.
[Tink 1.24.0 release](https://github.com/tink-crypto/tink-java/releases/tag/v1.24.0) ·
[OSV query API](https://google.github.io/osv.dev/api/).

The strict build passed 1,146 JVM tests and both lint variants.
The signed pair passed 69 focused checks on Android API 26 and API 37. Two
Android checks cover the real file-descriptor path to the reviewed restore
and the controller's cold staging cleanup. Backup selection screens,
Android document-picker wiring, and physical backup and restore remain open.

- Build receipt `20261119-131212-848f34f766c54d43b049685fbbd1b6b8`
- Source snapshot `8a4e5a6121c07db05dfe27e5b524aef0d31aa632360876341d8d30cba2e0cd69`
- Debug app SHA-256 `51c7fe4fcf5e2fc0585ade14ba172566dc0ad90e228f8c063ef42c9a34e1c117`
- Test companion SHA-256 `898452411b893fe1e71e1344383c7bbe1fdc7be90468b70dad2fe83487411945`
- API 26 instrumentation SHA-256 `5c08a7e39d7b467f389f0472f0ac34b296239e4a9b02ecab36e080247838a1e0`
- API 37 instrumentation SHA-256 `5fca9c3fce2dd919a24b606446dc4f52838f8223919119bdf109cdee633bce8c`
