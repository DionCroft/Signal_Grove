"""Explicit operator enrollment for the existing foreground Windows read broker.

Trusted callbacks supply current policy/registry bytes and the owner's existing
project validator. Phone JSON cannot enroll a scope, select roots or assert an
interactive identity. Importing starts no listener or job.
"""
from __future__ import annotations
import ctypes
from ctypes import wintypes as w
from dataclasses import dataclass,field
import hashlib
import json
import os
import re
import socket
import threading
from .broker_protocol import BrokerReadAdapter,CheckedReadContext,_unique
from .local_pipe import _Windows
from .workspace_provider import RegisteredProject,WorkspaceError,WorkspaceProvider,_root

_HEX=re.compile(r'[a-f0-9]{64}')
_PROJECT=re.compile(r'[A-Za-z0-9][A-Za-z0-9_-]{0,79}')
_SID=re.compile(r'S-1-(?:[0-9]+-){1,15}[0-9]+')


def scope_for(authority,subject,profile_id):
    """Use verified native actor/profile identity, never caller-supplied claims.

    Authority is a stable operator-enrolled identifier, not a bearer credential.
    The hash is domain separated and length delimited by canonical JSON.
    """
    values=(authority,subject,profile_id)
    for value in values:
        if (not isinstance(value,str) or not value or len(value)>4096
            or any(ord(c)<32 or ord(c)==127 or 0xd800<=ord(c)<=0xdfff for c in value)):raise WorkspaceError('INVALID')
    raw=json.dumps(['signal-grove-worker-read-v1',*values],ensure_ascii=True,separators=(',',':')).encode()
    return hashlib.sha256(raw).hexdigest()


@dataclass(frozen=True)
class OwnerObservation:
    device:str
    owner_sid:str=field(repr=False)
    pid:int
    started:int
    session:int
    active:bool


def observe_owner():
    """Fresh actual process token and WTS state; service/disconnected sessions fail."""
    native=_Windows();sid,started,session=native.process_identity(os.getpid())
    active=False
    if session>0:
        lib=ctypes.WinDLL('wtsapi32',use_last_error=True)
        lib.WTSQuerySessionInformationW.argtypes=[w.HANDLE,w.DWORD,ctypes.c_int,ctypes.POINTER(ctypes.c_void_p),ctypes.POINTER(w.DWORD)]
        lib.WTSQuerySessionInformationW.restype=w.BOOL;lib.WTSFreeMemory.argtypes=[ctypes.c_void_p];lib.WTSFreeMemory.restype=None
        buffer=ctypes.c_void_p();length=w.DWORD()
        native.check(lib.WTSQuerySessionInformationW(None,session,8,ctypes.byref(buffer),ctypes.byref(length)))
        try:active=length.value==ctypes.sizeof(w.DWORD) and ctypes.cast(buffer,ctypes.POINTER(w.DWORD)).contents.value==0
        finally:lib.WTSFreeMemory(buffer)
    return OwnerObservation(socket.gethostname().upper(),sid,os.getpid(),started,session,active)


@dataclass(frozen=True)
class RegistrySnapshot:
    """Revision must hash the exact fresh registry bytes used to parse rows.

    The trusted reader must hold the configured source stable while reading;
    a cached routing projection is never a read authorization source.
    """
    revision:str=field(repr=False)
    rows:tuple=field(repr=False)


@dataclass(frozen=True)
class OperatorPolicy:
    revision:str=field(repr=False)
    device:str
    owner_sid:str=field(repr=False)
    grants:tuple=field(repr=False)

    def projects(self,scope):
        return next((ids for enrolled,ids in self.grants if enrolled==scope),frozenset())


def parse_policy(raw):
    if type(raw) is not bytes or not 1<=len(raw)<=32768:raise WorkspaceError('INVALID')
    try:
        value=json.loads(raw.decode('utf8'),object_pairs_hook=_unique,parse_constant=lambda _:(_ for _ in ()).throw(ValueError()))
        if not isinstance(value,dict) or set(value)!={'schema','device','owner_sid','grants'} or type(value['schema']) is not int or value['schema']!=1:raise ValueError()
        device,sid,grants=value['device'],value['owner_sid'],value['grants']
        if not isinstance(device,str) or not re.fullmatch(r'[A-Z0-9][A-Z0-9_-]{0,62}',device):raise ValueError()
        if not isinstance(sid,str) or len(sid)>184 or not _SID.fullmatch(sid):raise ValueError()
        if not isinstance(grants,list) or not 1<=len(grants)<=8:raise ValueError()
        clean=[];seen=set()
        for row in grants:
            if not isinstance(row,dict) or set(row)!={'scope_id','project_ids'}:raise ValueError()
            scope,ids=row['scope_id'],row['project_ids']
            if not isinstance(scope,str) or not _HEX.fullmatch(scope) or scope in seen:raise ValueError()
            if (not isinstance(ids,list) or not 1<=len(ids)<=32
                or any(not isinstance(ident,str) or not _PROJECT.fullmatch(ident) for ident in ids)
                or len(ids)!=len(set(ids))):raise ValueError()
            seen.add(scope);clean.append((scope,frozenset(ids)))
        return OperatorPolicy(hashlib.sha256(raw).hexdigest(),device,sid,tuple(clean))
    except (ValueError,UnicodeError,TypeError,KeyError,RecursionError):raise WorkspaceError('INVALID') from None


