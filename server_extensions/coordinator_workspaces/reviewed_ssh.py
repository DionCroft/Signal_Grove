"""The fixed, host-key-verified reviewed scheduler client on the coordinator."""
import asyncio
from dataclasses import replace
import json
from pathlib import Path
import re
from time import monotonic, sleep

from .coordinator import CODES, ReadRefused, _DEVICE, _HEX, _ID, _INSTANCE, _PROJECT, scope_for
from .cron_run_once_approval import RunOnceScope
from .reviewed_worker import OriginalWorkerReceipt, ProjectWorkerRoute
from .ssh_transport import SingleCallSsh

if __package__ == "gateway.signal_grove_workspaces":
    from .approval_envelope import ApprovalRefused, MAX_APPROVAL_BYTES, _read, _matches
else:
    from ..worker_workspaces.approval_envelope import ApprovalRefused, MAX_APPROVAL_BYTES, _read, _matches


_MAX_CALL = MAX_APPROVAL_BYTES + 4096
_ALIAS = re.compile(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,126}")
_STATES = {"ready", "claimed", "running", "completed", "failed", "cancelled", "unknown"}
_TERMINAL = {"completed", "failed", "cancelled", "unknown"}
_REVIEW = {"authority", "scope_id", "profile", "project", "workspace", "branch", "commit",
           "worker", "worker_instance", "checkout_revision", "write_policy_revision"}


def reviewed_ssh_argv(executable, config, alias, command):
    """Build argv only from enrolled local configuration, with the command fixed per route."""
    if (not Path(executable).is_absolute() or not Path(config).is_absolute()
            or not _matches(_ALIAS, alias) or type(command) is not str
            or not command or len(command) > 4096 or command.startswith("-")):
        raise ReadRefused("INVALID")
    return (str(executable), "-T", "-F", str(config), "-oBatchMode=yes", "-oStrictHostKeyChecking=yes",
            "-oVerifyHostKeyDNS=no", "-oUpdateHostKeys=no", "-oClearAllForwardings=yes",
            "-oForwardAgent=no", "-oControlPath=none",
            alias, command)


