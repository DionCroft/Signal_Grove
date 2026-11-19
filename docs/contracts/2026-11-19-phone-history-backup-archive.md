# Versioned phone history archive

The archive captures the selected on-device chats in their reviewed order. Each
record keeps the saved transcript bytes, model fingerprint, optional title, pin,
archive state and any recorded branch or generation provenance. The reader
builds a review only after validating every record and the final checksum. When
the review's caller retrieves a record, it verifies the staged transcript bytes
again. The same transcript decoder reads the app's existing schema versions 1 through 3 and the
imported-parent schema 4.

## Version 1 wire layout

Numbers are big endian. The plain archive starts with the eight ASCII bytes
`SGHISTOR`, a 32-bit version and a 32-bit record count. Version 1 accepts one
through 50 records. Each record contains these fields in order.

1. Canonical 36-byte ASCII conversation UUID.
2. One flag byte. Bits 0, 1 and 2 indicate labels, pinned and archived. Other
   bits are reserved. When the labels bit is clear, the title and other flags
   must be absent.
3. Unsigned 16-bit UTF-8 title length and the title bytes, up to 320 bytes.
4. 32-bit transcript length and the UTF-8 transcript, up to 4 MiB.

The final 32 bytes are the SHA-256 digest of the preceding bytes. The file ends
after that digest. This catches accidental corruption of the plain archive.
The archive contains private plaintext. Passphrase protection remains a
separate delivery and will need authenticated encryption.

The inspection keeps one transcript in memory at a time and retains the
reviewed metadata and transcript offsets. It rejects invalid sizes, duplicate
or noncanonical UUIDs, unsupported transcript fields, malformed Unicode, active
generation state, mismatched branch model bindings and cycles through selected
branches. An unselected parent remains provenance for the restore planner to
reconcile. Cancellation ends inspection without returning a review. A failed
write may leave a partial file, which the reader rejects.

Restore review and publication are documented below. The Android backup
picker and passphrase flow remain open.

## Validation on 19 November 2026

The Windows build passed 1,117 JVM tests in 105 reports, including 10 new archive cases. Debug and release lint passed with zero errors and 51 and 50 warnings respectively.
The same signed debug app and companion passed 63 methods on API 26 and 63 on API 37. The two new Android cases use real private cache files to exercise a multichunk Unicode archive and refusal of a modified staged transcript or cancelled inspection.

- Build receipt `20261119-110135-3d4f3865d3ee4afc9d9c18ec60575f62`
- Source snapshot `dc29086755c2e85716bcdc3b771e5a6458711f380f38e38f39b6479911dc61e7`
- Debug app SHA-256 `16822b55636b59897e6e38cb6d4f515bbcd70e2de628372f5df3e1222af3a237`
- Test companion SHA-256 `f97f3540a69756333e55aaf5228b251fa2c060f36590d741734bbc3e46ce80ec`
- API 26 instrumentation SHA-256 `26078f94f9e780f4f4cb3a1cc0795fb3f450b92b25149eca878f807c9ad70fa1`
- API 37 instrumentation SHA-256 `289367851cd18f9f04573abe2841cb7d83fa82172f288f68b9a91ff161ea446d`

## Related physical phone check on 19 November 2026

The previously tested title, pin and archive controls saved all three labels
on the selected regenerated conversation on the Android 17 Xiaomi. The chat
appeared in Archived and opened its saved reply with no GGUF mapped. All four
original transcripts and both model files matched their pre-check hashes.
This verifies label Save and archive read on the prior organization build.
The physical Cancel and Android document-picker checks remain open.

## Restore review on 19 November 2026

The review retains an ordered list of source chats and their destination IDs.
It assigns fresh IDs for collisions with phone chats or saved labels, including
labels whose chat is missing. It checks capacity for both stores before
allocating IDs. A branch can follow an included parent's destination ID.
Parents outside the archive stay in the source provenance without a destination
link. The review compares the current library and labels before use and only
reads original records from its checked archive.

The Android build passed 1,123 JVM tests in 106
reports, including six restore-review regressions. Debug and release lint
reported zero errors. Both debug APKs compiled from the same source snapshot.
The checked review feeds the recoverable publication described below.

- Build receipt `20261119-112259-b9794a42aa4740779fab0fedd9dc92da`
- Source snapshot `de0fa6be3a296a79aaefd05a5c0485fc3654ffa88cc00c22f166791c41f13779`

## Recoverable restore on 19 November 2026

The confirmed plan is checked again under the chat and label store locks.
Each reviewed transcript is staged in private app storage. A separate link
in schema 4 records the restored parent while keeping the original parent ID,
timestamp and source digest. A branch whose parent was excluded remains
detached through subsequent backups. Existing schema 1 through 3 files retain
their current links and encoding.

The journal contains hashes for the recognized new transcripts and both
possible label-index states. A pending marker is durable before publication.
New chats use atomic renames to confirmed absent destinations under the library
locks. The label index uses atomic replacement. After syncing and verifying the new files, restore records a
committed marker. Application startup resolves the journal before exposing
history. Recovery rolls back a pending restore by removing only verified
new files and restoring the original index bytes or absence. Unknown bytes
halt recovery and remain available for inspection. A committed restore is
verified and retained. Recovery does not import the archive again.

The Windows build passed 1,132 JVM tests in 107
reports, including nine restore cases. Debug and release lint passed with zero
errors. The same signed debug pair passed 65 focused methods on each Android
API 26 and 37. Two Android cases exercise actual private-file publication and
roll back an injected interruption before the controller shows history.
The first two lint runs caught API 27 and API 30 descriptor calls. The verified
build opens directories read-only, checks the descriptor type, syncs and closes
it using Android APIs available from API 21.
[Android directory sync API](https://developer.android.com/reference/android/system/Os#fsync(java.io.FileDescriptor)).
Android 8 passed 63 standard methods in the first device run and denied the two
new restore cases at hard-link creation. The accepted path uses the atomic
rename already used by chat storage. A regression injects an occupied
destination after staging and verifies that recovery preserves its bytes.
The first rename build passed both restore methods on Android 8 and retained
a separate late-keyboard fixture timeout with no Cancel input. The fixture now
uses one explicit keyboard request for the already-focused editor and still
requires a real IME before its single Cancel tap.

The test suite models interruption at storage boundaries. A physical device
restore and the user-facing Android backup and import flows remain open.

- Build receipt `20261119-121401-2ae71310383c4f7ea228fd3fa4260739`
- Source snapshot `9ae6260e77284640ff48053240fadc1b0da77c87b0a54bd0362e6f24e87e75a0`
- Debug app SHA-256 `5dd36b8d37c33710aa38255e722bec8d6f5780f3b3459866f64b75666d0c1288`
- Test companion SHA-256 `8080c78ed43f98df726fb24bad9c193265f37bdc0c79069c2894f3fa96117764`
- API 26 instrumentation SHA-256 `9fbdc5479c52c894ab29e6e63cb2e17cca974fc4c4091927bea2d12daf03b8e8`
- API 37 instrumentation SHA-256 `8d485b0fa444619d05fbc6fd5c6fed8e0343c684290cf0c0b8d6562ba605f359`
