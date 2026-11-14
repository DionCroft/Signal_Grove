"""Fixed cutover/recovery order over an exclusive native journal.

There is no live CLI, default approval or default host adapter. The reviewed
caller supplies an actual CutoverJournal and bounded, authenticated native
operations. Observations must verify real effects, including exact target and
process/absence identities, not merely acknowledgements. This module dispatches
each named action once. Errors hold for explicit review; rollback is never an
automatic exception handler. Recovery has a separate approval, operation and
packet bound to the original history; it cannot resume forward steps or restore
databases/replay work. Hashes bind records and are not themselves authorization.
"""
from __future__ import annotations

from dataclasses import dataclass, asdict
import hashlib
import json
import math
import time

from cutover_journal import CutoverJournal, HASH, OPERATION, Refused, Unknown


FORWARD = ('preflight', 'backup_pre', 'pm_old', 'pm_new', 'gateway_drain',
    'serve_retirement', 'stop_gateway', 'stop_serve', 'secure_modes',
    'publish_reviewed', 'install_policy', 'bind_gateway', 'bind_serve', 'bind_cli',
    'reload_units', 'start_gateway', 'start_serve', 'verify_new', 'backup_post')
RECOVERY = ('recovery_preflight', 'recovery_backup', 'rollback_gateway_drain',
    'rollback_serve_retirement', 'rollback_stop_gateway', 'rollback_stop_serve',
    'rollback_policy', 'rollback_runtime', 'rollback_reload',
    'rollback_start_gateway', 'rollback_start_serve', 'verify_rollback', 'recovery_backup_post')
READ_ONLY = frozenset({'preflight', 'verify_new', 'recovery_preflight', 'verify_rollback'})
COMMON = frozenset({'authority_bound', 'packet_bound', 'source_inventory',
    'credentials_unchanged', 'models_unchanged', 'data_preserved',
    'no_unknown_work', 'work_zero', 'native_owner_or_verified_absence'})
