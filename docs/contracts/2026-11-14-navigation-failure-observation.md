# API26 failure capture before cleanup

14 November 2026. Tested code 827c6c0 adds observations to the two retained
navigation failure paths. The source change does not fix or waive the original
failures. The complete audit, original four priorities and release gates remain.

## Interaction and privacy boundaries

Phone-draft Chat navigation and setup-guide Settings navigation retain their
original selectors, click and existing assertions. A source reversal independently
recovers both files exactly as in the failed cea0cbf CI input. No successful-path
wait, retry, production source or original Demo-test change is introduced.
The wrapper catches the actual assertion inside the interaction, before JUnit
cleanup or the caller's language-restoration finally block. It retains the cause
and records only fixed counts, flags and lifecycle enum values.

The non-atomic snapshot distinguishes resumed Main/local/other activity counts,
focus, delivered IME insets, returned native window types/geometry, navigation
tags and fixed-language Settings caption counts. It copies no chat/draft text,
URI, credential, intent value or project identifier. Native window-query flags
are restored. Queries can advance Compose: the snapshot is not an atomic picture
or evidence that the failed interaction succeeded. Returned-window counts do not
establish complete visibility or authorize input. Empty native window reads are
explicitly indeterminate, with -1 counters rather than an absent-IME claim.

## Verified build and controlled native case

The source-addressed build passes required assembly, 1,011 JVM tests across 97
reports without failures/errors/skips, and both lint variants with zero errors.
Existing warnings remain: 51 debug and 50 release. The portable suite runs 605
cases: 602 pass and three existing actual-POSIX cases skip. No new test skips.

Independent source/APK review verifies the whole 958-input snapshot, package and
original debug signing identity, manifest, DEX alignment and 32 native libraries
across four ABIs with applicable 16-KiB ELF/ZIP checks. The new diagnostic fields
and method are present in the instrumentation DEX. This is a debug/test pair,
not a newly signed production release or complete final-source device gate.

The single declared controlled case passes on a fresh, explicitly owned API26
emulator with actual font scale 1.3. It opens the real private local editor and
retains a snapshot before its caller's finally closes that activity. The snapshot
has Main CREATED/not resumed, one resumed LocalAiActivity, one local draft field
and no main navigation nodes. The same case then requires Main resumed and no
resumed local activity after cleanup. Both debug-package UIDs and the previous
control artifact survive the in-place update. No model, remote write, physical
phone or pre-existing AVD is used or changed.

The first build's missing-import failure remains. The first controlled case also
remains failed: it assumed a populated focused native-window list immediately
after enabling queries. Its actual returned list was empty. The corrected code
records such reads as indeterminate; a nonempty returned list can still have no
focused app record. The updated control checks this observed contract and retains
all original navigation assertions. Its positive result belongs to the corrected
source only and is not a reproduction of either user-facing failure.

The stock API26 image lacks getconf. Its initial boot-verification helper fails,
but the existing emulator is not restarted. A separate observer verifies the
same boot/AVD/API/ABI and reads actual cat-process auxv AT_PAGESZ: 4,096 bytes.
No platform or kernel result is faked and no utility is installed to pass.

## Preplanned reproduction, currently running

After retaining both control artifacts, only that newly created disposable
fixture's synthetic app data is reset for the normal fresh-suite conditions.
Existing AVDs, physical phone and real app history are not reset. The standard
keyboard preparation passes with one reviewed first-use hint dismissal.

The installed APKs inventory 1,142 standard API26 methods. The selected shard
preserves all 540 original failed-run cases in exactly their original order,
with only the new controlled case added, for 541 cases. Both failed methods are
included. Existing external model gates remain explicitly not run; no new case
is excluded. There is one actual test command and no failure-triggered retry.
Its recorded host adb process is independently verified live at observation.
No terminal, full-cohort or original-cause acceptance is inferred from progress.

The exact next action is to observe that existing navigation-shard1839 operation
to terminal and inspect its original stream and before-cleanup diagnostics before
any dependent work. A non-recurrence would not prove the old cause. The original
source1180 Demo failure remains a separate unresolved investigation; its case is
not in this shard. Both recent failures and all wider Hermes/project/worker/
automation/integration/phone/release requirements remain in the full ledger.

## Evidence identities

| Evidence | SHA-256 |
| --- | --- |
| Corrected-source build/APK review | c545f2fb24c5eab78009a3024e92d4eee125585a8f870f667f950625920c85f7 |
| Independent interaction reversal | ad962ec51182668686853bf6e7203b4b757e2e775b356e7d5a4cdf15a85da78d |
| Current portable log | 64e5ddf767b100df3a1b20a9c082699dfa702e626d6b142da16751d715f08c22 |
| Positive controlled native stream | 4e3b359a9b078a1d2759fa7f75c632d03ba4f0f91aba341bbe4fbf561c0bde8d |
| Retained before-cleanup snapshot | fe2b5926544e5c1974ac804dc49a348e3207b5093b331bb75280a530cc0d1a56 |
| First controlled failure stream | 77ffbf48e99d17eea043beae57baa8b48d9595472d501e5764f3674a7dbf1cbe |
| Native kernel/boot reconciliation | 570712eb98bcb30ccfc230b2a003c8957815d57c86d07477380ff10961a1695d |
| Existing running-operation observation | b90ca730647595e0e77cd9e09dd65122d8fe4e56782737c7ade2b43ba5b4d8f0 |
| Debug APK | c3550e4f30bb18abfa3e5711f9b405f28eec72d83c32d06dab0651ff790d4973 |
| Instrumentation APK | f2cb1611eae907f800b968e1dde26fad3ef621cac436c1523af8a5c58ff0027a |