class ReviewedWorkerSsh:
    """Prepare a reviewed scope, submit once, or read one original durable state."""

    def __init__(self, authority, worker, argv, *, timeout=25.0, receipt_wait=120.0):
        if (type(authority) is not str or not 1 <= len(authority) <= 256
                or not _matches(_DEVICE, worker) or type(receipt_wait) not in (int, float)
                or not 0 <= receipt_wait <= 600):
            raise ReadRefused("INVALID")
        self._authority = authority
        self._worker = worker
        self._exchange = SingleCallSsh(argv, timeout=timeout)
        self._receipt_wait = receipt_wait

    def _call(self, operation, scope, profile, project, *, expected=None, **fields):
        if (not _matches(_HEX, scope) or not _matches(_PROJECT, project)
                or type(profile) is not str or not 1 <= len(profile) <= 256):
            raise ReadRefused("INVALID")
        request = {"action": "workspace_reviewed", "scope_id": scope, "worker_instance": expected,
                   "payload": {"operation": operation, "scope_id": scope, "profile": profile,
                               "project_id": project, **fields}}
        try:
            raw = json.dumps(request, ensure_ascii=False, allow_nan=False, separators=(",", ":")).encode("utf-8")
        except (TypeError, ValueError, RecursionError):
            raise ReadRefused("INVALID") from None
        if len(raw) > _MAX_CALL:
            raise ReadRefused("TOO_LARGE")
        submitting = operation == "reviewed_submit"
        response = asyncio.run(self._exchange._exchange(raw, reading=submitting))
        if (response.get("transport") == "error" and set(response) == {"transport", "code", "request_submitted"}
                and type(response["code"]) is str and response["code"] in CODES
                and type(response["request_submitted"]) is bool):
            raise ReadRefused(response["code"], request_submitted=submitting and response["request_submitted"])
        reply = response.get("reply")
        if (set(response) != {"transport", "reply"} or response["transport"] != "reply" or type(reply) is not dict
                or type(reply.get("schema")) is not int or reply["schema"] != 1
                or not _matches(_ID, reply.get("request_id")) or not _matches(_INSTANCE, reply.get("worker_instance"))
                or (expected is not None and reply["worker_instance"] != expected)):
            raise ReadRefused("CHANGED", request_submitted=submitting)
        common = {"schema", "request_id", "worker_instance", "status"}
        if (reply.get("status") == "error" and set(reply) == common | {"code"}
                and type(reply["code"]) is str and reply["code"] in CODES):
            raise ReadRefused(reply["code"], request_submitted=submitting)
        if reply.get("status") != "ok" or set(reply) != common | {"result"} or type(reply["result"]) is not dict:
            raise ReadRefused("INVALID", request_submitted=submitting)
        return reply["worker_instance"], reply["result"]

    def prepare(self, actor, profile, project, task, *, policy_revision):
        scope = scope_for(self._authority, actor.subject, actor.profile_id)
        instance, result = self._call("reviewed_prepare", scope, profile, project)
        if (set(result) != _REVIEW
                or (result["authority"], result["scope_id"], result["profile"], result["project"],
                    result["worker"], result["worker_instance"]) != (
                    self._authority, scope, profile, project, self._worker, instance)):
            raise ReadRefused("CHANGED")
        prepared = RunOnceScope(
            project, result["workspace"], result["branch"], self._worker, instance, "write", policy_revision,
            task, result["commit"], result["checkout_revision"], result["write_policy_revision"],
        )
        prepared.values()
        return prepared

    def refresh_scope(self, actor, profile, expected):
        observed = self.prepare(actor, profile, expected.project, expected.task,
                                policy_revision=expected.policy_revision)
        local = replace(observed, checkout_revision=expected.checkout_revision,
                        write_policy_revision=expected.write_policy_revision)
        if (local != expected or (expected.checkout_revision is not None
                and (expected.checkout_revision, expected.write_policy_revision) != (
                    observed.checkout_revision, observed.write_policy_revision))):
            raise ReadRefused("CHANGED")
        return observed

    @staticmethod
    def _receipt(result, ident, *, submitted=False):
        if (set(result) != {"id", "state"} or not _matches(_ID, ident) or result["id"] != ident
                or type(result["state"]) is not str or result["state"] not in _STATES):
            raise ReadRefused("CHANGED", request_submitted=submitted)
        return result

    def submit(self, raw):
        try:
            signed = _read(raw, MAX_APPROVAL_BYTES)
        except ApprovalRefused:
            raise ReadRefused("INVALID") from None
        approval = signed.get("approval")
        if (type(approval) is not dict or approval.get("authority") != self._authority
                or approval.get("worker") != self._worker or not _matches(_ID, approval.get("execution_id"))
                or not _matches(_INSTANCE, approval.get("worker_instance"))):
            raise ReadRefused("DENIED")
        _, result = self._call("reviewed_submit", approval.get("scope_id"), approval.get("profile"),
                               approval.get("project"), expected=approval["worker_instance"], approval=signed)
        return self._receipt(result, approval["execution_id"], submitted=True)

    def observe(self, selector):
        if (type(selector) is not OriginalWorkerReceipt or selector.worker != self._worker
                or not _matches(_ID, selector.execution_id) or not _matches(_INSTANCE, selector.worker_instance)):
            raise ReadRefused("DENIED")
        _, result = self._call("reviewed_observe", selector.scope_id, selector.profile, selector.project,
                               execution_id=selector.execution_id, receipt_worker_instance=selector.worker_instance)
        return self._receipt(result, selector.execution_id)

    def observe_until_settled(self, selector):
        deadline = monotonic() + self._receipt_wait
        while True:
            try:
                observed = self.observe(selector)
                if observed["state"] in _TERMINAL:
                    return observed
            except ReadRefused as error:
                if error.code not in {"NOT_FOUND", "UNAVAILABLE", "BUSY"}:
                    raise
            left = deadline - monotonic()
            if left <= 0:
                return None
            sleep(min(1.0, left))

    def project_route(self, key_directory, key_name):
        return ProjectWorkerRoute(Path(key_directory), key_name, self.submit, self.observe_until_settled)
