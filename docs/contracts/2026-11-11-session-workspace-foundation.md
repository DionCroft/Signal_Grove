# Canonical session workspace observations and review foundation

The canonical conversation viewer now offers **Session workspace** after an
explicitly reviewed attachment. It reveals only the directory reported in that
attachment and its local observation time. It does not live-refresh the path.
Close the viewer and explicitly review another attachment for a fresh snapshot.
Closing or changing authenticated scope clears the private observation. The
path is hidden until requested and is absent from operation summaries and logs.
Missing, malformed or oversized optional directory data does not prevent viewing
otherwise valid session activity.

This is a directory on the Hermes authority host. On a Pi-backed setup it is a
Pi-side directory. It does not establish a Windows/NAS working copy, worker
identity, application availability or file/execution permissions. Reading host
project metadata and changing an active-project pointer are separate operations.

An internal, unwired review type binds an exact proposed absolute directory to
the authenticated target, profile/home, resolved session, authority instance and
epoch, revision, execution generation and previous directory. A changed or used
review cannot produce another request. A running observation prevents preparing
it; running=false is never treated as an idle state or actor grant. There is no
workspace mutation control, new RPC, filesystem access or automatic retry in
this chunk. The internal payload still requires future transport, durable
receipts, supported permission/handle acquisition and real-runtime acceptance.

## Reviewed upstream scope

The source review verifies Git blob identities and parses source without
executing it at both immutable stable commits:

- v0.23.0: `2bd378ed1b8d6bf2192cb26e0a55ff3091e6bd19`.
- v0.23.1: `3b421578588243f920886e6fe79a6faa87ea026e`.

Workspace preparation normalizes and probes the directory on the authority
host, changes terminal CWD policy and lineage metadata, and advances execution
generation. The canonical workspace mutation uses revision and generation
compare-and-swap plus actor authorization. A socket loss may leave a committed
mutation; never turn a new observation into an automatic retry. The consumed
`CanonicalSnapshotInfo.cwd` field remains in the existing exact contract watch;
this change adds no accepted handler variant or baseline waiver. No deployed
Pi/Spark/runtime/default is changed or upgraded by this review.

## Validation

The frozen source passes 939 unique JVM tests in 92 reports, 229 policy tests,
both APK assemblies and zero-error lint (50 debug / 49 release warnings). Eight
new JVM cases cover optional parsing, UTF-8 bounds and platform paths, private
projection, exact identity/revision/generation checks and single-use/concurrent
consumption. These tests do not claim a server mutation or permission grant.

All 11 canonical-viewer Android methods pass freshly on each preserved API 26
and API 37 emulator: 22 executions. They include the new hidden-until-opened
workspace details and close cleanup, existing privacy/cursor handling, changed
identity, uncertain receipt, consent, large bilingual reachability and lifecycle
checks. The installed app/test hashes and package UIDs are verified. Two native
workspace screenshots are visually reviewed: directory, timestamp and host/worker
explanation are readable and Close remains reachable. This is a selected method
set, not a full Android or physical-phone suite.

Frozen build digest: `ebd7fca7fcc5dd22100651d8925fe115f4aeb08a88e05a2672f08d03b15f7abf`.
App SHA-256: `f19724039eced887aa9fdfb0046ef5198dfb3e840ecf2942742d41414d941f5a`.
Test SHA-256: `8722e12936d536d5910002571d087fff8bb51548bd5845e66bdbb3d716a9e5da`.
The original debug signing certificate, all four ABIs and native load alignment
are verified. No GGUF bytes are packaged. Production phone data/APK are untouched.


## Remaining acceptance

Complete the separately reviewed host-directory mutation with exact-handle
permissions, durable receipts, unknown-outcome recovery and real supported
runtime checks. Separately implement actual worker/project binding, worker
identity and scope discovery, repository/file access and reviewed dispatch.
A Pi directory must never substitute for a Windows project working copy.
The complete original audit, current-source physical phone, native/background/
reboot, helper, integrations and signed-release requirements remain open. The
production APK and pending physical helper installer request are unchanged.
