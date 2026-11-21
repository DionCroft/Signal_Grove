"""Explicit operator wiring for reviewed manual jobs on the owner gateway."""

import asyncio
from dataclasses import dataclass
import re
from time import monotonic

from .coordinator import ReadRefused
from .cron_history import execution_query
from .cron_run_once import RunOnceReceipt, run_once_receipt_query
from .cron_run_once_approval import RunOnceApprovals

_DIGEST = re.compile(r"[a-f0-9]{64}")
_SELECT = {"expected_profile", "job_id"}
_CONFIRM = _SELECT | {"request_id", "review_digest"}
_MAX_IN_FLIGHT = 4


@dataclass(frozen=True)
class RunOnceDecision:
    receipt: RunOnceReceipt
    digest: str
    decision: str | None = None


def prepare_query(params):
    if type(params) is not dict or set(params) != _SELECT:
        raise ReadRefused("INVALID")
    return execution_query(params)


def confirm_query(params, *, dismiss=False):
    keys = _CONFIRM | {"decision"} if dismiss else _CONFIRM
    if type(params) is not dict or set(params) != keys:
        raise ReadRefused("INVALID")
    digest = params["review_digest"]
    decision = params.get("decision")
    if (type(digest) is not str or not _DIGEST.fullmatch(digest)
            or (dismiss and decision not in ("defer", "deny"))):
        raise ReadRefused("INVALID")
    receipt = run_once_receipt_query({name: params[name] for name in _SELECT | {"request_id"}})
    return RunOnceDecision(receipt, digest, decision)


def _receipt_fields(receipt):
    return {"profile": receipt.profile, "job_id": receipt.job_id, "request_id": receipt.request_id}


class RunOnceOperator:
    """Opt-in bridge to a locally configured, already verified dispatcher.

    The resolver must observe the current grant and route without side effects.
    Dispatch receives the frozen scope and native claim, under the owning home.
    It owns scheduling and outcome bookkeeping for that exact execution.
    """

    def __init__(self, resolve_scope, dispatch):
        if not callable(resolve_scope) or not callable(dispatch):
            raise TypeError("Run once requires a scope resolver and dispatcher")
        self._approvals = RunOnceApprovals(resolve_scope)
        self._dispatch = dispatch
        self._tasks = set()

    def prepare(self, query, verify):
        review = self._approvals.prepare(query.profile, query.job_id, verify)
        scope = review.scope
        return {**_receipt_fields(review.receipt), "review_digest": review.digest,
                "expires_in_seconds": max(0, int(review.expires_at - monotonic())),
                "scope": {"project": scope.project, "workspace": scope.workspace, "branch": scope.branch,
                          "worker": scope.worker, "worker_instance": scope.worker_instance,
                          "access": scope.access, "task": scope.task}}

    def dismiss(self, request, verify):
        self._approvals.dismiss(request.receipt, request.digest, verify, request.decision)
        return {**_receipt_fields(request.receipt), "decision": request.decision}

    async def confirm(self, request, verify):
        if len(self._tasks) >= _MAX_IN_FLIGHT:
            raise ReadRefused("BUSY")
        loop = asyncio.get_running_loop()
        reply = loop.create_future()

        def publish(value):
            def resolve():
                if not reply.done():
                    reply.set_result(value)
            try:
                loop.call_soon_threadsafe(resolve)
            except RuntimeError:
                # The native attempt and original receipt outlive this reply.
                pass

        def dispatch(approved):
            publish({**_receipt_fields(approved.receipt),
                     "execution_id": approved.receipt.execution_id(), "state": "claimed"})
            self._dispatch(approved)

        def run():
            try:
                self._approvals.admit(request.receipt, request.digest, verify, on_reserved=dispatch)
            except ReadRefused as error:
                publish(error)
            except Exception:
                publish(ReadRefused("UNAVAILABLE", request_submitted=True))

        task = asyncio.create_task(asyncio.to_thread(run))
        self._tasks.add(task)
        task.add_done_callback(self._tasks.discard)
        # Socket closure must not cancel the work that won the durable claim.
        result = await asyncio.shield(reply)
        if isinstance(result, ReadRefused):
            raise result
        return result
