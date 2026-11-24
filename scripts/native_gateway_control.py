"""One bounded, read-only SDK control request on a supplied native Unix socket.

The caller selects a manager-owned gateway incarnation and connects explicitly.
Linux SO_PEERCRED and procfs bind this peer to that exact PID/start/boot/UID.
SDK v1 closes after each response: a later status sample needs a separately
supplied connection, never an internal reconnect/retry. No discovery, fallback,
credentials, mutation verb, process action or fleet-idle assertion is provided.
"""
import json
import math
import os
import secrets
import socket
import struct
import sys
import time
from datetime import datetime
import re

from retirement_guard import Owner
from native_retirement_owner import read_process_owner
from cutover_journal import Refused,Unknown

MAX_RESPONSE=512*1024
READ_VERBS=frozenset({'identify','status'})
LIVE_COUNTERS=frozenset({'owned_async_tasks','cron','delegations','background_processes','process_completions','pending_watchers',
    'executor_jobs','background_threads','periodic_jobs','deferred_cleanup_tasks','runner_background_tasks',
    'runner_turns','queued_runner_messages','adapter_sessions','queued_adapter_messages','adapter_tasks','api_work'})
LEDGER_COUNTERS=frozenset({'active_turns','active_tools','processes_awaiting_reconciliation','unverified_operations'})


class RegisteredStatusUnknown(Unknown):
    """An authenticated frame whose registered work could not be verified."""

    def __init__(self, reason):
        super().__init__('Registered gateway work observation is unproven; no idle permission')
        self.reason = reason


