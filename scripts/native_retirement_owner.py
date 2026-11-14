"""Read the Linux owner of an existing retirement TCP connection; never connect.

The caller selects a manager-owned process incarnation separately. A positive
sample ties this exact socket's reverse tuple to an established kernel inode and
an FD in that process, in this network namespace and effective user. Samples are
not atomic or a retirement permit. Keeping the original socket and refusing
reconnection prevents a replaced listener from silently becoming its successor.

Kernel formats: docs.kernel.org/networking/proc_net_tcp.html and the Linux
proc_pid_stat(5)/proc_pid_fd(5) manual pages. No injectable proc root, credential
read, process signal, subprocess, write or HTTP request is provided.
"""
from __future__ import annotations

import os
from pathlib import Path
import re
import socket
import sys
import time

from retirement_guard import Owner, Refused


def _incarnation(stat_text, boot_text, pid):
    if type(pid) is not int or pid <= 1 or type(stat_text) is not str or len(stat_text) > 8192:
        raise ValueError('Invalid native process record')
    head, separator, tail = stat_text.rpartition(')')
    fields = tail.split()
    if (not separator or not head.startswith(str(pid) + ' (') or len(fields) < 20
            or fields[0] not in {'R', 'S', 'D', 'T', 't', 'W', 'K', 'P', 'I'}
            or not re.fullmatch(r'[1-9][0-9]*', fields[19])):
        raise ValueError('Native process incarnation is unavailable')
    if type(boot_text) is not str or len(boot_text) > 64:
        raise ValueError('Invalid native boot identity')
    return Owner(pid, int(fields[19]), boot_text.strip())


def _endpoint(address):
    ip, port = address
    if ip != '127.0.0.1' or type(port) is not int or not 0 < port <= 65535:
        raise ValueError('Only an exact IPv4 loopback endpoint is accepted')
    return f'{int.from_bytes(socket.inet_aton(ip), sys.byteorder):08X}:{port:04X}'


def _reverse_inode(table, server, client, uid):
    if type(table) is not str or len(table) > 4 * 1024 * 1024 or type(uid) is not int or uid < 0:
        raise ValueError('Invalid bounded native TCP table')
    local, remote = _endpoint(server), _endpoint(client)
    found = []
    for line in table.splitlines()[1:]:
        row = line.split()
        if len(row) >= 10 and row[1:4] == [local, remote, '01']:
            if (not re.fullmatch(r'[0-9]{1,10}', row[7])
                    or not re.fullmatch(r'[1-9][0-9]{0,19}', row[9])):
                raise ValueError('Unaccepted or invalid server socket record')
            if int(row[7]) == uid:
                found.append(int(row[9]))
    return found[0] if len(found) == 1 else None


def _check_time(deadline):
    if time.monotonic() >= deadline:
        raise TimeoutError('Native observation bound elapsed')


def _read(path, maximum, deadline):
    _check_time(deadline)
    with path.open('rb') as stream:
        data = stream.read(maximum + 1)
    _check_time(deadline)
    if len(data) > maximum:
        raise ValueError('Native record exceeds its fixed bound')
    return data.decode('ascii')


def read_process_owner(pid):
    """One bounded native identity sample, or None; not manager authorization."""
    if sys.platform != 'linux' or type(pid) is not int or pid <= 1:
        return None
    deadline = time.monotonic() + 1.0
    try:
        return _incarnation(_read(Path('/proc') / str(pid) / 'stat', 8192, deadline),
                            _read(Path('/proc/sys/kernel/random/boot_id'), 64, deadline), pid)
    except (OSError, ValueError, TimeoutError):
        return None


class LinuxSocketOwner:
    """Finite read-only observation of one already selected native owner.

    It does not discover a new owner, accept an HTTP identity as authority, wait
    for a replacement or prove exclusive FD ownership. Unaccepted connections,
    inaccessible procfs, wrong user/namespace and every inconclusive read return
    None. The caller must establish a positive sample before sending credentials.
    """

    def __init__(self, expected):
        if sys.platform != 'linux' or type(expected) is not Owner:
            raise Refused('An explicit native Linux owner is required')
        self._expected = expected
        self._uid = os.geteuid()

    def _identity(self, deadline):
        root = Path('/proc') / str(self._expected.pid)
        observed = _incarnation(_read(root / 'stat', 8192, deadline),
                                _read(Path('/proc/sys/kernel/random/boot_id'), 64, deadline),
                                self._expected.pid)
        if observed != self._expected:
            return None
        status = _read(root / 'status', 65536, deadline)
        ids = [line.split()[1:] for line in status.splitlines() if line.startswith('Uid:')]
        if len(ids) != 1 or len(ids[0]) != 4 or any(not x.isdecimal() or int(x) != self._uid for x in ids[0]):
            return None
        if os.readlink(root / 'ns/net') != os.readlink('/proc/self/ns/net'):
            return None
        _check_time(deadline)
        return observed

    def _has_fd(self, inode, deadline):
        with os.scandir(Path('/proc') / str(self._expected.pid) / 'fd') as descriptors:
            for index, entry in enumerate(descriptors):
                _check_time(deadline)
                if index >= 4096:
                    return False
                try:
                    if os.readlink(entry.path) == f'socket:[{inode}]':
                        return True
                except FileNotFoundError:
                    # A different descriptor may close during this non-atomic
                    # sample. A positive result still needs this exact inode.
                    continue
        return False

    def __call__(self, connected_socket):
        deadline = time.monotonic() + 1.0
        try:
            if (type(connected_socket) is not socket.socket
                    or connected_socket.family != socket.AF_INET
                    or connected_socket.type != socket.SOCK_STREAM
                    or self._identity(deadline) != self._expected):
                return None
            server, client = connected_socket.getpeername(), connected_socket.getsockname()
            inode = _reverse_inode(_read(Path('/proc/net/tcp'), 4 * 1024 * 1024, deadline),
                                    server, client, self._uid)
            if inode is None or not self._has_fd(inode, deadline):
                return None
            if (self._identity(deadline) != self._expected
                    or connected_socket.getpeername() != server or connected_socket.getsockname() != client
                    or _reverse_inode(_read(Path('/proc/net/tcp'), 4 * 1024 * 1024, deadline),
                                      server, client, self._uid) != inode):
                return None
            _check_time(deadline)
            return self._expected
        except (OSError, ValueError, TimeoutError):
            return None
