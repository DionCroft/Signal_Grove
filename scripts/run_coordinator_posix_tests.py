"""Strict real-Linux acceptance of the optional coordinator's protected policy file."""
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


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-root',type=Path,required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args()
    if sys.platform!='linux':raise RuntimeError('Actual Linux acceptance is required; no host substitution')
    work=args.work_root.absolute();output=args.output.absolute()
    if work.exists() or work.is_symlink() or output.exists() or output.is_symlink():raise RuntimeError('Preserve existing evidence')
    if work.resolve().is_relative_to(ROOT.resolve()):raise RuntimeError('Use a new scratch root outside source')
    work.mkdir(mode=0o700)
    os.environ['SIGNAL_GROVE_POLICY_TEST_ROOT']=str(work)
    package=ROOT/'server_extensions/coordinator_workspaces'
    sys.path.insert(0,str(ROOT))
    def sources():
        inputs=[Path(__file__).resolve(),*sorted(package.rglob('*.py'))]
        return {p.relative_to(ROOT).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in inputs}
    before=sources();suite=unittest.defaultTestLoader.discover(str(package/'tests_posix'),pattern='test_*.py')
    expected=list(identities(suite));result=unittest.TextTestRunner(verbosity=2,resultclass=RecordedResult).run(suite)
    after=sources()
    accepted=bool(expected and len(expected)==len(set(expected)) and result.wasSuccessful()
                  and not result.skipped and not result.expectedFailures and not result.unexpectedSuccesses
                  and result.testsRun==len(expected)==len(result.positive) and set(expected)==set(result.positive)
                  and before==after)
    record=dict(schema=1,status='coordinator_posix_policy_pass' if accepted else 'failed_or_incomplete',
                platform=sys.platform,python=list(sys.version_info[:3]),source_revision=os.environ.get('GITHUB_SHA'),
                source_files=before,source_unchanged=before==after,declared_cases=sorted(expected),
                positive_cases=sorted(result.positive),tests_run=result.testsRun,failures=len(result.failures),
                errors=len(result.errors),skips=len(result.skipped),
                scope='Real Linux disposable owner-permission, descriptor/path and fresh-policy fixtures with synthetic worker replies; not Pi deployment, HTTP login, SSH/worker/project/NAS or release acceptance')
    output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(record,indent=2)+'\n',encoding='utf8')
    print(json.dumps({k:record[k] for k in ('status','tests_run','failures','errors','skips','source_unchanged')}))
    return 0 if accepted else 1


if __name__=='__main__':raise SystemExit(main())
