"""Read-only Windows workspace operations on the owning host.

Internal provider only: no HTTP listener, SSH connection, agent, model, shell or
registry writer. A trusted interactive broker supplies fresh registered bindings.
"""
from __future__ import annotations
import codecs
from contextlib import ExitStack,contextmanager
import ctypes
from ctypes import wintypes
from dataclasses import dataclass,field
import hashlib
import hmac
import json
import ntpath
import os
from pathlib import Path,PureWindowsPath
import re
import secrets
import socket
import sys
import threading
import time
import uuid

MAX_ENTRIES=200
MAX_SCANNED=1000
MAX_ENTRY_BYTES=96*1024
MAX_PREVIEW=32768
MAX_SOURCE=64*1024*1024
TTL=300
_PROJECT=re.compile(r'[A-Za-z0-9][A-Za-z0-9_-]{0,79}')
_TOKEN=re.compile(r'[a-f0-9]{64}')
_DENIED={'.git','.hg','.svn','.ssh','.gnupg','.aws','.azure','mcp-tokens','pairing',
    '.git-credentials','.envrc','auth.json','auth.lock','credentials','credentials.json',
    '.anthropic_oauth.json','google_token.json','google_oauth.json','google_oauth_pending.json',
    'webhook_subscriptions.json','config.yaml','id_rsa','id_ed25519','id_ecdsa'}
_DEVICE=re.compile(r'(?i)(?:con|prn|aux|nul|com[1-9¹²³]|lpt[1-9¹²³])(?:\..*)?')


class WorkspaceError(Exception):
    def __init__(self,code):
        assert code in {'INVALID','DENIED','CHANGED','EXPIRED','UNAVAILABLE','NOT_FOUND','BUSY','UNSUPPORTED','TOO_LARGE'}
        self.code=code;super().__init__(code)


@dataclass(frozen=True)
class RegisteredProject:
    project_id:str
    device:str
    root:str=field(repr=False)
    registry_revision:str=field(repr=False)
    scope_id:str=field(repr=False)


@dataclass(frozen=True)
class Meta:
    path:str=field(repr=False)
    attributes:int
    volume:int
    index:int
    size:int
    modified:int
    links:int
    @property
    def directory(self):return bool(self.attributes&0x10)
    @property
    def identity(self):return (self.volume,self.index)
    def fingerprint(self):return (self.attributes,self.volume,self.index,self.size,self.modified,self.links)


def _text(value,maximum):
    if (not isinstance(value,str) or not value or len(value.encode('utf-8',errors='surrogatepass'))>maximum
        or any(ord(c)<32 or ord(c)==127 or 0xd800<=ord(c)<=0xdfff for c in value)):raise WorkspaceError('INVALID')
    return value


def _part(value):
    _text(value,1024)
    if (value in ('.','..') or any(c in value for c in '/\\:<>"|?*') or value.rstrip(' .')!=value
        or _DEVICE.fullmatch(value)):raise WorkspaceError('INVALID')
    lower=value.lower()
    if lower in _DENIED or lower=='.env' or lower.startswith('.env.'):raise WorkspaceError('DENIED')
    return value


def relative(value,*,empty=False):
    if value=='' and empty:return ()
    _text(value,4096)
    if value.startswith('/') or '\\' in value or ':' in value:raise WorkspaceError('INVALID')
    parts=value.split('/')
    if len(parts)>32 or any(not p for p in parts):raise WorkspaceError('INVALID')
    return tuple(_part(p) for p in parts)


def _root(value):
    _text(value,8192)
    if value.startswith(('\\\\?\\','\\\\.\\')) or not ntpath.isabs(value):raise WorkspaceError('INVALID')
    path=PureWindowsPath(value)
    if any(p in ('.','..') for p in re.split(r'[\\/]',value)):raise WorkspaceError('INVALID')
    if not path.anchor or len(path.parts)<3 or any(p in ('.','..') for p in path.parts[1:]):raise WorkspaceError('INVALID')
    for part in path.parts[1:]:_part(part)
    return path


def _normal(value):return ntpath.normcase(ntpath.normpath(value))


def _error():
    code=ctypes.get_last_error()
    raise WorkspaceError({2:'NOT_FOUND',3:'NOT_FOUND',5:'DENIED',32:'BUSY',33:'BUSY'}.get(code,'UNAVAILABLE'))


