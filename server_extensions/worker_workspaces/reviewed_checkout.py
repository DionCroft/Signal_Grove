"""Verify the enrolled worker's checkout at review and admission.

Only the owning broker calls this module with its existing provider and an
operator-selected Git executable. A checkout observation grants no write access.
"""
from contextlib import contextmanager
from dataclasses import dataclass
import os
from pathlib import Path
import re
import subprocess

from .workspace_provider import RegisteredProject, WorkspaceError, _normal


_COMMIT = re.compile(r"(?:[a-f0-9]{40}|[a-f0-9]{64})")
_MAX_HEAD_BYTES = 1024


@dataclass(frozen=True, repr=False)
class ReviewedCheckout:
    registration: RegisteredProject
    worker_instance: str
    root: str
    root_identity: tuple[int, int]
    git_identity: tuple[int, int]
    branch: str
    commit: str


def _git_output(git_executable, root, arguments):
    git = Path(git_executable)
    if not git.is_absolute() or git.name.lower() != "git.exe" or not git.is_file():
        raise WorkspaceError("UNAVAILABLE")
    # The caller's env may select another checkout, expose secrets, or prompt.
    environment = {
        name: os.environ[name] for name in ("SystemRoot", "WINDIR") if name in os.environ
    }
    environment.update(
        GIT_CONFIG_NOSYSTEM="1",
        GIT_CONFIG_GLOBAL=os.devnull,
        GIT_TERMINAL_PROMPT="0",
    )
    argv = [
        str(git), "--no-optional-locks", "--no-replace-objects", "--no-lazy-fetch",
        "--git-dir=.git", *arguments,
    ]
    try:
        result = subprocess.run(
            argv, cwd=root, env=environment, stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
            timeout=3, creationflags=subprocess.CREATE_NO_WINDOW, check=True,
        )
        if not 1 <= len(result.stdout) <= 32768:
            raise ValueError()
        value = result.stdout.decode("utf-8")
        if not value.endswith("\n"):
            raise ValueError()
        value = value.removesuffix("\n").removesuffix("\r")
        if not value or any(ord(character) < 32 or ord(character) == 127 for character in value):
            raise ValueError()
        return value
    except (OSError, ValueError, subprocess.SubprocessError):
        raise WorkspaceError("UNAVAILABLE") from None


@contextmanager
def _observe(provider, project_id, git_executable):
    registration = provider._binding(project_id)
    with provider._root_handle(registration) as (files, root, _, root_meta):
        # The root and its ancestors are held by the provider. Require standard
        # in-place Git metadata until linked metadata has its own enrollment.
        git_directory = root / ".git"
        with files.opened(git_directory, directory=True) as (_, git_meta):
            if _normal(git_meta.path) != _normal(str(git_directory)):
                raise WorkspaceError("CHANGED")
            head = git_directory / "HEAD"
            # Read sharing alone prevents replacing HEAD while this context is held.
            with files.opened(head, directory=False, contents=True) as (handle, head_meta):
                if _normal(head_meta.path) != _normal(str(head)):
                    raise WorkspaceError("CHANGED")
                if not 1 <= head_meta.size <= _MAX_HEAD_BYTES:
                    raise WorkspaceError("UNSUPPORTED")
                raw = files.read(handle, head_meta.size)
                if len(raw) != head_meta.size or not raw.startswith(b"ref: refs/heads/"):
                    raise WorkspaceError("UNSUPPORTED")
                top = _git_output(git_executable, root_meta.path, ("rev-parse", "--show-toplevel"))
                if _normal(top) != _normal(root_meta.path):
                    raise WorkspaceError("CHANGED")
                ref = _git_output(git_executable, root_meta.path, ("symbolic-ref", "--quiet", "--no-recurse", "HEAD"))
                if raw.replace(b"\r\n", b"\n") != f"ref: {ref}\n".encode("utf-8"):
                    raise WorkspaceError("CHANGED")
                commit = _git_output(git_executable, root_meta.path, ("rev-parse", "--verify", "HEAD^{commit}"))
                if not _COMMIT.fullmatch(commit) or provider._binding(project_id) != registration:
                    raise WorkspaceError("CHANGED")
                yield ReviewedCheckout(
                    registration, provider.instance, root_meta.path, root_meta.identity,
                    git_meta.identity, ref.removeprefix("refs/heads/"), commit,
                )


def observe_checkout(provider, project_id, *, git_executable):
    """Capture the current root, branch and commit before asking for approval."""
    with _observe(provider, project_id, git_executable) as current:
        return current


@contextmanager
def hold_reviewed_checkout(provider, expected, *, git_executable):
    """Recheck a trusted reviewed snapshot immediately before local admission.

    Keep this context through the worker's admission boundary. A future process
    boundary still needs its own check. No work is scheduled by this function.
    """
    if (not isinstance(expected, ReviewedCheckout)
            or not isinstance(expected.registration, RegisteredProject)):
        raise WorkspaceError("INVALID")
    if (expected.worker_instance != provider.instance or expected.registration.device != provider.device
            or expected.registration.scope_id != provider.scope):
        raise WorkspaceError("CHANGED")
    with _observe(provider, expected.registration.project_id, git_executable) as current:
        if current != expected:
            raise WorkspaceError("CHANGED")
        yield current
