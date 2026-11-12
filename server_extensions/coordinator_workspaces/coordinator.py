"""Optional owner/profile-bound forwarding of fixed workspace reads.

Trusted native authentication and current operator policy are callbacks, never
request fields. No listener, content cache, model, execution queue or retry.
"""
from __future__ import annotations
from dataclasses import dataclass, field
import hashlib
import json
import re

MAX_REQUEST = 16 * 1024
MAX_REPLY = 512 * 1024  # bounded outer JSON may escape a 128 KiB broker frame
CODES = frozenset({'INVALID','DENIED','CHANGED','EXPIRED','NOT_FOUND','BUSY',
                   'UNAVAILABLE','UNSUPPORTED','TOO_LARGE','DUPLICATE','IDENTITY_FAILURE'})
_HEX = re.compile(r'[a-f0-9]{64}')
_ID = re.compile(r'[a-f0-9]{32}')
_INSTANCE = re.compile(r'[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}')
_PROJECT = re.compile(r'[A-Za-z0-9][A-Za-z0-9_-]{0,79}')
_DEVICE = re.compile(r'[A-Z0-9][A-Z0-9_-]{0,63}')


class ReadRefused(Exception):
    def __init__(self, code='UNAVAILABLE', *, request_submitted=False):
        self.code = code if code in CODES else 'UNAVAILABLE'
        self.request_submitted = request_submitted is not False
        super().__init__(self.code)


def _unique(pairs):
    value = {}
    for key, item in pairs:
        if key in value: raise ValueError('Duplicate field')
        value[key] = item
    return value


def encode(value, maximum=MAX_REQUEST):
    try: raw = json.dumps(value, ensure_ascii=True, allow_nan=False, separators=(',', ':')).encode('utf8')
    except (ValueError, TypeError, UnicodeError, RecursionError): raise ReadRefused('INVALID') from None
    if not 1 <= len(raw) <= maximum: raise ReadRefused('TOO_LARGE')
    return raw


def decode(raw, maximum=MAX_REPLY):
    if type(raw) is not bytes or not 1 <= len(raw) <= maximum: raise ReadRefused('TOO_LARGE')
    try:
        value = json.loads(raw.decode('utf8'), object_pairs_hook=_unique,
                          parse_constant=lambda _: (_ for _ in ()).throw(ValueError()))
    except (ValueError, UnicodeError, RecursionError): raise ReadRefused('UNAVAILABLE') from None
    if type(value) is not dict: raise ReadRefused('UNAVAILABLE')
    return value


def _identity(value):
    return (type(value) is str and 1 <= len(value) <= 4096
            and not any(ord(c) < 32 or ord(c) == 127 or 0xd800 <= ord(c) <= 0xdfff for c in value))


def scope_for(authority, subject, profile_id):
    if not all(_identity(v) for v in (authority, subject, profile_id)): raise ReadRefused('DENIED')
    raw = json.dumps(['signal-grove-worker-read-v1', authority, subject, profile_id],
                     ensure_ascii=True, separators=(',', ':')).encode('utf8')
    return hashlib.sha256(raw).hexdigest()


@dataclass(frozen=True)
class NativeReadContext:
    subject: str = field(repr=False)
    profile_id: str = field(repr=False)
    transport_id: str = field(repr=False)
    authority_instance: str = field(repr=False)


@dataclass(frozen=True)
class ReadGrant:
    revision: str = field(repr=False)
    projects: frozenset[str] = field(repr=False)