class EnrolledReadSession:
    """One explicitly enrolled scope; fresh policy and actual owner on every call.

    Registry/project callbacks are internal broker code, not transport callbacks
    selected by a requester. validate_project reuses the owner's existing routing,
    paused-project, root and infrastructure guards. Nothing is auto-enrolled.
    Any observed loss/change permanently retires this instance and its leases;
    an operator must deliberately create a new session. No job queue or replay.
    """
    def __init__(self,*,scope_id,read_policy,read_registry,validate_project,observe=observe_owner):
        if not isinstance(scope_id,str) or not _HEX.fullmatch(scope_id):raise WorkspaceError('INVALID')
        self.scope=scope_id;self._read_policy=read_policy;self._read_registry=read_registry
        self._validate_project=validate_project;self._observe=observe;self._lock=threading.Lock();self._closed=False;self._provider=None
        policy,registry,owner=self._view()
        self._revision=self._revision_for(policy,registry);self._owner=owner
        self._provider=WorkspaceProvider(self.lookup,scope_id=scope_id)
        try:self.adapter=BrokerReadAdapter(self._provider,checked_context=self.checked_context)
        except BaseException:self.close();raise

    @staticmethod
    def _revision_for(policy,registry):return hashlib.sha256((policy.revision+':'+registry.revision).encode()).hexdigest()

    def close(self):
        with self._lock:self._closed=True;provider=self._provider
        if provider is not None:provider.retire()

    def _view(self):
        with self._lock:
            if self._closed:raise WorkspaceError('UNAVAILABLE')
        try:
            owner=self._observe();policy=parse_policy(self._read_policy());registry=self._read_registry()
            if (not isinstance(owner,OwnerObservation) or owner.active is not True
                or owner.device!=socket.gethostname().upper() or policy.device!=owner.device
                or owner.owner_sid!=policy.owner_sid or not isinstance(owner.owner_sid,str) or not _SID.fullmatch(owner.owner_sid)
                or type(owner.pid) is not int or owner.pid!=os.getpid()
                or type(owner.started) is not int or owner.started<=0 or type(owner.session) is not int or owner.session<=0
                or not policy.projects(self.scope)):raise WorkspaceError('DENIED')
            if (not isinstance(registry,RegistrySnapshot) or not isinstance(registry.revision,str) or not _HEX.fullmatch(registry.revision)
                or type(registry.rows) is not tuple or not 1<=len(registry.rows)<=4096
                or any(not isinstance(row,dict) for row in registry.rows)):raise WorkspaceError('UNAVAILABLE')
            if hasattr(self,'_owner') and (owner!=self._owner or self._revision_for(policy,registry)!=self._revision):raise WorkspaceError('CHANGED')
            with self._lock:
                if self._closed:raise WorkspaceError('UNAVAILABLE')
            return policy,registry,owner
        except BaseException:
            self.close();raise

    def checked_context(self):
        policy,registry,owner=self._view()
        return CheckedReadContext(self.scope,owner.device,self._revision_for(policy,registry),True)

    def lookup(self,scope,ident):
        try:
            policy,registry,owner=self._view()
            if scope!=self.scope or not isinstance(ident,str) or ident not in policy.projects(scope):raise WorkspaceError('DENIED')
            result=self._validate_project(ident,registry.rows)
            if not isinstance(result,dict) or set(result)!={'id','root','device'} or result['id']!=ident or result['device']!=owner.device:raise WorkspaceError('DENIED')
            _root(result['root'])
            # Recheck after the owning worker's potentially blocking root check.
            self._view()
            return RegisteredProject(ident,owner.device,result['root'],self._revision,self.scope)
        except BaseException:
            self.close();raise
