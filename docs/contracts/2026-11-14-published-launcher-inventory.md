# Published launcher and separate runtime bytecode inventory

14 November 2026. The actual SDK-published POSIX launcher is staged and exercised
on the Pi. Native owner-home startup succeeds; an unprepared home is refused.
These are finite isolated launcher gates, not live service/HTTP/worker/phone
acceptance. The full goal and all four original priorities stay active.

## Supported publication and real invocation

The unchanged pinned SDK `stage_launcher` publishes into a fresh isolated command
directory. Its exact shell tokens and generated Python program match the SDK
writer. It binds the permanent source's own managed Python 3.14.9 tool, ignoring
an inherited transient runtime override during publication. No transient store
is created. Both owner/borrowed-home descriptor invocations exit zero and select
the same reviewed source/tool. Descriptor success alone is not startup success.

The saved executable is then invoked normally, without changing its script,
interpreter flags, SDK bootstrap or callbacks: `--run-module site` exits zero in
the isolated owner home and shows its exact source-keyed application environment,
with user-site packages disabled. In a separate unprepared fixture home, the
same command exits one with the SDK's dependency-environment refusal. No repair
command is issued to that home and no production home is provisioned.

The fixture wrapper stops because it also expects an internal exception class
name in the friendly refusal text. Its exit-one receipt and false expectation
are retained. Independent read-only recovery checks the original complete logs,
actual child exit statuses and absent child processes; startup is not replayed.
This does not turn the failed wrapper into a successful run.

## Source purity and generated outputs

Normal launch creates 12 native CPython bytecode files in this cohort. They are
retained separately from all 23,628 reviewed source inputs and their native modes.
The source-only copy preparer remains unchanged and still rejects extra files.
The new `scripts/hermes_runtime_inventory.py` is a distinct read-only check for
an executed runtime, bound to the exact immutable reviewed sibling, one-field
tools stamp rebind and actual Linux CPython 3.14.9.

Every extra file must be a non-executable, ordinary native timestamp cache for
an existing reviewed Python source. The verifier checks magic/flags/timestamp/
size, bounded bytes/count, exact source hashes and every code attribute including
nested code filenames and line/exception tables against a fresh trusted compile.
It unmarshals for comparison without executing cached code. Other modules,
optimized/foreign/pytest caches, links, executable caches, empty shadow directories
and unknown outputs are rejected. It deletes nothing and imports no SDK code.
The existing source-only preparer and protected operator-policy reader are not
relaxed. A cache inventory is not a runtime authorization or race-safety claim.

Twenty counterexample tests include forged matching-header code, nested filename
and line-table drift, source changes/removal/mode drift, malformed/trailing
payloads, foreign/optimized/orphan caches, bounds, symlinks and executable output.
All 20 pass on the actual Pi with zero skips. The full Windows portable suite has
564 tests: 561 pass and the three actual-POSIX mode/link checks are skipped there.
Those same three pass in the native Pi run. No new Android build is claimed.

Independent native postcheck verifies every input/native mode and all 12 cache
compilations. The frozen original/lifecycle sources, original and new PM facts,
79 selected live source paths, service identities, vendor inputs and ancestor
modes remain. Live services, launchers, credentials, models, grants, policies,
production application and beta.9 APK remain unchanged.

## Actual live home boundary and next gate

Read-only inventory finds an ordinary old install-state directory without facts
and no new source-keyed install state in the canonical live home. Staged owner
facts must not be silently treated as canonical-home facts. No install-state
alias or fact copy is introduced. Next provision one fresh borrowed fixture home
using supported PM and exercise the saved launcher against that exact selection.
Then finish the full start/stop/post-stop/serve wrapper, home/profile, protected
policy/ancestor permissions, idle-state and encrypted recovery/rollback packet.
Live activation and any permission changes require their separate approval.

## Exact evidence

| Artifact | SHA-256 |
| --- | --- |
| Portable 564-case log | 4e2ef399d5fe98b17f2bd23b816cd0c2758da6142eee2326cb4fc3c31b847187 |
| Native 20-case log | c0a03851cece85e2122d5f2a0de5995dfa2f881100aaf275e5fbe3a2d5bb8261 |
| Actual owner-home launcher log | b835399479883c6e2fc1019524bfcaf0083fab2deb300e46fe3789617cf13758 |
| Actual unprepared-home refusal log | f87c2dfbbf9d91efdbd9f7e4cef5c666315f4f1c728e8fb9d76c1acf7ee379a6 |
| Independent raw/input/bytecode/live review | f273c5130c17e7835806759e156c9827fb7c077969ea34822f1a8bf20e17eceb |
| Published executable | 6e4292d196bb17a0487d63877d78b8acac4d70f6d9687d7b81a4f69b9b0d3700 |