class CoordinatorReadService:
    """One explicitly installed operator service; callbacks must observe fresh state.

    ``route(payload)`` is the exact enrolled worker's one-shot private transport.
    ``grant(context, device)`` returns that native actor/profile's current grant,
    not a default profile or a cached project name. Context is rechecked after I/O.
    """
    def __init__(self, authority, routes, *, grant):
        if not _identity(authority) or type(routes) is not dict or not 1 <= len(routes) <= 8:
            raise ReadRefused('INVALID')
        if any(type(d) is not str or not _DEVICE.fullmatch(d) or not callable(r) for d,r in routes.items()):
            raise ReadRefused('INVALID')
        self._authority = authority
        self._routes = dict(routes)
        self._grant = grant
        self._inflight = 0

    def __repr__(self): return 'CoordinatorReadService(redacted)'

    def _checked(self, authenticate, device):
        if type(device) is not str or device not in self._routes: raise ReadRefused('DENIED')
        try:
            context = authenticate()
            if (not isinstance(context, NativeReadContext)
                    or not all(_identity(v) for v in (context.subject, context.profile_id,
                                                      context.transport_id, context.authority_instance))):
                raise ReadRefused('DENIED')
            grant = self._grant(context, device)
        except ReadRefused: raise
        except Exception: raise ReadRefused('DENIED') from None
        if (not isinstance(grant, ReadGrant) or type(grant.revision) is not str
                or not _HEX.fullmatch(grant.revision) or type(grant.projects) is not frozenset
                or not 1 <= len(grant.projects) <= 32
                or any(type(p) is not str or not _PROJECT.fullmatch(p) for p in grant.projects)):
            raise ReadRefused('DENIED')
        return context, grant, scope_for(self._authority, context.subject, context.profile_id)

    async def _call(self, authenticate, device, payload, before, *, reading):
        if self._inflight >= 4: raise ReadRefused('BUSY')
        self._inflight += 1
        try:
            encode(payload)
            reply = await self._routes[device](payload)
            # A changed actor, profile, authority instance or enrollment drops contents.
            try: after = self._checked(authenticate, device)
            except ReadRefused as exc: raise ReadRefused(exc.code, request_submitted=reading) from None
            if after != before: raise ReadRefused('CHANGED', request_submitted=reading)
            if type(reply) is not dict: raise ReadRefused('UNAVAILABLE', request_submitted=reading)
            try: encode(reply, MAX_REPLY)
            except ReadRefused as exc: raise ReadRefused(exc.code, request_submitted=reading) from None
            if reply.get('transport') == 'error':
                if (set(reply) != {'transport','code','request_submitted'}
                        or type(reply['code']) is not str or reply['code'] not in CODES
                        or type(reply['request_submitted']) is not bool):
                    raise ReadRefused('UNAVAILABLE', request_submitted=reading)
                raise ReadRefused(reply['code'], request_submitted=reading and reply['request_submitted'])
            return reply
        except ReadRefused: raise
        except Exception: raise ReadRefused('UNAVAILABLE', request_submitted=reading) from None
        finally: self._inflight -= 1

    async def describe(self, authenticate, device):
        before = self._checked(authenticate, device); scope = before[2]
        reply = await self._call(authenticate, device, {'action':'workspace_status','scope_id':scope}, before, reading=False)
        if (set(reply) != {'transport','scope_id','device','state','worker_instance','failure_code'}
                or reply['transport'] != 'status' or reply['scope_id'] != scope or reply['device'] != device
                or type(reply['state']) is not str or reply['state'] not in {'ready','starting','held','closed'}
                or (reply['failure_code'] is not None and (type(reply['failure_code']) is not str
                                                         or reply['failure_code'] not in CODES))):
            raise ReadRefused('UNAVAILABLE')
        instance = reply['worker_instance']
        if reply['state'] == 'ready':
            if type(instance) is not str or not _INSTANCE.fullmatch(instance) or reply['failure_code'] is not None:
                raise ReadRefused('UNAVAILABLE')
        elif instance is not None: raise ReadRefused('UNAVAILABLE')
        return dict(schema=1, device=device, state=reply['state'], worker_instance=instance,
                    failure_code=reply['failure_code'], project_ids=sorted(before[1].projects),
                    operations=['prepare','list','preview'] if reply['state']=='ready' else [])

    async def read(self, authenticate, device, request):
        before = self._checked(authenticate, device); scope = before[2]
        if type(request) is not dict or type(request.get('schema')) is not int or request['schema'] != 1:
            raise ReadRefused('INVALID')
        base = {'schema','request_id','worker_instance','operation','project_id'}
        operation = request.get('operation')
        if type(operation) is not str: raise ReadRefused('INVALID')
        if operation == 'prepare': expected = base
        elif operation in {'list','preview'}: expected = base | {'binding_token','relative_path','entry_token'}
        else: raise ReadRefused('INVALID')
        if (set(request) != expected or type(request['request_id']) is not str or not _ID.fullmatch(request['request_id'])
                or type(request['worker_instance']) is not str or not _INSTANCE.fullmatch(request['worker_instance'])):
            raise ReadRefused('INVALID')
        if type(request['project_id']) is not str or request['project_id'] not in before[1].projects:
            raise ReadRefused('DENIED')
        if operation != 'prepare':
            if (type(request['binding_token']) is not str or not _HEX.fullmatch(request['binding_token'])
                    or type(request['relative_path']) is not str or len(request['relative_path']) > 4096
                    or (request['entry_token'] is not None and (type(request['entry_token']) is not str
                                                               or not _HEX.fullmatch(request['entry_token'])))):
                raise ReadRefused('INVALID')
        payload = {k:v for k,v in request.items() if k not in {'request_id','worker_instance'}}
        payload['scope_id'] = scope
        frame = dict(schema=1, request_id=request['request_id'], worker_instance=request['worker_instance'], payload=payload)
        reply = await self._call(authenticate, device, {'action':'workspace_read','scope_id':scope,'frame':frame}, before, reading=True)
        if set(reply) != {'transport','reply'} or reply['transport'] != 'reply' or type(reply['reply']) is not dict:
            raise ReadRefused('UNAVAILABLE', request_submitted=True)
        result = reply['reply']
        common = {'schema','request_id','worker_instance','status'}
        if (type(result.get('schema')) is not int or result['schema'] != 1
                or result.get('request_id') != request['request_id']
                or result.get('worker_instance') != request['worker_instance']):
            raise ReadRefused('CHANGED', request_submitted=True)
        if result.get('status') == 'error':
            if set(result) != common | {'code'} or type(result['code']) is not str or result['code'] not in CODES:
                raise ReadRefused('UNAVAILABLE', request_submitted=True)
            raise ReadRefused(result['code'], request_submitted=True)
        if set(result) != common | {'result'} or result.get('status') != 'ok' or type(result['result']) is not dict:
            raise ReadRefused('UNAVAILABLE', request_submitted=True)
        data = result['result']
        if (type(data.get('schema')) is not int or data['schema'] != 1 or data.get('scope_id') != scope
                or data.get('project_id') != request['project_id']):
            raise ReadRefused('CHANGED', request_submitted=True)
        worker = data.get('worker') if operation == 'prepare' else None
        if operation == 'prepare':
            if worker != {'device':device,'instance_id':request['worker_instance']}:
                raise ReadRefused('CHANGED', request_submitted=True)
        elif data.get('worker_instance') != request['worker_instance'] or data.get('relative_path') != request['relative_path']:
            raise ReadRefused('CHANGED', request_submitted=True)
        return result
