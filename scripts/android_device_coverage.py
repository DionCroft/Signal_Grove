"""Exact case evidence for owned Android CI. Inventory never certifies test execution."""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re

APIS=(26,37)
SHARDS=2
CASE_KEYS={'class_name','method'}


def _sha(value):
    return isinstance(value,str) and re.fullmatch(r'[0-9a-f]{64}',value) is not None


def _cases(rows):
    if not isinstance(rows,list) or not 1<=len(rows)<=20000:raise ValueError('Invalid bounded case inventory')
    result=set()
    for row in rows:
        if not isinstance(row,dict) or set(row)!=CASE_KEYS:raise ValueError('Invalid case identity')
        c,m=row['class_name'],row['method']
        if (not isinstance(c,str) or len(c)>512 or not re.fullmatch(r'org\.hermesmobile\.app\.[A-Za-z0-9_.$]+',c)
            or not isinstance(m,str) or not 1<=len(m)<=512 or any(ord(x)<32 or ord(x)==127 for x in m)):raise ValueError('Invalid case name')
        key=(c,m)
        if key in result:raise ValueError('Duplicate case identity')
        result.add(key)
    return result


def case_records(output,returncode):
    """Require one complete native envelope and one start/finish per distinct case."""
    if not isinstance(output,str) or len(output)>16*1024*1024 or type(returncode) is not int or returncode!=0:raise ValueError('Case command failed or exceeded output bounds')
    normalized='\n'.join(output.splitlines())
    summaries=re.findall(r'^OK \(([1-9][0-9]*) tests?\)\s*$',normalized,re.M)
    terminals=re.findall(r'^INSTRUMENTATION_CODE: (.+)$',normalized,re.M)
    if len(summaries)!=1 or terminals!=['-1'] or re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|shortMsg=|Process crashed',output):raise ValueError('No exact successful native terminal envelope')
    total=int(summaries[0]);pending=None;status={};completed=[]
    if not 1<=total<=20000:raise ValueError('Case count outside bounds')
    for line in output.splitlines():
        if line.startswith('INSTRUMENTATION_STATUS: '):
            k,_,v=line[len('INSTRUMENTATION_STATUS: '):].partition('=')
            if k in ('class','test','current','numtests'):
                if k in status:raise ValueError('Duplicate native status metadata')
                status[k]=v
        elif line.startswith('INSTRUMENTATION_STATUS_CODE: '):
            code=line.partition(': ')[2]
            if set(status)!={'class','test','current','numtests'} or status['numtests']!=str(total) or status['current']!=str(len(completed)+1):raise ValueError('Partial or inconsistent native case metadata')
            key=(status['class'],status['test'])
            _cases([dict(class_name=key[0],method=key[1])])
            if code=='1':
                if pending is not None:raise ValueError('Overlapping case starts')
                pending=key
            elif code=='0':
                if pending!=key:raise ValueError('Case finish without its exact start')
                completed.append(dict(class_name=key[0],method=key[1]));pending=None
            else:raise ValueError('Failed skipped or unexpected native status')
            status={}
    if pending is not None or status or len(completed)!=total:raise ValueError('Incomplete native case list')
    _cases(completed)
    return sorted(completed,key=lambda r:(r['class_name'],r['method']))


def inventory_record(output,returncode):
    return dict(schema=1,status='inventory_only',native_acceptance=False,test_bodies_executed=False,cases=case_records(output,returncode))


def partition_record(*,api,index,source,apks,external,inventory_output,native_output,inventory_code=0,native_code=0):
    report=dict(schema=1,status='standard_partition_pass',api=api,shard_index=index,shard_count=SHARDS,
        source_revision=source,apk_sha256=apks,external_model_gates=external,
        command_returncodes=dict(inventory=inventory_code,native=native_code),
        inventory_cases=case_records(inventory_output,inventory_code),executed_cases=case_records(native_output,native_code),
        inventory_log_sha256=hashlib.sha256(inventory_output.encode()).hexdigest(),
        native_log_sha256=hashlib.sha256(native_output.encode()).hexdigest())
    verify_partition(report,inventory_output,native_output,source,external)
    return report


