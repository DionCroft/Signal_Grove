"""POSIX owner-protected policy observations using anchored native descriptors.

No chmod, cache, fallback reader, policy creation, subprocess or network access.
Unsupported native primitives refuse; permission/identity claims are not inputs.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import stat
from .coordinator import ReadRefused
from .operator_policy import MAX_POLICY, PolicyDocument, build_operator_service

_NAME = re.compile(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,79}')


def _stamp(value):
    return tuple(getattr(value, k) for k in ('st_dev','st_ino','st_uid','st_gid',
                 'st_mode','st_nlink','st_size','st_mtime_ns','st_ctime_ns'))


def _directory(value, uid):
    if (not stat.S_ISDIR(value.st_mode) or value.st_uid not in (0, uid)
            or value.st_mode & 0o022):
        raise ReadRefused('UNAVAILABLE')


def _file(value, uid):
    if (not stat.S_ISREG(value.st_mode) or value.st_uid not in (0, uid)
            or value.st_mode & 0o077 or value.st_nlink != 1 or value.st_ino <= 0
            or not 1 <= value.st_size <= MAX_POLICY):
        raise ReadRefused('UNAVAILABLE')


def read_owner_document(directory, name):
    """Read one fixed operator file; never follow a symlink or trust mode bits on Windows."""
    if (os.name != 'posix' or not all(hasattr(os, k) for k in ('O_NOFOLLOW','O_DIRECTORY','O_CLOEXEC','O_NONBLOCK'))
            or os.open not in os.supports_dir_fd or os.stat not in os.supports_dir_fd
            or os.stat not in os.supports_follow_symlinks):
        raise ReadRefused('UNSUPPORTED')
    parent = descriptor = None
    try:
        root = Path(directory)
        if (not root.is_absolute() or root.anchor != '/' or '..' in root.parts
                or len(str(root)) > 4096 or type(name) is not str or not _NAME.fullmatch(name)):
            raise ReadRefused('UNAVAILABLE')
        uid = os.geteuid()
        flags = os.O_RDONLY | os.O_DIRECTORY | os.O_CLOEXEC | os.O_NOFOLLOW
        parent = os.open('/', flags)
        _directory(os.fstat(parent), uid)
        for part in root.parts[1:]:
            child = os.open(part, flags, dir_fd=parent)
            try: _directory(os.fstat(child), uid)
            except BaseException:
                os.close(child)
                raise
            os.close(parent)
            parent = child
        descriptor = os.open(name, os.O_RDONLY | os.O_CLOEXEC | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=parent)
        before = os.fstat(descriptor)
        _file(before, uid)
        chunks = []; total = 0
        while True:
            piece = os.read(descriptor, min(8192, MAX_POLICY + 1 - total))
            if not piece: break
            total += len(piece)
            if total > MAX_POLICY: raise ReadRefused('UNAVAILABLE')
            chunks.append(piece)
        after = os.fstat(descriptor)
        named = os.stat(name, dir_fd=parent, follow_symlinks=False)
        if _stamp(before) != _stamp(after) or _stamp(after) != _stamp(named) or total != before.st_size:
            raise ReadRefused('UNAVAILABLE')
        data = b''.join(chunks)
        version = hashlib.sha256(json.dumps(_stamp(after),separators=(',',':')).encode('ascii')
                                 + b'\0' + data).hexdigest()
        return PolicyDocument(data, version)
    except ReadRefused: raise
    except (OSError, ValueError, TypeError):
        raise ReadRefused('UNAVAILABLE') from None
    finally:
        if descriptor is not None: os.close(descriptor)
        if parent is not None: os.close(parent)


def policy_file_service(directory, name, *, authority, routes):
    """Explicit bootstrap only. The supplied operator routes never come from policy JSON."""
    return build_operator_service(lambda: read_owner_document(directory, name),
                                  authority=authority, routes=routes)
