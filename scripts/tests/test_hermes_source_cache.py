from pathlib import Path
import ast,hashlib,importlib.util,tempfile,unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('cache_checker',Path(__file__).resolve().parents[1]/'check_hermes_contract.py');c=importlib.util.module_from_spec(spec);spec.loader.exec_module(c)

class SourceCacheTest(unittest.TestCase):
    pin='a'*40
    path='agent/fixture.py'
    data=b"raise RuntimeError('Upstream text must never execute')\n"
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory();self.addCleanup(self.folder.cleanup);self.cache=Path(self.folder.name)/'cache'
    def identity(self,data=None):
        data=self.data if data is None else data
        return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest(),len(data)
    def tree(self,**changes):
        oid,size=self.identity();row={'path':self.path,'type':'blob','mode':'100644','sha':oid,'size':size};row.update(changes)
        return {'sha':'b'*40,'truncated':False,'tree':[row]}
    def scope(self):
        return patch.multiple(c,SOURCES={'fixture':self.path},EXTRA_SOURCES={})
    def test_cold_and_warm_snapshots_recheck_tree_and_every_blob_without_executing_source(self):
        with self.scope(),patch.object(c,'resolve',return_value=('main',self.pin)),patch.object(c,'github',return_value=self.tree()) as tree,patch.object(c,'github_source',return_value=self.data.decode()) as download,patch.object(c,'extract',side_effect=lambda rows:{'statements':len(ast.parse(rows['fixture']).body)}):
            cold=c.snapshot('main',self.cache);warm=c.snapshot('main',self.cache)
            self.assertEqual({'verified_hits':0,'verified_downloads':1},cold['source_cache']);self.assertEqual({'verified_hits':1,'verified_downloads':0},warm['source_cache']);self.assertEqual(cold['contract'],warm['contract']);self.assertEqual(1,cold['contract']['statements'])
            self.assertEqual(2,tree.call_count);download.assert_called_once_with(self.path,self.pin)
            self.assertEqual(self.data,(self.cache/self.identity()[0]).read_bytes());self.assertEqual([self.identity()[0]],[p.name for p in self.cache.iterdir()])
    def test_changed_commit_and_blob_cannot_reuse_prior_text(self):
        changed=self.data+b'new_constant=7\n';oid,size=self.identity(changed)
        with self.scope(),patch.object(c,'resolve',side_effect=[('main',self.pin),('main','c'*40)]),patch.object(c,'github',side_effect=[self.tree(),self.tree(sha=oid,size=size)]),patch.object(c,'github_source',side_effect=[self.data.decode(),changed.decode()]) as download,patch.object(c,'extract',side_effect=lambda rows:{'text':rows['fixture']}):
            old=c.snapshot('main',self.cache);new=c.snapshot('main',self.cache);self.assertNotEqual(old['contract'],new['contract']);self.assertEqual(2,download.call_count);self.assertEqual('c'*40,download.call_args.args[1]);self.assertEqual(0,new['source_cache']['verified_hits'])
    def test_truncated_missing_duplicate_malformed_and_nonfile_tree_entries_are_refused(self):
        duplicate=self.tree();duplicate['tree']*=2
        truncated=self.tree();truncated['truncated']=True
        missing=self.tree();missing['tree']=[]
        malformed=self.tree();malformed['tree']=[{'path':[]}]
        for tree in [truncated,missing,duplicate,malformed,self.tree(mode='120000'),self.tree(type='tree'),self.tree(sha='../../other'),self.tree(size=True),self.tree(size=2_000_001),self.tree(size=-1)]:
            with self.subTest(tree=tree),self.scope(),patch.object(c,'github',return_value=tree),patch.object(c,'github_source') as download:
                with self.assertRaises(ValueError):c.source_identities(self.pin)
                download.assert_not_called()
    def test_corrupt_cached_bytes_fail_without_redownload_or_parse(self):
        self.cache.mkdir();oid,size=self.identity();(self.cache/oid).write_bytes(b'x'*size)
        with patch.object(c,'github_source') as download:
            with self.assertRaises(ValueError):c.cached_github_source(self.cache,self.path,self.pin,(oid,size))
            download.assert_not_called();self.assertEqual(b'x'*size,(self.cache/oid).read_bytes())
    def test_wrong_cache_size_is_refused_before_reading_content(self):
        self.cache.mkdir();oid,size=self.identity();(self.cache/oid).write_bytes(b'x')
        with patch.object(c,'github_source') as download,patch.object(Path,'open',side_effect=AssertionError('Wrong-size bytes must not be read')):
            with self.assertRaises(ValueError):c.cached_github_source(self.cache,self.path,self.pin,(oid,size))
            download.assert_not_called()
    def test_download_must_match_fresh_identity_before_atomic_publication(self):
        with patch.object(c,'github_source',return_value='different-public-text') as download:
            with self.assertRaises(ValueError):c.cached_github_source(self.cache,self.path,self.pin,self.identity())
            download.assert_called_once();self.assertEqual([],list(self.cache.iterdir()))
    def test_matching_non_utf8_cached_blob_is_still_refused(self):
        data=b'\xff\xfe';oid,size=self.identity(data);self.cache.mkdir();(self.cache/oid).write_bytes(data)
        with patch.object(c,'github_source') as download:
            with self.assertRaises(ValueError):c.cached_github_source(self.cache,self.path,self.pin,(oid,size))
            download.assert_not_called()
    def test_tree_rate_refusal_cannot_be_satisfied_by_a_complete_cache(self):
        self.cache.mkdir();(self.cache/self.identity()[0]).write_bytes(self.data)
        with self.scope(),patch.object(c,'resolve',return_value=('main',self.pin)),patch.object(c,'github',side_effect=c.GitHubReadError(429)) as read,patch.object(c,'github_source') as download,patch.object(c,'extract') as parse:
            with self.assertRaises(c.SourceReadError) as failure:c.snapshot('main',self.cache)
            self.assertEqual('source_tree',failure.exception.module);self.assertEqual(429,failure.exception.status);read.assert_called_once();download.assert_not_called();parse.assert_not_called()
    def test_cache_miss_rate_refusal_has_no_retry_or_published_bytes(self):
        with self.scope(),patch.object(c,'resolve',return_value=('main',self.pin)),patch.object(c,'github',return_value=self.tree()),patch.object(c,'github_source',side_effect=c.GitHubReadError(429)) as download:
            with self.assertRaises(c.SourceReadError) as failure:c.snapshot('main',self.cache)
            self.assertEqual('fixture',failure.exception.module);self.assertEqual(429,failure.exception.status);download.assert_called_once();self.assertEqual([],list(self.cache.iterdir()))
    def test_failed_atomic_publication_leaves_no_partial_cache_file(self):
        with patch.object(c,'github_source',return_value=self.data.decode()),patch.object(c.os,'replace',side_effect=OSError('fixture failure')):
            with self.assertRaises(OSError):c.cached_github_source(self.cache,self.path,self.pin,self.identity())
            self.assertEqual([],list(self.cache.iterdir()))
    def test_path_like_identity_and_non_file_cache_target_are_refused(self):
        with patch.object(c,'github_source') as download:
            for identity in [('../other',1),('A'*40,1),('a'*40,True),('a'*40,-1),('a'*40,2_000_001)]:
                with self.assertRaises(ValueError):c.cached_github_source(self.cache,self.path,self.pin,identity)
            self.assertFalse(self.cache.exists());self.cache.mkdir();(self.cache/self.identity()[0]).mkdir()
            with self.assertRaises(ValueError):c.cached_github_source(self.cache,self.path,self.pin,self.identity())
            download.assert_not_called()
    def test_symlink_cache_target_is_refused_before_any_read_or_download(self):
        self.cache.mkdir();target=self.cache/self.identity()[0]
        with patch.object(Path,'is_symlink',lambda p:p==target),patch.object(c,'github_source') as download,patch.object(Path,'open',side_effect=AssertionError('Symlink must not be followed')):
            with self.assertRaises(ValueError):c.cached_github_source(self.cache,self.path,self.pin,self.identity())
            download.assert_not_called()

if __name__=='__main__':unittest.main()
