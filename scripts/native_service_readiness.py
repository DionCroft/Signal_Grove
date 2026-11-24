"""Observe authenticated readiness of the exact running systemd service."""

from contextlib import closing
import re

from cutover_journal import Refused, Unknown
from native_gateway_control import NativeGatewayControl
from native_retirement_owner import LinuxSocketOwner
from native_service_manager import NativeServiceManager, UnitSnapshot
from pinned_retirement_http import PinnedRetirementHttp
from retirement_guard import Refused as HttpRefused, Unknown as HttpUnknown


def _running(manager, role, scope, expected):
    if (type(manager) is not NativeServiceManager or type(expected) is not UnitSnapshot
            or scope not in ("forward", "recovery")):
        raise Refused("A reviewed running service and cutover scope are required")
    binding = manager.bindings[role]
    if scope == "forward":
        configuration, executable = binding.new_configuration_sha256, binding.new_executable
    else:
        configuration, executable = binding.old_configuration_sha256, binding.old_executable
    current = manager.snapshot(role)
    if (current != expected or current.unit != binding.unit
            or current.configuration_sha256 != configuration or current.executable != executable
            or current.load_state != "loaded" or current.active_state != "active"
            or current.sub_state != "running" or current.result != "success"
            or current.owner is None or current.needs_daemon_reload or current.job_id or current.control_pid):
        raise Unknown("The reviewed running service incarnation is unproven")
    return current


def observe_gateway_readiness(manager, scope, expected, connected_socket, home_digest):
    """Read a single registered-work status over the native gateway control socket."""
    with closing(connected_socket):
        before = _running(manager, "gateway", scope, expected)
        if type(home_digest) is not str or not re.fullmatch("[a-f0-9]{64}", home_digest):
            raise Refused("The reviewed gateway home digest is required")
        with closing(NativeGatewayControl(before.owner, connected_socket)) as control:
            status = control.registered_status(home_digest)
        _running(manager, "gateway", scope, before)
        if status["lifecycle"] != "running" or status["admission_frozen"]:
            raise Unknown("The gateway has not reopened registered work admission")
        return dict(role="gateway", unit=before.unit, snapshot_sha256=before.sha256,
                    coverage=status["coverage"], busy=status["busy"])


def observe_serve_readiness(manager, scope, expected, connected_socket, access_token):
    """Read authenticated Serve identity and its separate idle observation."""
    with closing(connected_socket):
        before = _running(manager, "serve", scope, expected)
        try:
            with closing(PinnedRetirementHttp(before.owner, connected_socket, access_token,
                                              LinuxSocketOwner(before.owner), timeout=2.0)) as transport:
                answer, identity = transport("GET", "/api/host/identity", None)
                if (answer != 200 or identity.get("ok") is not True or identity.get("role") != "serve"
                        or type(identity.get("pid")) is not int or identity["pid"] != before.main_pid
                        or type(identity.get("protocolVersion")) is not int or identity["protocolVersion"] != 1):
                    raise Unknown("Authenticated Serve identity is unproven")
                answer, status = transport("GET", "/api/health/idle", None)
                if (answer != 200 or status.get("ok") is not True or "idle" not in status
                        or (status["idle"] is not None and type(status["idle"]) is not bool)):
                    raise Unknown("Authenticated Serve health response is unproven")
        except (HttpRefused, HttpUnknown, ValueError, OSError):
            raise Unknown("Authenticated Serve readiness is unproven") from None
        _running(manager, "serve", scope, before)
        return dict(role="serve", unit=before.unit, snapshot_sha256=before.sha256, idle=status["idle"])
