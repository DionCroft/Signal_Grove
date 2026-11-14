"""Explicit operator grants from fresh protected observations; no route discovery.

The operator supplies fixed transports and a trusted reader. Phone parameters
cannot provide policy, authority, subject, profile or command identity.
"""
from dataclasses import dataclass, field
import hashlib
from pathlib import Path
import re
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


def _rules(document, authority, devices):
    try:
        data = decode(document.data, MAX_POLICY)
        if (set(data) != {'schema','authority','grants'} or type(data['schema']) is not int
                or data['schema'] != 1 or data['authority'] != authority
                or type(data['grants']) is not list or len(data['grants']) > 64):
            raise ValueError('Invalid operator policy')
        rules = {}
        for item in data['grants']:
            if type(item) is not dict or set(item) != {'subject','profile_id','device','project_ids'}:
                raise ValueError('Invalid grant')
            subject, profile, device, projects = (item[k] for k in ('subject','profile_id','device','project_ids'))
            if (not _identity(subject) or not _identity(profile) or type(device) is not str
                    or device not in devices or not Path(profile).is_absolute()
                    or '..' in Path(profile).parts or str(Path(profile)) != profile
                    or type(projects) is not list or not 1 <= len(projects) <= 32
                    or any(type(p) is not str or not _PROJECT.fullmatch(p) for p in projects)
                    or len(set(projects)) != len(projects)):
                raise ValueError('Invalid grant scope')
            key = (subject, profile, device)
            if key in rules: raise ValueError('Duplicate grant')
            rules[key] = frozenset(projects)
        return rules
    except Exception:
        raise ReadRefused('UNAVAILABLE') from None


def build_operator_service(reader, *, authority, routes):
    """Bind an operator-owned fresh reader and fixed routes; never installs policy.

    Protected edits affect new reads. Both the data and reader's file generation
    enter the revision, so replacement or revoke/restore cannot reuse an old
    in-flight verdict. Caller authentication is still the native adapter's job.
    """
    if not callable(reader): raise ReadRefused('INVALID')
    def grant(context, device):
        document = _document(reader)
        rules = _rules(document, authority, service._routes)
        projects = rules.get((context.subject, context.profile_id, device))
        if projects is None: raise ReadRefused('DENIED')
        revision = hashlib.sha256(b'signal-grove-operator-policy-v1\0'
                                  + document.version.encode('ascii') + document.data).hexdigest()
        return ReadGrant(revision, projects)
    service = CoordinatorReadService(authority, routes, grant=grant)
    _rules(_document(reader), authority, service._routes)
    return service
