"""Strict Windows-only workspace-provider acceptance, with no skipped cases."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import unittest

sys.dont_write_bytecode=True
ROOT=Path(__file__).resolve().parents[1]


class RecordedResult(unittest.TextTestResult):
    def __init__(self,*args,**kwargs):super().__init__(*args,**kwargs);self.positive=[]
    def addSuccess(self,test):self.positive.append(test.id());super().addSuccess(test)


def identities(suite):
    for item in suite:
        if isinstance(item,unittest.TestSuite):yield from identities(item)
        else:yield item.id()


def acceptance(expected,result,before,after):
    return bool(expected and len(expected)==len(set(expected)) and result.wasSuccessful() and not result.skipped and not result.expectedFailures and not result.unexpectedSuccesses
        and len(result.positive)==result.testsRun==len(expected) and set(result.positive)==set(expected) and before==after)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    if sys.platform!='win32':raise RuntimeError('Real Windows native acceptance is required')
    output=args.output.absolute()
    if output.drive.lower()=='c:':raise RuntimeError('Native evidence must use the approved non-C scratch root')
    package=ROOT/'server_extensions/worker_workspaces'
    sys.path.insert(0,str(ROOT))
    def source_hashes():
        inputs=[Path(__file__).resolve(),*sorted(package.rglob('*.py'))]
        return {str(path.relative_to(ROOT)).replace('\\','/'):hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs}
    before=source_hashes()
    suite=unittest.defaultTestLoader.discover(str(package/'tests_windows'),pattern='test_*.py');expected=list(identities(suite))
    result=unittest.TextTestRunner(verbosity=2,resultclass=RecordedResult).run(suite)
    after=source_hashes()
    accepted=acceptance(expected,result,before,after)
    record=dict(schema=1,status='windows_native_provider_pass' if accepted else 'failed_or_incomplete',platform=sys.platform,
        python=list(sys.version_info[:3]),source_revision=os.environ.get('GITHUB_SHA'),source_files=before,source_unchanged=before==after,
        declared_cases=sorted(expected),positive_cases=sorted(result.positive),tests_run=result.testsRun,
        failures=len(result.failures),errors=len(result.errors),skips=len(result.skipped),scope='Real disposable Windows filesystem provider only; not broker, phone or deployed worker integration')
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:record[k] for k in ['status','tests_run','failures','errors','skips','source_unchanged']}))
    return 0 if accepted else 1


if __name__=='__main__':raise SystemExit(main())
