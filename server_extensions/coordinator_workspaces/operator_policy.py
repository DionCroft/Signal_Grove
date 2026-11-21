"""Explicit operator grants from fresh protected observations; no route discovery.

The operator supplies fixed transports and a trusted reader. Phone parameters
cannot provide policy, authority, subject, profile or command identity.
"""
from dataclasses import dataclass, field
import hashlib
from pathlib import Path, PureWindowsPath
import re
from .attached import AttachedWorkspaceReads, RegisteredAttachment
from .coordinator import (CoordinatorReadService, ReadGrant, ReadRefused,
                          _identity, _PROJECT, decode)

MAX_POLICY = 32 * 1024
_VERSION = re.compile(r'[a-f0-9]{64}')


@dataclass(frozen=True)
class PolicyDocument:
    data: bytes = field(repr=False)
    version: str = field(repr=False)

    def __repr__(self): return 'PolicyDocument(redacted)'


def _document(reader):
    try:
        value = reader()
        if (not isinstance(value, PolicyDocument) or type(value.data) is not bytes
                or not 1 <= len(value.data) <= MAX_POLICY or type(value.version) is not str
                or not _VERSION.fullmatch(value.version)):
            raise ValueError('Invalid protected observation')
        return value
    except Exception:
        raise ReadRefused('UNAVAILABLE') from None


def _absolute(value, path_type):
    if not _identity(value):
        return False
    path = path_type(value)
    return path.is_absolute() and '..' not in path.parts and str(path) == value


def _rules(document, authority, devices):
    try:
        data = decode(document.data, MAX_POLICY)
        schema = data.get('schema')
        expected = {'schema', 'authority', 'grants'}
        if schema == 2:
            expected.add('attachments')
        if (type(schema) is not int or schema not in (1, 2) or set(data) != expected
                or data['authority'] != authority
                or type(data['grants']) is not list or len(data['grants']) > 64):
            raise ValueError('Invalid operator policy')
        rules = {}
        for item in data['grants']:
            if type(item) is not dict or set(item) != {'subject','profile_id','device','project_ids'}:
                raise ValueError('Invalid grant')
            subject, profile, device, projects = (item[k] for k in ('subject','profile_id','device','project_ids'))
            if (not _identity(subject) or not _absolute(profile, Path)
                    or type(device) is not str or device not in devices
                    or type(projects) is not list or not 1 <= len(projects) <= 32
                    or any(type(p) is not str or not _PROJECT.fullmatch(p) for p in projects)
                    or len(set(projects)) != len(projects)):
                raise ValueError('Invalid grant scope')
            key = (subject, profile, device)
            if key in rules: raise ValueError('Duplicate grant')
            rules[key] = frozenset(projects)
        revision = hashlib.sha256(b'signal-grove-operator-policy-v1\0'
                                  + document.version.encode('ascii') + document.data).hexdigest()
        attachments = {}
        rows = data.get('attachments', [])
        if type(rows) is not list or len(rows) > 64:
            raise ValueError('Invalid attachment inventory')
        for item in rows:
            if type(item) is not dict or set(item) != {
                'subject', 'profile_id', 'session_id', 'coordinator_cwd', 'device', 'project_id', 'worker_root',
            }:
                raise ValueError('Invalid attachment')
            subject, profile, session = (item[key] for key in ('subject', 'profile_id', 'session_id'))
            device, project = item['device'], item['project_id']
            cwd, worker_root = item['coordinator_cwd'], item['worker_root']
            if (not _identity(subject) or not _absolute(profile, Path)
                    or not _identity(session) or len(session) > 256 or type(device) is not str
                    or type(project) is not str or project not in rules.get((subject, profile, device), ())
                    or not _absolute(cwd, Path) or not _absolute(worker_root, PureWindowsPath)):
                raise ValueError('Invalid attachment scope')
            path = PureWindowsPath(worker_root)
            if path.drive.startswith(('\\\\?\\', '\\\\.\\')) or len(path.parts) < 3:
                raise ValueError('Invalid worker root')
            key = (subject, profile, session)
            if key in attachments:
                raise ValueError('Duplicate session attachment')
            attachments[key] = RegisteredAttachment(device, project, cwd, worker_root, revision)
        return rules, attachments, revision
    except Exception:
        raise ReadRefused('UNAVAILABLE') from None


def build_operator_service(reader, *, authority, routes):
    """Bind an operator-owned fresh reader and fixed routes; never installs policy.

    Protected edits affect new reads. Both the data and reader's file generation
    enter the revision, so replacement or revoke/restore cannot reuse an old
    in-flight verdict. Caller authentication is still the native adapter's job.
    """
    if not callable(reader): raise ReadRefused('INVALID')
    def snapshot():
        return _rules(_document(reader), authority, service._routes)

    def grant(context, device):
        rules, _, revision = snapshot()
        projects = rules.get((context.subject, context.profile_id, device))
        if projects is None: raise ReadRefused('DENIED')
        return ReadGrant(revision, projects)

    def resolve_attachment(context, session_id):
        _, attachments, _ = snapshot()
        attachment = attachments.get((context.subject, context.profile_id, session_id))
        if attachment is None:
            raise ReadRefused('DENIED')
        return attachment

    service = CoordinatorReadService(authority, routes, grant=grant)
    snapshot()
    service.attachments = AttachedWorkspaceReads(service, resolve_attachment)
    return service
