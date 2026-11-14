"""Streaming verification of an exact SDK-selected Hermes backup archive.

No snapshot creation, restore, encryption, publication or approval issuer. The
transaction adapter must supply the pinned SDK selection, safe archive handle
and private scratch directory. A valid ZIP alone is not a complete backup.
"""
from dataclasses import dataclass,asdict
import hashlib
import json
import os
from pathlib import Path,PurePosixPath
import re
import sqlite3
import stat
from urllib.parse import quote
import uuid
import zipfile


class ArchiveRejected(RuntimeError):
    """Retain source archive and any failed private DB image for review."""


def _uint(value,maximum):return type(value) is int and 0<value<=maximum
def _hash(value):return type(value) is str and re.fullmatch('[a-f0-9]{64}',value) is not None
def _relative(name):
    return (type(name) is str and 1<=len(name)<=4096 and not name.startswith('/') and '\\' not in name
        and not any(c in name for c in '\0\r\n') and str(PurePosixPath(name))==name and '..' not in PurePosixPath(name).parts and name!='.')


@dataclass(frozen=True)
class ArchiveLimits:
    archive_bytes:int=16*1024**3
    members:int=250000
    member_bytes:int=8*1024**3
    total_bytes:int=64*1024**3

    def __post_init__(self):
        if (not _uint(self.archive_bytes,16*1024**3) or not _uint(self.members,250000)
                or not _uint(self.member_bytes,8*1024**3) or not _uint(self.total_bytes,64*1024**3)
                or self.member_bytes>self.total_bytes):raise ValueError('Exact finite archive/member/count bounds required')


@dataclass(frozen=True)
class BackupMember:
    name:str
    kind:str
    size:int|None
    sha256:str|None

    def __post_init__(self):
        if (not _relative(self.name) or self.kind not in ('file','sqlite')
                or (self.kind=='file' and (PurePosixPath(self.name).suffix=='.db' or type(self.size) is not int or not 0<=self.size<=8*1024**3 or not _hash(self.sha256)))
                or (self.kind=='sqlite' and (self.size is not None or self.sha256 is not None or PurePosixPath(self.name).suffix!='.db'))):
            raise ValueError('Exact selected ordinary-file digest or native SQLite snapshot expectation required')


def selection_sha256(members):
    if (type(members) is not tuple or not members or any(type(m) is not BackupMember for m in members)
            or tuple(sorted(m.name for m in members))!=tuple(m.name for m in members) or len({m.name for m in members})!=len(members)):
        raise ValueError('Nonempty unique sorted SDK selection required')
    return hashlib.sha256(json.dumps([asdict(m) for m in members],sort_keys=True,separators=(',',':')).encode()).hexdigest()


def _identity(s):return (s.st_dev,s.st_ino,s.st_mode,s.st_uid,s.st_gid,s.st_nlink,s.st_size,s.st_mtime_ns,s.st_ctime_ns)


class _DatabaseImage:
    """Fresh private image only; never opens or overwrites a source database."""
    def __init__(self,root):
        self.root=Path(root);self.directory=None;self.fd=None
        if not self.root.is_absolute() or self.root.resolve()!=self.root:raise ArchiveRejected('scratch_path')
        before=self.root.lstat()
        if not stat.S_ISDIR(before.st_mode):raise ArchiveRejected('scratch_type')
        if os.name=='posix':
            if before.st_uid!=os.geteuid() or stat.S_IMODE(before.st_mode)!=0o700:raise ArchiveRejected('scratch_private')
            self.directory=os.open(self.root,os.O_RDONLY|os.O_DIRECTORY|os.O_NOFOLLOW|os.O_CLOEXEC)
            if (os.fstat(self.directory).st_dev,os.fstat(self.directory).st_ino)!=(before.st_dev,before.st_ino):
                os.close(self.directory);self.directory=None;raise ArchiveRejected('scratch_identity')
        self.root_identity=(before.st_dev,before.st_ino)
        self.name='.signal-grove-backup-verify-'+uuid.uuid4().hex+'.db';self.path=self.root/self.name
        try:
            flags=os.O_RDWR|os.O_CREAT|os.O_EXCL|getattr(os,'O_NOFOLLOW',0)|getattr(os,'O_CLOEXEC',0)|getattr(os,'O_BINARY',0)
            self.fd=os.open(self.name if self.directory is not None else self.path,flags,0o600,**({'dir_fd':self.directory} if self.directory is not None else {}))
            self.native=(os.fstat(self.fd).st_dev,os.fstat(self.fd).st_ino);self.check()
        except BaseException:
            self.close();raise

    def check(self):
        r=self.root.lstat();s=os.fstat(self.fd);named=self.path.lstat()
        if ((r.st_dev,r.st_ino)!=self.root_identity or self.root.resolve()!=self.root or not stat.S_ISREG(s.st_mode)
                or s.st_nlink!=1 or (s.st_dev,s.st_ino)!=self.native or _identity(named)!=_identity(s)
                or (os.name=='posix' and (r.st_uid!=os.geteuid() or stat.S_IMODE(r.st_mode)!=0o700 or s.st_uid!=os.geteuid() or stat.S_IMODE(s.st_mode)!=0o600))):
            raise ArchiveRejected('scratch_changed')

    def write(self,data):
        remaining=memoryview(data)
        while remaining:
            count=os.write(self.fd,remaining)
            if count<=0:raise ArchiveRejected('sqlite_image_write')
            remaining=remaining[count:]

    def verify(self):
        os.fsync(self.fd);self.check()
        os.lseek(self.fd,0,os.SEEK_SET);header=os.read(self.fd,100);size=os.fstat(self.fd).st_size
        page=int.from_bytes(header[16:18],'big') if len(header)==100 else 0
        page=65536 if page==1 else page
        if (not header.startswith(b'SQLite format 3\0') or not 512<=page<=65536 or page&(page-1) or size<page or size%page):
            raise ArchiveRejected('sqlite_header_or_pages')
        path=Path('/proc/self/fd')/str(self.fd) if sys_is_linux() else self.path
        conn=None
        try:
            conn=sqlite3.connect('file:'+quote(path.as_posix(),safe='/')+'?mode=ro&immutable=1',uri=True,timeout=1)
            conn.execute('PRAGMA query_only=ON')
            rows=conn.execute('PRAGMA integrity_check').fetchall()
            if rows!=[('ok',)]:raise ArchiveRejected('sqlite_integrity')
        except sqlite3.Error:raise ArchiveRejected('sqlite_integrity') from None
        finally:
            if conn is not None:conn.close()
        self.check()

    def remove_verified(self):
        self.check()
        if os.name=='nt':
            # Windows does not share-delete the CRT-created file. Close only
            # this image, then recheck its exact private name before unlink.
            expected=_identity(os.fstat(self.fd));os.close(self.fd);self.fd=None
            if _identity(self.path.lstat())!=expected or self.path.resolve().parent!=self.root:raise ArchiveRejected('scratch_changed')
        os.unlink(self.name if self.directory is not None else self.path,**({'dir_fd':self.directory} if self.directory is not None else {}))
        if self.directory is not None:os.fsync(self.directory)

    def close(self):
        if self.fd is not None:os.close(self.fd);self.fd=None
        if self.directory is not None:os.close(self.directory);self.directory=None


