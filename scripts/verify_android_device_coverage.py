"""Verify all owned device partitions against their complete log-only inventories."""
from __future__ import annotations
import argparse
import json
from pathlib import Path
from android_device_coverage import APIS,SHARDS,aggregate_partitions,load_unique_json,read_log
from ci_android_device import external_model_gate_manifest


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts',type=Path,required=True)
    parser.add_argument('--source',required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();root=args.artifacts.resolve(strict=True);records=[]
    for api in APIS:
        for index in range(SHARDS):
            folder=(root/f'android-device-{api}-shard-{index}').resolve(strict=True)
            if folder.parent!=root:raise ValueError('Partition artifact escaped its root')
            paths=[folder/name for name in ['device-partition.json','inventory.log','instrumentation.log']]
            for path in paths:
                if path.is_symlink() or path.resolve(strict=True).parent!=folder or path.stat().st_size>16*1024*1024:raise ValueError('Invalid partition evidence file')
            records.append((load_unique_json(paths[0]),read_log(paths[1]),read_log(paths[2])))
    report=aggregate_partitions(records,args.source,external_model_gate_manifest())
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='external_model_gates'}))


if __name__=='__main__':main()