BEFORE = {
    'pm_old': {'fresh_backup_verified'}, 'pm_new': {'old_pm_ready'},
    'gateway_drain': {'old_pm_ready', 'new_pm_ready', 'fresh_backup_verified'},
    'serve_retirement': {'gateway_admission_closed'},
    'stop_gateway': {'gateway_admission_closed', 'serve_admission_closed', 'old_pm_ready'},
    'stop_serve': {'gateway_stopped', 'serve_admission_closed', 'old_pm_ready'},
    'secure_modes': {'old_incarnations_ended'},
    'publish_reviewed': {'old_incarnations_ended', 'named_modes_ready'},
    'install_policy': {'old_incarnations_ended', 'named_modes_ready', 'artifacts_published'},
    'bind_gateway': {'old_incarnations_ended', 'named_modes_ready', 'artifacts_published', 'policy_ready'},
    'bind_serve': {'old_incarnations_ended', 'named_modes_ready', 'artifacts_published', 'policy_ready'},
    'bind_cli': {'old_incarnations_ended', 'named_modes_ready', 'artifacts_published', 'policy_ready'},
    'reload_units': {'old_incarnations_ended', 'refs_installed'},
    'start_gateway': {'old_incarnations_ended', 'loaded_units_ready', 'new_pm_ready', 'named_modes_ready', 'policy_ready'},
    'start_serve': {'gateway_ready', 'loaded_units_ready', 'new_pm_ready'},
    'verify_new': {'gateway_ready', 'serve_ready', 'refs_installed', 'policy_ready'},
    'backup_post': {'new_health_verified'},
    'recovery_preflight': {'all_prior_effects_observed', 'runtime_only_recovery', 'no_work_replay'},
    'recovery_backup': {'all_prior_effects_observed', 'runtime_only_recovery', 'no_work_replay'},
    'rollback_gateway_drain': {'recovery_backup_verified'},
    'rollback_serve_retirement': {'gateway_quiescent'},
    'rollback_stop_gateway': {'gateway_quiescent', 'serve_fenced_or_absent'},
    'rollback_stop_serve': {'gateway_stopped', 'serve_fenced_or_absent'},
    'rollback_policy': {'current_incarnations_ended', 'policy_exact_or_absent'},
    'rollback_runtime': {'current_incarnations_ended', 'reviewed_policy_retired', 'runtime_only_recovery', 'no_work_replay'},
    'rollback_reload': {'current_incarnations_ended', 'runtime_refs_restored'},
    'rollback_start_gateway': {'old_pm_ready', 'runtime_refs_restored', 'loaded_old_units_ready'},
    'rollback_start_serve': {'gateway_ready', 'loaded_old_units_ready'},
    'verify_rollback': {'gateway_ready', 'serve_ready', 'runtime_refs_restored', 'runtime_only_recovery', 'no_work_replay'},
    'recovery_backup_post': {'rollback_health_verified'},
}
AFTER = {
    'backup_pre': {'fresh_backup_verified'}, 'pm_old': {'old_pm_ready'}, 'pm_new': {'new_pm_ready'},
    'gateway_drain': {'gateway_admission_closed'}, 'serve_retirement': {'serve_admission_closed'},
    'stop_gateway': {'gateway_stopped'}, 'stop_serve': {'old_incarnations_ended'},
    'secure_modes': {'named_modes_ready'}, 'publish_reviewed': {'artifacts_published'},
    'install_policy': {'policy_ready'}, 'bind_gateway': {'gateway_ref_installed'},
    'bind_serve': {'serve_ref_installed'}, 'bind_cli': {'refs_installed'},
    'reload_units': {'loaded_units_ready'}, 'start_gateway': {'gateway_ready'},
    'start_serve': {'serve_ready'}, 'verify_new': {'new_health_verified'},
    'backup_post': {'post_backup_verified'}, 'recovery_preflight': {'all_prior_effects_observed', 'runtime_only_recovery', 'no_work_replay'},
    'recovery_backup': {'recovery_backup_verified'}, 'rollback_gateway_drain': {'gateway_quiescent'},
    'rollback_serve_retirement': {'serve_fenced_or_absent'}, 'rollback_stop_gateway': {'gateway_stopped'},
    'rollback_stop_serve': {'current_incarnations_ended'}, 'rollback_policy': {'reviewed_policy_retired'},
    'rollback_runtime': {'runtime_refs_restored', 'runtime_only_recovery', 'no_work_replay'},
    'rollback_reload': {'loaded_old_units_ready'}, 'rollback_start_gateway': {'gateway_ready'},
    'rollback_start_serve': {'serve_ready'}, 'verify_rollback': {'rollback_health_verified'},
    'recovery_backup_post': {'post_backup_verified'},
}
CHECK_NAMES = COMMON | {'step_preimage', 'step_effect'} | frozenset().union(*BEFORE.values(), *AFTER.values())


def _hash(value):
    return type(value) is str and HASH.fullmatch(value) is not None


def _number(value):
    return type(value) in (int, float) and math.isfinite(value) and value >= 0


def _digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':'), allow_nan=False).encode()).hexdigest()


def required_checks(step, phase):
    if step not in FORWARD + RECOVERY or phase not in ('before', 'after'):
        raise ValueError('One fixed cutover stage and observation phase are required')
    safety = {'runtime_only_recovery', 'no_work_replay'} if step in RECOVERY else set()
    return COMMON | safety | ({'step_preimage'} | BEFORE.get(step, set()) if phase == 'before'
                     else {'step_effect'} | AFTER.get(step, set()))


@dataclass(frozen=True)
class Plan:
    packet_sha256: str
    authority_sha256: str
    preflight_snapshot_sha256: str
    rollback_packet_sha256: str

    def __post_init__(self):
        if not all(_hash(v) for v in asdict(self).values()) or self.packet_sha256 == self.rollback_packet_sha256:
            raise ValueError('Exact forward, authority, preflight and distinct rollback packet hashes are required')


@dataclass(frozen=True)
class ApprovedExecution:
    """Data supplied by an external approval gate, never an approval issuer."""
    operation: str
    packet_sha256: str
    authority_sha256: str
    scope: str
    approval_receipt_sha256: str
    deadline: float

    def __post_init__(self):
        if (type(self.operation) is not str or not OPERATION.fullmatch(self.operation)
                or not all(_hash(v) for v in (self.packet_sha256, self.authority_sha256, self.approval_receipt_sha256))
                or self.scope not in ('forward', 'recovery') or not _number(self.deadline)):
            raise ValueError('A bound explicit execution approval and native monotonic deadline are required')

    @property
    def binding_sha256(self):
        return _digest(asdict(self))