def sys_is_linux():
    import sys
    return sys.platform=='linux'


def verify_archive(handle,members,reviewed_selection_sha256,scratch,limits=ArchiveLimits()):
    """Verify exact contents, streamed CRC/hash/size and isolated DB integrity.

    The caller owns the archive FD/native identity, SDK source and selection
    observation. Returns content proof only, never `fresh_backup_verified` or
    remote publication/operational restore acceptance. Metadata is private.
    """
    if type(limits) is not ArchiveLimits or selection_sha256(members)!=reviewed_selection_sha256:
        raise ArchiveRejected('selection_binding')
    if len(members)>limits.members:raise ArchiveRejected('selection_count')
    try:
        handle.seek(0,2);size=handle.tell();handle.seek(0)
        if not 0<size<=limits.archive_bytes:raise ArchiveRejected('archive_size')
        expected={m.name:m for m in members};results=[];total=0
        with zipfile.ZipFile(handle,'r') as archive:
            infos=archive.infolist()
            if len(infos)>limits.members or len(infos)!=len(expected) or len({i.filename for i in infos})!=len(infos):raise ArchiveRejected('member_count_or_duplicate')
            if {i.filename for i in infos}!=set(expected):raise ArchiveRejected('member_selection')
            for info in sorted(infos,key=lambda i:i.filename):
                mode=(info.external_attr>>16)&0xffff
                if (not _relative(info.filename) or info.is_dir() or (stat.S_IFMT(mode) not in (0,stat.S_IFREG))
                        or info.flag_bits&1 or info.compress_type not in (zipfile.ZIP_STORED,zipfile.ZIP_DEFLATED)
                        or not 0<=info.file_size<=limits.member_bytes or total+info.file_size>limits.total_bytes):raise ArchiveRejected('member_type_or_bound')
                selected=expected[info.filename];image=_DatabaseImage(scratch) if selected.kind=='sqlite' else None
                observed=0;digest=hashlib.sha256()
                try:
                    with archive.open(info) as stream:
                        while True:
                            data=stream.read(1024*1024)
                            if not data:break
                            observed+=len(data);total+=len(data)
                            if observed>limits.member_bytes or total>limits.total_bytes:raise ArchiveRejected('stream_bound')
                            digest.update(data)
                            if image is not None:image.write(data)
                    if observed!=info.file_size:raise ArchiveRejected('member_size')
                    if selected.kind=='file' and (observed!=selected.size or digest.hexdigest()!=selected.sha256):raise ArchiveRejected('member_content')
                    if image is not None:image.verify();image.remove_verified()
                    results.append(dict(name=info.filename,kind=selected.kind,bytes=observed,sha256=digest.hexdigest(),sqlite_integrity_verified=image is not None))
                finally:
                    if image is not None:image.close()
        manifest=json.dumps(results,sort_keys=True,separators=(',',':')).encode()
        return dict(state='ARCHIVE_CONTENTS_VERIFIED',selection_sha256=reviewed_selection_sha256,members=len(results),sqlite_members=sum(m.kind=='sqlite' for m in members),
            uncompressed_bytes=total,archive_bytes=size,manifest_sha256=hashlib.sha256(manifest).hexdigest(),manifest=results,
            source_snapshot_or_encryption_verified=False,remote_publication_verified=False,operational_restore_verified=False)
    except ArchiveRejected:raise
    except Exception:raise ArchiveRejected('archive_read_or_format') from None
