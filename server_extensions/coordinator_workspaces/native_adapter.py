"""Combined workspace and scheduler methods for the native Hermes authority."""
import asyncio
import importlib
from pathlib import Path
from typing import Literal
from tui_gateway.contracts.base import JsonValue, Params
from tui_gateway.contracts.registry import canonical_method
from .coordinator import ReadRefused
from .native_workspace_adapter import (
    DESCRIBE, DESCRIBE_ATTACHED, READ, READ_ATTACHED, ReadResult,
    handlers as workspace_handlers, native_context,
)
from .cron_edit import apply_edit, edit_query, observe_edit, prepare_edit, review_edit
from .cron_history import execution_query, read_executions
from .cron_output import output_query, read_output
from .cron_project_holds import inbox_query, read_inbox
from .cron_project_decisions import ProjectScheduleOperator, decision_query, observe_occurrence, occurrence_query
from .cron_run_once import observe_run_once, run_once_receipt_query
from .cron_run_once_operator import RunOnceOperator, confirm_query, prepare_query

CRON_EXECUTIONS = 'signal_grove.cron.executions_v1'
CRON_OUTPUT = 'signal_grove.cron.output_v1'
CRON_PROJECT_INBOX = 'signal_grove.cron.project_inbox_v1'
CRON_PROJECT_PREPARE = 'signal_grove.cron.project_prepare_v1'
CRON_PROJECT_DECIDE = 'signal_grove.cron.project_decide_v1'
CRON_PROJECT_OBSERVE = 'signal_grove.cron.project_observe_v1'
CRON_EDIT_PREPARE = 'signal_grove.cron.edit_prepare_v1'
CRON_EDIT_REVIEW = 'signal_grove.cron.edit_review_v1'
CRON_EDIT_APPLY = 'signal_grove.cron.edit_apply_v1'
CRON_EDIT_OBSERVE = 'signal_grove.cron.edit_observe_v1'
CRON_RUN_ONCE_OBSERVE = 'signal_grove.cron.run_once_observe_v1'
CRON_RUN_ONCE_PREPARE = 'signal_grove.cron.run_once_prepare_v1'
CRON_RUN_ONCE_CONFIRM = 'signal_grove.cron.run_once_confirm_v1'
CRON_RUN_ONCE_DISMISS = 'signal_grove.cron.run_once_dismiss_v1'


class CronExecutionParams(Params):
    # A bare profile key is routed by Hermes before the method is validated.
    expected_profile: str
    job_id: str
    limit: int | None = None
    execution_id: str | None = None


class CronProjectInboxParams(Params):
    expected_profile: str


class CronProjectOccurrenceParams(CronProjectInboxParams):
    job_id: str
    occurrence_id: str


class CronProjectDecisionParams(CronProjectOccurrenceParams):
    review_digest: str
    decision: Literal['approve', 'defer', 'deny']


class CronOutputParams(Params):
    expected_profile: str
    job_id: str
    execution_id: str


class CronEditPrepareParams(Params):
    expected_profile: str
    job_id: str


class CronEditObserveParams(CronEditPrepareParams):
    request_id: str


class CronRunOnceObserveParams(Params):
    expected_profile: str
    job_id: str
    request_id: str


class CronRunOnceConfirmParams(CronRunOnceObserveParams):
    review_digest: str


class CronRunOnceDismissParams(CronRunOnceConfirmParams):
    decision: Literal['defer', 'deny']


class CronEditReviewParams(CronEditPrepareParams):
    expected_version: str
    changes: dict[str, JsonValue]


class CronEditApplyParams(CronEditReviewParams):
    request_id: str
    review_digest: str


canonical_method(CRON_EXECUTIONS, params=CronExecutionParams, result=ReadResult,
                 doc='Read a profile-owned job ledger page or one exact durable execution receipt.')

canonical_method(CRON_OUTPUT, params=CronOutputParams, result=ReadResult,
                 doc='Preview an exact owner-bound scheduler output archive.')

