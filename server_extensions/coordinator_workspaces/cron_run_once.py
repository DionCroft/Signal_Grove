"""Original receipt identity for an owner-reviewed, out-of-schedule execution."""

from dataclasses import dataclass
import hashlib

from .coordinator import ReadRefused, encode
from .cron_history import execution_query, read_executions

RUN_ONCE_SOURCE = "signal-grove-run-once-v1"


@dataclass(frozen=True)
class RunOnceReceipt:
    profile: str
    job_id: str
    request_id: str

    def execution_id(self):
        # A request stays addressable after a gateway or process restart.
        key = encode({"profile": self.profile, "job_id": self.job_id, "request_id": self.request_id})
        return hashlib.sha256(b"signal-grove-cron-run-once-v1\0" + key).hexdigest()[:32]


def run_once_receipt_query(params):
    if type(params) is not dict or set(params) != {"expected_profile", "job_id", "request_id"}:
        raise ReadRefused("INVALID")
    selected = execution_query({"expected_profile": params["expected_profile"],
                                "job_id": params["job_id"], "execution_id": params["request_id"]})
    return RunOnceReceipt(selected.profile, selected.job_id, selected.execution_id)


def observe_run_once(home, receipt):
    selected = execution_query({"expected_profile": receipt.profile, "job_id": receipt.job_id,
                                "execution_id": receipt.execution_id()})
    result = read_executions(home, selected, required_source=RUN_ONCE_SOURCE)
    return {"profile": receipt.profile, "job_id": receipt.job_id, "request_id": receipt.request_id,
            "execution": result["attempts"][0]}
