"""Pinned real request-registry and extracted dispatcher checks, without a gateway startup."""
from pathlib import Path
import __future__
import argparse
import ast
import contextlib
import hashlib
import json
import logging
import os
import sys
import threading
import time
import traceback
import types
import uuid

if not __debug__:
    raise SystemExit('Run without -O: this fixture requires enabled assertions.')
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--source-root', type=Path, required=True)
parser.add_argument('--work-root', type=Path, required=True)
parser.add_argument('--revision', choices=('aa', 'e826'), default='aa')
args = parser.parse_args()
source, root = args.source_root.resolve(), args.work_root.resolve()
assert source.is_dir() and not root.exists(), 'Existing exact source and a fresh output directory required'
assert not root.is_relative_to(source) and not source.is_relative_to(root)
if os.name == 'nt':
    assert source.drive.upper() == root.drive.upper() == 'E:', 'Temporary fixture material must stay on E:'
revisions = {
    'aa': ('aa389eba3df3c5839cf00a8d5941a83bbe3435cb', 'f2428742dcfe1c75fa1b47be2ec6739c3b38382a'),
    'e826': ('e826a14a467fa74a718b0010487a8eb12421f879', '7d089f8ad2701f8b220ffc061d2872cf6ecf13ef'),
}
pin, registry_blob = revisions[args.revision]
paths = {
    'requests': (source / 'tui_gateway/server_requests.py', registry_blob),
    'ws': (source / 'tui_gateway/ws.py', '833751e0df038b0c551814ee3bfea95a27d0dabf'),
}
verified = {}
for name, (path, blob) in paths.items():
    assert not path.is_symlink() and path.resolve().is_relative_to(source)
    raw = path.read_bytes()
    assert hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == blob, 'Wrong pinned source bytes'
    verified[name] = raw
root.mkdir(parents=True, exist_ok=False)
system = {k: os.environ[k] for k in ('SystemRoot', 'WINDIR') if k in os.environ}
os.environ.clear()
os.environ.update(system)
for key, sub in {'TEMP':'temp', 'TMP':'temp', 'HOME':'home', 'USERPROFILE':'home', 'APPDATA':'appdata',
                 'LOCALAPPDATA':'localappdata', 'HERMES_HOME':'home/default', 'HERMES_RUNTIME_DIR':'runtime'}.items():
    directory = root / sub
    directory.mkdir(parents=True, exist_ok=True)
    os.environ[key] = str(directory)
sys.dont_write_bytecode = True
denied, results = [], []
read_roots = [root, source, Path(sys.base_prefix).resolve(), Path(__file__).resolve()]

def audit(event, values):
    if event in ('socket.connect', 'socket.getaddrinfo', 'subprocess.Popen', 'os.system', 'os.kill',
                 'os.killpg', 'os.exec', 'os.posix_spawn'):
        denied.append(event)
        raise PermissionError('No network/process operations permitted')
    if event == 'open' and isinstance(values[0], (str, bytes, os.PathLike)):
        path = Path(os.fsdecode(values[0])).resolve()
        mode, flags = values[1] or '', values[2] or 0
        writing = any(v in mode for v in 'wax+') or flags & (os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND)
        if not any(path.is_relative_to(p) for p in ([root] if writing else read_roots)):
            denied.append('write' if writing else 'read')
            raise PermissionError('Fixture file boundary')
    if event in ('os.mkdir', 'os.remove', 'os.rmdir', 'os.rename', 'os.symlink'):
        affected = values[:2] if event in ('os.rename', 'os.symlink') else values[:1]
        if any(not Path(os.fsdecode(v)).resolve().is_relative_to(root) for v in affected):
            denied.append(event)
            raise PermissionError('Fixture mutation boundary')
sys.addaudithook(audit)

for name in ('tools', 'tui_gateway', 'tui_gateway.contracts', 'hermes_cli'):
    package = types.ModuleType(name)
    package.__path__ = []
    sys.modules[name] = package
clarify = types.ModuleType('tools.clarify_batch')
def unexpected_batch(*args, **kwargs):
    raise AssertionError('Batch clarification is outside this probe')
