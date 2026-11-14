"""Shared native directory boundary for publication, journal and PM inputs.

Read-only capture/check/open only. No mkdir, chmod, discovery, fallback, repair
or approval. Future backup/executor adapters must bind their actual file inputs
and effects separately; protecting a parent is not backup or health acceptance.
"""
from dataclasses import dataclass,asdict
from pathlib import Path

from cutover_journal import Refused,Unknown
from native_file_publication import DirectoryAnchor,NativeAncestry,digest,_hash

ROLES=('backup_identity','backup_output','backup_programs','executor','hermes_home',
       'journal','new_source','new_store','old_source','old_store')
PRIVATE=frozenset({'backup_identity','backup_output','executor','journal'})


@dataclass(frozen=True)
class OperationalDirectories:
    directories:tuple[tuple[str,DirectoryAnchor],...]

    def __post_init__(self):
        if (type(self.directories) is not tuple or len(self.directories)!=len(ROLES)
                or any(type(v) is not tuple or len(v)!=2 for v in self.directories)
                or tuple(n for n,d in self.directories)!=ROLES
                or any(type(d) is not DirectoryAnchor or d.mode not in ((0o700,) if n in PRIVATE else (0o700,0o755)) for n,d in self.directories)
                or len({d.path for n,d in self.directories})!=len(ROLES)):
            raise ValueError('Exact ten distinct owner-directory roles and private operational roots required')

    @property
    def sha256(self):return digest(asdict(self))


class NativeOperationalPaths:
    """One shared ancestry witness, including actual publication transitions.

    Capture before any mode/parent effects. All actors must share `ancestry`;
    independently recaptured guards cannot inherit transition authority. Owned
    scope deliberately proves nothing about ancestors above its private root.
    """
    @classmethod
    def capture(cls,publication,publication_sha256,directories,directories_sha256):
        if (type(publication) is not NativeAncestry or not _hash(publication_sha256) or publication.sha256!=publication_sha256
                or type(directories) is not OperationalDirectories or directories.sha256!=directories_sha256):
            raise Refused('Exact reviewed publication and operational catalogs required')
        publication.check()
        initial=digest(dict(catalog_sha256=publication.sha256,secured=[],created=[]))
        if publication.state_sha256!=initial:raise Refused('Operational paths must be bound before any publication transition')
        mode_paths={m.directory.path for m in publication.modes}
        if any(d.path in mode_paths for n,d in directories.directories):
            raise Refused('Operational endpoints cannot themselves be mutable mode targets')
        endpoints=tuple(sorted(set(publication.endpoints)|{d.path for n,d in directories.directories}))
        combined=NativeAncestry.capture(publication.scope,publication.root,endpoints,publication.modes,publication.creations,
            digest(dict(publication_ancestry_sha256=publication.sha256,operational_directories_sha256=directories.sha256)))
        nodes={n['path']:n for n in combined.metadata()['directories']}
        if any(nodes.get(d.path)!=asdict(d) for n,d in directories.directories):
            raise Unknown('Operational endpoint differs from its reviewed native identity')
        publication.check()
        self=cls();self.ancestry=combined;self.directories=directories;self.publication_sha256=publication.sha256
        self._sha256=digest(dict(publication_sha256=publication.sha256,directories_sha256=directories.sha256,ancestry_sha256=combined.sha256))
        self.check();return self

    @property
    def sha256(self):return self._sha256

    @property
    def scope(self):return self.ancestry.scope

    def check(self):
        current=self.ancestry.check()
        expected=digest(dict(publication_sha256=self.publication_sha256,directories_sha256=self.directories.sha256,ancestry_sha256=self.ancestry.sha256))
        if expected!=self._sha256:raise Unknown('Bound operational directory catalog changed')
        return digest(dict(operational_sha256=self.sha256,ancestry_state_sha256=current))

    def require(self,role,path):
        if role not in ROLES or str(path)!=dict(self.directories.directories)[role].path:
            raise Refused('Operational role/path differs from the reviewed catalog')
        self.check();return dict(self.directories.directories)[role]

    def contains(self,role,path):
        """Binding check only; nested file identity/content are separate gates."""
        if role not in ROLES:raise Refused('Unknown operational directory role')
        root=Path(dict(self.directories.directories)[role].path);path=Path(path)
        if path.resolve()!=path or not path.is_relative_to(root):raise Refused('Operational input lies outside its bound root')
        self.check()

    def open_directory(self,role):
        if role not in ROLES:raise Refused('Unknown operational directory role')
        self.check();return self.ancestry.open_directory(dict(self.directories.directories)[role])

    def metadata(self):
        self.check()
        return dict(scope=self.scope,publication_sha256=self.publication_sha256,operational_sha256=self.sha256,
            directories=asdict(self.directories)['directories'],ancestry=self.ancestry.metadata(),state_sha256=self.check())