@dataclass(frozen=True)
class RecoveryCase:
    packet_sha256: str
    rollback_packet_sha256: str
    original_operation: str
    original_plan_sha256: str
    history_sha256: str
    root_plan_sha256: str
    root_authorization_sha256: str
    observed_snapshot_sha256: str

    def __post_init__(self):
        if (type(self.original_operation) is not str or not OPERATION.fullmatch(self.original_operation)
                or not all(_hash(v) for k, v in asdict(self).items() if k != 'original_operation')
                or self.packet_sha256 == self.root_plan_sha256):
            raise ValueError('A separate reviewed recovery packet and exact original history are required')


@dataclass(frozen=True)
class Observation:
    operation: str
    packet_sha256: str
    authority_sha256: str
    scope: str
    step: str
    phase: str
    observed_at: float
    snapshot_sha256: str
    evidence_sha256: str
    checks: tuple

    def __post_init__(self):
        if (type(self.operation) is not str or not OPERATION.fullmatch(self.operation)
                or not all(_hash(v) for v in (self.packet_sha256, self.authority_sha256, self.snapshot_sha256, self.evidence_sha256))
                or self.scope not in ('forward', 'recovery') or self.step not in FORWARD + RECOVERY
                or self.phase not in ('before', 'after') or not _number(self.observed_at)
                or type(self.checks) is not tuple
                or any(type(p) is not tuple or len(p) != 2 or type(p[0]) is not str
                       or p[0] not in CHECK_NAMES or type(p[1]) is not bool for p in self.checks)
                or len({p[0] for p in self.checks}) != len(self.checks)):
            raise ValueError('A closed, fresh native observation is required')