def project_registered_status(body, owner_pid, home_digest, earliest, latest):
    """Verify registered work for one exact gateway and home."""

    def exact_pid(value):
        if type(value) is not int or value != owner_pid:
            raise ValueError('Wrong responding process')

    def fresh(value):
        if type(value) is str:
            parsed = datetime.fromisoformat(value)
            if parsed.tzinfo is None:
                raise ValueError('Unscoped timestamp')
            value = parsed.timestamp()
        if type(value) not in (int, float) or not math.isfinite(value) or not earliest <= value <= latest:
            raise ValueError('Stale observation')

    def counts(value, names):
        if type(value) is not dict or set(value) != names or any(type(n) is not int or n < 0 for n in value.values()):
            raise ValueError('Incomplete work counts')
        return dict(value)

    reason = 'observer_scope'
    try:
        if (type(owner_pid) is not int or owner_pid <= 1 or type(home_digest) is not str
                or not re.fullmatch('[a-f0-9]{64}', home_digest)
                or any(type(t) not in (int, float) or not math.isfinite(t) for t in (earliest, latest))
                or not 0 <= earliest <= latest or latest - earliest > 5):
            raise ValueError('Invalid observation scope')

        reason = 'reply_identity'
        if type(body) is not dict or type(body['protocol']) is not int or body['protocol'] != 1:
            raise ValueError('Wrong status protocol')
        exact_pid(body['answering_pid'])
        reason = 'reply_freshness'
        fresh(body['answered_at'])

        reason = 'activity_contract'
        live = body['live_activity']
        if (live['contract'] != 'gateway_live_work_v1'
                or live['coverage'] != 'REGISTERED_WORK_ONLY' or live['fleet_ready_to_switch'] is not False):
            raise ValueError('Incomplete registered activity scope')
        reason = 'activity_unobserved'
        if live['state'] != 'OBSERVED':
            raise ValueError('Registered activity is unavailable')
        reason = 'activity_sample'
        exact_pid(live['responding_pid'])
        fresh(live['observed_at'])
        reason = 'activity_counts'
        live_counts = counts(live['counters'], LIVE_COUNTERS)
        if (type(live['busy']) is not bool or live['busy'] != any(live_counts.values())
                or live['lifecycle'] not in ('running', 'draining')
                or any(type(live[k]) is not int or live[k] < 0
                       for k in ('adapter_instances_observed', 'secondary_profile_maps_observed'))):
            raise ValueError('Invalid activity counters or lifecycle')

        reason = 'admission_contract'
        ledger = body['project_admission']
        if ledger['contract'] != 'native_admission_snapshot_v1' or ledger['fleet_ready_to_switch'] is not False:
            raise ValueError('Incomplete registered admission scope')
        reason = 'admission_unobserved'
        if ledger['state'] != 'OBSERVED':
            raise ValueError('Registered admission is unavailable')
        reason = 'admission_sample'
        exact_pid(ledger['responding_pid'])
        fresh(ledger['observed_at'])
        if ledger['home_digest'] != home_digest:
            raise ValueError('Wrong admission home')
        reason = 'admission_fence'
        fence = ledger['fence']
        if type(fence) is not dict or set(fence) != LEDGER_COUNTERS | {'epoch', 'owner', 'frozen'}:
            raise ValueError('Incomplete ledger scope')
        ledger_counts = counts({k: fence[k] for k in LEDGER_COUNTERS}, LEDGER_COUNTERS)
        if (type(fence['frozen']) is not bool or type(fence['epoch']) is not int or fence['epoch'] < 0
                or (fence['epoch'] == 0 and (fence['owner'] is not None or fence['frozen']))
                or (fence['epoch'] > 0 and (type(fence['owner']) is not str
                    or not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}', fence['owner'])))):
            raise ValueError('Invalid native admission fence')

        return dict(
            coverage='REGISTERED_WORK_ONLY', home_digest=home_digest, pid=owner_pid,
            lifecycle=live['lifecycle'], busy=live['busy'] or any(ledger_counts.values()),
            live_counters=live_counts, ledger_counters=ledger_counts, admission_frozen=fence['frozen'],
            fence_epoch=fence['epoch'], fence_owner=fence['owner'],
            adapter_instances=live['adapter_instances_observed'],
            secondary_profile_maps=live['secondary_profile_maps_observed'], fleet_ready_to_switch=False,
        )
    except (KeyError, ValueError, TypeError, OverflowError, AttributeError):
        raise RegisteredStatusUnknown(reason) from None


def _object(pairs):
    result={}
    for key,value in pairs:
        if key in result:raise ValueError('Duplicate field')
        result[key]=value
    return result


def decode_control_reply(raw,request_id):
    """Strict SDK envelope only; result meaning/coverage are separate checks."""
    try:
        if (type(raw) is not bytes or not 0<len(raw)<=MAX_RESPONSE or not raw.endswith(b'\n')
            or b'\n' in raw[:-1] or type(request_id) is not str or len(request_id)!=32
            or any(c not in '0123456789abcdef' for c in request_id)):
            raise ValueError('Invalid bounded frame')
        reply=json.loads(raw.decode('utf-8'),object_pairs_hook=_object,
            parse_constant=lambda value:(_ for _ in ()).throw(ValueError('Non-finite JSON')))
        pending=[(reply,0)]
        while pending:
            value,depth=pending.pop()
            if depth>64 or (type(value) is float and not math.isfinite(value)):raise ValueError('Unbounded JSON value')
            if type(value) is dict:pending.extend((v,depth+1) for v in value.values())
            elif type(value) is list:pending.extend((v,depth+1) for v in value)
        if (type(reply) is not dict or set(reply)!={'id','ok','protocol','result'}
            or reply['id']!=request_id or reply['ok'] is not True or type(reply['protocol']) is not int
            or reply['protocol']!=1 or type(reply['result']) is not dict):
            raise ValueError('Unaccepted SDK control envelope')
        return reply['result']
    except (ValueError,TypeError,UnicodeError,RecursionError):
        raise Unknown('SDK control reply is unproven; inspect without fallback or replay') from None


class NativeGatewayControl:
    def __init__(self,expected,connected_socket,*,timeout=2.0):
        if (sys.platform!='linux' or os.geteuid()==0 or type(expected) is not Owner
            or type(connected_socket) is not socket.socket or connected_socket.family!=socket.AF_UNIX
            or connected_socket.type!=socket.SOCK_STREAM or type(timeout) not in (int,float)
            or not math.isfinite(timeout) or not 0<timeout<=2):
            raise Refused('An exact native Linux owner and connected Unix stream are required')
        self._owner=expected;self._socket=connected_socket;self._uid=os.geteuid();self._timeout=float(timeout)
        self._used=False;self.state='ready';self.events=[]
        try:self._peer()
        except BaseException:self.close();raise

    def __repr__(self):return '<one-use native read-only gateway control>'

    def registered_status(self,home_digest):
        started = time.time()
        body = self.exchange('status')
        finished = time.time()
        try:
            return project_registered_status(body, self._owner.pid, home_digest, started, finished)
        except RegisteredStatusUnknown:
            self.state = 'unknown'
            raise

    def _peer(self):
        try:
            pid,uid,_=struct.unpack('3i',self._socket.getsockopt(socket.SOL_SOCKET,socket.SO_PEERCRED,12))
            if uid!=self._uid or pid!=self._owner.pid or read_process_owner(pid)!=self._owner:
                raise Refused('Exact native gateway peer is unproven')
            self._socket.getpeername()
        except OSError:raise Refused('Exact connected native gateway peer is unproven') from None

    def close(self):
        try:self._socket.shutdown(socket.SHUT_RDWR)
        except OSError:pass
        self._socket.close()

    def exchange(self,verb):
        if self._used:raise Refused('This gateway control connection is spent')
        self._used=True;sent=False
        try:
            if type(verb) is not str or verb not in READ_VERBS:raise Refused('Only identify and status are permitted')
            deadline=time.monotonic()+self._timeout;self._peer();request_id=secrets.token_hex(16)
            request=json.dumps(dict(verb=verb,id=request_id,protocol=1),separators=(',',':')).encode()+b'\n'
            self.events.append(dict(phase='intent',verb=verb,pid=self._owner.pid,start_ticks=self._owner.start_ticks,boot_id=self._owner.boot_id))
            remaining=deadline-time.monotonic()
            if remaining<=0:raise TimeoutError('Control deadline elapsed')
            self.state='request_pending';self._socket.settimeout(remaining);sent=True;self._socket.sendall(request)
            raw=bytearray()
            while True:
                remaining=deadline-time.monotonic()
                if not math.isfinite(remaining) or remaining<=0:raise TimeoutError('Control deadline elapsed')
                self._socket.settimeout(remaining);block=self._socket.recv(min(65536,MAX_RESPONSE+1-len(raw)))
                if not block:break
                raw.extend(block)
                if len(raw)>MAX_RESPONSE:raise ValueError('Oversized control response')
            self._peer();result=decode_control_reply(bytes(raw),request_id)
            if time.monotonic()>=deadline:raise TimeoutError('Control observation deadline elapsed')
            self.state='observed';self.events.append(dict(phase='response',verb=verb,bytes=len(raw)))
            return result
        except BaseException as error:
            self.state='unknown' if sent else 'refused'
            if not isinstance(error,Exception):raise
            if not sent and isinstance(error,Refused):raise
            raise Unknown('Gateway control outcome unproven; inspect without fallback or replay') from None
        finally:self.close()