clarify.settle_batch = unexpected_batch
sys.modules[clarify.__name__] = clarify
contracts = types.ModuleType('tui_gateway.contracts.registry')
contracts.SERVER_REQUESTS = {'preview.act': object(), 'preview.read': object()}
def validate_params(contract, params):
    assert contract in contracts.SERVER_REQUESTS.values() and isinstance(params.get('session_id'), str)
    return params, None
contracts.validate_params = validate_params
sys.modules[contracts.__name__] = contracts
sys.modules['tui_gateway.contracts'].registry = contracts
requests = types.ModuleType('tui_gateway.server_requests')
sys.modules[requests.__name__] = requests
exec(compile(verified['requests'], str(paths['requests'][0]), 'exec', dont_inherit=True), requests.__dict__)

wire, events, broadcast, context_calls, authority_calls, legacy_calls = [], [], [], [], [], []
@contextlib.contextmanager
def serving_local_desktop(value):
    context_calls.append(('enter', value))
    try:
        yield
    finally:
        context_calls.append(('exit', value))
identity = types.ModuleType('hermes_cli.process_identity')
identity.serving_local_desktop = serving_local_desktop
sys.modules[identity.__name__] = identity
class Transport:
    desktop_local = False
    def __init__(self):
        self.callback = None
    def write(self, frame):
        wire.append((self, frame))
        if self.callback:
            self.callback(frame)
class Authority:
    actor = object()
    async def dispatch(self, frame):
        authority_calls.append(frame)
        return {'id': frame.get('id'), 'result': {'authority': True}}
def legacy_dispatch(frame, transport):
    legacy_calls.append((frame, transport))
    return {'result': {'legacy': True}}
async def direct_thread(function, *values):
    return function(*values)
scope = {'__name__': 'controlled_ws_dispatch', 'asyncio': types.SimpleNamespace(to_thread=direct_thread),
         'server': types.SimpleNamespace(dispatch=legacy_dispatch, _methods={}),
         'legacy_fallback_allowed': lambda actor, method: False}
tree = ast.parse(verified['ws'])
functions = [n for n in tree.body if isinstance(n, (ast.FunctionDef, ast.AsyncFunctionDef)) and n.name in ('_dispatch_request', '_is_unknown_method')]
assert len(functions) == 2
exec(compile(ast.fix_missing_locations(ast.Module(body=functions, type_ignores=[])), '<pinned-dispatch-functions>', 'exec', flags=__future__.annotations.compiler_flag), scope)
def complete(coroutine):
    try:
        coroutine.send(None)
    except StopIteration as done:
        return done.value
    finally:
        coroutine.close()
    raise AssertionError('Unexpected suspension in the controlled dispatcher')
def seed():
    with requests._lock:
        requests._open.clear()
        requests._answering_clients.clear()
    for values in (wire, events, broadcast, context_calls, authority_calls, legacy_calls):
        values.clear()
    requests.bind_sinks(broadcast.append, lambda *values: events.append(values), lambda sid: True, lambda sid: [])
    return Transport(), Transport()
def register(transport, *, vault=True):
    params = {'action':'vault', 'vault':{'operation':'evaluate', 'expression':'SYNTHETIC-PAGE-EXPRESSION'}} if vault else {}
    req = requests.ServerRequest('' if transport else 'fixture-session', 'preview.act' if vault else 'preview.read', params)
    req.transport = transport
    requests._register(req)
    return req
def answer(req, result=None):
    return {'jsonrpc':'2.0', 'id':req.id, 'result': result if result is not None else {'value':'fixture-answer'}}

def test_only_origin_socket_can_settle():
    origin, other = seed(); req = register(origin)
    for peer in (other, None):
        assert not requests.resolve_pinned_response(answer(req), peer)
        assert requests._open[req.id] is req and not req.event.is_set()
    assert requests.resolve_pinned_response(answer(req), origin)
    assert req.event.is_set() and req.answered and req.result == {'value':'fixture-answer'}
    assert req.id not in requests._open and not requests.resolve_pinned_response(answer(req), origin)
    assert not broadcast and len(wire) == 1 and wire[0][0] is origin

