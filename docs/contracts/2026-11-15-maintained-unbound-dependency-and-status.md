# Maintained SDK unbound dependency and status correction

15 November 2026. Isolated control-plane source/component qualification only.
The full original goal and final release requirements remain active.

## Verified result

The maintained Pi SDK now has a source-bound correction for two actual findings:
its ordinary turn path referenced a missing peer-context module, and its status
observer omitted active unbound turns. A fresh immutable SDK copy adds the exact
86-line maintained peer module and includes the existing unbound counter inside
the same read-only database snapshot. The original staged SDK is unchanged.

The corrected SDK passes **16 read-only status checks using two real native turn
receipts**. The original observer reports zero while the correction reports one
active outer turn and two during nesting. Checks retain strict peer-context
routing, profile separation, freeze/new-admission and active-thaw refusals,
completion while frozen, and five expected UNKNOWN outcomes for incomplete,
missing or locked ledgers. Every status observation leaves generated files
unchanged. Independent readback verifies both finished receipts, the retained
frozen fence, ended native groups and all source/dependency/PM/live baselines.

The exact patch postimages verify. Windows completes **1,158 policy cases:
885 positive / 273 platform skips**, matching the earlier distinct inventory.
The 16 native observations are separate component checks, not additional policy
methods. Android sources still match the previously verified build snapshot:
1,011 JVM tests, 27 focused methods per API26/API37 and zero lint errors, with
51 debug / 50 release warnings. No new Android build or production APK is claimed.

Both failed native preparations remain preserved: the first exposed the missing
module; the next hit a fixture assumption about cold configuration initialization.
The final probe accounts separately for the SDK's default SOUL/config backup,
then verifies read-only status behavior. No SDK function or counter is mocked.
The original SDK's prior configured freeze/drain/native-stop result remains
separate. The corrected SDK still needs actual gateway-wire/original-journal
qualification, concurrent snapshot races and a final PM generation. Upstream,
API26 original/navigation, fleet/worker/integration and production gates remain
open. Live Hermes, the owner's phone and published beta.9 are unchanged.

## Actual prerequisite defect and minimal correction

The first native probe calls the real maintained SDK `turn_scope` for an ordinary
general-chat turn. It fails at the import of `agent.project_peer_request` before
an unbound receipt can be created. The immutable 23,628-file Pi source map lacks
that module while its session-scope code imports it. The maintained Windows
patch contains the complete 86-line module. No fake module, absent-context
fallback, `remote=True` bypass or SDK function replacement is used to pass.

A new full SDK copy declares exactly one changed file and one added file:

- `agent/project_control_status.py`: add the SDK's own
  `active_unbound_count(db)` to `active_turns` inside the original SQLite BEGIN,
  after the existing counters. Keep read-only connection/query-only policy,
  configuration/epoch checks, profile binding and UNKNOWN handling.
- `agent/project_peer_request.py`: include the exact 86-line maintained source,
  including bounded decision/checkpoint parsing, default-empty ContextVar and
  native-enabled/binding checks. No helper logic changes or transport endpoints,
  grants, capability advertisements, approvals or credentials are added.

The corrected map has 23,629 files; every other original byte matches. The
reviewed PM interpreter and 5,033 dependency code files are reused unchanged.
This is not a newly locked/synced final PM source generation. The original SDK,
all PM facts, prior failed copies, seven existing directory modes and original
live service identities independently verify unchanged.

The tested source patch is
[hermes-maintained-unbound-status.patch](../../upstream_patches/hermes-maintained-unbound-status.patch).
It applies to the recorded maintained derivative, not pristine upstream. In a
new reviewed copy, command-local `git -c core.autocrlf=false apply --check` and
apply produce the exact declared postimages. The first Windows scratch check's
CRLF conversion mismatch is retained; hashes below bind LF source/patch bytes.
The final hunk uses one non-blank context line on each side, preserving the exact
reviewed source preimage/postimages while passing the original whitespace check.
No whitespace rule is disabled. No global Git setting, original source or live
runtime is changed.

## Real native behavior and read-only boundaries

The [native fixture](../../scripts/native/sdk_unbound_status_fixture.py) runs
under the reviewed PM Python with `-I -B`, a cleared environment, private generated
homes, an existing cooperative network/filesystem refusal fence and a finite
native unit. Its caller verifies the full source/dependency inventory before and
after execution. The original observer is loaded under a separate module name;
the correction is imported normally from the declared SDK copy.

