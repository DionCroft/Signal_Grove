"""Read one operator-enrolled workspace from the current followed session."""
from dataclasses import dataclass, field
import secrets
import time

from .coordinator import NativeReadContext, ReadRefused, _HEX, _ID, _INSTANCE


@dataclass(frozen=True)
class FollowedWorkspace:
    context: NativeReadContext
    session_id: str = field(repr=False)
    subscription: str = field(repr=False)
    cwd: str = field(repr=False)
    revision: int
    execution_generation: int


@dataclass(frozen=True)
class RegisteredAttachment:
    device: str
    project_id: str
    coordinator_cwd: str = field(repr=False)
    worker_root: str = field(repr=False)
    policy_revision: str = field(repr=False)


@dataclass(frozen=True)
class _Binding:
    view: FollowedWorkspace
    attachment: RegisteredAttachment
    worker_instance: str
    native_token: str = field(repr=False)
    expires: float


class AttachedWorkspaceReads:
    """Use the enrolled worker's fixed read route under a live attachment fence."""

    def __init__(self, reads, resolve, *, clock=time.monotonic):
        self._reads = reads
        self._resolve = resolve
        self._clock = clock
        self._bindings = {}

    def _checked(self, follow):
        view = follow()
        attachment = self._resolve(view.context, view.session_id)
        if view.cwd != attachment.coordinator_cwd:
            raise ReadRefused('CHANGED')
        return view, attachment

    def _recheck(self, follow, bound):
        current = self._checked(follow)
        if current != bound:
            raise ReadRefused('CHANGED')
        return current[0].context

    async def describe(self, follow):
        bound = self._checked(follow)
        _, attachment = bound
        status = await self._reads.describe(
            lambda: self._recheck(follow, bound), attachment.device,
            grant_revision=attachment.policy_revision,
        )
        return {
            **status, 'project_ids': [attachment.project_id],
            'root': attachment.worker_root,
        }

    async def read(self, follow, request):
        if type(request) is not dict:
            raise ReadRefused('INVALID')
        operation = request.get('operation')
        keys = {'request_id', 'worker_instance', 'operation'}
        if type(operation) is not str or operation not in ('prepare', 'list', 'preview'):
            raise ReadRefused('INVALID')
        if operation != 'prepare':
            keys |= {'binding_token', 'relative_path', 'entry_token'}
        if (set(request) != keys or not _matches(_ID, request['request_id'])
                or not _matches(_INSTANCE, request['worker_instance'])):
            raise ReadRefused('INVALID')

        bound = self._checked(follow)
        view, attachment = bound
        instance = request['worker_instance']
        native_request = {**request, 'schema': 1, 'project_id': attachment.project_id}
        if operation != 'prepare':
            binding = self._binding(bound, instance, request['binding_token'])
            native_request['binding_token'] = binding.native_token
        else:
            started = self._clock()
            self._bindings = {token: value for token, value in self._bindings.items()
                              if value.expires > started}
            if len(self._bindings) >= 64:
                raise ReadRefused('BUSY')

        reply = await self._reads.read(
            lambda: self._recheck(follow, bound), attachment.device, native_request,
            grant_revision=attachment.policy_revision,
        )
        result = reply['result']
        if operation != 'prepare':
            if binding.expires <= self._clock():
                raise ReadRefused('EXPIRED', request_submitted=True)
            base = {'schema', 'scope_id', 'worker_instance', 'project_id', 'relative_path', 'truncated'}
            fields = ({'entries', 'excluded_entries'} if operation == 'list'
                      else {'byte_size', 'preview_bytes', 'preview_sha256', 'binary', 'text'})
            if set(result) != base | fields:
                raise ReadRefused('UNAVAILABLE', request_submitted=True)
            return {**reply, 'result': {key: value for key, value in result.items()
                                        if key != 'scope_id'}}

        if result.get('root') != attachment.worker_root:
            raise ReadRefused('CHANGED', request_submitted=True)
        ttl = result.get('expires_in_seconds')
        native_token = result.get('binding_token')
        if type(ttl) is not int or not 1 <= ttl <= 300 or not _matches(_HEX, native_token):
            raise ReadRefused('UNAVAILABLE', request_submitted=True)
        remaining = int(started + ttl - self._clock())
        if remaining <= 0:
            raise ReadRefused('EXPIRED', request_submitted=True)
        if len(self._bindings) >= 64:
            raise ReadRefused('BUSY', request_submitted=True)
        token = secrets.token_hex(32)
        self._bindings[token] = _Binding(view, attachment, instance, native_token, started + ttl)
        return {
            **reply, 'result': {
                'schema': 1,
                'project_id': attachment.project_id,
                'worker': result['worker'],
                'root': attachment.worker_root,
                'binding_token': token,
                'expires_in_seconds': remaining,
            },
        }

    def _binding(self, bound, instance, token):
        if not _matches(_HEX, token):
            raise ReadRefused('INVALID')
        binding = self._bindings.get(token)
        if (binding is None or (binding.view, binding.attachment) != bound
                or binding.worker_instance != instance):
            raise ReadRefused('CHANGED')
        if binding.expires <= self._clock():
            raise ReadRefused('EXPIRED')
        return binding


def _matches(pattern, value):
    return type(value) is str and pattern.fullmatch(value) is not None
