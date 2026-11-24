from pathlib import Path
import ast,hashlib,importlib.util,io,tempfile,unittest
from contextlib import redirect_stderr, redirect_stdout
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
    def test_verified_partial_blobs_remain_publishable_while_inspection_still_fails(self):
        second=b'other_source=True\n';oid,size=self.identity(second);tree=self.tree();tree['tree'].append({'path':'agent/second.py','type':'blob','mode':'100644','sha':oid,'size':size})
        def download(path,commit):
            if path==self.path:return self.data.decode()
            raise c.GitHubReadError(429)
        with patch.multiple(c,SOURCES={'first':self.path,'second':'agent/second.py'},EXTRA_SOURCES={}),patch.object(c,'resolve',return_value=('main',self.pin)),patch.object(c,'github',return_value=tree),patch.object(c,'github_source',side_effect=download),patch.object(c,'extract') as parse:
            with self.assertRaises(c.SourceReadError):c.snapshot('main',self.cache)
            parse.assert_not_called();self.assertTrue(c.source_cache_publishable(self.cache));self.assertFalse((self.cache/oid).exists())
    def test_publication_refuses_empty_corrupt_unexpected_and_nonfile_entries(self):
        self.assertFalse(c.source_cache_publishable(self.cache));self.cache.mkdir();self.assertFalse(c.source_cache_publishable(self.cache))
        oid,size=self.identity();target=self.cache/oid;target.write_bytes(self.data);self.assertTrue(c.source_cache_publishable(self.cache));target.write_bytes(b'x'*size);self.assertFalse(c.source_cache_publishable(self.cache));target.write_bytes(self.data)
        extra=self.cache/'partial.tmp';extra.write_bytes(b'partial');self.assertFalse(c.source_cache_publishable(self.cache));extra.unlink();(self.cache/('c'*40)).mkdir();self.assertFalse(c.source_cache_publishable(self.cache))
    def test_publication_refuses_symlink_and_oversized_data_before_read(self):
        self.cache.mkdir();oid,size=self.identity();target=self.cache/oid;target.write_bytes(self.data)
        with patch.object(Path,'is_symlink',lambda path:path==target):self.assertFalse(c.source_cache_publishable(self.cache))
        target.write_bytes(b'x'*2_000_001)
        with patch.object(Path,'open',side_effect=AssertionError('Oversized cache must not be read')):self.assertFalse(c.source_cache_publishable(self.cache))
    def test_publication_file_count_is_bounded_before_data_reads(self):
        self.cache.mkdir()
        with patch.object(Path,'iterdir',return_value=iter([self.cache/('a'*40)]*513)),patch.object(Path,'open',side_effect=AssertionError('Overfull cache must not be read')):self.assertFalse(c.source_cache_publishable(self.cache))

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
    def test_missing_required_paths_report_the_pin_without_publishing_a_contract(self):
        tree = self.tree()
        tree['tree'].append({'path': 'upstream-untracked-sensitive-marker.py'})
        output = self.cache.parent / 'report.json'
        args = ['checker', '--ref', 'main', '--source-cache', str(self.cache), '--output', str(output)]
        sources = {'first': self.path, 'third': 'old/z.py', 'second': 'old/a.py', 'alias': 'old/a.py'}
        stderr, stdout = io.StringIO(), io.StringIO()
        with (
            patch.multiple(c, SOURCES=sources, EXTRA_SOURCES={}, _REQUEST_INTERVAL=0, _LAST_REQUEST_START=None),
            patch.object(c.sys, 'argv', args),
            patch.object(c, 'resolve', return_value=('main', self.pin)),
            patch.object(c, 'github', return_value=tree),
            patch.object(c, 'github_source') as download,
            patch.object(c, 'extract') as parse,
            redirect_stderr(stderr),
            redirect_stdout(stdout),
        ):
            self.assertEqual(3, c.main())
        self.assertEqual([
            f'Contract check incomplete: Required source paths missing at {self.pin}.',
            '  old/a.py',
            '  old/z.py',
        ], stderr.getvalue().splitlines())
        self.assertEqual('', stdout.getvalue())
        self.assertFalse(output.exists())
        self.assertFalse(self.cache.exists())
        download.assert_not_called()
        parse.assert_not_called()

    def test_reviewed_cron_move_preserves_source_key_and_reads_only_relocated_path(self):
        old_path = c.LEGACY_CRON_FORMAT_PATH
        new_path = c.CRON_FORMAT_PATH
        source = "_FORMAT_JOB_OPTIONAL_KEYS = ('script', 'timezone')\n"
        oid, size = self.identity(source.encode())
        tree = self.tree(path=new_path, sha=oid, size=size)
        for pin in sorted(c.REVIEWED_CRON_FORMAT_MOVES):
            with self.subTest(pin=pin), patch.multiple(c, SOURCES={}, EXTRA_SOURCES={'cron_format': old_path}), \
                    patch.object(c, 'resolve', return_value=('main', pin)), \
                    patch.object(c, 'github', return_value=tree) as tree_read, \
                    patch.object(c, 'github_source', return_value=source) as download, \
                    patch.object(c, 'extract', side_effect=lambda rows: dict(rows)):
                direct = c.snapshot('main')
                cached = c.snapshot('main', self.cache)
                self.assertEqual({'cron_format': source}, direct['contract'])
                self.assertEqual(direct['contract'], cached['contract'])
                self.assertEqual([dict(source='cron_format', original_path=old_path, path=new_path, commit=pin)],
                                 cached['reviewed_source_relocations'])
                self.assertEqual([], cached['reviewed_source_absences'])
                self.assertIn(new_path, tree_read.call_args.args[1])
                self.assertNotIn(old_path, tree_read.call_args.args[1])
                self.assertTrue(download.call_args_list)
                self.assertTrue(all(call.args == (new_path, pin) for call in download.call_args_list))

    def test_reviewed_cron_move_does_not_supply_missing_replacement_or_cover_newer_commits(self):
        old_path = c.LEGACY_CRON_FORMAT_PATH
        new_path = c.CRON_FORMAT_PATH
        for pin, available, required in (
            (min(c.REVIEWED_CRON_FORMAT_MOVES), old_path, new_path),
            (self.pin, new_path, old_path),
        ):
            with self.subTest(pin=pin), patch.multiple(c, SOURCES={}, EXTRA_SOURCES={'cron_format': old_path}), \
                    patch.object(c, 'resolve', return_value=('main', pin)), \
                    patch.object(c, 'github', return_value=self.tree(path=available)) as tree_read, \
                    patch.object(c, 'github_source', side_effect=c.GitHubReadError(404)) as download, \
                    patch.object(c, 'extract') as parse:
                with self.assertRaises(c.SourceReadError) as failure:
                    c.snapshot('main', self.cache)
                self.assertEqual('source_tree', failure.exception.module)
                self.assertIn(required, tree_read.call_args.args[1])
                download.assert_not_called()
                with self.assertRaises(c.SourceReadError) as failure:
                    c.snapshot('main')
                self.assertEqual('cron_format', failure.exception.module)
                self.assertEqual(404, failure.exception.status)
                download.assert_called_once_with(required, pin)
                parse.assert_not_called()

    def test_plugin_settings_split_keeps_existing_watch_key_and_pin_fence(self):
        old_path = c.LEGACY_PLUGIN_SETTINGS_PATH
        fields = c.PLUGIN_SETTINGS_FIELDS_PATH
        source = "def plugin_settings_fields():\n    return []\n"
        oid, size = self.identity(source.encode())
        tree = self.tree(path=fields, sha=oid, size=size)
        with patch.multiple(c, SOURCES={}, EXTRA_SOURCES={'plugin_settings': old_path}):
            self.assertEqual({'plugin_settings': old_path}, c.contract_source_paths(self.pin))
            for pin in sorted(c.REVIEWED_PLUGIN_SETTINGS_SPLITS):
                with (
                    self.subTest(pin=pin),
                    patch.object(c, 'resolve', return_value=('main', pin)),
                    patch.object(c, 'github', return_value=tree),
                    patch.object(c, 'github_source', return_value=source) as read,
                    patch.object(c, 'extract', side_effect=dict),
                ):
                    direct = c.snapshot('main')
                    cached = c.snapshot('main', self.cache)
                    self.assertEqual(direct['contract'], cached['contract'])
                    self.assertEqual({'plugin_settings': source}, cached['contract'])
                    self.assertEqual(
                        [dict(source='plugin_settings', original_path=old_path, path=fields, commit=pin)],
                        cached['reviewed_source_relocations'],
                    )
                    self.assertTrue(read.call_args_list)
                    self.assertTrue(all(call.args == (fields, pin) for call in read.call_args_list))
                    with patch.object(c, 'github', return_value=self.tree(path=old_path)):
                        with self.assertRaises(c.MissingSourcePaths):
                            c.source_identities(pin)

    def test_reviewed_pins_use_all_ten_verified_relocations(self):
        pins = (
            '242315ce571cde721e717a544cf8ae6c343315cf',
            'eb49b8920a25ffa7bb2fadf548fe819221ca79d7',
            'a15b9710395aa1c4858fb6dd9e4c5014c5ced4e9',
            '8fc0374447313e276f6b9a2764bfc750190c966a',
            '2506f5b0a4d310b96ec2eb6f2de4ea7349ba4d15',
        )
        for pin in pins:
            with self.subTest(pin=pin):
                legacy = c.SOURCES | c.EXTRA_SOURCES
                actual = c.contract_source_paths(pin)
                relocated = {key: actual[key] for key in legacy if actual[key] != legacy[key]}
                self.assertEqual(
                    c.PERSISTENCE_SOURCE_MOVES | {
                        'plugin_settings': c.PLUGIN_SETTINGS_FIELDS_PATH,
                        'cron_format': c.CRON_FORMAT_PATH,
                    },
                    relocated,
                )
                self.assertEqual(10, len(relocated))
                self.assertEqual(c.PERSISTENCE_SOURCE_DEPENDENCIES, {
                    key: actual[key] for key in actual.keys() - legacy.keys()
                })

    def test_persistence_split_requires_every_dependency_only_at_verified_pins(self):
        legacy = {name: c.EXTRA_SOURCES[name] for name in c.PERSISTENCE_SOURCE_MOVES}
        relocated = c.PERSISTENCE_SOURCE_MOVES | c.PERSISTENCE_SOURCE_DEPENDENCIES
        oid, size = self.identity()
        rows = [dict(path=path, sha=oid, size=size, mode='100644', type='blob')
                for path in sorted(set(relocated.values()))]
        tree = self.tree()
        tree['tree'] = rows
        with patch.multiple(c, SOURCES={}, EXTRA_SOURCES=legacy):
            self.assertEqual(legacy, c.contract_source_paths(self.pin))
            for pin in sorted(c.PERSISTENCE_LAYOUT_PINS):
                with (
                    self.subTest(pin=pin),
                    patch.object(c, 'resolve', return_value=('main', pin)),
                    patch.object(c, 'github', return_value=tree),
                    patch.object(c, 'github_source', return_value=self.data.decode()) as download,
                    patch.object(c, 'extract', side_effect=dict),
                ):
                    result = c.snapshot('main')
                    self.assertEqual(c.PERSISTENCE_SOURCE_DEPENDENCIES, result['watched_persistence_dependencies'])
                    self.assertEqual(set(relocated), set(result['contract']))
                    self.assertEqual(set(legacy), {row['source'] for row in result['reviewed_source_relocations']})
                    self.assertEqual({(path, pin) for path in relocated.values()},
                                     {call.args for call in download.call_args_list})
                    with patch.object(c, 'github', return_value={**tree, 'tree': rows[:-1]}):
                        with self.assertRaises(c.MissingSourcePaths):
                            c.source_identities(pin)
            with patch.object(c, 'github', return_value=tree):
                with self.assertRaises(c.MissingSourcePaths):
                    c.source_identities(self.pin)

    def test_persistence_dependency_change_alone_requires_mobile_review(self):
        baseline = {'upstream': {'commit': self.pin, 'contract': {
            'routes': [], 'static_features': {}, 'job_constraints': {},
            'job_update_fields': [], 'source_hashes': {},
        }}, 'mobile_used_routes': [], 'mobile_used_static_flags': [],
            'mobile_used_job_update_fields': []}
        key = next(iter(c.PERSISTENCE_SOURCE_DEPENDENCIES))
        current = {'commit': next(iter(c.PERSISTENCE_LAYOUT_PINS)),
                   'contract': {**baseline['upstream']['contract'], 'source_hashes': {key: '1' * 64}}}
        result = c.compare(baseline, current)
        self.assertTrue(result['mobile_review_required'])
        self.assertEqual([key], result['mobile_relevant_changes']['changed_persistence_sources'])

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


