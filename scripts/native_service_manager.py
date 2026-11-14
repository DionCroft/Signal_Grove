"""Pinned user-manager start/stop adapter, never an autonomous live cutover.

Use the reviewed hash-pinned Jeepney 0.9.0 side environment. One native user-bus
connection watches JobRemoved before any dispatch, addresses the manager's exact
unique name and correlates the job with fresh native process/cgroup outcomes.
Only the two bound Hermes roles (or explicitly named owned acceptance units) are
accepted. The caller/coordinator still owns real work/retirement/PM/backup gates,
reviewed file evidence and approval. No restart, kill, cancel, reload, reset,
configuration write, credential read, fallback, reconnect or action retry exists.
"""
from __future__ import annotations

from collections import deque
from dataclasses import dataclass, asdict
import hashlib
import importlib.metadata
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import socket
import stat
import struct
import sys
import threading
import time

from cutover_coordinator import ApprovedExecution, Plan, FORWARD, RECOVERY
from cutover_journal import CutoverJournal, HASH, Refused, Unknown
from native_retirement_owner import read_process_owner
from retirement_guard import Owner

MANAGER_PATH = '/org/freedesktop/systemd1'
MANAGER = 'org.freedesktop.systemd1.Manager'
UNIT = 'org.freedesktop.systemd1.Unit'
SERVICE = 'org.freedesktop.systemd1.Service'
DBUS = 'org.freedesktop.DBus'
JOB = re.compile(r'/org/freedesktop/systemd1/job/([1-9][0-9]{0,9})')
UNIQUE = re.compile(r':[0-9]{1,10}\.[0-9]{1,10}')
ACCEPTANCE = re.compile(r'signal-grove-acceptance-([a-f0-9]{32})-(gateway|serve)\.service')


def _hash(value):
    return type(value) is str and HASH.fullmatch(value) is not None


def _uint(value, bits=64):
    return type(value) is int and 0 <= value < 2**bits


def _digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def _path(value, *, empty=False):
    return (empty and value == '') or (type(value) is str and len(value) <= 2048
        and value.startswith('/') and '\0' not in value and str(PurePosixPath(value)) == value
        and '..' not in PurePosixPath(value).parts and not value.startswith('//'))


@dataclass(frozen=True)
class BusAuthority:
    uid: int
    bus_device: int
    bus_inode: int
    peer_owner: Owner
    manager_unique: str
    manager_owner: Owner

    def __post_init__(self):
        if (not _uint(self.uid, 32) or not self.uid or not _uint(self.bus_device)
                or not _uint(self.bus_inode) or not self.bus_inode
                or type(self.peer_owner) is not Owner or type(self.manager_owner) is not Owner
                or self.peer_owner.boot_id != self.manager_owner.boot_id
                or type(self.manager_unique) is not str or not UNIQUE.fullmatch(self.manager_unique)):
            raise ValueError('Exact native user-bus and manager identities are required')


@dataclass(frozen=True)
class UnitBinding:
    role: str
    unit: str
    old_configuration_sha256: str
    new_configuration_sha256: str
    old_executable: str
    new_executable: str

    def __post_init__(self):
        fixture = ACCEPTANCE.fullmatch(self.unit) if type(self.unit) is str else None
        if (self.role not in ('gateway', 'serve') or
                not (self.unit == 'hermes-'+self.role+'.service' or (fixture and fixture[2] == self.role))
                or not _hash(self.old_configuration_sha256) or not _hash(self.new_configuration_sha256)
                or not _path(self.old_executable) or not _path(self.new_executable)):
            raise ValueError('Only a reviewed Hermes role or exact owned acceptance unit is allowed')


def command_rows(rows):
    """Drop runtime counters only; retain complete loaded command/argv/flag identity."""
    if type(rows) not in (list, tuple) or len(rows) > 16:
        raise Unknown('Loaded command shape is unproven')
    result = []
    for row in rows:
        if (type(row) not in (list, tuple) or len(row) != 10 or not _path(row[0])
                or type(row[1]) not in (list, tuple) or not 1 <= len(row[1]) <= 128
                or any(type(a) is not str or len(a) > 4096 or '\0' in a for a in row[1])
                or type(row[2]) is not bool or not all(_uint(n) for n in row[3:7])
                or not _uint(row[7], 32) or any(type(n) is not int or not -2**31 <= n < 2**31 for n in row[8:])):
            raise Unknown('Loaded command types or bounds are unproven')
        result.append((row[0], tuple(row[1]), row[2]))
    return tuple(result)