def verify_partition(report,inventory_output,native_output,source,external):
    fields={'schema','status','api','shard_index','shard_count','source_revision','apk_sha256','external_model_gates',
        'command_returncodes','inventory_cases','executed_cases','inventory_log_sha256','native_log_sha256'}
    if not isinstance(report,dict) or set(report)!=fields:raise ValueError('Unexpected partition fields')
    if (type(report['schema']) is not int or report['schema']!=1 or report['status']!='standard_partition_pass'
        or type(report['api']) is not int or report['api'] not in APIS
        or type(report['shard_count']) is not int or report['shard_count']!=SHARDS
        or type(report['shard_index']) is not int or report['shard_index'] not in range(SHARDS)
        or not isinstance(source,str) or re.fullmatch(r'[0-9a-f]{40}',source) is None or report['source_revision']!=source):raise ValueError('Partition identity mismatch')
    apks=report['apk_sha256']
    if not isinstance(apks,dict) or set(apks)!={'app','test'} or not all(_sha(x) for x in apks.values()):raise ValueError('Invalid APK binding')
    if report['external_model_gates']!=external:raise ValueError('External gate scope changed')
    codes=report['command_returncodes']
    if not isinstance(codes,dict) or set(codes)!={'inventory','native'} or any(type(x) is not int or x!=0 for x in codes.values()):raise ValueError('Unsuccessful or unknown command')
    for name,text in [('inventory',inventory_output),('native',native_output)]:
        if not isinstance(text,str) or not _sha(report[name+'_log_sha256']) or hashlib.sha256(text.encode()).hexdigest()!=report[name+'_log_sha256']:raise ValueError('Native evidence hash mismatch')
    inventory=case_records(inventory_output,codes['inventory']);native=case_records(native_output,codes['native'])
    if report['inventory_cases']!=inventory or report['executed_cases']!=native:raise ValueError('Case projection differs from raw native evidence')
    all_cases=_cases(inventory);executed=_cases(native)
    if not executed<=all_cases:raise ValueError('Executed cases outside the complete inventory')
    excluded={row['class_name'] for row in external['gates']}
    if any(c in excluded for c,_ in all_cases):raise ValueError('External model gate unexpectedly included')
    return all_cases,executed


def aggregate_partitions(records,source,external):
    """Only this cross-partition union can establish complete standard-suite coverage."""
    if not isinstance(records,list) or len(records)!=len(APIS)*SHARDS:raise ValueError('All four native partitions are required')
    rows={};apks=None
    for report,inventory_output,native_output in records:
        inventory,executed=verify_partition(report,inventory_output,native_output,source,external)
        key=(report['api'],report['shard_index'])
        if key in rows:raise ValueError('Duplicate partition')
        rows[key]=(inventory,executed)
        if apks is None:apks=report['apk_sha256']
        elif apks!=report['apk_sha256']:raise ValueError('Partitions used different APK pairs')
    if set(rows)!={(api,index) for api in APIS for index in range(SHARDS)}:raise ValueError('Partition matrix incomplete')
    accepted={}
    for api in APIS:
        first,second=rows[api,0],rows[api,1]
        if first[0]!=second[0]:raise ValueError('Full inventories disagree')
        if not first[1].isdisjoint(second[1]):raise ValueError('A native case executed in both partitions')
        if first[1]|second[1]!=first[0]:raise ValueError('Native partitions omit inventory cases')
        accepted[str(api)]=dict(inventory_cases=len(first[0]),executed_cases=len(first[1]|second[1]),shard_cases=[len(first[1]),len(second[1])])
    return dict(schema=1,status='standard_suite_complete',source_revision=source,apk_sha256=apks,apis=accepted,
        external_model_gates=external,external_model_acceptance=False)


def load_unique_json(path):
    if path.stat().st_size>16*1024*1024:raise ValueError('Oversized native report')
    def unique(pairs):
        result={}
        for key,value in pairs:
            if key in result:raise ValueError('Duplicate JSON field')
            result[key]=value
        return result
    return json.loads(path.read_text(encoding='utf-8'),object_pairs_hook=unique)


def read_log(path):
    with Path(path).open(encoding='utf-8',newline='') as stream:return stream.read()
