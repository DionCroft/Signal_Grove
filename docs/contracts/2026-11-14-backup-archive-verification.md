# Exact backup archive verification and native SDK/age evidence

14 November 2026. The streaming archive verifier and an isolated actual SDK/age
fixture pass. The production backup transaction and private Release publication
remain unimplemented/unaccepted; no live home is archived.

`scripts/native_backup_archive.py` consumes an explicit sorted, unique selection
of `BackupMember` values and its reviewed digest. Ordinary members bind their
size/hash. Lowercase `.db` entries must be typed SQLite snapshots, never ordinary
byte copies: live database main-file hashes cannot verify a WAL-safe snapshot.
The caller must establish the actual pinned SDK selection, archive native
identity, safe file handle, directory ancestry and approval. This verifier does
not create a snapshot, encrypt, publish, restore or issue approval.

The verifier checks the exact ZIP member set/count, duplicates, literal relative
paths, ordinary-file types, supported compression, declared and streamed size
bounds, CRC and every ordinary member's hash. Finite defaults cap archive size,
member count, per-member bytes and total uncompressed bytes. It streams one MiB
at a time rather than holding an entire archive/database in memory. Names and
per-member hashes form a private manifest; they must not enter public logs.

SQLite members are streamed into exclusively created private scratch images.
Their header, page-size geometry and full integrity check must pass. Linux uses
the exact existing image FD for the read-only immutable SQLite connection; source
databases are never opened by this verifier. Native directory/name/inode checks
precede removal of only the successful image. Failed images remain for review.
Windows closes its CRT handle, then rechecks its exact private name before
removing a verified test image. Scratch is explicitly supplied, on the execution
owner; no implicit system-temp location is selected.

## Important discovered counterexample

Before publication, direct execution of the first draft verified a ZIP containing
a zero-byte `state.db`: SQLite interprets the file as an empty database and its
integrity check returns `ok`. The same artifact now refuses with
`sqlite_header_or_pages`. SQLite magic, a supported power-of-two page size,
minimum complete page and complete page geometry are required before SQL.
Regressions also refuse truncated/all-zero images and a `.db` expectation forged
as an ordinary file. The counterexample and the earlier positive results remain
recorded; the final source was tested in a fresh acceptance directory.

## Actual source and native acceptance

The maintained backup-review template references an older selection helper.
The inspected pinned SDK owns selection through `_iter_backup_files`, and its
`run_backup` path snapshots SQLite through the backup API. The new fixture calls
these unmodified functions with `keep=0`; it does not execute the maintained
script, change a deployed workflow or assume the two are identical.

All **22 Pi methods pass without skips**: 20 portable/schema/archive cases and
two actual Linux scratch cases. Windows runs **927 cases: 757 pass/170 platform
skips**. Cases cover exact selections, digests/counts/bounds, duplicate/unknown/
missing entries, unsafe types, CRC/truncation, empty ordinary files, SQLite
snapshots/header/geometry, retained corrupt images and unsafe scratch roots.
The unchanged 208-method operational/journal/publication/PM/service cohort keeps
its previous exact source scope. Of current platform cases, 167 have scoped Pi
evidence; three older cases retain their separate scope. Counts overlap.

A separate fresh private SDK home contains only generated fixtures. The actual
backup includes 11 required state members: credentials-shaped fixture files,
identity/memory, a named profile, user skills (including `.archive` and a nested
`hermes-agent` name), media cache and `state.db`. Seven named runtime/cache/
symlink exclusions are checked. Two committed WAL rows are verified inside the
archive, not inferred from the main database file. The source fixture remains
readable with the same two rows afterward.

The Pi's existing age/keygen v1.3.1 binaries are observed and hashed, without
installing tools. A disposable fixture identity is generated privately. Native
age encryption/decryption produces an identical ZIP hash, then the decrypted
archive passes the same content checks. Encryption/decryption use exclusively
created output handles; existing files are not overwritten. Only this fixture's
verified plaintext ZIPs/database image are removed by their recorded identities.
The encrypted pair and fixture key remain private for independent review. No
production key is read or transferred. See [age's official CLI usage](https://github.com/FiloSottile/age#usage).

The first native wrapper stopped before starting a unit because it assumed the
wrong tool directory. Read-only recovery proves zero phases started, discovers
the installed tools and verifies their native identities/hashes. Its receipt is
retained. The first independent reviewer could not open an anonymous-memory DB
through SQLite's file VFS. Corrected readback uses one new private fixture-only
image and removes that exact inode after SQL checks; it does not recreate a
backup, rerun tests or import/restore data. That failed receipt is retained too.
A subsequent private-image reviewer finished artifact checks but shadowed the
source-inventory manifest in its final conservation check. That failure also
remains. The final observer scopes its own variables inside a function, keeping
the original source/facts/service conservation inputs intact; it repeats only
fixture readback and does not regenerate an archive or rerun tests.

Independent review authenticates all 22 positive raw method names, final source
bytes, actual SDK source, encrypted checksum/header, decryption hash, every ZIP
member and SQL integrity/rows. Both owned units are stopped with empty cgroups.
It also checks all 23,628 source inputs, 41 SDK caches, original app/tool facts,
seven directory modes and live service incarnations. No backup is uploaded and
no live home, model, credential, service, grant or APK is changed.

| Evidence | SHA-256 |
| --- | --- |
| Archive verifier | 30ad2cf517a8d2cbe7337f118a823b59ed1059420e0c39f42ed7db92b960b429 |
| Archive regressions | a9b390a4073ba89ef3cbf884164322ba478c8a2b6f1e596472c2d68f57af41e6 |
| Native SDK/age fixture | 55bc63baaf88c5dc4a80690bb950e71ca8cb08af88332af66ca89176f38ada6a |
| Zero-byte before/after counterexample | 742dc8fa890e5ebbb457935432e014c6ac5bef683a8df75d058fa794e63bbd4e |
| Independent final native/raw readback | 534a020be83f4328fad4ba34af296a45a73be0e379c487338f5eeae4ab26eae7 |
| Actual tool observation/failure recovery | b25a6cd6ca218175ce88f9ea0ec68fc178c5b85c6ff9790a14ee591455e83ff5 |
| Full Windows suite | 505ebc90544533a0b961810d22d55c09aae7bca9893dc872625b309116f274ec |

Machine paths, manifests, encrypted fixture data, private key and raw receipts
stay in private evidence outside Git. No plaintext/key bytes are published.

## Required next implementation

Implement a one-use backend for the fixed pre/post/recovery backup stages, bound
to the existing exclusive journal, approval, finite native unit and shared
operational path witness. Pin the actual SDK/interpreter/tool/key inputs and
fresh output preimages. Handle external memory-provider selection explicitly;
a helper swallowing provider errors is not proof of empty external state.
Run the supported SDK snapshot without retention or restore, verify the exact
selection/SQLite and encryption round-trip, then publish without clobber/retry
and independently download/verify the existing private Release assets. Preserve
uncertain effects rather than regenerate an archive. Only the complete verified
stage may supply `fresh_backup_verified` to the coordinator. This verifier
deliberately supplies none of those transaction/remote/restore assurances.

Then complete admission/retirement, manager reload, authenticated health/history/
graph and full runtime-only recovery. Canonical PM preparation, seven named mode
changes, policy/references and idle live cutover still require the concrete final
packet and separate approval. All original API26, upstream used/transitive,
project/workspace/worker/NAS/automation/integration and final phone/local-AI/
security/original-key release requirements remain active.

[Shared operational boundary](2026-11-14-operational-ancestry.md) ·
[Full runtime packet](2026-11-14-runtime-cutover-packet.md).
