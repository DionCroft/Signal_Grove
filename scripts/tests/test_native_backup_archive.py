from dataclasses import replace
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import sqlite3
import stat
import sys
import tempfile
import unittest
import zipfile
from types import SimpleNamespace
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('native_backup_archive',Path(__file__).resolve().parents[1]/'native_backup_archive.py');m=importlib.util.module_from_spec(spec);sys.modules['native_backup_archive']=m;spec.loader.exec_module(m)
from native_backup_archive import ArchiveLimits,BackupMember,ArchiveRejected,selection_sha256,verify_archive

def file(name,data):return BackupMember(name,'file',len(data),hashlib.sha256(data).hexdigest())
def make_zip(items):
    out=io.BytesIO()
    with zipfile.ZipFile(out,'w',compression=zipfile.ZIP_DEFLATED) as z:
        for name,data in items:z.writestr(name,data)
    out.seek(0);return out

class BackupArchiveSchemaTests(unittest.TestCase):
    def test_limits_refuse_boolean_infinite_zero_and_excessive_bounds(self):
        for change in (dict(archive_bytes=True),dict(members=0),dict(member_bytes=2**64),dict(total_bytes=float('inf')),dict(total_bytes=1)):
            with self.subTest(change=change),self.assertRaises(ValueError):replace(ArchiveLimits(),**change)
    def test_member_paths_are_literal_safe_archive_relatives(self):
        for name in ('/absolute','../escape','x/../escape','a\\b','a\0b','a\nb','.','x//y'):
            with self.subTest(name=name),self.assertRaises(ValueError):file(name,b'x')
    def test_sqlite_expectations_cannot_pretend_to_be_live_byte_copies(self):
        for values in (('state.db','sqlite',1,'a'*64),('text','sqlite',None,None),('file','file',None,None),('state.db','file',0,'a'*64)):
            with self.subTest(values=values),self.assertRaises(ValueError):BackupMember(*values)
    def test_selection_must_be_complete_unique_nonempty_and_sorted(self):
        a,b=file('a',b'a'),file('b',b'b')
        for values in ((),[a],(a,a),(b,a)):
            with self.subTest(values=values),self.assertRaises(ValueError):selection_sha256(values)
    def test_selection_digest_binds_paths_types_sizes_and_content(self):
        one=selection_sha256((file('a',b'a'),))
        self.assertNotEqual(one,selection_sha256((file('a',b'b'),)));self.assertNotEqual(one,selection_sha256((file('b',b'a'),)))


class BackupNativeIdentityTests(unittest.TestCase):
    def observation(self,**changes):
        fields=dict(st_dev=1,st_ino=2,st_mode=stat.S_IFREG|0o600,st_uid=1000,st_gid=1000,st_nlink=1,st_size=8192,st_mtime_ns=30,st_ctime_ns=20,st_birthtime_ns=10)
        fields.update(changes);return SimpleNamespace(**fields)
    def test_windows_creation_stamp_matches_across_deprecated_ctime_disagreement(self):
        # Actual Windows 3.14 observation: path ctime=birth, fd ctime=change.
        with patch.object(m.os,'name','nt'):
            self.assertEqual(m._identity(self.observation(st_ctime_ns=10)),m._identity(self.observation(st_ctime_ns=30)))
    def test_windows_without_explicit_creation_stamp_is_refused(self):
        fields=vars(self.observation()).copy();del fields['st_birthtime_ns']
        with patch.object(m.os,'name','nt'),self.assertRaises(ArchiveRejected):m._identity(SimpleNamespace(**fields))
    def test_windows_creation_identity_still_binds_native_file_and_content_metadata(self):
        with patch.object(m.os,'name','nt'):
            baseline=m._identity(self.observation())
            for change in (dict(st_ino=3),dict(st_dev=3),dict(st_mode=stat.S_IFLNK|0o600),dict(st_nlink=2),dict(st_size=0),dict(st_mtime_ns=31),dict(st_birthtime_ns=11)):
                with self.subTest(change=change):self.assertNotEqual(baseline,m._identity(self.observation(**change)))
    def test_posix_still_requires_identical_metadata_change_time(self):
        with patch.object(m.os,'name','posix'):
            self.assertNotEqual(m._identity(self.observation()),m._identity(self.observation(st_ctime_ns=21)))


class BackupArchiveTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='signal-grove-backup-');self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name).resolve()
        if os.name=='posix':self.root.chmod(0o700)
    def verify(self,items,members=None,**kwargs):
        members=members or tuple(sorted((file(n,b) for n,b in items),key=lambda f:f.name))
        return verify_archive(make_zip(items),members,selection_sha256(members),self.root,**kwargs)
    def test_exact_streamed_file_content_and_private_manifest_are_verified(self):
        data=b'bounded content'*180000;items=[('.env',b'fixture only'),('sessions/fixture.txt',data)];r=self.verify(items)
        self.assertEqual(r['state'],'ARCHIVE_CONTENTS_VERIFIED');self.assertEqual(r['members'],2);self.assertEqual(r['uncompressed_bytes'],sum(len(b) for _,b in items))
        self.assertEqual(r['manifest_sha256'],hashlib.sha256(json.dumps(r['manifest'],sort_keys=True,separators=(',',':')).encode()).hexdigest())
        self.assertFalse(r['remote_publication_verified']);self.assertFalse(r['operational_restore_verified']);self.assertEqual(list(self.root.iterdir()),[])
    def test_wrong_reviewed_selection_digest_is_refused(self):
        members=(file('a',b'a'),)
        with self.assertRaises(ArchiveRejected):verify_archive(make_zip([('a',b'a')]),members,'f'*64,self.root)
    def test_missing_selected_member_is_not_a_complete_backup(self):
        with self.assertRaises(ArchiveRejected):self.verify([('a',b'a')],(file('a',b'a'),file('b',b'b')))
    def test_unknown_extra_member_is_not_silently_accepted(self):
        with self.assertRaises(ArchiveRejected):self.verify([('a',b'a'),('other',b'b')],(file('a',b'a'),))
    def test_duplicate_members_are_not_hidden_by_the_zip_name_dictionary(self):
        with self.assertWarns(UserWarning),self.assertRaises(ArchiveRejected):self.verify([('a',b'a'),('a',b'a')],(file('a',b'a'),))
    def test_changed_content_and_size_each_refuse(self):
        for data in (b'z',b'zz'):
            with self.subTest(data=data),self.assertRaises(ArchiveRejected):self.verify([('a',data)],(file('a',b'a'),))
    def test_zip_symlinks_and_directories_are_not_ordinary_selected_files(self):
        for mode in (stat.S_IFLNK|0o777,stat.S_IFDIR|0o700):
            stream=io.BytesIO();info=zipfile.ZipInfo('a');info.create_system=3;info.external_attr=mode<<16
            with zipfile.ZipFile(stream,'w') as z:z.writestr(info,b'a')
            members=(file('a',b'a'),)
            with self.subTest(mode=mode),self.assertRaises(ArchiveRejected):verify_archive(stream,members,selection_sha256(members),self.root)
    def test_archive_count_member_and_total_bounds_are_independent(self):
        cases=[ArchiveLimits(archive_bytes=1),ArchiveLimits(members=1),ArchiveLimits(member_bytes=1),ArchiveLimits(member_bytes=2,total_bytes=2)]
        for limits in cases:
            with self.subTest(limits=limits),self.assertRaises(ArchiveRejected):self.verify([('a',b'aa'),('b',b'bb')],limits=limits)
    def test_crc_corruption_and_truncation_never_pass(self):
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_STORED) as z:z.writestr('a',b'UNIQUE_PAYLOAD')
        raw=stream.getvalue();broken=raw.replace(b'UNIQUE_PAYLOAD',b'BROKEN_PAYLOAD');members=(file('a',b'UNIQUE_PAYLOAD'),)
        for value in (broken,raw[:-12],b'not a zip'):
            with self.subTest(value=len(value)),self.assertRaises(ArchiveRejected):verify_archive(io.BytesIO(value),members,selection_sha256(members),self.root)
    def test_valid_sqlite_snapshot_is_checked_in_a_fresh_private_image(self):
        source=self.root/'source.db';c=sqlite3.connect(source);c.execute('CREATE TABLE evidence (n INTEGER)');c.execute('INSERT INTO evidence VALUES (42)');c.commit();c.close()
        raw=source.read_bytes();before=hashlib.sha256(raw).hexdigest();r=self.verify([('state.db',raw)],(BackupMember('state.db','sqlite',None,None),))
        self.assertEqual(r['sqlite_members'],1);self.assertTrue(r['manifest'][0]['sqlite_integrity_verified']);self.assertEqual(hashlib.sha256(source.read_bytes()).hexdigest(),before)
        self.assertEqual(list(self.root.glob('.signal-grove-backup-verify-*')),[])
    def test_corrupt_sqlite_image_is_retained_and_never_touches_source_files(self):
        marker=self.root/'owner-file';marker.write_bytes(b'preserve')
        with self.assertRaises(ArchiveRejected):self.verify([('state.db',b'corrupt SQLite fixture')],(BackupMember('state.db','sqlite',None,None),))
        retained=list(self.root.glob('.signal-grove-backup-verify-*'));self.assertEqual(len(retained),1);self.assertEqual(retained[0].read_bytes(),b'corrupt SQLite fixture');self.assertEqual(marker.read_bytes(),b'preserve')
    def test_valid_empty_file_is_preserved_in_the_manifest(self):
        r=self.verify([('empty',b'')]);self.assertEqual(r['manifest'][0]['bytes'],0)
    def test_zero_byte_sqlite_file_is_not_a_healthy_empty_database(self):
        with self.assertRaises(ArchiveRejected):self.verify([('state.db',b'')],(BackupMember('state.db','sqlite',None,None),))
        self.assertEqual(len(list(self.root.glob('.signal-grove-backup-verify-*'))),1)
    def test_sqlite_header_and_partial_page_are_not_accepted(self):
        source=self.root/'source.db';c=sqlite3.connect(source);c.execute('CREATE TABLE evidence (n INTEGER)');c.commit();c.close();raw=source.read_bytes()
        for content in (raw[:100],raw[:-1],b'\0'*1024):
            with self.subTest(size=len(content)),self.assertRaises(ArchiveRejected):self.verify([('state.db',content)],(BackupMember('state.db','sqlite',None,None),))
    def test_unsupported_compression_is_refused_without_extraction(self):
        stream=io.BytesIO()
        with zipfile.ZipFile(stream,'w',compression=zipfile.ZIP_BZIP2) as z:z.writestr('a',b'a')
        members=(file('a',b'a'),)
        with self.assertRaises(ArchiveRejected):verify_archive(stream,members,selection_sha256(members),self.root)


@unittest.skipUnless(sys.platform=='linux','Native private scratch traversal runs on Linux')
class NativeBackupScratchTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='signal-grove-backup-scratch-');self.addCleanup(self.temp.cleanup);self.root=Path(self.temp.name).resolve();self.root.chmod(0o700)
    def test_nonprivate_scratch_is_refused_without_an_image(self):
        self.root.chmod(0o755);members=(BackupMember('state.db','sqlite',None,None),)
        with self.assertRaises(ArchiveRejected):verify_archive(make_zip([('state.db',b'bad')]),members,selection_sha256(members),self.root)
        self.assertEqual(list(self.root.iterdir()),[])
    def test_scratch_symlink_is_not_followed(self):
        link=self.root/'link';link.symlink_to(self.root,target_is_directory=True);members=(BackupMember('state.db','sqlite',None,None),)
        with self.assertRaises(ArchiveRejected):verify_archive(make_zip([('state.db',b'bad')]),members,selection_sha256(members),link)
        self.assertEqual(list(self.root.glob('.signal-grove-backup-verify-*')),[])

if __name__=='__main__':unittest.main()