def job_result(body, expected_path, unit):
    """None is another bounded observed job, never success of this action."""
    if (type(body) is not tuple or len(body) != 4 or not _uint(body[0], 32) or not body[0]
            or type(body[1]) is not str or not JOB.fullmatch(body[1]) or int(JOB.fullmatch(body[1])[1]) != body[0]
            or type(body[2]) is not str or type(body[3]) is not str or len(body[3]) > 64):
        raise Unknown('Job completion body is unproven')
    if body[1] != expected_path:
        return None
    if body[2] != unit or body[3] != 'done':
        raise Unknown('The exact service job did not complete successfully')
    return 'done'


def invocation_id(value):
    if type(value) is bytes:
        raw=value
    elif type(value) in (tuple,list) and len(value)<=16 and all(_uint(n,8) for n in value):
        raw=bytes(value)
    else:
        raise Unknown('Native invocation type refused')
    if len(raw) not in (0,16): raise Unknown('Native invocation length refused')
    return raw.hex()


@dataclass(frozen=True)
class UnitSnapshot:
    unit: str
    load_state: str
    active_state: str
    sub_state: str
    main_pid: int
    control_pid: int
    invocation: str
    cgroup: str
    job_id: int
    job_path: str
    result: str
    configuration_sha256: str
    owner: Owner | None
    executable: str
    stop_helpers_ok: bool

    def __post_init__(self):
        if (type(self.unit) is not str or not re.fullmatch(r'[A-Za-z0-9_.-]{1,128}\.service',self.unit)
                or self.load_state not in ('loaded','not-found','error','bad-setting','masked','merged','stub')
                or self.active_state not in ('active','inactive','activating','deactivating','failed','reloading','maintenance','refreshing')
                or type(self.sub_state) is not str or not re.fullmatch(r'[a-z][a-z-]{0,63}',self.sub_state)
                or not _uint(self.main_pid,32) or self.main_pid==1 or not _uint(self.control_pid,32)
                or type(self.invocation) is not str or not re.fullmatch(r'(?:[a-f0-9]{32})?',self.invocation)
                or not _path(self.cgroup,empty=True) or not _uint(self.job_id,32)
                or type(self.job_path) is not str or (self.job_path!='/' if not self.job_id else
                    not JOB.fullmatch(self.job_path) or int(JOB.fullmatch(self.job_path)[1])!=self.job_id)
                or type(self.result) is not str or not re.fullmatch(r'[a-z][a-z-]{0,63}',self.result)
                or not _hash(self.configuration_sha256) or type(self.stop_helpers_ok) is not bool
                or (self.main_pid and (type(self.owner) is not Owner or self.owner.pid!=self.main_pid
                                      or not _path(self.executable) or not self.cgroup or not self.invocation))
                or (not self.main_pid and (self.owner is not None or self.executable!=''))):
            raise ValueError('A closed native unit snapshot is required')

    @property
    def sha256(self):
        return _digest(asdict(self))


def validate_preimage(binding,action,scope,current,expected):
    if (type(binding) is not UnitBinding or type(current) is not UnitSnapshot or type(expected) is not UnitSnapshot
            or action not in ('start','stop') or scope not in ('forward','recovery')
            or current!=expected or current.unit!=binding.unit or current.job_id or current.control_pid or current.load_state!='loaded'):
        raise Refused('Native unit preimage or idle job state changed')
    if current.configuration_sha256 not in (binding.old_configuration_sha256,binding.new_configuration_sha256):
        raise Refused('Loaded unit commands are outside the reviewed packet')
    if action=='stop':
        allowed=set()
        if current.configuration_sha256==binding.old_configuration_sha256: allowed.add(binding.old_executable)
        if current.configuration_sha256==binding.new_configuration_sha256: allowed.add(binding.new_executable)
        if scope=='forward': allowed={binding.old_executable}
        if ((scope=='forward' and current.configuration_sha256!=binding.old_configuration_sha256)
                or current.active_state!='active' or current.sub_state!='running' or current.owner is None
                or current.executable not in allowed):
            raise Refused('Exact active native stop owner is required')
    else:
        required=binding.old_configuration_sha256 if scope=='recovery' else binding.new_configuration_sha256
        if current.configuration_sha256!=required or current.active_state!='inactive' or current.main_pid or current.owner is not None:
            raise Refused('Reviewed inactive native start preimage is required')


