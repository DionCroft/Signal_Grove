# CI device command outcomes — 7 November 2026

The [ef5 full Android run](https://github.com/DionCroft/Signal_Grove/actions/runs/42345795933)
is terminal overall failed. API37 passed937 unique cases. API26 has912 positive
methods/913 starts of936, leaving24 unverified, with no failed assertion or
terminal instrumentation verdict. Its last-started case is the bilingual setup
guide. This is an incomplete run, not evidence that the guide assertion failed.

The exact-file replay corrects the earlier log attribution: instrumentation.log
ends at the case's start status. The waiting-for-device message is in the later
cleanup test-runner.log, followed only by logcat buffer headers. The original
instrument command return code and pre-cleanup emulator state were not retained;
the exact cause cannot be reconstructed from those files. Cleanup shutdown is
not proof of a crash/OOM, and passing local guide cases does not establish full
long-session stability. Keep the original failed cohort and artifact digests.

## New retained facts

The finite one-invocation runner now writes instrumentation-result.json beside
the exact stdout log. It records command return code or an explicit unknown,
timeout/command-error type, elapsed time, the textual OK count, failure markers,
bounded status counts, declared total and last started/finished method names.
It excludes arbitrary stream/stack fields and limits stored method names. A
positive summary cannot override a nonzero exit, timeout, failed/ignored/unknown
negative method status or missing evidence. The normal global
INSTRUMENTATION_CODE -1 is distinct from a negative method status.

device-state.json observes the same owned emulator process before any cleanup:
its poll return code and one bounded get-state call. An already-exited emulator
skips the ADB probe. Unexpected raw ADB text is classified rather than copied;
probe timeout/errors remain explicit. Observation never starts/restarts or
resets a device. Existing logcat/pull/shutdown then execute even when diagnostic
storage is unavailable; the original instrumentation error/timeout is preserved.
A nominal successful command cannot make the job pass with missing evidence.

Both JSON files are included in the always-uploaded device artifact. SDK/AVD
ownership guards, package setup policy, exact APK selection, full one-shot
instrumentation,95-minute suite limit and existing cleanup remain in force.
No app data, display configuration, signing identity, model or live Hermes
backend is changed by this tooling work.

## Validation and next cohort

Nine new regressions cover incomplete output/exit status, timeout despite an OK
summary, negative method statuses, command errors, failed evidence storage,
bounded original-process observation, dead/timeout probes, preserving the
original failure during cleanup, and bounded reaping of only the owned process.
All23 CI-runner tests,179 policy tests and15 runtime tests pass. The earlier
14 setup/one-invocation checks remain. No new Android build/native success is
claimed from those Python checks: f793's552 JVM/27 current-pair cases per API
remain their original cohort and their app/test/build inputs are unchanged.

The actual saved API26 stdout replays to912 unique named positives,913 starts,
declared936 and an unknown-command outcome. Its missing return code/duration
were not guessed. The initial replay assertion incorrectly expected the wait
message in instrumentation; that failed probe is retained and corrected by
reading both exact files, without changing the parser to hide the discrepancy.
This replay is not a new device execution or recovery of missing evidence.

A fresh final-source full cohort is justified only after the prior terminal
reports and these changed runner inputs are reviewed. It is a new validation
run on fresh isolated CI fixtures, not resumption/replay of unknown owner work.
This change improves observability; it is not a proven fix for the disconnection.
Review the newer Hermes E826 registry change, close the final-source compatibility
gate and run the full Android gate before final signed publication.