canonical_method(CRON_EDIT_PREPARE, params=CronEditPrepareParams, result=ReadResult,
                 doc='Prepare the exact stored paused schedule for a timing review.')
canonical_method(CRON_EDIT_REVIEW, params=CronEditReviewParams, result=ReadResult,
                 doc='Project one paused timing edit with the native scheduler and report its repeat effect.')
canonical_method(CRON_EDIT_APPLY, params=CronEditApplyParams, result=ReadResult,
                 doc='Apply one exact approved timing edit while the canonical job is paused.')
canonical_method(CRON_EDIT_OBSERVE, params=CronEditObserveParams, result=ReadResult,
                 doc='Observe a retained timing edit receipt without submitting work.')


canonical_method(CRON_RUN_ONCE_OBSERVE, params=CronRunOnceObserveParams, result=ReadResult,
                 doc='Observe the original reviewed manual execution using its request ID.')


canonical_method(CRON_PROJECT_INBOX, params=CronProjectInboxParams, result=ReadResult,
                 doc='List held project-write occurrences for the current owner profile.')

canonical_method(CRON_PROJECT_PREPARE, params=CronProjectOccurrenceParams, result=ReadResult,
                 doc='Review one exact held project occurrence on the current owner transport.')
canonical_method(CRON_PROJECT_DECIDE, params=CronProjectDecisionParams, result=ReadResult,
                 doc='Approve, defer or deny one held project occurrence.')
canonical_method(CRON_PROJECT_OBSERVE, params=CronProjectOccurrenceParams, result=ReadResult,
                 doc='Observe the original durable decision for one held project occurrence.')

canonical_method(CRON_RUN_ONCE_PREPARE, params=CronEditPrepareParams, result=ReadResult,
                 doc='Read the exact stored job and current verified operator route for one review.')
canonical_method(CRON_RUN_ONCE_CONFIRM, params=CronRunOnceConfirmParams, result=ReadResult,
                 doc='Confirm a one-use review and return the reserved native execution receipt.')
canonical_method(CRON_RUN_ONCE_DISMISS, params=CronRunOnceDismissParams, result=ReadResult,
                 doc='Retire a pending review without submitting a native execution.')


def _run_once_operator():
    try:
        operator = importlib.import_module('gateway.signal_grove_workspace_operator')
    except Exception:
        raise ReadRefused('UNAVAILABLE') from None
    service = getattr(operator, 'run_once', None)
    if not isinstance(service, RunOnceOperator):
        raise ReadRefused('UNAVAILABLE')
    return service


def _project_schedule_operator():
    try:
        operator = importlib.import_module('gateway.signal_grove_workspace_operator')
    except Exception:
        raise ReadRefused('UNAVAILABLE') from None
    schedule = getattr(operator, 'project_schedule', None)
    if not isinstance(schedule, ProjectScheduleOperator):
        raise ReadRefused('UNAVAILABLE')
    return schedule