class _LimitedSocket:
    def __init__(self, sock):
        self.sock = sock; self.received = 0; self.sent = 0
    def fileno(self): return self.sock.fileno()
    def recv(self, size):
        data = self.sock.recv(min(size, 4096)); self.received += len(data)
        if self.received > 8*1024*1024: raise Unknown('User-bus byte bound exceeded')
        return data
    def sendall(self, data):
        self.sent += len(data)
        if len(data)>16*1024 or self.sent > 4*1024*1024: raise Unknown('User-bus request byte bound exceeded')
        return self.sock.sendall(data)
    def close(self): self.sock.close()


class _Deadline:
    """Interrupt only this owned socket, including a blocked Hello/send/receive."""
    def __init__(self, sock, deadline):
        self.sock = sock; self.expired = False; self.active = True; self.lock = threading.Lock()
        delay = deadline-time.monotonic()
        if not math.isfinite(delay) or delay <= 0: raise Unknown('User-bus deadline elapsed')
        self.timer = threading.Timer(delay, self._expire); self.timer.daemon = True
    def _expire(self):
        with self.lock:
            if not self.active: return
            self.expired = True
            try: self.sock.shutdown(socket.SHUT_RDWR)
            except OSError: pass
    def __enter__(self): self.timer.start(); return self
    def __exit__(self, *_):
        with self.lock: self.active = False
        self.timer.cancel()
        if self.expired: raise Unknown('User-bus outcome unknown after deadline')


class _Signals:
    def __init__(self): self.queue = deque()
    def append(self, value):
        if len(self.queue) >= 128: raise Unknown('Job signal bound exceeded; no dropped result may grant success')
        self.queue.append(value)
    def __len__(self): return len(self.queue)
    def popleft(self): return self.queue.popleft()
    def clear(self): self.queue.clear()


