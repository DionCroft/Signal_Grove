"""Guard the SDK's standalone-serve retirement protocol; never retry or stop a service.

The caller owns authentication, native process observations and an exclusive,
durable transaction journal. `record` must persist each event before returning.
An unresolved request in that journal must be reconciled before constructing a
new guard. Credentials and the process-owned permit remain in memory, never in
events, representations or exception messages. This module has no CLI effects.
"""
from __future__ import annotations

from dataclasses import dataclass
import math
import re
import time


@dataclass(frozen=True)
class Owner:
    pid: int
    start_ticks: int
    boot_id: str

    def __post_init__(self):
        if (type(self.pid) is not int or self.pid <= 1
                or type(self.start_ticks) is not int or self.start_ticks <= 0
                or type(self.boot_id) is not str
                or not re.fullmatch(r'[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}', self.boot_id)):
            raise ValueError('A native Linux process incarnation is required')


class Refused(RuntimeError):
    """No retirement permit is established by this operation."""


class Unknown(RuntimeError):
    """Inspect the existing operation; do not repeat or infer its effect."""


class _Permit:
    __slots__ = ()

    def __repr__(self):
        return '<process-owned retirement permit>'


class RetirementGuard:
    """One owner's diagnostic/prepare/commit/cancel exchange, with no retry loop.

    `request(method, path, body)` returns `(HTTP status, decoded JSON)` and must
    use a connection pinned to the verified native process incarnation and a
    supported authenticated provider session. It must not reconnect between
    identity verification and a mutation. It must disable redirects and retries, bound
    response sizes/time, and preserve TLS verification where TLS is used.
    `observe_owner()` must positively read the kernel identity of the selected
    manager-owned listener, not infer it from a file or HTTP response.

    The 25-second local bound is deliberately shorter than the pinned SDK's
    30-second prepare lifetime. A diagnostic True never grants permission to
    stop: only a confirmed prepare and commit reach `committed`.
    """

    def __init__(self, owner, request, observe_owner, record, *, now=time.monotonic):
        if type(owner) is not Owner or not all(callable(x) for x in (request, observe_owner, record, now)):
            raise ValueError('An exact owner and explicit transaction adapters are required')
        self.owner = owner
        self._request = request
        self._observe = observe_owner
        self._record = record
        self._now = now
        self.state = 'new'
        self.idle = None
        self._permit = None
        self._token = None
        self._deadline = None

    def _forget(self):
        self._permit = None
        self._token = None
        self._deadline = None

    def _owner(self):
        try:
            observed = self._observe()
        except BaseException as exc:
            if not isinstance(exc, Exception):
                self.state = 'unknown'
                self._forget()
                raise
            observed = None
        if type(observed) is not Owner or observed != self.owner:
            self.state = 'unknown'
            self._forget()
            raise Unknown('Native owner identity is unproven; inspect the existing operation')

    def _clock(self):
        try:
            value = self._now()
        except BaseException as exc:
            if not isinstance(exc, Exception):
                self.state = 'unknown'
                self._forget()
                raise
            value = None
        if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
            self.state = 'unknown'
            self._forget()
            raise Unknown('The local monotonic deadline is unproven')
        return value

    def _event(self, step, phase, **fields):
        try:
            self._record(dict(step=step, phase=phase, pid=self.owner.pid,
                              start_ticks=self.owner.start_ticks, boot_id=self.owner.boot_id, **fields))
        except BaseException as exc:
            self.state = 'unknown'
            self._forget()
            if not isinstance(exc, Exception):
                raise
            raise Unknown('Transaction journal unavailable; inspect the existing operation') from None

    def _exchange(self, method, path, body, step, *, deadline=None):
        self._owner()
        self._event(step, 'intent')
        self._owner()
        if deadline is not None:
            now = self._clock()
            if now < deadline - 25.0 or now >= deadline:
                self.state = 'unknown'
                self._forget()
                raise Unknown('Prepare deadline elapsed before dispatch; inspect without replay')
        self.state = step + '_requested'
        try:
            status, data = self._request(method, path, body)
        except BaseException as exc:
            self.state = 'unknown'
            self._forget()
            if not isinstance(exc, Exception):
                raise
            raise Unknown('Request outcome unknown; inspect the existing operation without replay') from None
        self._owner()
        if type(status) is not int or type(data) is not dict or status != 200:
            self.state = 'unknown' if method == 'POST' else 'refused'
            self._forget()
            self._event(step, 'refused', http_status=status if type(status) is int else None)
            error = Unknown if method == 'POST' else Refused
            raise error('Endpoint refused or returned an invalid response; no automatic retry')
        self._event(step, 'response', http_status=status)
        return data

    def inspect(self):
        if self.state not in {'new', 'ready', 'held', 'cancelled', 'refused'}:
            raise Refused('This operation needs reconciliation before another inspection')
        identity = self._exchange('GET', '/api/host/identity', None, 'identity')
        if (identity.get('ok') is not True or type(identity.get('pid')) is not int
                or identity['pid'] != self.owner.pid or identity.get('role') != 'serve'
                or type(identity.get('protocolVersion')) is not int
                or identity['protocolVersion'] != 1):
            self.state = 'refused'
            raise Refused('The authenticated listener did not prove the selected standalone owner')
        snapshot = self._exchange('GET', '/api/health/idle', None, 'idle')
        if (snapshot.get('ok') is not True or 'idle' not in snapshot
                or (snapshot['idle'] is not None and type(snapshot['idle']) is not bool)):
            self.state = 'refused'
            raise Refused('A valid standalone idle diagnostic is required')
        self.idle = snapshot['idle']
        self.state = 'ready'
        return self.idle

    def prepare(self):
        if self.state != 'ready' or self.idle is not True:
            raise Refused('First verify this owner and a positive idle diagnostic')
        issued = self._clock()
        data = self._exchange('POST', '/api/health/retirement', {'action': 'prepare'}, 'prepare')
        if (data.get('ok') is False and 'idle' in data
                and (data['idle'] is None or data['idle'] is False) and 'token' not in data):
            self.state = 'held'
            self.idle = data['idle']
            return None
        token = data.get('token')
        if (data.get('ok') is not True or data.get('idle') is not True
                or type(token) is not str or not re.fullmatch(r'[A-Za-z0-9_-]{32,256}', token)):
            self.state = 'unknown'
            self._forget()
            raise Unknown('No valid process-owned prepare permit; inspect without replay')
        self._permit = _Permit()
        self._token = token
        self._deadline = issued + 25.0
        self.state = 'prepared'
        self._valid(self._permit)
        self._event('prepare', 'verified')
        return self._permit

    def _valid(self, permit):
        if self.state != 'prepared' or permit is None or permit is not self._permit:
            raise Refused('The exact unspent in-memory permit is required')
        now = self._clock()
        if now < self._deadline - 25.0 or now >= self._deadline:
            self.state = 'unknown'
            self._forget()
            raise Unknown('Prepare deadline is expired or unproven; inspect without replay')

    def _finish(self, action, permit):
        self._valid(permit)
        data = self._exchange('POST', '/api/health/retirement',
                              {'action': action, 'token': self._token}, action,
                              deadline=self._deadline)
        if data.get('ok') is not True:
            self.state = 'unknown'
            self._forget()
            raise Unknown('Permit action was not confirmed; inspect without replay')
        self.state = 'committed' if action == 'commit' else 'cancelled'
        self._forget()
        self._event(action, 'verified')

    def commit(self, permit):
        self._finish('commit', permit)

    def cancel(self, permit):
        self._finish('cancel', permit)