def test_pinned_errors_are_final_refusals():
    for code in (requests.NOT_SHOWN_CODE, -32603):
        origin, _ = seed(); req = register(origin)
        assert requests.resolve_pinned_response({'jsonrpc':'2.0','id':req.id,'error':{'code':code}}, origin)
        assert req.event.is_set() and not req.answered and req.result is None and req.id not in requests._open

def test_unpinned_and_malformed_frames_are_not_intercepted():
    origin, _ = seed(); req = register(None, vault=False)
    assert not requests.resolve_pinned_response(answer(req), origin)
    for frame in (None, [], {}, {'id':1,'result':{}}, {'id':req.id}, {'id':req.id,'method':'preview.act','result':{}}):
        assert not requests.resolve_pinned_response(frame, origin)
    assert requests._open[req.id] is req
    assert requests.resolve_response(answer(req), origin) and req.answered

def test_vault_payload_is_live_only_and_not_broadcast():
    origin, _ = seed(); req = register(origin)
    assert wire[0][1]['params']['vault']['expression'] == 'SYNTHETIC-PAGE-EXPRESSION'
    assert req.params == {} and requests.open_requests('') == []
    assert 'SYNTHETIC-PAGE-EXPRESSION' not in json.dumps(req.snapshot()) and not broadcast
    assert requests.cancel('', 'fixture-cancel', method='preview.act') == 1
    assert not requests.resolve_pinned_response(answer(req), origin) and req.id not in requests._open
    assert 'SYNTHETIC-PAGE-EXPRESSION' not in json.dumps(events)

def test_ordinary_reconnect_snapshot_is_preserved():
    seed(); req = register(None, vault=False)
    assert requests.open_requests('fixture-session') == [req.snapshot()]
    assert requests.resolve_response(answer(req)) and requests.open_requests('fixture-session') == []

def test_unanswerable_ordinary_request_does_not_send():
    seed(); requests._answerable = lambda sid: False
    assert requests.send('preview.read','fixture-session',{},timeout=0) is None
    assert not wire and not broadcast and not requests._open

def test_explicit_pin_can_answer_without_session_subscription():
    origin, _ = seed(); requests._answerable = lambda sid: False
    origin.callback = lambda frame: requests.resolve_pinned_response({'id':frame['id'],'result':{'value':'explicit'}}, origin)
    result = requests.send('preview.act','',{'action':'vault'},timeout=0,transport=origin)
    assert result == {'value':'explicit'} and len(wire) == 1 and not broadcast and not requests._open

def test_timeout_and_late_response_do_not_replay():
    origin, _ = seed()
    assert requests.send('preview.act','',{'action':'vault'},timeout=0,transport=origin) is None
    assert len(wire) == 1 and len(events) == 1 and not requests._open
    assert not requests.resolve_pinned_response({'id':wire[0][1]['id'],'result':{}},origin)
    assert len(wire) == 1 and not broadcast

def test_pinned_dispatch_bypasses_authority_only_for_its_origin():
    origin, _ = seed(); req = register(origin)
    assert complete(scope['_dispatch_request'](Authority(),answer(req),None,origin)) is None
    assert req.answered and not authority_calls and not legacy_calls and not context_calls

def test_foreign_and_unpinned_dispatch_stay_with_authority():
    origin, other = seed(); req = register(origin)
    frame = answer(req)
    result = complete(scope['_dispatch_request'](Authority(),frame,None,other))
    assert result['result']['authority'] and authority_calls == [frame] and not req.answered
    assert context_calls == [('enter',False),('exit',False)] and not legacy_calls
    frame = {'id':'unknown','result':{}}
    assert complete(scope['_dispatch_request'](Authority(),frame,None,origin))['result']['authority']
    assert authority_calls[-1] is frame and not req.answered

def test_no_authority_keeps_legacy_dispatch():
    origin, _ = seed(); req = register(origin); frame = answer(req)
    result = complete(scope['_dispatch_request'](None,frame,None,origin))
    assert result['result']['legacy'] and legacy_calls == [(frame,origin)] and not authority_calls and not req.answered

