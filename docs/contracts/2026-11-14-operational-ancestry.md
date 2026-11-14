# Shared operational ancestry for journal, PM and publication

14 November 2026. The ten-role path boundary passes native checks and a real SDK
install in a fresh isolated home. Canonical provisioning and live activation
remain unapproved and unperformed.

`scripts/native_operational_paths.py` extends the immutable publication witness
with a closed directory catalog. The combined guard walks from `/` in production
review scope, opening every component through directory descriptors with
no-follow checks. Ownership, mode and native device/inode must match. Owned tests
start at an explicit private catalog root and claim nothing above it.

| Role | Boundary |
| --- | --- |
| `journal` | Exact private durable journal directory |
| `hermes_home` | Exact home for this operation, distinct from a source/store |
| `old_source`, `new_source` | Reviewed old and candidate SDK source roots |
| `old_store`, `new_store` | Their exact permanent tool stores |
| `backup_output` | Private encrypted-backup output parent |
| `backup_programs` | Reviewed backup program parent |
| `backup_identity` | Private identity parent; no key content is read by this guard |
| `executor` | Private controller/code root |

All ten paths are distinct. Journal, executor, backup-output and identity roots
must be 0700; the remaining endpoint modes may be 0700 or 0755. No endpoint may
itself be one of the seven mutable mode targets. All actual intermediate parents
are checked; only the existing exact seven planned mode transitions may differ
from a protected initial mode. Missing operational paths are refused, never
created or adopted by this guard. Publication's existing explicit one-level
parent-creation intents keep their original native witnesses.

The combined identity binds both original publication and operational catalogs.
It is captured before any transition. All effect actors share that same ancestry
object, so an independent old guard cannot adopt a mode change. Every role/path
and returned descriptor is explicitly checked. There is no mkdir, chmod,
discovery, repair, approval, backup execution or restore API here.

`CutoverJournal` can now open through the exact bound `journal` descriptor and
rechecks the whole shared boundary before names/content/writes/claims. Supplying
only a guard hash or a mismatched role/hash refuses before opening journal files.
Ancestor drift poisons the journal and preserves its original bytes. Its existing
unguarded component API remains separately scoped; production PM now requires a
guarded journal sharing its exact native boundary. Owned-root PM fixtures retain
their explicit private-root scope. A production PM context additionally requires
real-root review scope and controller-code containment under the executor root.
PM receipts bind the operational identity/state before the install intent.

Composition required an ordering correction: a publisher observes a successful
native chmod or new-parent inode, advances that exact in-memory witness, then
writes its durable receipt. The journal's receipt check can therefore see the
actual effect through the shared guard. Failure still leaves an unknown outcome;
memory state grants no recovery/resume authority. No permission is inferred or
changed by the guard itself.

## Verification and limits

The Windows suite runs **905 cases: 737 pass/168 platform skips**. The Pi's exact
**208-method cohort has no failures/skips**: journal 58, ancestry 18, execution
scope 6, file publication 46, operational paths 21, PM 18, service manager 25 and
renderer catalog 16. These are overlapping component cohorts, not a fleet-wide
interoperability total. All 165 applicable platform cases have scoped native Pi
evidence; three older platform cases retain their separate scope.

The 21 operational cases include fixed schemas and real descriptor opens,
review/hash/role swaps, same-mode inode replacement, symlinks, writable parents,
missing endpoints, private roots, wrong journal binding, poisoning/no-write on
drift, actual seven-mode publication and actual parent creation with a guarded
journal. A separate PM regression refuses a production context without its
boundary before any cache or receipt is created. SDK stubs remain boundary
fixtures and are not presented as real Hermes interoperability.

A separate actual SDK flow captures the real-root ancestry, uses the ten-role
guard for both the journal and PM actor, and performs one supported full install
in a new private home. It verifies committed facts, supported currency, four
activation stamps, native ARM64 Python 3.14.9 / ContextGraph 0.8.1 / PyYAML 6.0.3
and preserved newer fixture data. Non-PM prerequisites, including backups and
old-PM readiness, are explicitly synthetic. No canonical home is prepared and
none of the seven real 0775 modes is changed.

Independent read-only review checks all 208 exact positive method names, current
source hashes, the actual guarded SDK receipt/journal/facts/ABI, shared catalog
digests and every recorded real ancestor inode/mode. Both owned units are stopped
with empty cgroups. It also verifies 23,628 source inputs, 41 SDK caches, existing
tool/app facts and unchanged live service incarnations. A separate read-only
observation binds ten already existing actual operational directory roles, all
0700, including the current canonical home; it does not open its journal or run
PM. Earlier failed/held receipts retain their original scope and are not replayed.
The first independent reviewer looked for the sentinel at the fixture root;
the source creates it under the isolated data directory. That read-only failure
is retained. Corrected readback verifies the original sentinel in its exact
source-defined location, without replaying tests or the install.

| Evidence | SHA-256 |
| --- | --- |
| Operational boundary | 50728b9591ecb4ec65477b1e0a17f36dc8027cd51ddbfb54214b8850f557d975 |
| Guarded journal | c7f9bd1683b868040fd20f5bf4abdcfbf730c44e29fcc796938ea39724cefbec |
| Publisher transition ordering | 6cf9a607a19dfd996ed6043f380655250f8083aa03796686fc27a31bb308d933 |
| Guarded PM actor | fd3b337b158b809fd57ca19a7909653bfe8ffabb3b69e69f9e30cd0d79ff7484 |
| Operational regressions | 23642b3461d4a9ca140b8e94c7f11d2fbcf0ed68a21c4b16bf1902286a761dfd |
| Actual guarded SDK fixture | af299b82c2d123abfbe294891579be771b9b064d06d8c65dccf62f0ba5810859 |
| Independent native/raw review | 04fbb48834d8be3b48d1f1885ec98c4178788038f9b80c2bfb1a877bc8b72641 |
| Actual directory observation | eb3def3cf97405d462d9543bd0ebf87340fefcf68077b52797b29651fc83fc44 |
| Full Windows suite | 57b5bbaa75966494311f310600dc20937c3f3905d2026d26d18606606a207019 |

Paths, directory identities, native logs and operation receipts remain private
outside Git. There is no key/credential content or project data in this packet.

Remaining: bind actual backup program/key files and nested inputs; implement and
verify fresh encrypted backups through the supported archive selection rules;
complete gateway/serve admission, manager reload, authenticated live-user
health/history/graph and the full runtime-only recovery backend. Parent ancestry
does not prove those effects. Finish the isolated full transaction and concrete
final packet before separately approved canonical PM, seven named mode changes,
policy/references and idle live cutover. Keep every original audit, phone,
local-AI, security and signed-release requirement active.

[PM install contract](2026-11-14-native-pm-preparation.md) ·
[Publication ancestry](2026-11-14-publication-ancestry.md) ·
[Full runtime packet](2026-11-14-runtime-cutover-packet.md).
