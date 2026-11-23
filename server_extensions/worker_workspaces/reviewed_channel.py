"""Private reviewed scheduler frames for an enrolled Windows owner pipe."""
from dataclasses import asdict, fields
import json
from threading import Event
from uuid import uuid4

from .approval_envelope import (
    ApprovalRefused, MAX_APPROVAL_BYTES, _HEX, _ID, _INSTANCE, _PROJECT, _json, _matches, _read, _text,
)
from .broker_protocol import CheckedReadContext
from .local_pipe import LocalReadPipe, _CODES, call_owner_frame
from .reviewed_operator import WriteReview, _QUEUE_STATES
from .workspace_provider import WorkspaceError


_REQUEST_LIMIT = MAX_APPROVAL_BYTES + 2048
_REPLY_LIMIT = 24 * 1024
_REVIEW_FIELDS = {field.name for field in fields(WriteReview)}
_BASE = {"scope_id", "profile", "project_id", "operation"}
_EXTRA = {
    "reviewed_prepare": set(),
    "reviewed_submit": {"approval"},
    "reviewed_observe": {"execution_id", "receipt_worker_instance"},
}


def _decode(raw, maximum):
    try:
        return _read(raw, maximum)
    except ApprovalRefused:
        raise WorkspaceError("INVALID") from None


def _encode(fields, maximum):
    try:
        raw = json.dumps(fields, ensure_ascii=False, allow_nan=False, separators=(",", ":")).encode("utf-8")
    except (TypeError, ValueError, RecursionError):
        raise WorkspaceError("INVALID") from None
    if not 1 <= len(raw) <= maximum:
        raise WorkspaceError("TOO_LARGE")
    return raw


def _request(raw, scope, instance):
    frame = _decode(raw, _REQUEST_LIMIT)
    if (set(frame) != {"schema", "request_id", "worker_instance", "payload"}
            or type(frame["schema"]) is not int or frame["schema"] != 1
            or not _matches(_ID, frame["request_id"]) or type(frame["payload"]) is not dict):
        raise WorkspaceError("INVALID")
    payload = frame["payload"]
    operation = payload.get("operation")
    if (type(operation) is not str or operation not in _EXTRA
            or set(payload) != _BASE | _EXTRA[operation]
            or not _text(payload["profile"], 256) or not _matches(_PROJECT, payload["project_id"])):
        raise WorkspaceError("INVALID")
    if frame["worker_instance"] != instance or payload["scope_id"] != scope:
        raise WorkspaceError("CHANGED")
    if operation == "reviewed_submit" and type(payload["approval"]) is not dict:
        raise WorkspaceError("INVALID")
    if operation == "reviewed_observe" and (not _matches(_ID, payload["execution_id"])
            or not _matches(_INSTANCE, payload["receipt_worker_instance"])):
        raise WorkspaceError("INVALID")
    return frame


def _reply(raw, frame, endpoint):
    response = _decode(raw, _REPLY_LIMIT)
    common = {"schema", "request_id", "worker_instance", "status"}
    if (type(response.get("schema")) is not int or response["schema"] != 1
            or response.get("request_id") != frame["request_id"]
            or response.get("worker_instance") != endpoint.worker_instance):
        raise WorkspaceError("CHANGED")
    if response.get("status") == "error":
        if set(response) != common | {"code"} or type(response["code"]) is not str or response["code"] not in _CODES:
            raise WorkspaceError("INVALID")
        return response
    if (response.get("status") != "ok" or set(response) != common | {"result"}
            or type(response["result"]) is not dict):
        raise WorkspaceError("INVALID")
    payload, result = frame["payload"], response["result"]
    operation = payload["operation"]
    if operation == "reviewed_prepare":
        if set(result) != _REVIEW_FIELDS or (result["scope_id"], result["profile"], result["project"], result["worker_instance"]) != (
                endpoint.scope_id, payload["profile"], payload["project_id"], endpoint.worker_instance):
            raise WorkspaceError("CHANGED")
    else:
        approval = payload.get("approval", {}).get("approval")
        ident = payload.get("execution_id") if operation == "reviewed_observe" else (
            approval.get("execution_id") if type(approval) is dict else None)
        if (set(result) != {"id", "state"} or not _matches(_ID, ident) or result["id"] != ident
                or type(result["state"]) is not str or result["state"] not in _QUEUE_STATES):
            raise WorkspaceError("CHANGED")
    return response


class ReviewedPipeAdapter:
    """Bind the fixed operations to one already enrolled native scope."""

    def __init__(self, operator, *, scope_id, instance, verify_scope):
        if not _matches(_HEX, scope_id) or not _matches(_INSTANCE, instance):
            raise WorkspaceError("INVALID")
        self._operator = operator
        self._scope = scope_id
        self.instance = instance
        self._verify_scope = verify_scope
        self._closed = Event()
        self._context()

    def _context(self):
        if self._closed.is_set():
            raise WorkspaceError("UNAVAILABLE")
        context = self._verify_scope()
        if type(context) is not CheckedReadContext or context.interactive is not True or context.scope_id != self._scope:
            raise WorkspaceError("DENIED")
        return context

    def close(self):
        self._closed.set()

    def handle(self, raw):
        request_id = None
        try:
            frame = _request(raw, self._scope, self.instance)
            request_id = frame["request_id"]
            before = self._context()
            payload = frame["payload"]
            scope = self._scope, payload["profile"], payload["project_id"]
            operation = payload["operation"]
            if operation == "reviewed_prepare":
                result = asdict(self._operator.prepare(*scope))
            elif operation == "reviewed_submit":
                result = self._operator.accept(_json(payload["approval"]), expected_route=(*scope, self.instance))
            else:
                result = self._operator.observe(*scope, payload["execution_id"], payload["receipt_worker_instance"])
            if self._context() != before:
                raise WorkspaceError("CHANGED")
            response = {"status": "ok", "result": result}
        except WorkspaceError as error:
            response = {"status": "error", "code": error.code}
        except ApprovalRefused as error:
            response = {"status": "error", "code": str(error)}
        except Exception:
            response = {"status": "error", "code": "UNAVAILABLE"}
        return _encode({"schema": 1, "request_id": request_id, "worker_instance": self.instance, **response}, _REPLY_LIMIT)


def reviewed_pipe(adapter):
    return LocalReadPipe(adapter, request_limit=_REQUEST_LIMIT, reply_limit=_REPLY_LIMIT)


def reviewed_call(endpoint, payload, *, timeout=10):
    raw = _encode({"schema": 1, "request_id": uuid4().hex, "worker_instance": endpoint.worker_instance,
                   "payload": payload}, _REQUEST_LIMIT)
    reply = call_owner_frame(
        endpoint, raw, request_check=lambda frame, end: _request(frame, end.scope_id, end.worker_instance),
        reply_check=_reply, reply_limit=_REPLY_LIMIT, timeout=timeout,
    )
    return _decode(reply, _REPLY_LIMIT)
