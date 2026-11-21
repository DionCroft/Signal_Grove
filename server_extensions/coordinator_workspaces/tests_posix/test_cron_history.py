"""The scoped cron read uses real protected directories and SQLite WAL snapshots."""

from contextlib import closing, nullcontext
from dataclasses import replace
import json
import os
from pathlib import Path
import sqlite3
import sys
import tempfile
from types import ModuleType
import unittest
from unittest.mock import patch

from server_extensions.coordinator_workspaces import cron_history, cron_output, cron_run_once
from server_extensions.coordinator_workspaces import cron_run_once_approval as approval
from server_extensions.coordinator_workspaces.coordinator import NativeReadContext, ReadRefused

JOB = 'f1e2d3c4b5a6'
OTHER_JOB = '1029384756ab'
RECEIPT = '1' * 32
OTHER_RECEIPT = '2' * 32
UTC = '2026-11-21T02:15:00+00:00'
OFFSET = '2026-11-21T03:00:00+02:00'


class CronHistoryPosixTest(unittest.TestCase):
    def setUp(self):
        root = os.environ['SIGNAL_GROVE_POLICY_TEST_ROOT']
        tmp = tempfile.TemporaryDirectory(prefix='cron-ledger-', dir=root)
        self.addCleanup(tmp.cleanup)
        self.home = Path(tmp.name) / 'default'
        self.other_home = Path(tmp.name) / 'secondary'
        for profile in (self.home, self.other_home):
            (profile / 'cron').mkdir(parents=True, mode=0o700)
            self.catalog(profile, [{'id': JOB}])
        self.query = cron_history.execution_query({'expected_profile': 'default', 'job_id': JOB})

    def catalog(self, profile, rows):
        target = profile / 'cron' / 'jobs.json'
        target.write_text(json.dumps({'jobs': rows}), encoding='utf-8')
        target.chmod(0o600)

    def open_ledger(self, profile):
        target = profile / 'cron' / 'executions.db'
        db = sqlite3.connect(target)
        db.execute('PRAGMA journal_mode=WAL')
        db.execute('PRAGMA wal_autocheckpoint=0')
        db.execute('''CREATE TABLE executions (
            id TEXT PRIMARY KEY, job_id TEXT NOT NULL, status TEXT NOT NULL, claimed_at TEXT NOT NULL,
            started_at TEXT, finished_at TEXT, scheduled_instant TEXT, delivery_outcome TEXT, error TEXT, source TEXT
        )''')
        return db

    def add(self, db, receipt=RECEIPT, job=JOB, status='completed', claimed=UTC, source='tick'):
        db.execute('INSERT INTO executions VALUES (?, ?, ?, ?, NULL, NULL, NULL, ?, ?, ?)',
                   (receipt, job, status, claimed, 'delivered', 'private diagnostic', source))
        db.commit()

    def expect(self, code, call):
        with self.assertRaises(ReadRefused) as error:
            call()
        self.assertEqual(code, error.exception.code)

    def protected(self, home):
        cron = home / 'cron'
        return {name: (cron / name).read_bytes() for name in ('jobs.json', 'executions.db', 'executions.db-wal')}

    def test_identical_jobs_and_receipts_read_only_in_the_selected_profile_with_live_wal(self):
        with closing(self.open_ledger(self.home)) as first, closing(self.open_ledger(self.other_home)) as second:
            self.add(first, status='unknown')
            self.add(second, status='completed')
            first_before = self.protected(self.home)
            second_before = self.protected(self.other_home)
            exact = replace(self.query, limit=1, execution_id=RECEIPT)
            one = cron_history.read_executions(self.home, exact)
            two = cron_history.read_executions(self.other_home, replace(exact, profile='secondary'))
            self.assertEqual(('default', 'unknown'), (one['profile'], one['attempts'][0]['status']))
            self.assertEqual(('secondary', 'completed'), (two['profile'], two['attempts'][0]['status']))
            self.assertEqual(JOB, one['job_id'])
            self.assertEqual(RECEIPT, one['attempts'][0]['execution_id'])
            self.assertTrue(one['attempts'][0]['reported_error'])
            self.assertNotIn('private diagnostic', str(one))
            self.assertEqual(first_before, self.protected(self.home))
            self.assertEqual(second_before, self.protected(self.other_home))

    def test_limit_orders_instants_and_exact_receipt_never_falls_back_to_another_job(self):
        self.catalog(self.home, [{'id': JOB}, {'id': OTHER_JOB}])
        with closing(self.open_ledger(self.home)) as db:
            self.add(db, RECEIPT, claimed=OFFSET)
            self.add(db, OTHER_RECEIPT, claimed=UTC)
            self.add(db, '3' * 32, job=OTHER_JOB)
            current = cron_history.read_executions(self.home, replace(self.query, limit=1))
            self.assertTrue(current['limited'])
            self.assertEqual([OTHER_RECEIPT], [row['execution_id'] for row in current['attempts']])
            self.expect('NOT_FOUND', lambda: cron_history.read_executions(
                self.home, replace(self.query, execution_id='3' * 32)))

    def test_reviewed_manual_observation_is_original_and_survives_job_removal(self):
        target = {'expected_profile': 'default', 'job_id': JOB, 'request_id': RECEIPT}
        original = cron_run_once.run_once_receipt_query(target)
        same_id = original.execution_id()
        with closing(self.open_ledger(self.home)) as own, closing(self.open_ledger(self.other_home)) as other:
            self.add(own, same_id, source='dashboard')
            self.add(other, same_id, source=cron_run_once.RUN_ONCE_SOURCE)
            self.expect('NOT_FOUND', lambda: cron_run_once.observe_run_once(self.home, original))

            own.execute('UPDATE executions SET source=?, status=? WHERE id=?',
                        (cron_run_once.RUN_ONCE_SOURCE, 'unknown', same_id))
            own.commit()
            self.catalog(self.home, [])
            before = self.protected(self.home)
            value = cron_run_once.observe_run_once(self.home, original)
            self.assertEqual(('default', JOB, RECEIPT), (value['profile'], value['job_id'], value['request_id']))
            self.assertEqual((same_id, 'unknown'), (value['execution']['execution_id'], value['execution']['status']))
            self.assertEqual(before, self.protected(self.home))
            self.assertNotIn('private diagnostic', str(value))
            for change in ({'request_id': OTHER_RECEIPT}, {'job_id': OTHER_JOB}, {'expected_profile': 'secondary'}):
                lookup = cron_run_once.run_once_receipt_query(target | change)
                self.expect('NOT_FOUND', lambda: cron_run_once.observe_run_once(self.home, lookup))

    def test_run_once_review_is_read_only_and_lost_save_keeps_original_wal_receipt(self):
        job_path = self.home / 'cron/jobs.json'
        actor = NativeReadContext('owner', str(self.home), 'transport', 'native-authority')
        scope = approval.RunOnceScope(None, 'exact-workspace', None, 'local', 'instance-a',
                                      'read', 'a' * 64, 'Read one fixture')
        service = approval.RunOnceApprovals(lambda *_: scope)
        profiles = ModuleType('hermes_cli.profiles')
        profiles.profile_matches_home = lambda name, home: (name, home) == ('default', self.home)
        fixture = self

        class Native:
            class CronStoreLockUnavailable(RuntimeError):
                pass

            def claim_reviewed_run_once(self, job_id, *, execution_id, authorize):
                row = json.loads(job_path.read_bytes())['jobs'][0]
                assert job_id == row['id']
                assert authorize(row) is True
                fixture.add(db, receipt=execution_id, status='claimed', source=cron_run_once.RUN_ONCE_SOURCE)
                raise OSError('Saved native receipt, then simulated lost job save')

        with (closing(self.open_ledger(self.home)) as db,
              patch.dict(sys.modules, {'hermes_cli.profiles': profiles}),
              patch.object(approval, '_native_jobs', return_value=Native()),
              patch.object(approval, '_profile_home', side_effect=lambda *_: nullcontext())):
            before = self.protected(self.home)
            reviewed = service.prepare('default', JOB, lambda: actor)
            self.assertEqual(before, self.protected(self.home))
            replacement = job_path.with_name('copy.json')
            replacement.write_bytes(job_path.read_bytes())
            replacement.chmod(0o600)
            replacement.replace(job_path)
            self.expect('CHANGED', lambda: service.admit(reviewed.receipt, reviewed.digest, lambda: actor))
            self.expect('NOT_FOUND', lambda: cron_run_once.observe_run_once(self.home, reviewed.receipt))
            valid = service.prepare('default', JOB, lambda: actor)
            with self.assertRaises(ReadRefused) as error:
                service.admit(valid.receipt, valid.digest, lambda: actor)
            self.assertEqual(('UNAVAILABLE', True), (error.exception.code, error.exception.request_submitted))
            original = cron_run_once.observe_run_once(self.home, valid.receipt)
            self.assertEqual('claimed', original['execution']['status'])
            self.assertEqual(valid.receipt.execution_id(), original['execution']['execution_id'])
            self.expect('DUPLICATE', lambda: service.admit(valid.receipt, valid.digest, lambda: actor))
            self.assertEqual(1, db.execute('SELECT COUNT(*) FROM executions').fetchone()[0])

    def test_name_alias_and_duplicate_ids_cannot_authorize_a_read(self):
        self.catalog(self.home, [{'id': OTHER_JOB, 'name': JOB}])
        self.expect('NOT_FOUND', lambda: cron_history.read_executions(self.home, self.query))
        self.catalog(self.home, [{'id': JOB}, {'id': JOB}])
        self.expect('DUPLICATE', lambda: cron_history.read_executions(self.home, self.query))

    def test_missing_ledger_remains_absent_and_missing_receipt_is_not_an_outcome(self):
        result = cron_history.read_executions(self.home, self.query)
        self.assertEqual(('absent', [], False), (result['ledger'], result['attempts'], result['limited']))
        self.assertEqual(['jobs.json'], sorted(item.name for item in (self.home / 'cron').iterdir()))
        self.expect('NOT_FOUND', lambda: cron_history.read_executions(
            self.home, replace(self.query, execution_id=RECEIPT)))

    def test_exact_receipt_survives_removal_of_its_one_shot_job(self):
        with closing(self.open_ledger(self.home)) as db:
            self.add(db)
            self.catalog(self.home, [])
            self.expect('NOT_FOUND', lambda: cron_history.read_executions(self.home, self.query))
            query = replace(self.query, limit=1, execution_id=RECEIPT)
            row = cron_history.read_executions(self.home, query)['attempts'][0]
            self.assertEqual((RECEIPT, 'completed'), (row['execution_id'], row['status']))
            self.expect('NOT_FOUND', lambda: cron_history.read_executions(
                self.home, replace(query, job_id=OTHER_JOB)))

    def test_symbolic_links_and_unprotected_database_replacements_are_refused(self):
        own = self.home / 'cron'
        other = self.other_home / 'cron'
        own.joinpath('executions.db').symlink_to(other / 'executions.db')
        self.expect('UNAVAILABLE', lambda: cron_history.read_executions(self.home, self.query))
        own.joinpath('executions.db').unlink()
        with closing(self.open_ledger(self.home)) as db:
            self.add(db)
        own.joinpath('executions.db').chmod(0o666)
        self.expect('UNAVAILABLE', lambda: cron_history.read_executions(self.home, self.query))
        own.joinpath('executions.db').chmod(0o600)
        own.joinpath('executions.db-wal').symlink_to(other / 'executions.db-wal')
        self.expect('UNAVAILABLE', lambda: cron_history.read_executions(self.home, self.query))
        own.joinpath('executions.db-wal').unlink()
        own.joinpath('jobs.json').unlink()
        own.joinpath('jobs.json').symlink_to(other / 'jobs.json')
        self.expect('UNAVAILABLE', lambda: cron_history.read_executions(self.home, self.query))

    def test_catalog_replacement_during_read_drops_the_observation(self):
        read_ledger = cron_history._read_ledger
        with closing(self.open_ledger(self.home)) as db:
            self.add(db)

            def changed(parent, query, **kwargs):
                result = read_ledger(parent, query, **kwargs)
                target = self.home / 'cron' / 'jobs.json'
                candidate = target.with_name('updated.json')
                candidate.write_text(json.dumps({'jobs': [{'id': JOB, 'enabled': False}]}), encoding='utf-8')
                candidate.chmod(0o600)
                candidate.replace(target)
                return result

            with patch.object(cron_history, '_read_ledger', side_effect=changed):
                self.expect('CHANGED', lambda: cron_history.read_executions(self.home, self.query))

    def test_invalid_schema_is_read_only_and_cannot_become_empty_success(self):
        ledger = self.home / 'cron' / 'executions.db'
        with closing(sqlite3.connect(ledger)) as db:
            db.execute('CREATE TABLE executions (id TEXT PRIMARY KEY)')
            db.commit()
        raw = ledger.read_bytes()
        self.expect('UNAVAILABLE', lambda: cron_history.read_executions(self.home, self.query))
        self.assertEqual(raw, ledger.read_bytes())


    def archive(self, home, output, *, filename="2026-11-21_02-15-00.md", job=JOB):
        path = home / "cron" / "output" / job / filename
        path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        temporary = path.with_suffix(".pending")
        temporary.write_bytes(output.encode("utf-8"))
        temporary.chmod(0o600)
        temporary.replace(path)
        return path

    def output(self, home=None, receipt=RECEIPT, job=JOB):
        query = cron_output.output_query(dict(expected_profile="default", job_id=job, execution_id=receipt))
        return cron_output.read_output(self.home if home is None else home, query)

    def test_output_association_preserves_stock_bytes_and_rejects_lookalike_time(self):
        output = "# Results\nOne exact archived reply"
        with closing(self.open_ledger(self.home)) as db:
            self.add(db, status="unknown")
            path = self.archive(self.home, output)
            before = (path.read_bytes(), path.stat().st_ino)
            self.assertEqual("unlinked", self.output()["archive_state"])
            self.assertFalse(cron_output.bind_output(JOB, RECEIPT, path, "another output"))
            self.assertTrue(cron_output.bind_output(JOB, RECEIPT, path, output))
            self.assertEqual(before, (path.read_bytes(), path.stat().st_ino))
            saved = self.output()
            self.assertEqual((RECEIPT, "unknown", "available", output, False),
                             tuple(saved[key] for key in ("execution_id", "status", "archive_state", "text", "limited")))
            self.assertNotIn(str(path), str(saved))
            self.assertEqual({"profile", "job_id", "execution_id", "status", "archive_state", "text", "limited"},
                             set(saved))

    def test_same_second_archive_replacement_never_adopts_the_later_output(self):
        with closing(self.open_ledger(self.home)) as db:
            self.add(db)
            self.add(db, OTHER_RECEIPT)
            first = self.archive(self.home, "identical archived bytes")
            self.assertTrue(cron_output.bind_output(JOB, RECEIPT, first, "identical archived bytes"))
            later = self.archive(self.home, "identical archived bytes")
            self.assertEqual(first, later)
            self.assertTrue(cron_output.bind_output(JOB, OTHER_RECEIPT, later, "identical archived bytes"))
            self.assertFalse(cron_output.bind_output(JOB, RECEIPT, later, "identical archived bytes"))
            self.assertEqual(("changed", None), tuple(self.output()[key] for key in ("archive_state", "text")))
            self.assertEqual("identical archived bytes", self.output(receipt=OTHER_RECEIPT)["text"])
            later.unlink()
            self.assertEqual("missing", self.output(receipt=OTHER_RECEIPT)["archive_state"])

    def test_exact_output_remains_in_its_profile_even_with_matching_job_and_execution_ids(self):
        with closing(self.open_ledger(self.home)) as first, closing(self.open_ledger(self.other_home)) as second:
            self.add(first)
            self.add(second)
            path = self.archive(self.home, "default archive")
            other = self.archive(self.other_home, "secondary archive")
            self.assertTrue(cron_output.bind_output(JOB, RECEIPT, path, "default archive"))
            self.assertTrue(cron_output.bind_output(JOB, RECEIPT, other, "secondary archive"))
            self.assertEqual("default archive", self.output()["text"])
            self.assertEqual("secondary archive", self.output(self.other_home)["text"])
            self.catalog(self.home, [])
            self.assertEqual("default archive", self.output()["text"])
            self.expect("NOT_FOUND", lambda: self.output(self.home, job=OTHER_JOB))
            first.execute("DELETE FROM executions WHERE id = ?", (RECEIPT,))
            first.commit()
            self.expect("NOT_FOUND", self.output)

    def test_utf8_projection_is_bounded_and_actor_cannot_choose_an_archive_name(self):
        with closing(self.open_ledger(self.home)) as db:
            self.add(db)
            output = "a" * (cron_output._MAX_PREVIEW - 1) + "庭院"
            path = self.archive(self.home, output)
            self.assertTrue(cron_output.bind_output(JOB, RECEIPT, path, output))
            result = self.output()
            self.assertTrue(result["limited"])
            self.assertEqual("a" * (cron_output._MAX_PREVIEW - 1), result["text"])
            link = path.parent / cron_output._LINKS / (RECEIPT + ".json")
            record = json.loads(link.read_text(encoding="utf-8"))
            record["archive"] = "../../other.md"
            link.write_text(json.dumps(record), encoding="utf-8")
            self.expect("UNAVAILABLE", self.output)

    def test_foreign_symlink_and_writable_link_are_refused(self):
        with closing(self.open_ledger(self.home)) as db:
            self.add(db)
            output = "the original output"
            path = self.archive(self.home, output)
            other = self.archive(self.other_home, output)
            self.assertTrue(cron_output.bind_output(JOB, RECEIPT, path, output))
            link = path.parent / cron_output._LINKS / (RECEIPT + ".json")
            link.chmod(0o640)
            self.expect("UNAVAILABLE", self.output)
            link.chmod(0o600)
            path.unlink()
            path.symlink_to(other)
            self.expect("UNAVAILABLE", self.output)
            self.assertFalse(cron_output.bind_output(JOB, OTHER_RECEIPT, path, output))

    def test_binding_retention_prunes_metadata_and_keeps_stock_archives(self):
        with patch.object(cron_output, "_KEEP_LINKS", 2):
            paths = []
            for index in range(3):
                output = f"archived result {index}"
                path = self.archive(self.home, output, filename=f"2026-11-21_02-15-0{index}.md")
                paths.append(path)
                self.assertTrue(cron_output.bind_output(JOB, f"{index:032x}", path, output))
            links = list((paths[0].parent / cron_output._LINKS).iterdir())
            self.assertEqual(2, len(links))
            self.assertIn(f"{2:032x}.json", {link.name for link in links})
            self.assertTrue(all(path.is_file() for path in paths))
