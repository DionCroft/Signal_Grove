"""Process-local full-content proof with fresh descriptor-relative identity checks.

Capture performs the complete reviewed content verification before login. Later
checks compare all native file and directory identities, including Linux ctime,
without rehashing the tree while a short-lived HTTP connection is idle. No
external/cached witness is accepted. Checks are observations, not an atomic
filesystem freeze or permission to execute a transaction.
"""
import os
from pathlib import PurePosixPath
import stat
import sys

from backup_sdk_snapshot import SourceInventory,stamp
from cutover_journal import Refused,Unknown
from native_file_publication import digest
from native_operational_paths import NativeOperationalPaths


class VerifiedInventory:
    def __init__(self,*_):
        raise Refused('Capture a fresh process-local inventory witness')

    @classmethod
    def capture(cls,inventory,paths,role):
        if (cls is not VerifiedInventory or sys.platform!='linux' or os.geteuid()==0
            or type(inventory) is not SourceInventory or type(paths) is not NativeOperationalPaths):
            raise Refused('Exact native inventory/path boundary required')
        paths.require(role,inventory.root.path)
        self=object.__new__(cls);self._inventory=inventory;self._paths=paths;self._role=role;self._pid=os.getpid()
        # The only producer of this witness verifies every file's content first.
        self._files=inventory.observe();self._directories={};self._scan(capture=True)
        self._seal=self._fingerprint();self.check(inventory,paths,role)
        return self

    def _fingerprint(self):
        return digest(dict(inventory_sha256=self._inventory.sha256,paths_sha256=self._paths.sha256,role=self._role,
            pid=self._pid,files=self._files,directories=self._directories))

    def _scan(self,*,capture=False):
        # A depth-first walk retains only ancestor descriptors. Each intermediate
        # component is opened with O_NOFOLLOW; no path lookup traverses a link.
        groups={'':dict(files=[],children=set())}
        for name,_ in self._inventory.files:
            parts=PurePosixPath(name).parts
            if len(parts)>64:raise Refused('Prepared inventory depth exceeds the descriptor bound')
            current=''
            for part in parts[:-1]:
                child=part if not current else current+'/'+part
                groups.setdefault(child,dict(files=[],children=set()));groups[current]['children'].add(part);current=child
            groups[current]['files'].append((parts[-1],name))
        seen=set()
        def walk(fd,relative):
            before=os.fstat(fd);identity=stamp(before)
            if not stat.S_ISDIR(before.st_mode) or before.st_uid!=os.geteuid() or stat.S_IMODE(before.st_mode) not in (0o700,0o755):
                raise Unknown('Prepared source ancestry is unprotected')
            if capture:self._directories[relative]=identity
            elif self._directories.get(relative)!=identity:raise Unknown('Prepared source directory identity changed')
            seen.add(relative)
            for leaf,name in groups[relative]['files']:
                if stamp(os.stat(leaf,dir_fd=fd,follow_symlinks=False))!=self._files[name]:
                    raise Unknown('Prepared source file identity changed')
            for leaf in sorted(groups[relative]['children']):
                child=leaf if not relative else relative+'/'+leaf
                nested=os.open(leaf,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC,dir_fd=fd)
                try:
                    walk(nested,child)
                    if stamp(os.stat(leaf,dir_fd=fd,follow_symlinks=False))!=stamp(os.fstat(nested)):
                        raise Unknown('Prepared source directory path changed')
                finally:os.close(nested)
            if stamp(os.fstat(fd))!=identity:raise Unknown('Prepared source directory changed during observation')
        fd=self._paths.open_directory(self._role)
        try:
            walk(fd,'')
            if seen!=set(groups) or (not capture and seen!=set(self._directories)):
                raise Unknown('Prepared source directory coverage differs')
            self._paths.require(self._role,self._inventory.root.path)
        except (OSError,KeyError,ValueError):
            raise Unknown('Prepared source descriptor observation is unproven') from None
        finally:os.close(fd)

    def check(self,inventory,paths,role):
        if (type(self) is not VerifiedInventory or os.getpid()!=self._pid
            or inventory is not self._inventory or paths is not self._paths or role!=self._role
            or self._fingerprint()!=self._seal):
            raise Refused('Exact original process/inventory/path witness required')
        self._scan()
        if self._fingerprint()!=self._seal:raise Unknown('Prepared inventory witness changed')

    def summary(self):
        if os.getpid()!=self._pid or self._fingerprint()!=self._seal:raise Refused('Original verified inventory witness required')
        return dict(inventory_sha256=self._inventory.sha256,files=len(self._files),identities_sha256=digest(self._files),
            directories=len(self._directories),directories_sha256=digest(self._directories))
