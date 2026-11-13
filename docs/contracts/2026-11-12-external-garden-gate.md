# Register the physical garden model gate and verify code114

12 November 2026. The new garden streaming regression belongs to the already
explicit model-dependent physical test class. CI correctly refuses its missing
registration: [b297 parser policy failure](https://github.com/DionCroft/Signal_Grove/actions/runs/43318401711)
reports one failure among422 checks; downstream compatibility jobs do not run.
Do not describe that particular run as a fresh upstream40/101 review.

This correction adds only `verifiedLfm3StreamsOnlyAnswerTextForGardenPrompt` to
the existing LocalModelPhysicalTest method inventory. The closed inventory now
contains five classes/twelve methods, retaining all eleven original methods and
every prerequisite. Three strict count assertions are updated alongside it.
Standard-suite class exclusions are unchanged; the new method is not marked run
or passed and no skip is accepted as native execution.

An isolated exact-Git-blob source copy with the four corrected Python files
passes all422 policy checks. The first archive guard encountered normal text
line-ending conversion; restored LF bytes are verified against exact Git blob
hashes before testing. A first corrected-copy run still found two other strict
eleven-method count assertions; both are retained and updated to twelve. A
restricted Windows execution then times out in an asyncio fixture; its cause
is not established. The same source under normal Windows execution completes
all422 checks in18,098ms. No assertion, old method or suite exclusion is removed.

The clean code114 source `0bb325e4970a3c4a4a8e73077783229d413ba8ce` independently
passes1,011 JVM cases/97 reports, zero-error lint (51 debug/50 release warnings),
original-key production/debug/test signing, all four native ABIs,64-bit16-KiB
and uncompressed/aligned DEX. Its865-file source digest is
`df8025b6f09161bb2f05de73eb7dd172435fb19f31c44ef6c95b7b6bf3bbd5bf`.
The original-key APKs are production71,052,646 bytes, debug92,609,254 bytes and
test7,909,383 bytes. These exact artifacts are unpublished. Debug and companion
are hash-verified in the reconnected phone's Downloads; installation/native
phone checks remain pending. Before staging, two original model hashes and
an encrypted debug-metadata round trip verify. Production-private file hashes
are not claimed. The old normal app and all package UIDs remain unchanged.

This registration/documentation chunk changes no Android/native/build input.
That input equality does not turn a queued/failed exact-source CI run or the
pending physical garden/helper/family/cancellation/upgrade gates into a pass.
Public release requires the exact passing release commit per the release policy;
no APK, tag or release is published here. Compatibility findings, worker/runtime
limits and every original audit row remain active.

[Helper correction](2026-11-12-bounded-helper-selections.md) ·
[Production findings](2026-11-12-production-phone-offline-output.md) ·
[Release requirements](../RELEASING.md) · [Full audit](../AUDIT_EXECUTION_PLAN.md).