def handlers(connection):
    async def scheduler_read(params, parse, read):
        try:
            from hermes_cli.profiles import profile_matches_home
            context = native_context(connection)
            query = parse(params)
            home = Path(context.profile_id)
            if not profile_matches_home(query.profile, home):
                raise ReadRefused('DENIED')
            payload = await asyncio.to_thread(read, home, query)
            if native_context(connection) != context or not profile_matches_home(query.profile, home):
                raise ReadRefused('CHANGED')
            return dict(schema=1, state='ok', payload=payload)
        except ReadRefused as exc:
            return dict(schema=1, state='error', code=exc.code, request_submitted=False)

    async def schedule_prepare(ref, params):
        return await scheduler_read(params, lambda values: edit_query(values, 'prepare'), prepare_edit)

    async def schedule_review(ref, params):
        return await scheduler_read(params, lambda values: edit_query(values, 'review'), review_edit)

    async def schedule_observe(ref, params):
        return await scheduler_read(params, lambda values: edit_query(values, 'observe'), observe_edit)

    async def schedule_apply(ref, params):
        from hermes_cli.profiles import profile_matches_home
        committed = False
        try:
            context = native_context(connection)
            query = edit_query(params, 'apply')
            home = Path(context.profile_id)

            def verify_authority():
                if native_context(connection) != context or not profile_matches_home(query.profile, home):
                    raise ReadRefused('DENIED')

            verify_authority()
            payload = await asyncio.to_thread(apply_edit, home, query, verify_authority)
            committed = True
            verify_authority()
            return dict(schema=1, state='ok', payload=payload)
        except ReadRefused as exc:
            return dict(schema=1, state='error', code=exc.code,
                        request_submitted=committed or exc.request_submitted)

    async def project_inbox(ref, params):
        def read(home, query):
            from cron import jobs
            if not callable(getattr(jobs, '_ordinary_cron_allowed', None)):
                raise ReadRefused('UNSUPPORTED')
            return read_inbox(home, query)

        return await scheduler_read(params, inbox_query, read)

    async def project_action(params, *, prepare):
        reserved = False
        try:
            context = native_context(connection)

            def verify():
                fresh = native_context(connection)
                if fresh != context:
                    raise ReadRefused('DENIED')
                return fresh

            request = occurrence_query(params) if prepare else decision_query(params)
            service = _project_schedule_operator()
            if prepare:
                payload = await asyncio.to_thread(service.prepare, request, verify)
            else:
                payload = await service.decide(request, verify)
                reserved = payload.get('state') in ('claimed', 'denied')
            verify()
            return dict(schema=1, state='ok', payload=payload)
        except ReadRefused as exc:
            return dict(schema=1, state='error', code=exc.code,
                        request_submitted=reserved or exc.request_submitted)

    async def project_prepare(ref, params):
        return await project_action(params, prepare=True)

    async def project_decide(ref, params):
        return await project_action(params, prepare=False)

    async def project_observe(ref, params):
        return await scheduler_read(params, occurrence_query, observe_occurrence)

    async def run_once_observe(ref, params):
        return await scheduler_read(params, run_once_receipt_query, observe_run_once)

    async def run_once(ref, params, *, action):
        reserved = False
        try:
            context = native_context(connection)
            request = (prepare_query(params) if action == 'prepare'
                       else confirm_query(params, dismiss=action == 'dismiss'))

            def verify():
                fresh = native_context(connection)
                if fresh != context:
                    raise ReadRefused('DENIED')
                return fresh

            service = _run_once_operator()
            if action == 'confirm':
                result = await service.confirm(request, verify)
                reserved = True
            else:
                call = service.prepare if action == 'prepare' else service.dismiss
                result = await asyncio.to_thread(call, request, verify)
            verify()
            return dict(schema=1, state='ok', payload=result)
        except ReadRefused as exc:
            return dict(schema=1, state='error', code=exc.code,
                        request_submitted=reserved or exc.request_submitted)

    async def run_once_prepare(ref, params): return await run_once(ref, params, action='prepare')
    async def run_once_confirm(ref, params): return await run_once(ref, params, action='confirm')
    async def run_once_dismiss(ref, params): return await run_once(ref, params, action='dismiss')
    async def executions(ref, params): return await scheduler_read(params, execution_query, read_executions)
    async def output(ref, params): return await scheduler_read(params, output_query, read_output)
    return {**workspace_handlers(connection),
            CRON_EXECUTIONS:executions, CRON_OUTPUT:output, CRON_PROJECT_INBOX:project_inbox,
            CRON_PROJECT_PREPARE:project_prepare, CRON_PROJECT_DECIDE:project_decide,
            CRON_PROJECT_OBSERVE:project_observe,
            CRON_EDIT_PREPARE:schedule_prepare, CRON_EDIT_REVIEW:schedule_review, CRON_EDIT_OBSERVE:schedule_observe,
            CRON_EDIT_APPLY:schedule_apply, CRON_RUN_ONCE_OBSERVE:run_once_observe,
            CRON_RUN_ONCE_PREPARE:run_once_prepare, CRON_RUN_ONCE_CONFIRM:run_once_confirm,
            CRON_RUN_ONCE_DISMISS:run_once_dismiss}
