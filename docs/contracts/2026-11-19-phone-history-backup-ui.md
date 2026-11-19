# Android phone history backup UI

19 November 2026. Unreleased on `feat/offline-history-backup`.

## Scope

The backup controls in **On-device AI → Phone history** open a frozen
selection. Passphrase protection starts enabled and the user may choose a
plain archive. A separate review shows the format, size and chat count
before creating a document. Imports freeze one selected document and verify
its contents in private storage before displaying a restore plan. The review
shows any ID collision and missing parent. Only the confirm action publishes a
restore. The app retains a boolean interruption marker across process death.
The passphrase and document URI live in memory for the current operation.
The app keeps one picker result per review and waits for the provider or
restore operation to return before allowing an app update.

## Validation

The strict Windows build and Android tests use the same source snapshot.
All 1,146 JVM methods from 109 reports passed.
Both lint variants reported zero errors. Android 8 and Android 17 passed
all 72 selected methods, including the earlier 69 plus three backup UI cases.
The protected round trip uses an Android FileProvider, rejects a wrong
passphrase, checks that review leaves both originals intact, and writes the
confirmed new chat and labels. The plain case confirms cancellation and
abandoned picker handling before publication.

A separate run used Android 8's actual DocumentsUI, saving a plain backup in Downloads, reopening it and restoring one copy. The original chat's hash matched and the restored record differed only in its reviewed ID. The app-private staging directory was empty.

The first build failed because the new coordinator file was empty. The
corrected build contains the complete coordinator. The earlier build
receipt and raw errors are retained privately. The first 71-case Android
17 run had one failure. Cancelling a restore review kept the app-update guard
active. The corrected app releases the guard and passes the same cancellation
assertion. An actual Android 8 app check also exposed a collapsed review
body above the keyboard at 1.3× with all three languages. The updated dialog
scrolls its heading and content together and puts both actions on one row.
The failed runs remain in the validation record.

A separate physical Android 17 check used the earlier exact Markdown
debug pair and Android's actual Downloads picker. All 799
bytes match the independently reconstructed export, and its SHA-256 is
`1891103f7beb5b07478e43c4bee9e5c27d4f080cd12e8d014e48a810887636f9`. The four selected phone chats, both models and
saved labels match their earlier hashes. The model was unloaded.
Physical backup/restore, the full branch matrix and production release remain open.

- Build receipt `20261119-140421-e4d650280ab248ad9dd568803b1130d3`
- Source snapshot SHA-256 `bc8de4ccf65bd9f5a9d8eb7b01ccb73d4b1570623d41ff15c6fa103505cd29f1`
- Debug APK SHA-256 `e6aaa567ca10e8bc7263af59fc5eddbfd0354b6d6800eaf42311e205c3329137`
- Test companion SHA-256 `31d2e656268a05301a1f120c5ea24f3cbc83f31fc8750a6d89ae74863ee78523`
- API 26 instrumentation log SHA-256 `a2e95246259887238be68c8c79151e8a404f0349512a80d7dc7058f82ee9f83a`
- API 37 instrumentation log SHA-256 `3f79501ba61cea3e9abf9fe4a3e4960b3fbec1f24a60413a6f0e5a55934a30e8`