Actual SDK `Ledger`, `turn_scope`, native PID/process-birth ownership, empty real
ContextGraph session registries and SQLite receipts are used. No model request,
worker dispatch, user project, inference credential or live identity is involved.
Both the outer turn and nested child are genuinely admitted and finish. The
original status function reports zero while the corrected one reports one/two;
SDK fence counts and owned active receipt rows independently agree during the
fixture. The other generated profile stays idle with a different home digest.

A fixture-set peer context selects the strict admission path and the native-
enabled guard refuses disabled admission. This is a routing/guard component
check, not authenticated peer transport or authorization acceptance. An SDK
freeze preserves the existing active turn, refuses a new turn and refuses thaw
while active. Existing work can finish while frozen; the exact epoch-1 fence and
owner remain. Independent final readback finds two FINISHED receipts, no
registered turn or project binding, and a read-only mode-0400 ledger.

Five observations deliberately return UNKNOWN: either half of the two-table
unbound schema missing, a missing phase column, an absent ledger and an exclusive
database lock. The absent ledger is not created and no schema repair is attempted.
After the lock releases, the correct zero is observed again. Whole generated-home
file hashes before/after every one of the 16 status observations match.

The SDK's `load_config_readonly` skips copying the returned config object; it
does not promise no cold disk initialization. An earlier fixture expectation
failed when the real cold loader created default SOUL plus a config backup.
That failed unit/home remains preserved. The final fixture performs this
supported initialization separately, verifies only those two default additions,
checks existing files unchanged and the backup equal to input configuration,
then begins its read-only observation checks. SDK initialization is not mocked
or suppressed and no status write is excused.

## Checks, conservation and limits

All 1,158 Windows policy cases complete: 885 positive / 273 platform skips,
zero failures/errors and the exact prior distinct inventory. Interleaved expected
warning output is matched to each method's terminal status. The 16 native checks
are not counted as additional unit-test methods. The fixture's actual source and
the applied patch/postimages verify independently.

Independent readback matches the original native result, all 16 observation
records, complete corrected/original source maps, original dependency code and
copied helper hashes. All three observed native groups have no main/control PID
or cgroup. Both earlier failed trials, their default files and every raw receipt
remain. Two failed read-only review attempts had verifier variable-name collisions;
the final reader isolates its local namespace and passes both original conservation
boundaries. No native admission or failed action is replayed to repair a reader.

Android source hashes match their last verified snapshot. No new Android build
is needed for these server-maintenance artifacts; the recorded 1,011 JVM / focused
27-per-API checks retain their original source/build scope. Original API26 failure
causes, full device/phone gates and final APK delivery remain unclosed.

This does not qualify actual gateway wire counts, a general-chat turn through
the full AI agent, concurrent SQLite snapshot races, corrected original-journal
drain/native shutdown, unprofiled operation, all-profile/worker admission, full
upstream compatibility, final PM generation or production. The prior original-SDK
configured lifecycle is separately retained; it does not pass this new candidate.
No live cutover, permission/policy/grant/model/credential/launcher change, gateway
restart, real-home backup or production release occurs here.

## Exact evidence identities

| Item | SHA-256 |
| --- | --- |
| `combined component review` | `f56d647220ace0ab5c8e07808cde7c0f12cea19d13d31539a9b94c7af06a8b74` |
| `independent native readback` | `5e243aa1c60673a1c4cd264ae0c54d310fc07f0062add2e71762ce41f42e18d2` |
| `Windows distinct-policy review` | `4bd1ecd566b32994cf430de6d41f3973cc32e9519cc7863217af32da2cafb072` |
| `two-file patch application review` | `b6720a3a8ae64287d91c96599592d6a7c87f67c196a8358141eeceb852552d97` |
| `scripts/native/sdk_unbound_status_fixture.py` | `1a142cb73cd1f58136b567f4781924cf32b5aea8169f2f39c8002b302f746482` |
| `upstream_patches/hermes-maintained-unbound-status.patch` | `0abecdc1576445cf373d2346ef89f69dccedc593c2ef25aec1eaf9156f0fc2fe` |
| `original status preimage` | `42554afb1c28dc582ab0004a78c8a6be9cf566f518ffd98834cba7b5b74af6f2` |
| `corrected status postimage` | `52eb79ddc1daec1f7834526fdab89292cd4a923a4e76ddc2a01f44d2dcdaa707` |
| `exact maintained peer module` | `dbad7c32eb2aaf072fcb0242918d51d5fed94a6bfc43db9262b09fc0b87c7067` |

All original raw observations, source maps, SDK receipts, failed preparations,
readers and logs stay in private validation storage and are bound by the combined
review. Machine paths, source configuration contents and owner data are excluded
from this public record.

[Next action](../../PROJECT_STATE.md) · [Full goal](../GOAL.md) · [Full audit](../AUDIT_EXECUTION_PLAN.md).