class CutoverCoordinator:
    """One explicit forward operation or separately reviewed runtime recovery.

    `operations.observe(step, phase, execution)` is read-only and returns a fresh
    Observation with evidence persisted by the native adapter. Each required
    check must derive from actual source/process/files/receipts and relevant
    authenticated SDK fences. `perform` must recheck exact native preimages and
    have bounded I/O; its return value is ignored. Neither callbacks nor this
    class may restore databases, change models/credentials, expand grants or
    replay tasks. There are no action retries or implicit compensating actions.
    """
    def __init__(self, plan, journal, operations, *, now=time.monotonic):
        if (type(plan) is not Plan or type(journal) is not CutoverJournal
                or not all(callable(v) for v in (getattr(operations, 'observe', None), getattr(operations, 'perform', None), now))):
            raise ValueError('An exact plan, native exclusive journal and reviewed operations are required')
        self.plan, self.journal, self.operations, self.now = plan, journal, operations, now
        self.state = 'new'; self._last_time = None; self._started = False

    def _clock(self, execution=None):
        try:
            value = self.now()
        except Exception:
            raise Unknown('Native monotonic time is unavailable') from None
        if not _number(value) or (self._last_time is not None and value < self._last_time):
            raise Unknown('Native monotonic time is unproven')
        self._last_time = value
        if execution is not None and value >= execution.deadline:
            raise Unknown('Execution deadline elapsed; inspect without replay')
        return value

    def _observe(self, step, phase, execution, expected_snapshot=None):
        entered = self._clock(execution)
        result = self.operations.observe(step, phase, execution)
        ended = self._clock(execution)
        if (type(result) is not Observation or result.operation != execution.operation
                or result.packet_sha256 != execution.packet_sha256 or result.authority_sha256 != execution.authority_sha256
                or result.scope != execution.scope or result.step != step or result.phase != phase
                or not entered <= result.observed_at <= ended or ended - result.observed_at > 5
                or (expected_snapshot is not None and result.snapshot_sha256 != expected_snapshot)):
            raise Refused('Native observation context, freshness or approved preimage refused')
        checks = dict(result.checks)
        if any(checks.get(key) is not True for key in required_checks(step, phase)):
            raise Refused('Native work, identity, readiness or effect proof refused')
        return result

    def _step(self, step, execution, snapshot=None):
        self._observe(step, 'before', execution, snapshot)
        self.journal.intent(step)
        # A slow intent write or a new admission must prevent dispatch.
        self._observe(step, 'before', execution, snapshot)
        self.journal.assert_current(); self._clock(execution)
        if step not in READ_ONLY:
            self.operations.perform(step, execution)
            self._clock(execution)
        result = self._observe(step, 'after', execution, snapshot)
        self.journal.verified(step, result.evidence_sha256)
        self.journal.assert_current(); self._clock(execution)
        return result.evidence_sha256

    def _execute(self, execution, recovery):
        if self.state != 'new':
            raise Refused('This coordinator cannot be reused or resumed')
        self.state = 'preflighting'
        try:
            if type(execution) is not ApprovedExecution or execution.authority_sha256 != self.plan.authority_sha256:
                raise Refused('Explicit target-bound approval required')
            started = self._clock()
            if execution.deadline <= started or execution.deadline > started + 1800:
                raise Refused('Approval deadline is expired or outside its local bound')
            self.journal.assert_current(); view = self.journal.view
            if recovery is None:
                if execution.scope != 'forward' or execution.packet_sha256 != self.plan.packet_sha256 or view['recovery_required']:
                    raise Refused('Forward approval or prior journal reconciliation is missing')
                steps = FORWARD; snapshot = self.plan.preflight_snapshot_sha256
            else:
                if (type(recovery) is not RecoveryCase or execution.scope != 'recovery'
                        or execution.packet_sha256 != recovery.packet_sha256
                        or recovery.rollback_packet_sha256 != self.plan.rollback_packet_sha256
                        or recovery.root_plan_sha256 != self.plan.packet_sha256
                        or recovery.root_plan_sha256 != view['root_plan_sha256']
                        or recovery.root_authorization_sha256 != view['root_authorization_sha256']
                        or recovery.original_operation != view['operation'] or recovery.original_plan_sha256 != view['plan_sha256']
                        or recovery.history_sha256 != self.journal.history_sha256 or not view['recovery_required']):
                    raise Refused('Separate recovery approval or exact original history refused')
                steps = RECOVERY; snapshot = recovery.observed_snapshot_sha256
            first = self._observe(steps[0], 'before', execution, snapshot)
            self.journal.assert_current(); self._clock(execution)
            if recovery is None:
                self.journal.begin(execution.operation, execution.packet_sha256, authorization_sha256=execution.binding_sha256)
            else:
                self.journal.begin_recovery(execution.operation, execution.packet_sha256,
                    original_operation=recovery.original_operation, original_plan_sha256=recovery.original_plan_sha256,
                    history_sha256=recovery.history_sha256, evidence_sha256=first.evidence_sha256,
                    authorization_sha256=execution.binding_sha256)
            self._started = True; self.state = 'running'
            if self.journal.view['authorization_sha256'] != execution.binding_sha256:
                raise Unknown('Durable authorization binding is unproven')
            proofs = [(step, self._step(step, execution, snapshot if index == 0 else None)) for index, step in enumerate(steps)]
            self._clock(execution); self.journal.assert_current()
            digest = _digest(proofs); self.journal.complete(digest)
            self.state = 'completed'
            return dict(state='completed', scope=execution.scope, operation=execution.operation,
                        packet_sha256=execution.packet_sha256, authority_sha256=execution.authority_sha256,
                        authorization_sha256=execution.binding_sha256, completed_steps=list(steps), evidence_sha256=digest)
        except BaseException as exc:
            self.state = 'unknown' if self._started else 'held'
            if self._started:
                try:
                    self.journal.unknown()
                except BaseException:
                    pass  # Preserve original bytes/error; never repair or dispatch compensation.
            if not isinstance(exc, Exception):
                raise
            error = Unknown if self._started else Refused
            raise error('Transaction held; inspect exact effects and history without replay') from None

    def run(self, execution):
        return self._execute(execution, None)

    def recover(self, execution, case):
        if case is None:
            raise Refused('An explicit reviewed recovery case is required')
        return self._execute(execution, case)