class NativeServiceManager:
    """One pinned connection; correlated job completion AND native outcomes.

    `change` requires the real journal's exact current approved stage prefix.
    Loaded commands must match the role's reviewed old/new fingerprints. The
    coordinator/native caller must additionally verify work/admission gates and
    source-file inventory. This class never claims a job ACK proves an outcome.
    """
    def __init__(self, authority, plan, journal, bindings):
        if (sys.platform != 'linux' or type(authority) is not BusAuthority or type(plan) is not Plan
                or type(journal) is not CutoverJournal or type(bindings) is not tuple or len(bindings) != 2
                or any(type(b) is not UnitBinding for b in bindings) or {b.role for b in bindings} != {'gateway','serve'}):
            raise ValueError('Native authority, exclusive journal and exact two-role bindings required')
        groups = [ACCEPTANCE.fullmatch(b.unit) for b in bindings]
        if any(groups) and (not all(groups) or groups[0][1] != groups[1][1]):
            raise ValueError('Owned acceptance roles must belong to the same exact fixture')
        self.authority, self.plan, self.journal = authority, plan, journal
        self.bindings = {b.role:b for b in bindings}; self.state = 'connecting'
        self._busy = threading.Lock(); self._spent = set(); self._pinned = set(); self._filters = []
        self._raw = None; self._conn = None; self._signals = _Signals()
        self._bus = Path('/run/user',str(authority.uid),'bus'); self._stamp = None
        try:
            self._native()
            if importlib.metadata.version('jeepney') != '0.9.0': raise Refused('Reviewed Jeepney runtime required')
            from jeepney import DBusAddress, new_method_call, MatchRule, MessageType, HeaderFields
            from jeepney.bus_messages import message_bus
            from jeepney.io.blocking import DBusConnection, prep_socket
            self._Address, self._method, self._Types, self._Fields = DBusAddress, new_method_call, MessageType, HeaderFields
            self._bus_messages = message_bus
            self._raw = prep_socket(str(self._bus), enable_fds=False, timeout=1.0)
            self._native(); self._io = _LimitedSocket(self._raw)
            with _Deadline(self._raw,time.monotonic()+2): self._conn = DBusConnection(self._io,enable_fds=False)
            self.state = 'connected'
            if self._rpc(message_bus.GetConnectionUnixProcessID(authority.manager_unique),'u',DBUS)[0] != authority.manager_owner.pid:
                raise Refused('Pinned manager connection does not name the native owner')
            for binding in bindings:
                rule = MatchRule(type='signal',sender=authority.manager_unique,interface=MANAGER,member='JobRemoved',path=MANAGER_PATH)
                rule.add_arg_condition(2,binding.unit)
                handle = self._conn.filter(rule,queue=self._signals); handle.__enter__(); self._filters.append(handle)
                self._rpc(message_bus.AddMatch(rule),'',DBUS)
            self._manager('Subscribe','',(),'')
            self._native()
        except BaseException as exc:
            self.close(); self.state = 'unknown'
            if not isinstance(exc,Exception): raise
            raise Refused('Native user-manager connection could not be established safely') from None

    def _native(self):
        a=self.authority
        if os.getuid()!=a.uid or os.geteuid()!=a.uid or self._bus.resolve()!=self._bus:
            raise Unknown('Native user-bus context changed')
        parent=self._bus.parent.lstat(); info=self._bus.lstat()
        if (not stat.S_ISDIR(parent.st_mode) or parent.st_uid!=a.uid or stat.S_IMODE(parent.st_mode)!=0o700
                or not stat.S_ISSOCK(info.st_mode) or info.st_uid!=a.uid or info.st_nlink!=1
                or (info.st_dev,info.st_ino)!=(a.bus_device,a.bus_inode)):
            raise Unknown('Native user-bus inode or ownership changed')
        stamp=(info.st_dev,info.st_ino,info.st_ctime_ns)
        if self._stamp is not None and self._stamp!=stamp: raise Unknown('Native user-bus identity drifted')
        self._stamp=stamp
        for owner in (a.peer_owner,a.manager_owner):
            if read_process_owner(owner.pid)!=owner or Path('/proc',str(owner.pid)).stat().st_uid!=a.uid:
                raise Unknown('Native bus/manager process incarnation changed')
        if self._raw is not None:
            pid,uid,_=struct.unpack('3i',self._raw.getsockopt(socket.SOL_SOCKET,socket.SO_PEERCRED,12))
            if pid!=a.peer_owner.pid or uid!=a.uid: raise Unknown('Connected user-bus peer changed')

    def _remaining(self,deadline):
        now=time.monotonic()
        if type(deadline) not in (int,float) or not math.isfinite(deadline) or deadline<=now:
            raise Unknown('Native service deadline elapsed')
        return deadline-now

    def _rpc(self,message,signature,sender=None,deadline=None):
        if self.state in ('closed','unknown'): raise Unknown('Pinned service connection is unavailable')
        deadline=min(time.monotonic()+3,deadline) if deadline is not None else time.monotonic()+3
        self._native()
        with _Deadline(self._raw,deadline): reply=self._conn.send_and_get_reply(message,timeout=self._remaining(deadline))
        self._native(); self._remaining(deadline)
        if (reply.header.message_type is not self._Types.method_return
                or reply.header.fields.get(self._Fields.sender)!=(sender or self.authority.manager_unique)
                or reply.header.fields.get(self._Fields.signature,'')!=signature or type(reply.body) is not tuple):
            raise Unknown('Native service reply refused; no automatic retry')
        return reply.body

    def _manager(self,method,signature,body,result_signature,deadline=None):
        address=self._Address(MANAGER_PATH,bus_name=self.authority.manager_unique,interface=MANAGER)
        return self._rpc(self._method(address,method,signature,body),result_signature,deadline=deadline)

    def _get(self,path,interface,name,signature,deadline):
        address=self._Address(path,bus_name=self.authority.manager_unique,interface='org.freedesktop.DBus.Properties')
        body=self._rpc(self._method(address,'Get','ss',(interface,name)),'v',deadline=deadline)
        if len(body)!=1 or type(body[0]) is not tuple or len(body[0])!=2 or body[0][0]!=signature:
            raise Unknown('Native unit property type is unproven')
        return body[0][1]

    def _snapshot(self,role):
        binding=self.bindings[role]; deadline=time.monotonic()+5
        result=self._manager('GetUnit','s',(binding.unit,),'o',deadline)
        if len(result)!=1 or not _path(result[0]): raise Unknown('Native unit object path is unproven')
        path=result[0]
        def get(name,signature,interface=UNIT): return self._get(path,interface,name,signature,deadline)
        pid=get('MainPID','u',SERVICE); invocation=get('InvocationID','ay'); job=get('Job','(uo)')
        if not _uint(pid,32) or type(job) is not tuple or len(job)!=2 or not _uint(job[0],32) or not _path(job[1]):
            raise Unknown('Native unit process or job shape is unproven')
        invocation=invocation_id(invocation)
        properties={name:get(name,'s') for name in ('Id','LoadState','ActiveState','SubState','FragmentPath')}
        drops=get('DropInPaths','as'); control=get('ControlPID','u',SERVICE); group=get('ControlGroup','s',SERVICE)
        result=get('Result','s',SERVICE); kill=get('KillMode','s',SERVICE); service_type=get('Type','s',SERVICE)
        commands={name:get(name,'a(sasbttttuii)',SERVICE) for name in ('ExecStart','ExecStop','ExecStopPost')}
        if (properties['Id']!=binding.unit or any(type(v) is not str or len(v)>2048 for v in properties.values())
                or not _path(properties['FragmentPath'],empty=True) or type(drops) not in (list,tuple)
                or len(drops)>32 or any(not _path(p) for p in drops) or not _uint(control,32)
                or not _path(group,empty=True) or type(result) is not str or len(result)>64
                or type(kill) is not str or type(service_type) is not str):
            raise Unknown('Native unit property bounds refused')
        normalized={name:command_rows(rows) for name,rows in commands.items()}
        configuration=_digest(dict(unit=binding.unit,fragment=properties['FragmentPath'],dropins=list(drops),
                                   kill_mode=kill,service_type=service_type,commands=normalized))
        owner=read_process_owner(pid) if pid else None; executable=''
        if pid:
            if owner is None or Path('/proc',str(pid)).stat().st_uid!=self.authority.uid:
                raise Unknown('Native service main process is unproven')
            executable=os.readlink('/proc/'+str(pid)+'/exe')
            if not _path(executable): raise Unknown('Native executable is unproven')
            cg=Path('/proc',str(pid),'cgroup').read_text()
            if len(cg)>8192 or not group or '0::'+group not in cg.splitlines():
                raise Unknown('Native service cgroup does not own the manager PID')
        if group and PurePosixPath(group).name!=binding.unit: raise Unknown('Service cgroup scope refused')
        last_stop=all(row[6]>0 and row[8]==1 and row[9]==0 for name in ('ExecStop','ExecStopPost') for row in commands[name])
        if (get('MainPID','u',SERVICE)!=pid or invocation_id(get('InvocationID','ay'))!=invocation
                or get('Job','(uo)')!=job or (pid and read_process_owner(pid)!=owner)):
            raise Unknown('Native service changed during its snapshot')
        self._native(); self._remaining(deadline)
        return UnitSnapshot(binding.unit,properties['LoadState'],properties['ActiveState'],properties['SubState'],
            pid,control,invocation,group,job[0],job[1],result,configuration,owner,executable,last_stop)

    def snapshot(self,role):
        if role not in self.bindings or not self._busy.acquire(False): raise Refused('One bound native role observation at a time')
        try: return self._snapshot(role)
        except BaseException as exc:
            self.close(); self.state='unknown'
            if not isinstance(exc,Exception): raise
            raise Unknown('Native service observation is unproven') from None
        finally: self._busy.release()

    def _stage(self,role,action,execution):
        if type(execution) is not ApprovedExecution or execution.authority_sha256!=self.plan.authority_sha256:
            raise Refused('Exact native execution approval required')
        step=('rollback_' if execution.scope=='recovery' else '')+action+'_'+role
        stages=RECOVERY if execution.scope=='recovery' else FORWARD
        self.journal.assert_current(); view=self.journal.view
        if (view['state']!='pending' or view['pending']!=step or view['operation']!=execution.operation
                or view['plan_sha256']!=execution.packet_sha256 or view['authorization_sha256']!=execution.binding_sha256
                or view['root_plan_sha256']!=self.plan.packet_sha256 or view['mode']!=execution.scope
                or (execution.scope=='forward' and execution.packet_sha256!=self.plan.packet_sha256)):
            raise Refused('Exact native service stage is not durably authorized')
        index=stages.index(step); rows=[r for r in view['records'] if r['operation']==execution.operation]
        if ([r['step'] for r in rows if r['kind']=='intent']!=list(stages[:index+1])
                or [r['step'] for r in rows if r['kind']=='verified']!=list(stages[:index])):
            raise Refused('Native service stage prefix has not been verified')
        self._remaining(execution.deadline)
        return step

    def change(self,role,action,expected,execution):
        if role not in self.bindings or action not in ('start','stop') or type(expected) is not UnitSnapshot:
            raise Refused('One exact bound start/stop request is required')
        if not self._busy.acquire(False): raise Refused('Another native service operation is active')
        dispatched=False
        try:
            step=self._stage(role,action,execution); key=(execution.operation,step)
            if key in self._spent: raise Refused('This native service action has already been spent')
            binding=self.bindings[role]; current=self._snapshot(role)
            validate_preimage(binding,action,execution.scope,current,expected)
            self._signals.clear(); self._spent.add(key); dispatched=True
            if binding.unit not in self._pinned:
                self._manager('RefUnit','s',(binding.unit,),'',execution.deadline); self._pinned.add(binding.unit)
            if self._snapshot(role)!=expected: raise Unknown('Native service changed before dispatch')
            self._stage(role,action,execution); self._native()
            deadline=min(execution.deadline,time.monotonic()+30)
            body=self._manager('StartUnit' if action=='start' else 'StopUnit','ss',(binding.unit,'fail'),'o',deadline)
            if len(body)!=1 or type(body[0]) is not str or not JOB.fullmatch(body[0]): raise Unknown('Native job acceptance is unproven')
            job_path=body[0]; completed=False
            while not completed:
                self._native(); self._remaining(deadline)
                while len(self._signals):
                    message=self._signals.popleft()
                    if message.header.fields.get(self._Fields.signature)!='uoss': raise Unknown('Job signal signature refused')
                    completed=job_result(message.body,job_path,binding.unit)=='done'
                    if completed: break
                if not completed:
                    with _Deadline(self._raw,deadline): self._conn.recv_messages(timeout=self._remaining(deadline))
            after=self._snapshot(role); self._stage(role,action,execution)
            if after.job_id or after.control_pid or after.result!='success': raise Unknown('Native service job outcome is not ready')
            if action=='stop':
                group=Path('/sys/fs/cgroup')/expected.cgroup.lstrip('/')
                if (after.active_state!='inactive' or after.main_pid or after.owner is not None or not after.stop_helpers_ok
                        or after.configuration_sha256!=expected.configuration_sha256
                        or read_process_owner(expected.main_pid)==expected.owner
                        or (group.exists() and (group/'cgroup.procs').read_text().strip())):
                    raise Unknown('Native stop/cleanup/cgroup outcome is unproven')
            else:
                config=binding.old_configuration_sha256 if execution.scope=='recovery' else binding.new_configuration_sha256
                exe=binding.old_executable if execution.scope=='recovery' else binding.new_executable
                if (after.active_state!='active' or after.sub_state!='running' or after.owner is None
                        or not after.invocation or after.invocation==expected.invocation
                        or after.configuration_sha256!=config or after.executable!=exe):
                    raise Unknown('Native start process/configuration outcome is unproven')
            return dict(state='native_service_effect_verified',step=step,unit=binding.unit,job_path=job_path,
                        before_sha256=expected.sha256,after_sha256=after.sha256,owner=asdict(after.owner) if after.owner else None)
        except BaseException as exc:
            if dispatched:
                self.close(); self.state='unknown'
                if not isinstance(exc,Exception): raise
                raise Unknown('Native service outcome unknown; inspect without replay or cancellation') from None
            if not isinstance(exc,Exception) or isinstance(exc,Refused): raise
            self.close(); self.state='unknown'; raise Unknown('Native service preflight is unproven') from None
        finally: self._busy.release()

    def close(self):
        self.state='closed'
        if self._conn is not None: self._conn.close()
        elif self._raw is not None: self._raw.close()
        self._conn=None; self._raw=None

    def __enter__(self): return self
    def __exit__(self,*_): self.close()
