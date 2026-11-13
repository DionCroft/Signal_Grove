"""Opt-in owner-only local Windows pipe for foreground workspace reads.

Importing starts nothing. No TCP, shared secret, pickle, queue, cache or retry.
OS user authentication complements the broker's separate actor/profile policy.
"""
from __future__ import annotations
import ctypes
from ctypes import wintypes as w
from dataclasses import dataclass,field
import hashlib
import json
import math
import os
import re
import secrets
import sys
import threading
import time
from .broker_protocol import MAX_REQUEST_BYTES,MAX_REPLY_BYTES,decode_frame,_unique
from .workspace_provider import WorkspaceError

_PIPE=re.compile(r'\\\\\.\\pipe\\SignalGrove\.Workspace\.[a-f0-9]{32}')
_UUID=re.compile(r'[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}')
_SCOPE=re.compile(r'[a-f0-9]{64}')
_CODES={'INVALID','DENIED','CHANGED','EXPIRED','UNAVAILABLE','NOT_FOUND','BUSY','UNSUPPORTED','TOO_LARGE','DUPLICATE'}


class ReadTransportError(Exception):
    def __init__(self,code,*,request_submitted=False):
        self.code=code;self.request_submitted=request_submitted;super().__init__(code)


class IdentityRevertFailure(BaseException):
    """Fail-stop the dedicated pipe thread if its impersonation cannot be reverted."""


@dataclass(frozen=True)
class PipeEndpoint:
    address:str=field(repr=False)
    worker_instance:str
    scope_id:str=field(repr=False)
    owner_sid:str=field(repr=False)
    server_pid:int
    server_started:int
    server_session:int


def _deadline(timeout):
    if type(timeout) not in (float,int) or not .05<=timeout<=10 or not math.isfinite(timeout):raise WorkspaceError('INVALID')
    return time.monotonic()+timeout


def _endpoint(value):
    if (not isinstance(value,PipeEndpoint) or not isinstance(value.address,str) or not _PIPE.fullmatch(value.address)
        or not isinstance(value.worker_instance,str) or not _UUID.fullmatch(value.worker_instance)
        or not isinstance(value.scope_id,str) or not _SCOPE.fullmatch(value.scope_id)
        or not isinstance(value.owner_sid,str) or len(value.owner_sid)>184 or not re.fullmatch(r'S-1-(?:[0-9]+-){1,15}[0-9]+',value.owner_sid)
        or type(value.server_pid) is not int or value.server_pid<=0
        or type(value.server_started) is not int or value.server_started<=0
        or type(value.server_session) is not int or value.server_session<0):raise WorkspaceError('INVALID')
    return value


