# Loader/start-entry and complete hosted checkpoint - 12 November 2026

Exact current-stable probe passes 87 cases with 55 critical source guards and 949
loaded module hashes. All original 77 cases remain; real loader/import-lock and
startup/PM boundary checks use explicit stopping adapters, not a live backend.
Three source/invocation controls and 422/runtime 15/checksum policies pass.

Complete hosted d505 coverage independently verifies 1,132 API26/1,133 API37 raw
unique cases. Newer workspace tests still need their complete exact-head gate.
Phone/backend and all Android/native/build inputs are unchanged. Both newly owned
review emulators are normally stopped; the unverified API37 boot is not accepted.
No old failed/unknown result, compatibility finding or original audit row closes
without matching evidence. [Exact scope and next action](../docs/contracts/2026-11-12-native-loader-startup.md).
