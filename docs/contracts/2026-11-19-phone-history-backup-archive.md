# Versioned phone history archive

The archive captures the selected on-device chats in their reviewed order. Each
record keeps the saved transcript bytes, model fingerprint, optional title, pin,
archive state and any recorded branch or generation provenance. The reader
builds a review only after validating every record and the final checksum. When
the review's caller retrieves a record, it verifies the staged transcript bytes
again. The same transcript decoder reads the app's existing schema versions 1,
2 and 3.

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

The Android backup picker, collision review and atomic restore still need
implementation. Restore will separately test interrupted publication and
retention of existing phone chats and metadata.

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