class ErrorCapture(logging.Handler):
    def __init__(self):
        super().__init__()
        self.records = []
    def emit(self, record):
        unlocked = requests._lock.acquire(blocking=False)
        assert unlocked, 'Error logging must be outside the request lock'
        requests._lock.release()
        self.records.append((record.levelno, record.getMessage()))

@contextlib.contextmanager
def capture_errors():
    handler = ErrorCapture()
    old_level, old_propagation = requests.logger.level, requests.logger.propagate
    requests.logger.addHandler(handler)
    requests.logger.setLevel(logging.DEBUG)
    requests.logger.propagate = False
    try:
        yield handler.records
    finally:
        requests.logger.removeHandler(handler)
        requests.logger.setLevel(old_level)
        requests.logger.propagate = old_propagation
        handler.close()

def test_error_logging_is_outside_lock_and_excludes_error_data():
    origin, _ = seed(); req = register(origin); callbacks = []
    req.on_result = callbacks.append
    frame = {'id':req.id, 'error':{'code':-32603, 'message':'fixture-refusal', 'data':{'private':'SYNTHETIC-DATA-MARKER'}}}
    with capture_errors() as records:
        assert requests.resolve_pinned_response(frame, origin)
    assert len(records) == 1 and records[0][0] == logging.WARNING
    assert '-32603' in records[0][1] and 'fixture-refusal' in records[0][1]
    assert 'SYNTHETIC-DATA-MARKER' not in records[0][1]
    assert callbacks == [None] and req.event.is_set() and not req.answered and req.result is None
    assert req.id not in requests._open

def test_malformed_errors_refuse_without_logging_the_payload():
    for error in (None, ['SYNTHETIC-DATA-MARKER'], 'SYNTHETIC-DATA-MARKER', {'data':'SYNTHETIC-DATA-MARKER'}):
        origin, _ = seed(); req = register(origin)
        with capture_errors() as records:
            assert requests.resolve_pinned_response({'id':req.id,'error':error}, origin)
        assert req.event.is_set() and not req.answered and req.result is None and req.id not in requests._open
        assert len(records) == 1 and records[0][0] == logging.WARNING
        assert 'SYNTHETIC-DATA-MARKER' not in records[0][1]

tests = [test_only_origin_socket_can_settle, test_pinned_errors_are_final_refusals,
         test_unpinned_and_malformed_frames_are_not_intercepted, test_vault_payload_is_live_only_and_not_broadcast,
         test_ordinary_reconnect_snapshot_is_preserved, test_unanswerable_ordinary_request_does_not_send,
         test_explicit_pin_can_answer_without_session_subscription, test_timeout_and_late_response_do_not_replay,
         test_pinned_dispatch_bypasses_authority_only_for_its_origin, test_foreign_and_unpinned_dispatch_stay_with_authority,
         test_no_authority_keeps_legacy_dispatch]
if args.revision == 'e826':
    tests.extend([test_error_logging_is_outside_lock_and_excludes_error_data,
                  test_malformed_errors_refuse_without_logging_the_payload])
expected_cases = 13 if args.revision == 'e826' else 11
try:
    for test in tests:
        test(); results.append(test.__name__)
    assert len(results) == len(set(results)) == expected_cases and not denied
    report = {'result':'PASS_BOUNDED_REAL_SERVER_REQUESTS', 'tests':results, 'cases':expected_cases,
              'upstream':pin, 'revision':args.revision,
              'sourceBlobs':{name:blob for name,(_,blob) in paths.items()}, 'deniedEvents':denied,
              'scope':'Actual server_requests module and two extracted ws functions only. Synthetic contract validation, transport, authority, context and thread runner; no parent startup, schemas, real threads, HTTP, authentication, vault/account, deployment or owner-phone certification. Python audit hooks are not an OS sandbox.'}
except BaseException:
    report = {'result':'FAIL', 'passedBeforeFailure':results, 'deniedEvents':denied, 'traceback':traceback.format_exc()}
    raise
finally:
    (root/'result.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
