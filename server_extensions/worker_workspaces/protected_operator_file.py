"""Read the fixed local write policy or key only from an owner-protected native file."""
from contextlib import ExitStack
import ctypes
from ctypes import wintypes as w

from .workspace_provider import WindowsFiles, WorkspaceError, _normal, _root


def _private_acl(handle, owner_sid):
    """Verify the owner and protected DACL on the exact open file."""
    api = ctypes.WinDLL("advapi32", use_last_error=True)
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    ptr = ctypes.c_void_p
    ref = ctypes.POINTER(ptr)
    api.GetSecurityInfo.argtypes = [w.HANDLE, ctypes.c_int, w.DWORD, ref, ref, ref, ref, ref]
    api.GetSecurityInfo.restype = w.DWORD
    api.GetSecurityDescriptorControl.argtypes = [ptr, ctypes.POINTER(w.WORD), ctypes.POINTER(w.DWORD)]
    api.GetSecurityDescriptorControl.restype = w.BOOL
    api.GetAclInformation.argtypes = [ptr, ptr, w.DWORD, ctypes.c_int]
    api.GetAclInformation.restype = w.BOOL
    api.GetAce.argtypes = [ptr, w.DWORD, ref]
    api.GetAce.restype = w.BOOL
    api.ConvertSidToStringSidW.argtypes = [ptr, ref]
    api.ConvertSidToStringSidW.restype = w.BOOL
    kernel.LocalFree.argtypes = [ptr]
    kernel.LocalFree.restype = ptr

    def sid(value):
        name = ptr()
        if not value or not api.ConvertSidToStringSidW(value, ctypes.byref(name)):
            raise WorkspaceError("DENIED")
        try:
            return ctypes.wstring_at(name)
        finally:
            kernel.LocalFree(name)

    owner, acl, descriptor = ptr(), ptr(), ptr()
    trusted = {owner_sid, "S-1-5-18", "S-1-5-32-544"}
    if api.GetSecurityInfo(handle, 1, 5, ctypes.byref(owner), None, ctypes.byref(acl),
                           None, ctypes.byref(descriptor)):
        raise WorkspaceError("DENIED")
    try:
        control, revision = w.WORD(), w.DWORD()
        info = (w.DWORD * 3)()
        if (not acl or sid(owner) not in trusted
                or not api.GetSecurityDescriptorControl(descriptor, ctypes.byref(control), ctypes.byref(revision))
                or not control.value & 0x1000
                or not api.GetAclInformation(acl, ctypes.byref(info), ctypes.sizeof(info), 2)
                or not 1 <= info[0] <= 64):
            raise WorkspaceError("DENIED")
        # Only basic allow/deny ACEs are understood. Unknown grants fail closed.
        for index in range(info[0]):
            entry = ptr()
            if not api.GetAce(acl, index, ctypes.byref(entry)):
                raise WorkspaceError("DENIED")
            kind = ctypes.c_ubyte.from_address(entry.value).value
            if kind not in (0, 1) or (kind == 0 and sid(entry.value + 8) not in trusted):
                raise WorkspaceError("DENIED")
    finally:
        kernel.LocalFree(descriptor)


def read_protected_file(path, *, owner_sid, maximum):
    target = _root(path)
    if target.drive.startswith("\\\\"):
        raise WorkspaceError("INVALID")
    if type(maximum) is not int or not 1 <= maximum <= 32768:
        raise WorkspaceError("INVALID")
    native = WindowsFiles()
    with ExitStack() as handles:
        current = type(target)(target.anchor)
        for part in ("", *target.parts[1:-1]):
            current /= part
            _, meta = handles.enter_context(native.opened(current, directory=True))
            if _normal(meta.path) != _normal(str(current)):
                raise WorkspaceError("CHANGED")
        handle, before = handles.enter_context(native.opened(target, directory=False, contents=True))
        if _normal(before.path) != _normal(path):
            raise WorkspaceError("CHANGED")
        if not 1 <= before.size <= maximum:
            raise WorkspaceError("TOO_LARGE")
        _private_acl(handle, owner_sid)
        data = native.read(handle, before.size)
        if len(data) != before.size or native.read(handle, 1) or native.info(handle).fingerprint() != before.fingerprint():
            raise WorkspaceError("CHANGED")
        _private_acl(handle, owner_sid)
        return data