class WindowsFiles:
    """Open existing disk handles without following a final reparse point."""
    def __init__(self):
        if sys.platform!='win32':raise WorkspaceError('UNSUPPORTED')
        class Info(ctypes.Structure):
            _fields_=[('attrs',wintypes.DWORD),('created',wintypes.FILETIME),('accessed',wintypes.FILETIME),
                ('written',wintypes.FILETIME),('volume',wintypes.DWORD),('size_hi',wintypes.DWORD),
                ('size_lo',wintypes.DWORD),('links',wintypes.DWORD),('index_hi',wintypes.DWORD),('index_lo',wintypes.DWORD)]
        class FileIdInfo(ctypes.Structure):
            _fields_=[('volume',ctypes.c_ulonglong),('identifier',ctypes.c_ubyte*16)]
        self.Info=Info;self.FileIdInfo=FileIdInfo;k=self.k=ctypes.WinDLL('kernel32',use_last_error=True)
        k.CreateFileW.argtypes=[wintypes.LPCWSTR,wintypes.DWORD,wintypes.DWORD,ctypes.c_void_p,wintypes.DWORD,wintypes.DWORD,wintypes.HANDLE];k.CreateFileW.restype=wintypes.HANDLE
        k.CloseHandle.argtypes=[wintypes.HANDLE];k.CloseHandle.restype=wintypes.BOOL
        k.GetFileInformationByHandle.argtypes=[wintypes.HANDLE,ctypes.POINTER(Info)];k.GetFileInformationByHandle.restype=wintypes.BOOL
        k.GetFileInformationByHandleEx.argtypes=[wintypes.HANDLE,ctypes.c_int,ctypes.c_void_p,wintypes.DWORD];k.GetFileInformationByHandleEx.restype=wintypes.BOOL
        k.GetFinalPathNameByHandleW.argtypes=[wintypes.HANDLE,wintypes.LPWSTR,wintypes.DWORD,wintypes.DWORD];k.GetFinalPathNameByHandleW.restype=wintypes.DWORD
        k.GetFileType.argtypes=[wintypes.HANDLE];k.GetFileType.restype=wintypes.DWORD
        k.ReadFile.argtypes=[wintypes.HANDLE,ctypes.c_void_p,wintypes.DWORD,ctypes.POINTER(wintypes.DWORD),ctypes.c_void_p];k.ReadFile.restype=wintypes.BOOL

    def info(self,handle):
        data=self.Info()
        if not self.k.GetFileInformationByHandle(handle,ctypes.byref(data)):_error()
        identity=self.FileIdInfo()
        # FileIdInfo (18) returns the complete ID; the legacy 64-bit ID may collide on ReFS.
        if not self.k.GetFileInformationByHandleEx(handle,18,ctypes.byref(identity),ctypes.sizeof(identity)):
            if ctypes.get_last_error() in (1,50,87,120):raise WorkspaceError('UNSUPPORTED')
            _error()
        buf=ctypes.create_unicode_buffer(32768);length=self.k.GetFinalPathNameByHandleW(handle,buf,len(buf),0)
        if not 0<length<len(buf):_error()
        final=buf.value
        if final.startswith('\\\\?\\UNC\\'):final='\\\\'+final[8:]
        elif final.startswith('\\\\?\\'):final=final[4:]
        index=int.from_bytes(bytes(identity.identifier),'little')
        if not index:raise WorkspaceError('UNSUPPORTED')
        return Meta(final,data.attrs,identity.volume,index,(data.size_hi<<32)|data.size_lo,(data.written.dwHighDateTime<<32)|data.written.dwLowDateTime,data.links)

    @contextmanager
    def opened(self,path,*,contents=False,directory=None):
        value=str(path)
        extended='\\\\?\\UNC\\'+value[2:] if value.startswith('\\\\') else '\\\\?\\'+value
        # No FILE_SHARE_DELETE: held directories cannot be swapped during this operation.
        access=0x80000000 if contents else 0x80
        sharing=1 if contents else 3
        handle=self.k.CreateFileW(extended,access,sharing,None,3,0x02000000|0x00200000,None)
        if handle in (None,ctypes.c_void_p(-1).value):_error()
        try:
            if self.k.GetFileType(handle)!=1:raise WorkspaceError('UNSUPPORTED')
            meta=self.info(handle)
            if meta.attributes&0x400 or (not meta.directory and meta.links!=1):raise WorkspaceError('DENIED')
            if directory is not None and meta.directory is not directory:raise WorkspaceError('CHANGED')
            yield handle,meta
        finally:
            if not self.k.CloseHandle(handle):_error()

    def read(self,handle,amount):
        if not 0<=amount<=MAX_PREVIEW:raise WorkspaceError('INVALID')
        if amount==0:return b''
        buffer=ctypes.create_string_buffer(amount);got=wintypes.DWORD()
        if not self.k.ReadFile(handle,buffer,amount,ctypes.byref(got),None):_error()
        return buffer.raw[:got.value]


