# Windows temporary build layout

The Windows wrapper keeps authoritative source and published APKs/reports in
the requested checkout. It builds a verified temporary source snapshot on E:,
with Gradle intermediates, project caches and Kotlin persistent data there. It retains the existing SDK, Gradle user home,
emulator, temporary-file and historical debug-key rules.

The previous required build took9m19s. An owned JVM thread observation showed
two Android lint encoders in FileOutputStream.write; the build completed
successfully. This motivated moving temporary output off shared storage.
The first direct relocation failed in KSP because source and generated paths
had different Windows roots. Supported nonincremental processing failed too
while collecting generated outputs. Both failures are retained. The revised
implementation therefore uses a same-drive temporary snapshot. The complete
revised build and artifact publication are accepted below. The thread observation
does not establish a deadlock or isolate a performance cause.

## Layout and publication

Each absolute checkout root has a SHA-256 identity. Its short directory name
is checked against the full identity and source root in layout-v2.json, so
different checkouts cannot silently share project state. A process mutex
prevents concurrent wrapper builds of the same checkout. Unexpected abandoned
ownership requires inspection of the prior receipt/build processes.

The snapshot comes from regular Git-tracked/nonignored working-copy files,
including authorised uncommitted changes. Private/generated paths, .git and keys
are excluded/refused. Deleted tracked inputs are represented in its digest.
The full file/hash manifest addresses an E: context and checks any existing
partial copy before reuse. No Git repository/worktree is created there. The
canonical source revision/dirty state is retained in each receipt; read-only
Git metadata queries remain bound to the real checkout.

Before publication, every snapshot input must still match and the canonical
source digest/revision/dirty state must be unchanged. Generated source changes
(such as a new Room schema) are held for review and deliberate source updates.
The snapshot never becomes the authoritative working copy. Signing/embedded
release provenance still needs its independent release checks.

The wrapper sets SIGNAL_GROVE_BUILD_ROOT to an ordinary directory on E:.
Root Gradle configuration redirects root/subproject buildDirectory only when
that explicit Windows override is present. Normal Gradle/CI layout stays as
before. The wrapper also supplies --project-cache-dir and Kotlin persistent
directory/compatibility-write properties. No machine path enters tracked
Gradle properties and no existing signing key moves.

After Gradle exits0, the wrapper copies available APKs, metadata, app/root
reports and raw TEST-*.xml to their familiar build paths inside the checkout.
Every copied/retained file is SHA-256 checked against its E: source. Obsolete
raw unit XML receives a verified E: backup before retirement, preventing stale
test classes from inflating later counts. Other existing generated files are
preserved; temporary intermediates are read from the recorded E: layout.

Each invocation keeps an E: receipt containing tasks, layout, phases, timings,
exit status and published file hashes. A built receipt is not a published one.
Build/publish failure exits with failure and retains its own outcome; it does
not replay the command. Available files are not proof that every task or test
executed freshly. Consumers must check the requested task results and exact
source/artifact evidence, including cache status.

Existing ignored caches/intermediates in the checkout are not deleted. A clean
task cleans the configured temporary build location; it does not remove last
published outputs. Use the current receipt to identify outputs from an
invocation. Production/owner-phone release signing and installation remain
separate.

## Primary configuration references

Gradle's [ProjectLayout](https://docs.gradle.org/current/dsl/org.gradle.api.file.ProjectLayout.html)
provides buildDirectory. Its
[directory guide](https://docs.gradle.org/current/userguide/directory_layout.html)
describes project/user caches. Kotlin documents
[persistent project data](https://kotlinlang.org/docs/gradle-configure-project.html#kotlin-gradle-plugin-data-in-a-project)
and the properties used by this wrapper.

## Accepted local validation — 8 November 2026

The first complete revised snapshot build executes all106 actionable tasks,
including the five required tasks. Gradle reports3m21s; the measured wrapper
invocation is240.9s, including verified publication. All638 unique JVM methods
pass freshly, both debug/release lint have0 errors, and186 policy tests pass.
All605 source/context files remain exact;157 published files match E: hashes.
The historical debug key and both APK signers are unchanged.

An unchanged repeat reports8s in Gradle and43.2s for the wrapper. Of106 tasks,
103 are up-to-date and3 execute. All72 raw JVM/lint reports and both APKs are
byte-identical; this retains the638 cases rather than claiming a fresh run.
These timings are observations with existing shared dependency caches, not a
controlled benchmark or a promise for another machine.

Seven negative preflights verify C: work/SDK/signing refusal, foreign layout
binding, concurrent wrapper ownership, direct Gradle C: output refusal and no
publication after Gradle failure. Forbidden paths are never created;28 existing
checkout cache files remain byte-identical. A synthetic zero-test publisher
fixture is backed up, hash-verified and retired. It is not a JUnit test result;
the actual70 XML reports still contain638 distinct passing methods.

Existing native authentication/project clients pass their two methods on each
of three preserved API26/large37/compact37 emulator profiles: six method
invocations,24 semantic checks per profile. Geometry, data and reverse bindings
are preserved; the owner phone is not targeted. This checks unchanged clients,
not canonical attachment or a production release. Failed direct-relocation and
earlier preflight attempts remain recorded separately.

See [validation](../VALIDATION.md) for artifact hashes and CI scope. Final
documentation follows the accepted freeze; exact-head gates still precede a
merge or release.