class _Windows:
    def __init__(self):
        if sys.platform!='win32':raise WorkspaceError('UNSUPPORTED')
        import _winapi
        self.api=_winapi;self.k=ctypes.WinDLL('kernel32',use_last_error=True);self.a=ctypes.WinDLL('advapi32',use_last_error=True)
        class Attributes(ctypes.Structure):_fields_=[('length',w.DWORD),('descriptor',ctypes.c_void_p),('inherit',w.BOOL)]
        class Sid(ctypes.Structure):_fields_=[('pointer',ctypes.c_void_p),('attributes',w.DWORD)]
        self.Attributes=Attributes;self.Sid=Sid
        signatures=[(self.k,'GetCurrentProcess',[],w.HANDLE),(self.k,'GetCurrentThread',[],w.HANDLE),
            (self.k,'CloseHandle',[w.HANDLE],w.BOOL),(self.k,'LocalFree',[ctypes.c_void_p],ctypes.c_void_p),
            (self.k,'OpenProcess',[w.DWORD,w.BOOL,w.DWORD],w.HANDLE),
            (self.k,'GetProcessTimes',[w.HANDLE,*([ctypes.POINTER(w.FILETIME)]*4)],w.BOOL),
            (self.k,'ProcessIdToSessionId',[w.DWORD,ctypes.POINTER(w.DWORD)],w.BOOL),
            (self.k,'GetNamedPipeServerProcessId',[w.HANDLE,ctypes.POINTER(w.DWORD)],w.BOOL),
            (self.k,'CancelIoEx',[w.HANDLE,ctypes.c_void_p],w.BOOL),
            (self.k,'DisconnectNamedPipe',[w.HANDLE],w.BOOL),
            (self.k,'CreateNamedPipeW',[w.LPCWSTR,*([w.DWORD]*6),ctypes.POINTER(Attributes)],w.HANDLE),
            (self.k,'CreateFileW',[w.LPCWSTR,w.DWORD,w.DWORD,ctypes.c_void_p,w.DWORD,w.DWORD,w.HANDLE],w.HANDLE),
            (self.k,'WaitNamedPipeW',[w.LPCWSTR,w.DWORD],w.BOOL),
            (self.a,'OpenProcessToken',[w.HANDLE,w.DWORD,ctypes.POINTER(w.HANDLE)],w.BOOL),
            (self.a,'OpenThreadToken',[w.HANDLE,w.DWORD,w.BOOL,ctypes.POINTER(w.HANDLE)],w.BOOL),
            (self.a,'GetTokenInformation',[w.HANDLE,ctypes.c_int,ctypes.c_void_p,w.DWORD,ctypes.POINTER(w.DWORD)],w.BOOL),
            (self.a,'ConvertSidToStringSidW',[ctypes.c_void_p,ctypes.POINTER(ctypes.c_void_p)],w.BOOL),
            (self.a,'ConvertStringSecurityDescriptorToSecurityDescriptorW',[w.LPCWSTR,w.DWORD,ctypes.POINTER(ctypes.c_void_p),ctypes.POINTER(w.DWORD)],w.BOOL),
            (self.a,'ImpersonateNamedPipeClient',[w.HANDLE],w.BOOL),(self.a,'RevertToSelf',[],w.BOOL)]
        for library,name,args,result in signatures:
            fn=getattr(library,name);fn.argtypes=args;fn.restype=result

    def error(self,code=None):
        code=ctypes.get_last_error() if code is None else code
        raise WorkspaceError({2:'NOT_FOUND',3:'NOT_FOUND',5:'DENIED',231:'BUSY',234:'TOO_LARGE'}.get(code,'UNAVAILABLE'))

    def check(self,value):
        if not value:self.error()
        return value

    def wait_available(self,address,deadline):
        # One pre-admission wait shares the request deadline. Availability can
        # race another client; CreateFile still gets exactly one attempt.
        remaining=deadline-time.monotonic()
        if remaining<=0:raise WorkspaceError('UNAVAILABLE')
        self.check(self.k.WaitNamedPipeW(address,max(1,math.ceil(remaining*1000))))
        if time.monotonic()>=deadline:raise WorkspaceError('UNAVAILABLE')

    def token_sid(self,token):
        needed=w.DWORD();self.a.GetTokenInformation(token,1,None,0,ctypes.byref(needed))
        if not 1<=needed.value<=65536:raise WorkspaceError('UNAVAILABLE')
        buffer=ctypes.create_string_buffer(needed.value);self.check(self.a.GetTokenInformation(token,1,buffer,len(buffer),ctypes.byref(needed)))
        sid=ctypes.cast(buffer,ctypes.POINTER(self.Sid)).contents;value=ctypes.c_void_p()
        self.check(self.a.ConvertSidToStringSidW(sid.pointer,ctypes.byref(value)))
        try:return ctypes.wstring_at(value)
        finally:
            if self.k.LocalFree(value):self.error()

    def process_identity(self,pid):
        handle=self.k.OpenProcess(0x1000,False,pid);self.check(handle);token=w.HANDLE()
        try:
            self.check(self.a.OpenProcessToken(handle,8,ctypes.byref(token)))
            try:sid=self.token_sid(token)
            finally:self.check(self.k.CloseHandle(token))
            created,exit_time,kernel,user=(w.FILETIME() for _ in range(4));self.check(self.k.GetProcessTimes(handle,ctypes.byref(created),ctypes.byref(exit_time),ctypes.byref(kernel),ctypes.byref(user)))
            session=w.DWORD();self.check(self.k.ProcessIdToSessionId(pid,ctypes.byref(session)))
            return sid,(created.dwHighDateTime<<32)|created.dwLowDateTime,session.value
        finally:self.check(self.k.CloseHandle(handle))

    def client_owner(self,handle,expected):
        self.check(self.a.ImpersonateNamedPipeClient(handle));token=w.HANDLE()
        try:
            self.check(self.a.OpenThreadToken(self.k.GetCurrentThread(),8,True,ctypes.byref(token)))
            try:
                if self.token_sid(token)!=expected:raise WorkspaceError('DENIED')
            finally:self.check(self.k.CloseHandle(token))
        finally:
            if not self.a.RevertToSelf():raise IdentityRevertFailure('Identity revert failed; stop this pipe thread')

    def pipe(self,address,sid):
        descriptor=ctypes.c_void_p();self.check(self.a.ConvertStringSecurityDescriptorToSecurityDescriptorW('D:P(A;;GA;;;'+sid+')',1,ctypes.byref(descriptor),None))
        try:
            attributes=self.Attributes(ctypes.sizeof(self.Attributes),descriptor,False)
            # Overlapped duplex, first instance only; message mode; local clients only.
            handle=self.k.CreateNamedPipeW(address,3|0x40000000|0x00080000,4|2|8,1,MAX_REPLY_BYTES+1,MAX_REQUEST_BYTES+1,0,ctypes.byref(attributes))
            if handle in (None,ctypes.c_void_p(-1).value):self.error()
            return handle
        finally:
            if self.k.LocalFree(descriptor):self.error()

    def wait(self,operation,deadline):
        try:
            remaining=deadline-time.monotonic()
            if remaining<=0 or self.api.WaitForSingleObject(operation.event,max(1,math.ceil(remaining*1000)))!=0:
                raise WorkspaceError('UNAVAILABLE')
        except BaseException:
            try:operation.cancel()
            finally:operation.GetOverlappedResult(True)
            raise
        amount,error=operation.GetOverlappedResult(True)
        if error:self.error(error)
        return amount

    def begin_read(self,handle,limit):return self.api.ReadFile(handle,limit+1,overlapped=True)[0]
    def begin_write(self,handle,data):return self.api.WriteFile(handle,data,overlapped=True)[0]
    def read(self,handle,limit,deadline):
        operation=self.begin_read(handle,limit);amount=self.wait(operation,deadline);data=bytes(operation.getbuffer())
        if not 0<amount==len(data)<=limit:raise WorkspaceError('TOO_LARGE')
        return data
    def write(self,handle,data,deadline):
        operation=self.begin_write(handle,data)
        if self.wait(operation,deadline)!=len(data):raise WorkspaceError('UNAVAILABLE')