class ReviewedSourceAbsenceTest(unittest.TestCase):
    alias='plugin_auto_update';path='hermes_cli/plugins_auto_update.py'
    pin='b200f5bc60e31588ba2cb60e288029c4a54ba5fa'
    ordinary='agent/fixture.py';data=b"raise RuntimeError('Never execute upstream source')\n"
    def setUp(self):
        self.folder=tempfile.TemporaryDirectory();self.addCleanup(self.folder.cleanup);self.cache=Path(self.folder.name)/'cache'
    def identity(self,data):return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest(),len(data)
    def row(self,path,data):
        oid,size=self.identity(data);return {'path':path,'type':'blob','mode':'100644','sha':oid,'size':size}
    def tree(self,extra=None):return {'sha':'c'*40,'truncated':False,'tree':[self.row(self.ordinary,self.data)]+(extra or [])}
    def scope(self,alias=None,path=None):return patch.multiple(c,SOURCES={'fixture':self.ordinary},EXTRA_SOURCES={alias or self.alias:path or self.path})
    def test_known_cached_absence_requires_a_fresh_complete_tree_and_never_caches_empty_text(self):
        with self.scope(),patch.object(c,'resolve',return_value=('latest',self.pin)),patch.object(c,'github',return_value=self.tree()) as tree,patch.object(c,'github_source',return_value=self.data.decode()) as download,patch.object(c,'extract',side_effect=lambda rows: {'statements':len(ast.parse(rows['fixture']).body),'absent':rows[self.alias]}):
            cold=c.snapshot('latest',self.cache);warm=c.snapshot('latest',self.cache)
        self.assertEqual(2,tree.call_count);download.assert_called_once_with(self.ordinary,self.pin)
        self.assertEqual({'statements':1,'absent':''},cold['contract']);self.assertEqual(cold['contract'],warm['contract'])
        expected=[{'source':self.alias,'path':self.path,'commit':self.pin}]
        self.assertEqual(expected,cold['reviewed_source_absences']);self.assertEqual(expected,warm['reviewed_source_absences'])
        self.assertEqual([self.identity(self.data)[0]],list(p.name for p in self.cache.iterdir()))
        self.assertEqual({'verified_hits':1,'verified_downloads':0},warm['source_cache'])
    def test_known_absence_never_hides_a_present_file_or_bypasses_blob_validation(self):
        other=b"raise RuntimeError('Never execute new dependency')\n";tree=self.tree([self.row(self.path,other)])
        def read(path,commit):return (other if path==self.path else self.data).decode()
        with self.scope(),patch.object(c,'resolve',return_value=('latest',self.pin)),patch.object(c,'github',return_value=tree),patch.object(c,'github_source',side_effect=read) as download,patch.object(c,'extract',side_effect=lambda rows: {'nodes':sum(len(ast.parse(v).body) for v in rows.values())}):
            result=c.snapshot('latest',self.cache)
        self.assertEqual(2,result['contract']['nodes']);self.assertEqual([],result['reviewed_source_absences']);self.assertEqual(2,download.call_count)
        oid,size=self.identity(other);(self.cache/oid).write_bytes(b'x'*size)
        with self.scope(),patch.object(c,'resolve',return_value=('latest',self.pin)),patch.object(c,'github',return_value=tree),patch.object(c,'github_source') as download,patch.object(c,'extract') as parse:
            with self.assertRaises(c.SourceReadError):c.snapshot('latest',self.cache)
            parse.assert_not_called();download.assert_not_called()
    def test_unknown_commit_alias_path_other_missing_source_and_truncated_tree_fail_closed(self):
        cases=[('d'*40,self.alias,self.path,self.tree()),(self.pin,'other_alias',self.path,self.tree()),(self.pin,self.alias,'hermes_cli/other.py',self.tree()),(self.pin,self.alias,self.path,{'sha':'c'*40,'truncated':False,'tree':[]}),(self.pin,self.alias,self.path,{**self.tree(),'truncated':True})]
        for pin,alias,path,tree in cases:
            with self.subTest(pin=pin,alias=alias,path=path),self.scope(alias,path),patch.object(c,'resolve',return_value=('main',pin)),patch.object(c,'github',return_value=tree),patch.object(c,'github_source') as read,patch.object(c,'extract') as parse:
                with self.assertRaises(c.SourceReadError):c.snapshot('main',self.cache)
                read.assert_not_called();parse.assert_not_called()
    def test_no_cache_requires_the_actual_known_404_and_does_not_swallow_other_statuses(self):
        for status in [404,401,403,429,500,None]:
            def read(path,commit):
                if path==self.path:raise c.GitHubReadError(status)
                return self.data.decode()
            with self.subTest(status=status),self.scope(),patch.object(c,'resolve',return_value=('latest',self.pin)),patch.object(c,'github_source',side_effect=read),patch.object(c,'extract',side_effect=lambda rows: {'blank':rows[self.alias]}) as parse:
                if status==404:
                    result=c.snapshot('latest');self.assertEqual({'blank':''},result['contract']);self.assertEqual([{'source':self.alias,'path':self.path,'commit':self.pin}],result['reviewed_source_absences'])
                else:
                    with self.assertRaises(c.SourceReadError) as failure:c.snapshot('latest')
                    self.assertEqual(self.alias,failure.exception.module);self.assertEqual(status,failure.exception.status);parse.assert_not_called()
    def test_no_cache_unknown_commit_or_renamed_alias_cannot_treat_a_404_as_success(self):
        for pin,alias,path in [('d'*40,self.alias,self.path),(self.pin,'other_alias',self.path),(self.pin,self.alias,'hermes_cli/other.py')]:
            with self.subTest(pin=pin,alias=alias,path=path),self.scope(alias,path),patch.object(c,'resolve',return_value=('main',pin)),patch.object(c,'github_source',side_effect=c.GitHubReadError(404)),patch.object(c,'extract') as parse:
                with self.assertRaises(c.SourceReadError):c.snapshot('main')
                parse.assert_not_called()
    def test_reviewed_absence_does_not_satisfy_a_tree_permission_or_rate_failure(self):
        for status in [403,429]:
            with self.subTest(status=status),self.scope(),patch.object(c,'resolve',return_value=('latest',self.pin)),patch.object(c,'github',side_effect=c.GitHubReadError(status)),patch.object(c,'github_source') as read,patch.object(c,'extract') as parse:
                with self.assertRaises(c.SourceReadError) as failure:c.snapshot('latest',self.cache)
                self.assertEqual('source_tree',failure.exception.module);self.assertEqual(status,failure.exception.status);read.assert_not_called();parse.assert_not_called()

if __name__=='__main__':unittest.main()
