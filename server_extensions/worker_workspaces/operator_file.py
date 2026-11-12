"""Read configured operator metadata while holding ancestor and source handles.

This is internal broker configuration I/O, never a phone-selectable file reader.
Metadata reads do not issue workspace leases or weaken the provider's mandatory
128-bit workspace identities. Configured source bytes define their own revision.
"""
from contextlib import ExitStack,contextmanager
import ctypes
import ntpath
from pathlib import PureWindowsPath
from .workspace_provider import WindowsFiles,WorkspaceError,_error


def _same_path(a,b):
    return ntpath.normcase(ntpath.normpath(a)).rstrip("\\")==ntpath.normcase(ntpath.normpath(b)).rstrip("\\")


def read_operator_file(path,*,maximum=1024*1024):
    if (not isinstance(path,str) or not path or len(path)>4096 or '/' in path
        or any(ord(c)<32 for c in path) or path.startswith(('\\\\?\\','\\\\.\\'))
        or any(part in ('.','..') for part in path.split('\\'))
        or type(maximum) is not int or not 1<=maximum<=1024*1024):raise WorkspaceError('INVALID')
    parsed=PureWindowsPath(path)
    if not parsed.is_absolute() or len(parsed.parts)<3 or any(':' in p for p in parsed.parts[1:]):raise WorkspaceError('INVALID')
    native=WindowsFiles();k=native.k
    def info(handle,expected,directory):
        data=native.Info()
        if not k.GetFileInformationByHandle(handle,ctypes.byref(data)):_error()
        if k.GetFileType(handle)!=1 or data.attrs&0x400 or bool(data.attrs&0x10)!=directory or (not directory and data.links!=1):raise WorkspaceError('DENIED')
        buf=ctypes.create_unicode_buffer(32768);length=k.GetFinalPathNameByHandleW(handle,buf,len(buf),0)
        if not 0<length<len(buf):_error()
        final=buf.value
        if final.startswith('\\\\?\\UNC\\'):final='\\\\'+final[8:]
        elif final.startswith('\\\\?\\'):final=final[4:]
        if not _same_path(final,str(expected)):raise WorkspaceError('CHANGED')
        return ((data.size_hi<<32)|data.size_lo,(data.written.dwHighDateTime<<32)|data.written.dwLowDateTime,data.attrs,data.links)
    @contextmanager
    def opened(expected,directory):
        value=str(expected);extended='\\\\?\\UNC\\'+value[2:] if value.startswith('\\\\') else '\\\\?\\'+value
        handle=k.CreateFileW(extended,0x80 if directory else 0x80000000,3 if directory else 1,None,3,0x02000000|0x00200000,None)
        if handle in (None,ctypes.c_void_p(-1).value):_error()
        try:yield handle,info(handle,expected,directory)
        finally:
            if not k.CloseHandle(handle):_error()
    with ExitStack() as stack:
        current=PureWindowsPath(parsed.anchor)
        stack.enter_context(opened(current,True))
        for part in parsed.parts[1:-1]:
            current=current/part;stack.enter_context(opened(current,True))
        handle,before=stack.enter_context(opened(parsed,False));size=before[0]
        if not 1<=size<=maximum:raise WorkspaceError('TOO_LARGE')
        raw=bytearray()
        while len(raw)<size:
            data=native.read(handle,min(32768,size-len(raw)))
            if not data:raise WorkspaceError('CHANGED')
            raw.extend(data)
        if native.read(handle,1) or info(handle,parsed,False)!=before:raise WorkspaceError('CHANGED')
        return bytes(raw)