@dataclass(frozen=True)
class Grant:
    binding:RegisteredProject=field(repr=False)
    root_path:str=field(repr=False)
    root_id:tuple[int,int]=field(repr=False)
    deadline:float
    token:str=field(repr=False)


class WorkspaceProvider:
    """Call only inside the registered owner's trusted interactive worker process."""
    def __init__(self,lookup,*,scope_id,clock=time.monotonic):
        if not isinstance(scope_id,str) or not _TOKEN.fullmatch(scope_id):raise WorkspaceError('INVALID')
        self.scope=scope_id;self.lookup=lookup;self.clock=clock;self.device=socket.gethostname().upper();self.instance=str(uuid.uuid4())
        self._key=secrets.token_bytes(32);self._grants={};self._lock=threading.Lock();self._slots=threading.BoundedSemaphore(4);self._retired=False
        self._native=None

    def _files(self):
        with self._lock:
            if self._native is None:self._native=WindowsFiles()
            return self._native

    def retire(self):
        with self._lock:self._retired=True;self._grants.clear()

    def _binding(self,project_id):
        with self._lock:
            if self._retired:raise WorkspaceError('UNAVAILABLE')
        if not isinstance(project_id,str) or not _PROJECT.fullmatch(project_id):raise WorkspaceError('INVALID')
        if socket.gethostname().upper()!=self.device:raise WorkspaceError('CHANGED')
        try:binding=self.lookup(self.scope,project_id)
        except Exception:raise WorkspaceError('UNAVAILABLE') from None
        if (not isinstance(binding,RegisteredProject) or binding.project_id!=project_id
            or binding.device!=self.device or binding.scope_id!=self.scope):raise WorkspaceError('CHANGED')
        _root(binding.root);_text(binding.registry_revision,256)
        return binding

    def _mac(self,*values):
        raw=json.dumps(values,ensure_ascii=True,separators=(',',':')).encode()
        return hmac.new(self._key,raw,hashlib.sha256).hexdigest()

    @contextmanager
    def _root_handle(self,binding):
        native=self._files();root=_root(binding.root)
        with ExitStack() as stack:
            path=Path(root.anchor);handle,meta=stack.enter_context(native.opened(path,directory=True))
            expected=meta.path
            for part in root.parts[1:]:
                path=path/part;expected=ntpath.join(expected,part)
                handle,meta=stack.enter_context(native.opened(path,directory=True))
                if _normal(meta.path)!=_normal(expected):raise WorkspaceError('CHANGED')
            yield native,root,handle,meta

    def prepare(self,project_id):
        binding=self._binding(project_id)
        with self._root_handle(binding) as (_,_,_,meta):
            if self._binding(project_id)!=binding:raise WorkspaceError('CHANGED')
            now=self.clock();token=self._mac('binding',secrets.token_hex(16),self.scope,project_id,self.instance,meta.path,meta.identity,binding.registry_revision)
            grant=Grant(binding,meta.path,meta.identity,now+TTL,token)
            with self._lock:
                if self._retired:raise WorkspaceError('UNAVAILABLE')
                self._grants={key:value for key,value in self._grants.items() if value.deadline>now}
                if len(self._grants)>=16:raise WorkspaceError('BUSY')
                self._grants[token]=grant
            return dict(schema=1,scope_id=self.scope,worker=dict(device=self.device,instance_id=self.instance),project_id=project_id,
                root=meta.path,binding_token=token,expires_in_seconds=TTL)

    def _grant(self,project_id,token):
        if not isinstance(token,str) or not _TOKEN.fullmatch(token):raise WorkspaceError('INVALID')
        with self._lock:grant=self._grants.get(token)
        if grant is None or grant.binding.project_id!=project_id:raise WorkspaceError('CHANGED')
        if self.clock()>=grant.deadline:raise WorkspaceError('EXPIRED')
        if self._binding(project_id)!=grant.binding:raise WorkspaceError('CHANGED')
        return grant

    def _entry_token(self,grant,relative_path,meta):
        return self._mac('entry',grant.token,relative_path,meta.fingerprint())

    @contextmanager
    def _target(self,grant,parts,entry_token,*,contents=False):
        with self._root_handle(grant.binding) as (native,root,handle,meta),ExitStack() as stack:
            if meta.identity!=grant.root_id or _normal(meta.path)!=_normal(grant.root_path):raise WorkspaceError('CHANGED')
            path=root;expected=meta.path
            for index,part in enumerate(parts):
                path=path/part;expected=ntpath.join(expected,part)
                handle,meta=stack.enter_context(native.opened(path,contents=contents and index==len(parts)-1,
                    directory=True if index<len(parts)-1 else None))
                if _normal(meta.path)!=_normal(expected):raise WorkspaceError('CHANGED')
            if parts:
                if not isinstance(entry_token,str) or not _TOKEN.fullmatch(entry_token) or not hmac.compare_digest(entry_token,self._entry_token(grant,'/'.join(parts),meta)):raise WorkspaceError('CHANGED')
            elif entry_token is not None:raise WorkspaceError('INVALID')
            yield native,path,handle,meta
            if self._binding(grant.binding.project_id)!=grant.binding:raise WorkspaceError('CHANGED')
            if self.clock()>=grant.deadline:raise WorkspaceError('EXPIRED')

    def listing(self,project_id,token,relative_path,entry_token):
        parts=relative(relative_path,empty=True);grant=self._grant(project_id,token)
        with self._target(grant,parts,entry_token) as (native,path,_,meta):
            if not meta.directory:raise WorkspaceError('CHANGED')
            entries=[];excluded=0;scanned=0;truncated=False;entry_bytes=0
            try:
                with os.scandir(path) as iterator:
                    for item in iterator:
                        scanned+=1
                        if scanned>MAX_SCANNED or len(entries)>=MAX_ENTRIES:truncated=True;break
                        try:
                            _part(item.name);child_path='/'.join((*parts,item.name))
                            with native.opened(path/item.name) as (_,child):
                                if _normal(child.path)!=_normal(ntpath.join(meta.path,item.name)):raise WorkspaceError('CHANGED')
                                relative(child_path)
                                row=dict(name=item.name,relative_path=child_path,kind='directory' if child.directory else 'file',
                                    byte_size=None if child.directory else child.size,entry_token=self._entry_token(grant,child_path,child))
                                size=len(json.dumps(row,ensure_ascii=True,separators=(',',':')).encode())
                                if entry_bytes+size>MAX_ENTRY_BYTES:truncated=True;break
                                entries.append(row);entry_bytes+=size
                        except WorkspaceError as error:
                            if error.code not in ('DENIED','INVALID','NOT_FOUND','BUSY'):raise
                            excluded+=1
            except OSError:raise WorkspaceError('UNAVAILABLE') from None
            entries.sort(key=lambda row:(row['kind']!='directory',row['name'].lower(),row['name']))
            return dict(schema=1,scope_id=self.scope,worker_instance=self.instance,project_id=project_id,relative_path=relative_path,
                entries=entries,truncated=truncated,excluded_entries=excluded)

    def preview(self,project_id,token,relative_path,entry_token):
        parts=relative(relative_path);grant=self._grant(project_id,token)
        with self._target(grant,parts,entry_token,contents=True) as (native,_,handle,meta):
            if meta.directory:raise WorkspaceError('CHANGED')
            if meta.size>MAX_SOURCE:raise WorkspaceError('TOO_LARGE')
            data=native.read(handle,min(meta.size,MAX_PREVIEW));after=native.info(handle)
            if meta.fingerprint()!=after.fingerprint() or meta.path!=after.path or (meta.size and not data):raise WorkspaceError('CHANGED')
            truncated=len(data)<meta.size;binary=b'\0' in data;reader=codecs.getincrementaldecoder('utf-8-sig')()
            try:text=None if binary else reader.decode(data,final=not truncated)
            except UnicodeError:binary=True;text=None
            if text is not None and any(ord(c)<32 and c not in '\t\n\r' for c in text):binary=True;text=None
            return dict(schema=1,scope_id=self.scope,worker_instance=self.instance,project_id=project_id,relative_path=relative_path,
                byte_size=meta.size,preview_bytes=len(data),preview_sha256=hashlib.sha256(data).hexdigest(),
                binary=binary,text=text,truncated=truncated)

    def dispatch(self,request):
        if not isinstance(request,dict) or type(request.get('schema')) is not int or request['schema']!=1:raise WorkspaceError('INVALID')
        operation=request.get('operation');base={'schema','operation','scope_id','project_id'}
        if operation=='prepare':
            if set(request)!=base:raise WorkspaceError('INVALID')
        elif operation in ('list','preview'):
            if set(request)!=base|{'binding_token','relative_path','entry_token'}:raise WorkspaceError('INVALID')
        else:raise WorkspaceError('INVALID')
        scope=request['scope_id']
        if not isinstance(scope,str) or not _TOKEN.fullmatch(scope):raise WorkspaceError('INVALID')
        if scope!=self.scope:raise WorkspaceError('CHANGED')
        if not self._slots.acquire(blocking=False):raise WorkspaceError('BUSY')
        try:
            if operation=='prepare':return self.prepare(request['project_id'])
            args=[request[k] for k in ['project_id','binding_token','relative_path','entry_token']]
            return self.listing(*args) if operation=='list' else self.preview(*args)
        finally:self._slots.release()
