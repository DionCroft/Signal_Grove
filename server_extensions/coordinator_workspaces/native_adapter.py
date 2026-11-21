"""Versioned methods on the existing authenticated Hermes authority socket.

Loaded only by the reviewed source adapter. An operator module must explicitly
install ``service``; absence answers UNAVAILABLE and makes no readiness claim.
"""
import asyncio
import importlib
from pathlib import Path
from typing import Literal
from pydantic import Field
from tui_gateway.contracts.base import JsonValue, Params, Result
from tui_gateway.contracts.registry import canonical_method
from .attached import FollowedWorkspace
from .coordinator import CoordinatorReadService, NativeReadContext, ReadRefused, _identity
from .cron_history import execution_query, read_executions

DESCRIBE = 'signal_grove.workspaces.describe_v1'
READ = 'signal_grove.workspaces.read_v1'
DESCRIBE_ATTACHED = 'signal_grove.workspaces.describe_attachment_v1'
READ_ATTACHED = 'signal_grove.workspaces.read_attachment_v1'
CRON_EXECUTIONS = 'signal_grove.cron.executions_v1'


class DescribeParams(Params):
    device: str
    profile: str | None = None


class ReadParams(DescribeParams):
    request: dict[str, JsonValue]


class DescribeAttachmentParams(Params):
    # The upstream dispatcher treats "session_id" as a request to adopt/restore
    # missing sessions before executing the handler. Use only a live attachment.
    attached_session_id: str
    expected_revision: int
    expected_generation: int


class ReadAttachmentParams(DescribeAttachmentParams):
    request: dict[str, JsonValue]


class CronExecutionParams(Params):
    # A bare profile key is routed by Hermes before the method is validated.
    expected_profile: str
    job_id: str
    limit: int | None = None
    execution_id: str | None = None


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


canonical_method(DESCRIBE_ATTACHED, params=DescribeAttachmentParams, result=ReadResult,
                 doc='Observe the single registered workspace for this followed session.')
canonical_method(READ_ATTACHED, params=ReadAttachmentParams, result=ReadResult,
                 doc='Prepare, list or preview the registered workspace of the live attached session.')


canonical_method(CRON_EXECUTIONS, params=CronExecutionParams, result=ReadResult,
                 doc='Read a profile-owned job ledger page or one exact durable execution receipt.')


def _identity_instance_matches(connection):
    """Bind a verified HTTP identity to the actual current served authority.

    Explicit claims and native bootstrap grants never use the missing-field
    path. Owner/operator/profile/transport checks still apply before/after I/O.
    """
    identity = connection._identity
    if 'instance_id' in identity:
        return identity['instance_id'] == connection.authority.instance_id
    if identity.get('native_bootstrap') is True or identity.get('profile_scope') != 'host':
        return False
    from gateway.owner_identity import host_owner_subject, principal_subject
    if principal_subject(identity) != host_owner_subject():
        return False
    registry = getattr(connection.authority.runner, 'session_authorities', None)
    return (registry is not None
            and registry.require(Path(connection.authority.profile_id)) is connection.authority)


def native_context(connection):
    from gateway.session_authorities import require_serving
    from gateway.owner_identity import host_owner_subject
    try:
        require_serving(connection.authority)
        actor = connection.actor
        if (connection._operator is not True or actor.subject != host_owner_subject()
                or actor.profile_id != connection.authority.profile_id
                or not _identity_instance_matches(connection)
                or connection.authority.events.get(actor.transport_id) is not connection.transport
                or getattr(connection.authority,'retiring',False)
                or not {'session:read','session:operator'} <= actor.capabilities):
            raise ReadRefused('DENIED')
        return NativeReadContext(actor.subject, actor.profile_id, actor.transport_id,
                                 connection.authority.instance_id)
    except ReadRefused: raise
    except Exception: raise ReadRefused('DENIED') from None


