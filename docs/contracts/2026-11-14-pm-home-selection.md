# Exact PM home and committed-selection observations

14 November 2026. The backend now has a source-bound read-only PM selection
observer and four actual Pi context observations. Both canonical selections are
missing; both owning homes have committed selections. This verifies the current
home-specific preparation requirement without provisioning anything.

## What the observer proves

`scripts/native_pm_selection.py` requires a fresh isolated, no-site, no-bytecode
Linux child with the exact reviewed home and no other inherited Hermes overrides.
Its context binds literal source/home/store/owner paths, seven fixed input hashes
and exact expected facts presence/content. A source-derived install key stays
constant while dependency facts are scoped separately for each active home.

The observer reads six fixed Python modules and the install stamp through bounded
no-follow native reads. It refuses foreign ownership, links, writable inputs and
native identity/content drift. A dedicated loader executes the already reviewed
module bytes and refuses unreviewed SDK submodules or preloaded SDK aliases.
It does not load bytecode caches or run bootstrap. A process audit hook refuses
writes, permission changes, process execution/signalling and network operations.
An attempted effect prevents a successful result even if SDK code catches it.

Unmodified SDK selection functions compute the install key, facts path, owning
home, permanent store, committed environment and selected Python. The observer
rereads input/facts identities after those calls. Facts replacement with identical
bytes is detected through its native inode. Explicit expected absence cannot be
silently replaced by a newly arrived facts file. The observer is once-only in its
child; it offers no retry/repair or environment alias.

This is a selection observation. It deliberately returns false for PM-readiness
and normal-launcher verification, even when a committed environment and Python
exist. A plausible legacy fallback Python is reported without being promoted to
a committed selection. Lock currency, dependency/tool digests, native ABI,
supported PM verification, launchability and operational ownership remain gates.
There is no sync, doctor, PM client/manager request, credential read or model call.

## Actual four-context result

| Runtime/context | Committed dependency selection | Remaining implication |
| --- | --- | --- |
| Old runtime, canonical home | Missing | Prepare and verify the old stop/cleanup launch context before retiring it. |
| Candidate, canonical home | Missing | Prepare and verify the new startup context before starting it. |
| Old runtime, owning home | Observed | Existing owner facts do not establish the canonical home's selection. |
| Candidate, owning home | Observed | Existing staged readiness keeps its original separate scope. |

Independent recovery verifies all four source/home/store/owner bindings, seven
native input identities per context, six loaded SDK module hashes, exact facts
presence/hashes, committed environment containment and terminal child identity.
The SDK's borrowing logic keeps updates/publication with the owner, while the
active home still needs its own committed dependency state. No owner facts are
copied and no override is used to manufacture a selection.

Static comparison separately finds eight inspected PM/bootstrap Python files
identical between old and candidate; the shell activation runner differs. The
first source-inspection helper wrongly applied the new manifest to that old
runner and refused. The corrected static observation keeps old-observed and
candidate-map provenance distinct. It executes no old runner or PM action.

## Native tests and preserved wrapper failure

All **14 Pi methods pass with no skips**: four schema and ten Linux boundary
methods. They cover false-ready fallbacks, changed source, unknown SDK imports,
write attempts, preloaded aliases, home/override mismatches, facts links, identical-
byte inode replacement during SDK reads and same-child reuse. Boundary fixtures
use pinned stub modules; the four observations above separately use actual SDK
sources. No stub result is presented as Hermes interoperability.

All five native phases (unit cohort plus four observations) exit zero. The outer
wrapper then fails: its facts-hash loop overwrites the variable holding the source
inventory, so the final dictionary comparison cannot pass. Its failed terminal
receipt remains failed. Separate read-only recovery authenticates raw results,
source/facts/native identities, all 14 exact positive method names, ended phases
and unchanged 23,628 source inputs, 41 caches, tool facts, modes and live service
incarnations. No test or observation is replayed, and no service is restarted.
Future operator wrappers must use a distinct facts-hash name.

Windows runs **860 cases: 723 pass/137 platform skips**. The ten new Linux cases
run on the Pi; 134 platform methods now have accepted Pi cohort evidence, while
three older cases retain separate scope. The unchanged 80-method publication/
ancestry and 124-method service/journal cohorts keep their earlier exact source
identities. Android/build inputs, APK and production-phone acceptance are unchanged.

| Evidence | SHA-256 |
| --- | --- |
| Read-only selection observer | 892761dbaba14f800e551603274a42d812f209c749fb8cf20ec4857fa7c4f824 |
| Observer regressions | ef1cadc5f611ac64da5670e3e0ca6c9d32d94abaf0632eda36028074811df7d2 |
| Preserved failed outer receipt | a251b9647c690a1b006ca1a1678bd99ee9894b983ccc87d4585320f9e5943114 |
| Independent raw/context recovery | 2ec534fc4264a78e3dbb79f665d0b4c1a9dec693f6329c70adf22490310d371e |
| Separate conservation-field observation | 7fbde87b9ecd7098ae0ea6f70d5cf97d97a487404b636725197e5137b38142b8 |
| Static old/candidate source observation | 79c421bfda7c21c02ba77fa4233f3cbbd1607dfcc40d2227dadf725066f7f7a6 |
| Full Windows suite | 1eddd81bbb31a29d9440a4e061a7314ee0a591888d1ec7e6bf0f0d74e1ec858a |

Exact paths, dependency facts, original targets and routing metadata stay in
private evidence outside Git. No policy body, credential or personal content is
published. No canonical-home file, source, permission, reference, grant, model or
service is changed by this observer.

Next implement the one-use supported PM preparation operations and compose them
with fresh encrypted backups, exact work/admission fences, native publication/
ancestry, manager reload, authenticated health/history/graph and explicit runtime-
only recovery. Bind journal and other operational paths separately. Complete the
full isolated transaction and concrete packet before separate live approval.
All original API26 cause, used/transitive compatibility/shutdown, actual projects/
workspaces/workers/NAS, automation/integrations and final phone/local-model/security/
original-key production release requirements remain in the active full goal.

[Publication ancestry](2026-11-14-publication-ancestry.md) ·
[Staged PM readiness](2026-11-14-staged-pm-readiness-and-sdk-pass.md) ·
[Full runtime packet](2026-11-14-runtime-cutover-packet.md).
