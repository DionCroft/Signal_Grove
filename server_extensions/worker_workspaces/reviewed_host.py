"""Opt-in lifecycle and one private SSH action for the reviewed Windows broker."""
from contextlib import closing
import json
import sqlite3
import time

from .approval_envelope import ApprovalRefused, _INSTANCE, _PROJECT, _HEX, _matches, _read
from .broker_enrollment import observe_owner
from .local_pipe import ReadTransportError
from .operator_file import read_operator_file
from .protected_operator_file import read_protected_file
from .read_host import ReadHost, encode_projection, select_projection
from .reviewed_channel import reviewed_call, reviewed_pipe, ReviewedPipeAdapter
from .reviewed_operator import ReviewedWorkerOperator, parse_write_policy
from .workspace_provider import WorkspaceError, _root


def _write_policy(control):
    owner = observe_owner()
    path = control / "workspace-write-policy.json"
    policy = parse_write_policy(read_protected_file(str(path), owner_sid=owner.owner_sid, maximum=32768))
    if policy.device != owner.device or policy.owner_sid != owner.owner_sid:
        raise WorkspaceError("DENIED")
    return owner, policy


def _authorize_transport(control, scope, project):
    owner, policy = _write_policy(control)
    if not any(grant.scope_id == scope and grant.project == project for grant in policy.grants):
        raise WorkspaceError("DENIED")
    return owner, policy


def _maintenance(control):
    if (control / "workspace-maintenance.json").exists():
        raise WorkspaceError("BUSY")


class ReviewedHost:
    """Start only for scopes already enrolled in the existing owner broker."""

    def __init__(self, worker, control, read_sessions):
        owner, policy = _write_policy(control)
        if not owner.active or worker.HOST_CONTEXT is not True or policy.device != worker.DEVICE:
            raise WorkspaceError("DENIED")
        try:
            config = _read(read_protected_file(str(control / "workspace-write-git.json"),
                                               owner_sid=owner.owner_sid, maximum=8192), 8192)
        except ApprovalRefused:
            raise WorkspaceError("INVALID") from None
        if set(config) != {"schema", "git_executable"} or type(config["schema"]) is not int or config["schema"] != 1:
            raise WorkspaceError("INVALID")
        _root(config["git_executable"])
        sessions = {session.scope: session for session in read_sessions}
        scopes = {grant.scope_id for grant in policy.grants}
        if not scopes or not scopes <= sessions.keys():
            raise WorkspaceError("DENIED")
        self._worker = worker

        chosen = [sessions[scope] for scope in sorted(scopes)]
        by_adapter = {session.adapter: session for session in chosen}

        def provider(scope):
            _maintenance(worker.STATE)
            session = sessions.get(scope)
            if session is None:
                raise WorkspaceError("DENIED")
            session.checked_context()
            return session._provider

        def read_queue():
            path = (worker.STATE / "requests.sqlite3").absolute()
            return closing(sqlite3.connect(path.as_uri() + "?mode=ro", uri=True, timeout=1))

        self._operator = ReviewedWorkerOperator(
            policy_path=str(control / "workspace-write-policy.json"), key_path=str(control / "workspace-write-key.bin"),
            provider_for_scope=provider, queue_db=worker.queue_db, read_queue=read_queue,
            git_executable=config["git_executable"],
        )

        def pipe_for(read_adapter):
            session = by_adapter[read_adapter]
            adapter = ReviewedPipeAdapter(self._operator, scope_id=session.scope,
                                          instance=session.adapter.instance, verify_scope=session.checked_context)
            return reviewed_pipe(adapter)

        self._operator._credentials()
        self._host = ReadHost(chosen, pipe_factory=pipe_for, owns_sessions=False, exchange_timeout=10).start()

    def lookup(self, ident):
        _maintenance(self._worker.STATE)
        return self._operator.lookup(ident)

    def tick(self):
        raw = encode_projection(self._host.statuses(), device=self._worker.DEVICE, observed=time.time())
        self._worker.atomic(self._worker.STATE / "workspace-reviewed.safe.json", json.loads(raw))

    def close(self):
        self._host.close()
        settled = self._host.wait_closed()
        self.tick()
        return settled


def rpc(worker, control, data):
    """Route one SSH stdio request through the current local authenticated pipe."""
    if (type(data) is not dict or set(data) != {"action", "scope_id", "worker_instance", "payload"}
            or data["action"] != "workspace_reviewed" or not _matches(_HEX, data["scope_id"])
            or type(data["payload"]) is not dict):
        raise WorkspaceError("INVALID")
    scope, expected, payload = data["scope_id"], data["worker_instance"], data["payload"]
    operation, project = payload.get("operation"), payload.get("project_id")
    if (type(operation) is not str or operation not in ("reviewed_prepare", "reviewed_submit", "reviewed_observe")
            or not _matches(_PROJECT, project) or payload.get("scope_id") != scope
            or (operation == "reviewed_submit" and not _matches(_INSTANCE, expected))
            or (operation != "reviewed_submit" and expected is not None)):
        raise WorkspaceError("INVALID")
    if operation == "reviewed_submit":
        _maintenance(worker.STATE)
    _, policy = _authorize_transport(control, scope, project)
    path = worker.STATE / "workspace-reviewed.safe.json"
    raw = read_operator_file(str(path), maximum=32768)
    status = select_projection(raw, scope_id=scope, device=worker.DEVICE, now=time.time())
    if status.state != "ready" or status.endpoint is None:
        raise WorkspaceError("UNAVAILABLE")
    if expected is not None and expected != status.endpoint.worker_instance:
        raise WorkspaceError("CHANGED")
    reply = reviewed_call(status.endpoint, payload)
    try:
        _, current = _authorize_transport(control, scope, project)
        if current.revision != policy.revision:
            raise WorkspaceError("CHANGED")
    except WorkspaceError as error:
        raise ReadTransportError(error.code, request_submitted=operation == "reviewed_submit") from None
    return {"transport": "reply", "reply": reply}
