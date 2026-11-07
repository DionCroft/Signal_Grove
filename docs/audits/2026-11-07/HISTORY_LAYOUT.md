# Existing history layout — 7 November 2026

The history dialog previously placed a large multilingual search/filter header outside a weighted chat list. On small screens the header could consume the list viewport. Filters, help and rows now share a single bounded scrollable body, with pagination and Close kept outside it. The paging fixture reaches an older Discord row, imports its actual messages and confirms zero writes.

A separate controlled API26 check showed that the long fixed read-only explanation plus disabled composer left too little chat space with Chinese, pinyin and 130% font enabled. A compact read-only bar now opens the full scrollable explanation; Send remains disabled. Existing VM/request/repository origin guards, source retention, profile isolation, draft data and Room schema are unchanged.

All four required Gradle tasks and 548 JVM cases passed. The same 17 origin/storage/history/title methods passed on API26 and API37, with original debug signer and installed APK/source checks. Strong UI checks require a readable message viewport before the deliberate write rejection and after explicit banner dismissal; they await the controls dialog and an observed metadata GET, then verify lock/fork stay disabled. The controlled pre-fix failure and the earlier incomplete/failed full hosted runs remain retained.

Five fresh small-screen screenshots were reviewed. The compact chat, full reason, older-history row and disabled controls are readable. The initial history screenshot also revealed an unresolved keyboard/footer overlap: Search can acquire focus automatically, and semantic visibility alone did not prove a real tap could reach the footer. Initial focus, IME-aware layout and an actual Android-input pagination regression are the exact next correction. This checkpoint is not a release verdict.

The API37 emulator stopped before compact-suite installation/test preflight. Its existing configuration and userdata were preserved on normal restart; the separate resumed 17-case run passed. No device reset, reseed, resize, owner-phone fixture, production server change or foreign-history write was performed. Private screenshots, transcripts, IDs, endpoints, source archives and receipts remain outside Git.

## Keyboard and Android-input follow-up — 7 November 2026

The history title, search, filters and rows now share the scrollable body. The short localized More footer retains the full Load more accessibility description. The dialog respects keyboard insets, initially focuses an explicitly eligible Close button after its window gains focus, and dismisses the keyboard when selecting a conversation or completing search.

All four required Gradle tasks passed with 548 JVM cases. All 18 origin/storage/history/title cases passed on each owned API26 and API37 emulator, including a new case that checks initial focus, observes the actual keyboard, and uses real Android input to page and close while it is open. The same original-key APK pair was verified installed with unchanged source inputs, display, density, font scale and AVD configuration. Fresh normal and keyboard-open screenshots were reviewed. Earlier focus/layout failures remain retained; no reset, resize, data clear, skipped case or owner-phone credential fixture was used.

These focused checks do not replace full exact-source hosted CI, production assembly/signature/manifest checks or retained-data phone upgrade acceptance. The broader audit and native worker project-file execution remain open.
