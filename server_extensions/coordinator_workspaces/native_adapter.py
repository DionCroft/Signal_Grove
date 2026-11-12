"""Versioned methods on the existing authenticated Hermes authority socket.

Loaded only by the reviewed source adapter. An operator module must explicitly
install ``service``; absence answers UNAVAILABLE and makes no readiness claim.
"""
import importlib
from pathlib import Path
from typing import Literal
from pydantic import Field
from tui_gateway.contracts.base import JsonValue, Params, Result
from tui_gateway.contracts.registry import canonical_method
from .coordinator import CoordinatorReadService, NativeReadContext, ReadRefused

DESCRIBE = 'signal_grove.workspaces.describe_v1'
READ = 'signal_grove.workspaces.read_v1'


class DescribeParams(Params):
    device: str
    profile: str | None = None


class ReadParams(DescribeParams):
    request: dict[str, JsonValue]


class ReadResult(Result):
    schema_version: Literal[1] = Field(alias='schema')
    state: Literal['ok','error']
    payload: dict[str, JsonValue] | None = None
    code: str | None = None
    request_submitted: bool | None = None


canonical_method(DESCRIBE, params=DescribeParams, result=ReadResult,
                 doc='Optional Signal Grove v1 registered-worker read capability, checked for this native owner/profile now.')
canonical_method(READ, params=ReadParams, result=ReadResult,
                 doc='One fixed prepare/list/preview over the enrolled owner route; never creates an execution admission or retries.')


def native_context(connection):
    from gateway.session_authorities import require_serving
    from gateway.owner_identity import host_owner_subject
    try:
        require_serving(connection.authority)
        actor = connection.actor
        if (connection._operator is not True or actor.subject != host_owner_subject()
                or actor.profile_id != connection.authority.profile_id
                or connection._identity.get('instance_id') != connection.authority.instance_id
                or connection.authority.events.get(actor.transport_id) is not connection.transport
                or getattr(connection.authority,'retiring',False)
                or not {'session:read','session:operator'} <= actor.capabilities):
            raise ReadRefused('DENIED')
        return NativeReadContext(actor.subject, actor.profile_id, actor.transport_id,
                                 connection.authority.instance_id)
    except ReadRefused: raise
    except Exception: raise ReadRefused('DENIED') from None


def handlers(connection):
    async def invoke(ref, params, *, reading):
        try:
            native_context(connection)
            expected = {'device','request'} if reading else {'device'}
            if type(params) is not dict or set(params) - (expected | {'profile'}) or not expected <= set(params):
                raise ReadRefused('INVALID')
            if 'profile' in params:
                from hermes_cli.profiles import profile_matches_home
                profile = params['profile']
                if type(profile) is not str or not profile or not profile_matches_home(profile, Path(connection.authority.profile_id)):
                    raise ReadRefused('DENIED')
            # Lazy and after authentication: missing policy never breaks unrelated methods.
            try: operator = importlib.import_module('gateway.signal_grove_workspace_operator')
            except Exception: raise ReadRefused('UNAVAILABLE') from None
            service = getattr(operator, 'service', None)
            if not isinstance(service, CoordinatorReadService): raise ReadRefused('UNAVAILABLE')
            authenticate = lambda: native_context(connection)
            payload = (await service.read(authenticate, params['device'], params['request']) if reading
                       else await service.describe(authenticate, params['device']))
            return dict(schema=1, state='ok', payload=payload)
        except ReadRefused as exc:
            return dict(schema=1, state='error', code=exc.code, request_submitted=exc.request_submitted)

    async def describe(ref, params): return await invoke(ref, params, reading=False)
    async def read(ref, params): return await invoke(ref, params, reading=True)
    return {DESCRIBE:describe, READ:read}
