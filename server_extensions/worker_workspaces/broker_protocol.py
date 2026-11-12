"""Bounded byte frames for an already authenticated interactive worker broker.

No listener, job queue, file cache, credentials, shell, model or implicit retry.
The transport must supply fresh trusted context; request JSON cannot supply it.
"""
from __future__ import annotations
from dataclasses import dataclass,field
import json
import re
import socket
import threading
from .workspace_provider import WorkspaceError

MAX_REQUEST_BYTES=16*1024
MAX_REPLY_BYTES=128*1024
MAX_REQUEST_IDS=256
_ID=re.compile(r'[a-f0-9]{32}')


@dataclass(frozen=True)
class CheckedReadContext:
    scope_id:str=field(repr=False)
    device:str
    policy_revision:str=field(repr=False)
    interactive:bool


def _unique(pairs):
    result={}
    for key,value in pairs:
        if key in result:raise ValueError('Duplicate field')
        result[key]=value
    return result


def decode_frame(raw):
    if type(raw) is not bytes or not 1<=len(raw)<=MAX_REQUEST_BYTES:raise WorkspaceError('INVALID')
    try:
        frame=json.loads(raw.decode('utf-8'),object_pairs_hook=_unique,parse_constant=lambda value:(_ for _ in ()).throw(ValueError()))
    except (ValueError,UnicodeError,RecursionError):raise WorkspaceError('INVALID') from None
    if (not isinstance(frame,dict) or set(frame)!={'schema','request_id','worker_instance','payload'}
        or type(frame['schema']) is not int or frame['schema']!=1
        or not isinstance(frame['request_id'],str) or not _ID.fullmatch(frame['request_id'])
        or not isinstance(frame['worker_instance'],str) or not isinstance(frame['payload'],dict)):raise WorkspaceError('INVALID')
    return frame


class BrokerReadAdapter:
    """One authorized transport context owns one provider and its binding lifetime."""
    def __init__(self,provider,*,checked_context):
        self._provider=provider;self._checked_context=checked_context
        self._lock=threading.Lock();self._slots=threading.BoundedSemaphore(4);self._seen=set();self._closed=False
        self._scope=provider.scope;self._device=provider.device;self.instance=provider.instance
        self._revision=self._context().policy_revision

    def _context(self):
        with self._lock:
            if self._closed:raise WorkspaceError('UNAVAILABLE')
        try:value=self._checked_context()
        except Exception:raise WorkspaceError('UNAVAILABLE') from None
        if (not isinstance(value,CheckedReadContext) or value.interactive is not True
            or value.scope_id!=self._scope or value.device!=self._device
            or socket.gethostname().upper()!=self._device
            or not isinstance(value.policy_revision,str) or not 1<=len(value.policy_revision)<=256):raise WorkspaceError('DENIED')
        if hasattr(self,'_revision') and value.policy_revision!=self._revision:raise WorkspaceError('CHANGED')
        with self._lock:
            if self._closed:raise WorkspaceError('UNAVAILABLE')
        return value

    def close(self):
        with self._lock:self._closed=True
        self._provider.retire()

    def _reply(self,request_id,*,result=None,code=None):
        frame=dict(schema=1,request_id=request_id,worker_instance=self.instance,
                   status='error' if code else 'ok')
        if code:frame['code']=code
        else:frame['result']=result
        try:raw=json.dumps(frame,ensure_ascii=False,allow_nan=False,separators=(',',':')).encode('utf-8')
        except (TypeError,ValueError,UnicodeError):return self._reply(request_id,code='UNAVAILABLE')
        if len(raw)>MAX_REPLY_BYTES:return self._reply(request_id,code='TOO_LARGE')
        return raw

    def handle(self,raw):
        request_id=None;acquired=False
        try:
            frame=decode_frame(raw);request_id=frame['request_id'];self._context()
            if frame['worker_instance']!=self.instance:raise WorkspaceError('CHANGED')
            if not self._slots.acquire(blocking=False):raise WorkspaceError('BUSY')
            acquired=True
            with self._lock:
                if self._closed:raise WorkspaceError('UNAVAILABLE')
                if request_id in self._seen:return self._reply(request_id,code='DUPLICATE')
                if len(self._seen)>=MAX_REQUEST_IDS:raise WorkspaceError('BUSY')
                self._seen.add(request_id)
            # Dispatch validates the closed scope/project/path schema before file I/O.
            result=self._provider.dispatch(frame['payload'])
            self._context() # Revocation/owner loss drops observed contents before serialization.
            with self._lock:
                if self._closed:raise WorkspaceError('UNAVAILABLE')
                return self._reply(request_id,result=result)
        except WorkspaceError as error:return self._reply(request_id,code=error.code)
        except Exception:return self._reply(request_id,code='UNAVAILABLE')
        finally:
            if acquired:self._slots.release()