class LocalReadPipe:
    """One opt-in owner pipe; the existing broker owns serving/thread lifecycle."""
    def __init__(self,adapter):
        adapter._context()
        self._windows=_Windows();self._adapter=adapter;self._lock=threading.Lock();self._serving=False;self._closed=False;self._closed_event=threading.Event();self._listening=threading.Event()
        sid,started,session=self._windows.process_identity(os.getpid())
        self.endpoint=PipeEndpoint('\\\\.\\pipe\\SignalGrove.Workspace.'+secrets.token_hex(16),adapter.instance,adapter._scope,sid,os.getpid(),started,session)
        _endpoint(self.endpoint);self._handle=self._windows.pipe(self.endpoint.address,sid)

    def _begin(self,fn,*args):
        with self._lock:
            if self._closed:raise WorkspaceError('UNAVAILABLE')
            return fn(self._handle,*args)

    def _read(self,limit,deadline):
        operation=self._begin(self._windows.begin_read,limit);amount=self._windows.wait(operation,deadline);data=bytes(operation.getbuffer())
        if not 0<amount==len(data)<=limit:raise WorkspaceError('TOO_LARGE')
        return data

    def _write(self,data,deadline):
        operation=self._begin(self._windows.begin_write,data)
        if self._windows.wait(operation,deadline)!=len(data):raise WorkspaceError('UNAVAILABLE')

    def _finish_close(self):
        if self._handle is not None:self._windows.check(self._windows.k.CloseHandle(self._handle));self._handle=None
        self._closed_event.set()

    def close(self):
        try:
            with self._lock:
                self._closed=True
                if self._handle is not None:
                    if not self._windows.k.CancelIoEx(self._handle,None) and ctypes.get_last_error()!=1168:self._windows.error()
                if not self._serving:self._finish_close()
        finally:self._adapter.close()

    @property
    def accepting(self):
        with self._lock:return not self._closed and self._listening.is_set()

    def wait_closed(self,timeout=3):return self._closed_event.wait(timeout)

    def serve_one(self,*,timeout=3):
        """Bound idle accept and the authenticated exchange separately.

        A client arriving near the end of an idle listen still receives one full
        exchange budget. Request, reply and acknowledgement share that deadline;
        no individual I/O or request is retried or given another extension.
        """
        deadline=_deadline(timeout)
        with self._lock:
            if self._closed:raise WorkspaceError('UNAVAILABLE')
            if self._serving:raise WorkspaceError('BUSY')
            self._serving=True
        try:
            operation=self._begin(lambda handle:self._windows.api.ConnectNamedPipe(handle,overlapped=True));self._listening.set()
            try:self._windows.wait(operation,deadline)
            finally:self._listening.clear()
            deadline=_deadline(timeout)
            raw=self._read(MAX_REQUEST_BYTES,deadline);self._windows.client_owner(self._handle,self.endpoint.owner_sid)
            reply=self._adapter.handle(raw)
            if time.monotonic()>=deadline:raise WorkspaceError('UNAVAILABLE')
            self._write(reply,deadline)
            if self._read(35,deadline)!=b'ACK'+hashlib.sha256(reply).digest():raise WorkspaceError('INVALID')
            return 'served'
        except IdentityRevertFailure:
            self.close();raise
        except WorkspaceError as error:
            if error.code=='DENIED':self.close()
            raise
        except OSError as error:self._windows.error(error.winerror)
        finally:
            with self._lock:
                if self._handle is not None:self._windows.k.DisconnectNamedPipe(self._handle)
                self._serving=False
                if self._closed:self._finish_close()


