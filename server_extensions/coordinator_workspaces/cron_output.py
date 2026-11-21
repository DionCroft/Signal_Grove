"""Bind the stock cron archive to its exact durable execution under the write fence."""

import hashlib
import json
import os
from pathlib import Path
import re
import sys
import uuid

from .coordinator import ReadRefused, decode
from .cron_history import execution_query, read_executions
from .policy_file import _stamp, open_owner_directory, read_owner_file

_ARCHIVE = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{2}-[0-9]{2}-[0-9]{2}\.md")
_STORED_ID = re.compile(r"[a-f0-9]{32}\.json")
_DIGEST = re.compile(r"[a-f0-9]{64}")
_LINKS = ".signal-grove-receipts"
_MAX_LINK = 4096
_MAX_ARCHIVE = 512 * 1024
_MAX_PREVIEW = 16 * 1024
_KEEP_LINKS = 50


def output_query(params):
    if type(params) is not dict or set(params) != {"expected_profile", "job_id", "execution_id"}:
        raise ReadRefused("INVALID")
    return execution_query(params)


def _read_named(parent, name, maximum):
    try:
        os.stat(name, dir_fd=parent, follow_symlinks=False)
    except FileNotFoundError:
        return None
    return read_owner_file(parent, name, maximum)


def _same_directory(descriptor, observed):
    actual = os.fstat(descriptor)
    return (actual.st_dev, actual.st_ino) == (observed.st_dev, observed.st_ino)


def _prune_links(parent, current):
    old = []
    with os.scandir(parent) as directory:
        for entry in directory:
            if entry.name != current and _STORED_ID.fullmatch(entry.name) and entry.is_file(follow_symlinks=False):
                old.append((entry.stat(follow_symlinks=False).st_mtime_ns, entry.name))
    for _, name in sorted(old, reverse=True)[_KEEP_LINKS - 1:]:
        os.unlink(name, dir_fd=parent)


def _publish_link(parent, name, content):
    # Publish complete metadata once, even if a caller presents the same ID again.
    temporary = ".pending-" + uuid.uuid4().hex
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_CLOEXEC | os.O_NOFOLLOW
    fd = os.open(temporary, flags, 0o600, dir_fd=parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(temporary, name, src_dir_fd=parent, dst_dir_fd=parent, follow_symlinks=False)
    finally:
        os.unlink(temporary, dir_fd=parent)
    os.fsync(parent)


def bind_output(job_id, execution_id, archive, output):
    """Called by the native scheduler inside its existing output side-effect fence."""
    if sys.platform != "linux":
        return False
    try:
        query = output_query({"expected_profile": "default", "job_id": job_id, "execution_id": execution_id})
        archive = Path(archive)
        if (not _ARCHIVE.fullmatch(archive.name) or archive.parent.name != query.job_id
                or archive.parent.parent.name != "output" or archive.parent.parent.parent.name != "cron"
                or type(output) is not str or len(output) > _MAX_ARCHIVE):
            return False
        written = output.encode("utf-8")
        if not 1 <= len(written) <= _MAX_ARCHIVE:
            return False
        with open_owner_directory(archive.parent) as parent:
            raw, identity = read_owner_file(parent, archive.name, _MAX_ARCHIVE)
            if raw != written:
                return False
            record = {
                "schema": 1, "job_id": query.job_id, "execution_id": query.execution_id,
                "archive": archive.name, "sha256": hashlib.sha256(raw).hexdigest(),
                "identity": list(identity),
            }
            content = json.dumps(record, separators=(",", ":"), allow_nan=False).encode("ascii")
            try:
                os.mkdir(_LINKS, 0o700, dir_fd=parent)
            except FileExistsError:
                pass
            name = query.execution_id + ".json"
            with open_owner_directory(archive.parent / _LINKS) as links:
                directory = os.stat(_LINKS, dir_fd=parent, follow_symlinks=False)
                if not _same_directory(links, directory):
                    return False
                _publish_link(links, name, content)
                _prune_links(links, name)
        return True
    except (OSError, ReadRefused, ValueError, TypeError):
        return False


def _parse_link(raw, query):
    record = decode(raw, maximum=_MAX_LINK)
    if (set(record) != {"schema", "job_id", "execution_id", "archive", "sha256", "identity"}
            or type(record["schema"]) is not int or record["schema"] != 1
            or record["job_id"] != query.job_id or record["execution_id"] != query.execution_id
            or type(record["archive"]) is not str or not _ARCHIVE.fullmatch(record["archive"])
            or type(record["sha256"]) is not str or not _DIGEST.fullmatch(record["sha256"])
            or type(record["identity"]) is not list or len(record["identity"]) != 9
            or any(type(item) is not int for item in record["identity"])):
        raise ReadRefused("UNAVAILABLE")
    return record


def _load_archive(directory, query):
    name = query.execution_id + ".json"
    try:
        os.lstat(directory)
    except FileNotFoundError:
        return "unlinked", None
    with open_owner_directory(directory) as archive_parent:
        try:
            binding_entry = os.stat(_LINKS, dir_fd=archive_parent, follow_symlinks=False)
        except FileNotFoundError:
            return "unlinked", None
        with open_owner_directory(directory / _LINKS) as bindings:
            if not _same_directory(bindings, binding_entry):
                raise ReadRefused("CHANGED")
            link = _read_named(bindings, name, _MAX_LINK)
            if link is None:
                return "unlinked", None
            record = _parse_link(link[0], query)
            archive = _read_named(archive_parent, record["archive"], _MAX_ARCHIVE)
            if archive is None:
                return "missing", None
            raw, observed = archive
            if record["identity"] != list(observed) or hashlib.sha256(raw).hexdigest() != record["sha256"]:
                return "changed", None
            with open_owner_directory(directory) as current:
                current_bindings = os.stat(_LINKS, dir_fd=current, follow_symlinks=False)
                current_archive = os.stat(record["archive"], dir_fd=current, follow_symlinks=False)
                if (not _same_directory(archive_parent, os.fstat(current))
                        or not _same_directory(bindings, current_bindings)
                        or _stamp(current_archive) != observed
                        or link != _read_named(bindings, name, _MAX_LINK)):
                    raise ReadRefused("CHANGED")
            return "available", raw


def read_output(home, query):
    """Read a bounded archive only while its exact execution remains in the owner ledger."""
    if query.execution_id is None:
        raise ReadRefused("INVALID")
    read_executions(home, query)
    try:
        archive_state, raw = _load_archive(Path(home) / "cron" / "output" / query.job_id, query)
        receipt = read_executions(home, query)["attempts"][0]
        preview = None
        if raw is not None:
            raw.decode("utf-8")  # Reject an invalid archive before clipping at a character boundary.
            preview = raw[:_MAX_PREVIEW].decode("utf-8", errors="ignore")
        return {
            "profile": query.profile, "job_id": query.job_id, "execution_id": query.execution_id,
            "status": receipt["status"], "archive_state": archive_state, "text": preview,
            "limited": raw is not None and len(raw) > _MAX_PREVIEW,
        }
    except (OSError, ValueError, TypeError):
        raise ReadRefused("UNAVAILABLE") from None