def _followed_workspace(connection, session_id, revision, generation):
    from gateway.session_policy import policy_for_source

    context = native_context(connection)
    authority = connection.authority
    live = authority.sessions.get(session_id)
    subscription = connection.subscriptions.get(session_id)
    if live is None or not subscription or live.subscribers.get(subscription) != connection.actor:
        raise ReadRefused('DENIED')
    try:
        # Only these durable columns are needed. A single read ties the cwd to
        # the same revision/generation. The live route can lag a commit briefly.
        row = authority.db._read_one(
            'SELECT cwd, runtime_revision, runtime_generation, archived FROM sessions WHERE id=?',
            (session_id,),
        )
        if row is None or row['archived']:
            raise ReadRefused('DENIED')
        policy = policy_for_source(authority.runner, live.source)
        if policy is None:
            raise ReadRefused('DENIED')
        if ((row['runtime_revision'], row['runtime_generation']) != (revision, generation)
                or row['cwd'] != policy.cwd or not _identity(row['cwd'])):
            raise ReadRefused('CHANGED')
    except ReadRefused:
        raise
    except Exception:
        raise ReadRefused('UNAVAILABLE') from None
    return FollowedWorkspace(context, session_id, subscription, row['cwd'], revision, generation)


def _operator_service():
    # Lazy and after authentication: missing policy never breaks unrelated methods.
    try:
        operator = importlib.import_module('gateway.signal_grove_workspace_operator')
    except Exception:
        raise ReadRefused('UNAVAILABLE') from None
    service = getattr(operator, 'service', None)
    if not isinstance(service, CoordinatorReadService):
        raise ReadRefused('UNAVAILABLE')
    return service


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
            service = _operator_service()
            authenticate = lambda: native_context(connection)
            payload = (await service.read(authenticate, params['device'], params['request']) if reading
                       else await service.describe(authenticate, params['device']))
            return dict(schema=1, state='ok', payload=payload)
        except ReadRefused as exc:
            return dict(schema=1, state='error', code=exc.code, request_submitted=exc.request_submitted)

    async def attached(ref, params, *, reading):
        try:
            native_context(connection)
            fields = {'attached_session_id', 'expected_revision', 'expected_generation'}
            if reading:
                fields.add('request')
            if type(params) is not dict or set(params) != fields:
                raise ReadRefused('INVALID')
            session_id = params['attached_session_id']
            revision = params['expected_revision']
            generation = params['expected_generation']
            if (not _identity(session_id) or len(session_id) > 256
                    or type(revision) is not int or not 0 <= revision < 2**63
                    or type(generation) is not int or not 0 <= generation < 2**63):
                raise ReadRefused('INVALID')

            def follow():
                return _followed_workspace(connection, session_id, revision, generation)

            service = _operator_service().attachments
            if service is None:
                raise ReadRefused('UNAVAILABLE')
            payload = (await service.read(follow, params['request']) if reading
                       else await service.describe(follow))
            return dict(schema=1, state='ok', payload=payload)
        except ReadRefused as exc:
            return dict(schema=1, state='error', code=exc.code, request_submitted=exc.request_submitted)

    async def executions(ref, params):
        try:
            from hermes_cli.profiles import profile_matches_home
            context = native_context(connection)
            query = execution_query(params)
            home = Path(context.profile_id)
            if not profile_matches_home(query.profile, home):
                raise ReadRefused('DENIED')
            payload = await asyncio.to_thread(read_executions, home, query)
            if native_context(connection) != context or not profile_matches_home(query.profile, home):
                raise ReadRefused('CHANGED')
            return dict(schema=1, state='ok', payload=payload)
        except ReadRefused as exc:
            return dict(schema=1, state='error', code=exc.code, request_submitted=False)

    async def describe(ref, params): return await invoke(ref, params, reading=False)
    async def read(ref, params): return await invoke(ref, params, reading=True)
    async def describe_attached(ref, params): return await attached(ref, params, reading=False)
    async def read_attached(ref, params): return await attached(ref, params, reading=True)
    return {DESCRIBE:describe, READ:read, DESCRIBE_ATTACHED:describe_attached, READ_ATTACHED:read_attached,
            CRON_EXECUTIONS:executions}