def _reply(raw,request,endpoint):
    try:
        value=json.loads(raw.decode('utf-8'),object_pairs_hook=_unique,parse_constant=lambda value:(_ for _ in ()).throw(ValueError()))
        base={'schema','request_id','worker_instance','status'}
        if (not isinstance(value,dict) or type(value.get('schema')) is not int or value['schema']!=1
            or value.get('request_id')!=request['request_id'] or value.get('worker_instance')!=endpoint.worker_instance):raise ValueError()
        if value['status']=='error':
            if set(value)!=base|{'code'} or value['code'] not in _CODES:raise ValueError()
        elif value['status']=='ok':
            result=value['result'];payload=request['payload']
            if (set(value)!=base|{'result'} or not isinstance(result,dict) or type(result.get('schema')) is not int or result['schema']!=1
                or result.get('scope_id')!=endpoint.scope_id or result.get('project_id')!=payload.get('project_id')):raise ValueError()
            if payload.get('operation')=='prepare':
                if result.get('worker',{}).get('instance_id')!=endpoint.worker_instance:raise ValueError()
            elif result.get('worker_instance')!=endpoint.worker_instance:raise ValueError()
        else:raise ValueError()
    except (ValueError,TypeError,KeyError,UnicodeError,RecursionError,AttributeError):raise WorkspaceError('INVALID') from None
    return raw


def call_once(endpoint,raw,*,timeout=3):
    """Return one authenticated reply or a fixed error with submission uncertainty."""
    submitted=False;handle=None;windows=None
    try:
        deadline=_deadline(timeout);_endpoint(endpoint);request=decode_frame(raw)
        if request['worker_instance']!=endpoint.worker_instance or request['payload'].get('scope_id')!=endpoint.scope_id:raise WorkspaceError('CHANGED')
        windows=_Windows();sid,_,_=windows.process_identity(os.getpid())
        if endpoint.owner_sid!=sid:raise WorkspaceError('DENIED')
        windows.wait_available(endpoint.address,deadline)
        # Identification-only SQOS: the server can query identity, not use client credentials.
        handle=windows.k.CreateFileW(endpoint.address,0xc0000000,0,None,3,0x40000000|0x100000|0x10000,None)
        if handle in (None,ctypes.c_void_p(-1).value):handle=None;windows.error()
        pid=w.DWORD();windows.check(windows.k.GetNamedPipeServerProcessId(handle,ctypes.byref(pid)))
        if pid.value!=endpoint.server_pid:raise WorkspaceError('DENIED')
        owner,started,session=windows.process_identity(pid.value)
        if (owner,started,session)!=(endpoint.owner_sid,endpoint.server_started,endpoint.server_session):raise WorkspaceError('DENIED')
        windows.api.SetNamedPipeHandleState(handle,2,None,None)
        submitted=True # A failed/expired write can still have reached the broker.
        windows.write(handle,raw,deadline);reply=windows.read(handle,MAX_REPLY_BYTES,deadline);_reply(reply,request,endpoint)
        windows.write(handle,b'ACK'+hashlib.sha256(reply).digest(),deadline)
        return reply
    except WorkspaceError as error:raise ReadTransportError(error.code,request_submitted=submitted) from None
    except OSError as error:
        code={2:'NOT_FOUND',3:'NOT_FOUND',5:'DENIED',231:'BUSY',234:'TOO_LARGE'}.get(error.winerror,'UNAVAILABLE')
        raise ReadTransportError(code,request_submitted=submitted) from None
    finally:
        if handle is not None and not windows.k.CloseHandle(handle):raise ReadTransportError('UNAVAILABLE',request_submitted=submitted)
