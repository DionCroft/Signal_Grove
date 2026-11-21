"""The scoped cron read uses real protected directories and SQLite WAL snapshots."""

from contextlib import closing
from dataclasses import replace
import json
import os
from pathlib import Path
import sqlite3
import tempfile
import unittest
from unittest.mock import patch

from server_extensions.coordinator_workspaces import cron_history
from server_extensions.coordinator_workspaces.coordinator import ReadRefused

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
            started_at TEXT, finished_at TEXT, scheduled_instant TEXT, delivery_outcome TEXT, error TEXT
        )''')
        return db

    def add(self, db, receipt=RECEIPT, job=JOB, status='completed', claimed=UTC):
        db.execute('INSERT INTO executions VALUES (?, ?, ?, ?, NULL, NULL, NULL, ?, ?)',
                   (receipt, job, status, claimed, 'delivered', 'private diagnostic'))
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

            def changed(parent, query):
                result = read_ledger(parent, query)
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
