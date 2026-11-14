"""Single-socket HTTP adapter for the standalone retirement guard.

This is local administration on the owner: an already connected IPv4 loopback
socket and a supported provider's native-app access token are supplied in memory.
It neither connects, discovers credentials, signs in, refreshes, follows redirects
nor retries. The caller must prove the server-side socket's exact native Owner
from the kernel in observe_socket_owner(socket), and owns the durable journal.
No remote HTTP/TLS URL is accepted; remote clients keep their normal verified TLS.
"""
from __future__ import annotations

import http.client
import json
import math
import re
import socket
import threading
import time

from retirement_guard import Owner, Refused, Unknown


class _HeaderBudget:
    def __init__(self, raw):
        self.raw = raw
        self.remaining = 16384

    def readline(self, limit=-1):
        limit = min(limit if limit >= 0 else self.remaining + 1, self.remaining + 1)
        line = self.raw.readline(limit)
        self.remaining -= len(line)
        if self.remaining < 0:
            raise ValueError('Response headers exceed the fixed bound')
        return line

    def close(self):
        self.raw.close()


class _Response(http.client.HTTPResponse):
    def begin(self):
        raw = self.fp
        self.fp = _HeaderBudget(raw)
        try:
            super().begin()
        finally:
            self.fp = raw


class _ExistingConnection(http.client.HTTPConnection):
    response_class = _Response

    def connect(self):
        raise Unknown('The pinned connection cannot reconnect')


def _unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('Duplicate response field')
        result[key] = value
    return result


class PinnedRetirementHttp:
    """Own one connected socket after construction; failure permanently closes it.

    The observer must establish this socket's reverse TCP tuple/inode in the
    expected process, including PID/start ticks/boot identity. Returning an HTTP
    PID or merely finding a listener on the same port is insufficient. Portable
    transport tests supply a synthetic observer; they do not prove native pinning.
    """

    def __init__(self, owner, connected_socket, provider_access_token,
                 observe_socket_owner, *, timeout=5.0):
        if (type(owner) is not Owner or type(connected_socket) is not socket.socket
                or not callable(observe_socket_owner)
                or type(timeout) not in (int, float) or not math.isfinite(timeout)
                or not 0 < timeout <= 10
                or type(provider_access_token) is not str
                or not re.fullmatch(r'[A-Za-z0-9._~+/-]{1,4096}=*', provider_access_token)
                or len(provider_access_token) > 4096):
            raise ValueError('An explicit native owner, connected socket and provider session are required')
        if (connected_socket.family != socket.AF_INET
                or connected_socket.type != socket.SOCK_STREAM
                or connected_socket.getpeername()[0] != '127.0.0.1'
                or connected_socket.getsockname()[0] != '127.0.0.1'):
            raise ValueError('Only an already connected owner-local IPv4 loopback socket is accepted')
        self._socket = connected_socket
        self._owner = owner
        self._observe = observe_socket_owner
        self._token = provider_access_token
        self._timeout = float(timeout)
        self._lock = threading.Lock()
        self._usable = True
        self._connection = _ExistingConnection('127.0.0.1', connected_socket.getpeername()[1], timeout=timeout)
        self._connection.sock = connected_socket
        try:
            self._verify_owner()
        except BaseException as exc:
            self.close()
            if not isinstance(exc, Exception):
                raise
            raise Unknown('The initial native socket owner is unproven') from None

    def __repr__(self):
        return '<single-socket retirement HTTP adapter>'

    def close(self):
        self._usable = False
        self._token = None
        try:
            self._socket.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self._connection.close()

    def _verify_owner(self):
        if (not self._usable or self._connection.sock is not self._socket
                or self._socket.fileno() < 0):
            raise Unknown('The original pinned socket is no longer usable')
        observed = self._observe(self._socket)
        if type(observed) is not Owner or observed != self._owner:
            raise Unknown('The native socket owner is unproven')

    @staticmethod
    def _body(method, path, body):
        if type(method) is not str or type(path) is not str:
            raise Refused('Only the fixed retirement protocol is accepted')
        if method == 'GET' and path in {'/api/host/identity', '/api/health/idle'} and body is None:
            return None
        if method == 'POST' and path == '/api/health/retirement' and type(body) is dict:
            action = body.get('action')
            if action == 'prepare' and set(body) == {'action'}:
                return json.dumps(body).encode('ascii')
            if (type(action) is str and action in {'commit', 'cancel'} and set(body) == {'action', 'token'}
                    and type(body['token']) is str
                    and re.fullmatch(r'[A-Za-z0-9_-]{32,256}', body['token'])):
                return json.dumps(body).encode('ascii')
        raise Refused('Only the fixed retirement protocol is accepted')

    def __call__(self, method, path, body):
        payload = self._body(method, path, body)
        if not self._lock.acquire(blocking=False):
            raise Refused('A request already owns this connection')
        timer = None
        response = None
        try:
            deadline = time.monotonic() + self._timeout
            # Interrupt an infinite trickle as well as a single blocked read.
            # Per-read socket timeouts alone do not enforce an absolute bound.
            timer = threading.Timer(self._timeout, self.close)
            timer.daemon = True
            timer.start()
            self._verify_owner()
            self._socket.settimeout(self._timeout)
            self._connection.request(method, path, body=payload, headers={
                'Authorization': 'Bearer ' + self._token,
                'Accept': 'application/json', 'Content-Type': 'application/json',
            })
            response = self._connection.getresponse()
            lengths = response.headers.get_all('Content-Length', [])
            if (response.version != 11 or response.will_close or response.chunked
                    or response.headers.get_all('Transfer-Encoding', [])
                    or len(lengths) != 1 or not re.fullmatch(r'[0-9]{1,6}', lengths[0])
                    or not 0 < int(lengths[0]) <= 65536
                    or response.headers.get_all('Content-Encoding', [])
                    or len(response.headers.get_all('Content-Type', [])) != 1
                    or response.headers['Content-Type'].split(';', 1)[0].strip().lower() != 'application/json'):
                raise ValueError('Response framing is not bounded persistent JSON')
            raw = response.read(int(lengths[0]) + 1)
            if len(raw) != int(lengths[0]):
                raise ValueError('Response body is incomplete or oversized')
            data = json.loads(raw.decode('utf8'), object_pairs_hook=_unique_object,
                              parse_constant=lambda _: (_ for _ in ()).throw(ValueError('Nonfinite JSON')))
            if type(data) is not dict:
                raise ValueError('A JSON response object is required')
            self._verify_owner()
            if time.monotonic() >= deadline:
                raise Unknown('The absolute request deadline elapsed')
            status = response.status
            if status != 200:
                self.close()
            return status, data
        except BaseException as exc:
            self.close()
            if not isinstance(exc, Exception):
                raise
            raise Unknown('Pinned HTTP outcome is unproven; inspect without reconnect or replay') from None
        finally:
            if timer is not None:
                timer.cancel()
                timer.join()
            if response is not None:
                response.close()
            self._lock.release()
