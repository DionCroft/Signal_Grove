"""Guarded source adapter for an explicitly reviewed Hermes API runtime.

Default is preview only. Back up the live control plane before --apply; restart
only its existing idle gateway. No configuration, inference or service changes here.
"""
import argparse
import ast
import hashlib
import os
import stat
from pathlib import Path


def patch_source(source):
    edits = [
        ('    "session_resources": True, "model_options": True, "session_chat": True,',
         '    "model_runtime_status_v1": True,\n    "session_resources": True, "model_options": True, "session_chat": True,'),
        ('    ("models", ("GET", "/v1/models")), ("model_options", ("GET", "/api/model/options")),',
         '    ("model_runtime_status", ("GET", "/api/model/runtime-status", "model_runtime_status_v1")),\n    ("models", ("GET", "/v1/models")), ("model_options", ("GET", "/api/model/options")),'),
        ('            ("GET", "/api/model/options", self._handle_model_options),',
         '            ("GET", "/api/model/runtime-status", self._handle_model_runtime_status),\n            ("GET", "/api/model/options", self._handle_model_options),'),
        ('    @_require_auth\n    async def _handle_capabilities(self, request: web.Request) -> web.Response:',
         '    @_require_auth\n    async def _handle_model_runtime_status(self, request: web.Request) -> web.Response:\n'
         '        if not self._expected_api_key():\n'
         '            return self._auth_failed_response()\n'
         '        if request.query:\n'
         '            return _error_response("Runtime status accepts no parameters.", 400, code="invalid_runtime_status_query")\n'
         '        from gateway.platforms.api_server_model_status import read_runtime_status\n'
         '        return web.json_response(await read_runtime_status())\n\n'
         '    @_require_auth\n    async def _handle_capabilities(self, request: web.Request) -> web.Response:')]
    for before, after in edits:
        if source.count(before) != 1:
            raise ValueError('Runtime source differs from the reviewed adapter anchors; review first.')
        source = source.replace(before, after)
    ast.parse(source)
    return source


def prepare(api, expected):
    raw = api.read_bytes()
    if hashlib.sha256(raw).hexdigest() != expected:
        raise ValueError('Runtime source hash changed; no files written.')
    newline = '\r\n' if b'\r\n' in raw else '\n'
    if newline == '\r\n' and raw.count(b'\r\n') != raw.count(b'\n'):
        raise ValueError('Mixed source newlines require explicit review.')
    source = raw.decode('utf-8').replace('\r\n', '\n')
    return patch_source(source).replace('\n', newline).encode('utf-8')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--runtime-root', required=True, type=Path)
    parser.add_argument('--expected-api-sha256', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    api = args.runtime_root / 'gateway/platforms/api_server.py'
    target = api.with_name('api_server_model_status.py')
    updated = prepare(api, args.expected_api_sha256)
    module = Path(__file__).with_name(target.name).read_bytes()
    ast.parse(module)
    if target.exists():
        raise ValueError('Preserve the existing runtime observer; explicit review required.')
    if args.apply:
        # Module first: the old in-memory gateway cannot call a missing module during deployment.
        with target.open('xb') as output:
            output.write(module)
            output.flush(); os.fsync(output.fileno())
        temporary = api.with_name('.api_server.signalgrove.tmp')
        created = False
        try:
            with temporary.open('xb') as output:
                created = True
                output.write(updated)
                output.flush(); os.fsync(output.fileno())
            temporary.chmod(stat.S_IMODE(api.stat().st_mode))
            if hashlib.sha256(api.read_bytes()).hexdigest() != args.expected_api_sha256:
                raise ValueError('Runtime changed during staging; preserve and review the observer.')
            os.replace(temporary, api)
        finally:
            if created and temporary.exists():
                temporary.unlink()
    print('Applied' if args.apply else 'Preview', 'api_sha256=' + hashlib.sha256(updated).hexdigest(),
          'observer_sha256=' + hashlib.sha256(module).hexdigest())


if __name__ == '__main__':
    main()
