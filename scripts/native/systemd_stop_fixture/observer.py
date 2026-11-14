from pathlib import Path
import json,os,subprocess,sys

root=Path(sys.argv[1]);expected=json.loads((root/'expected.json').read_text(encoding='utf8'));ready=json.loads((root/'ready.json').read_text(encoding='utf8'));stopped=json.loads((root/'stop-observed.json').read_text(encoding='utf8'))
def call(path,interface,method,*args):
    result=subprocess.run(['busctl','--user','--json=short','call','org.freedesktop.systemd1',path,interface,method,*args],capture_output=True,check=True,timeout=8)
    return json.loads(result.stdout)['data'][0]
unit_object=call('/org/freedesktop/systemd1','org.freedesktop.systemd1.Manager','GetUnit','s',expected['unit'])
def prop(name):return call(unit_object,'org.freedesktop.DBus.Properties','Get','ss','org.freedesktop.systemd1.Service',name)
def value(value):
    # Properties.Get returns a variant whose JSON representation carries its type.
    return value['data'] if isinstance(value,dict) and set(value)>={'type','data'} else value
stop=value(prop('ExecStop'));post=value(prop('ExecStopPost'))
with (root/'raw-stop-commands.json').open('x',encoding='utf8') as stream:json.dump(dict(stop=stop,post=post,pid=os.getpid()),stream,indent=2);stream.write('\n');stream.flush();os.fsync(stream.fileno())
assert len(stop)==1 and len(post)==2
stopper,cleaner=stop[0],post[0]
assert stopper[0]==cleaner[0]==expected['launcher'] and stopper[1]==[expected['launcher'],'--run-module','gateway.systemd_stop_mark'] and cleaner[1]==[expected['launcher'],'--run-module','gateway.cgroup_cleanup']
assert stopper[8:]==[1,0] and cleaner[8:]==[1,0] and not stopper[2] and not cleaner[2]
assert post[1][7]==os.getpid()
own_cgroup=next(line[3:] for line in Path('/proc/self/cgroup').read_text().splitlines() if line.startswith('0::'))
assert own_cgroup==expected['controlGroup']==ready['cgroup']==stopped['cgroup'] and own_cgroup.endswith('/'+expected['unit'])
assert Path('/proc',str(os.getppid()),'comm').read_text().strip()=='systemd'
assert set(int(x) for x in Path('/sys/fs/cgroup'+own_cgroup+'/cgroup.procs').read_text().split())=={os.getpid()}
assert ready['pid']==stopped['pid']==expected['mainPid'] and ready['startTicks']==stopped['startTicks']==expected['mainStartTicks'] and stopped['signal']==15
marker=stopped['marker'];assert marker['target_pid']==ready['pid'] and type(marker['target_start_time']) is int and marker['target_start_time']==int(expected['mainStartTicks']) and marker['stopper_pid']==stopper[7]
assert os.environ['SERVICE_RESULT']=='success' and os.environ['EXIT_CODE']=='exited' and os.environ['EXIT_STATUS']=='0'
for child in ready['children']:
    assert not (root/f"sentinel-{child['pid']}.term.json").exists()
    path=Path('/proc',str(child['pid']),'stat')
    if path.exists():
        fields=path.read_text().rsplit(')',1)[1].split()
        assert fields[19]!=child['startTicks'] or fields[0]=='Z'
report=dict(state='REAL_SYSTEMD_STOP_AND_CGROUP_HELPERS_PASS',unit=expected['unit'],managerParent=True,controlGroup=own_cgroup,fixtureMainPid=ready['pid'],sentinels=ready['children'],mainSawExactPlannedStopMarker=True,sentinelSigtermObserved=False,stopCommand=stopper,cleanupCommand=cleaner,observerPid=os.getpid(),serviceResult=os.environ['SERVICE_RESULT'],mainExitCode=os.environ['EXIT_CODE'],mainExitStatus=os.environ['EXIT_STATUS'])
path=root/'observer.json';temp=root/'observer.pending'
with temp.open('x',encoding='utf8') as stream:json.dump(report,stream,indent=2);stream.write('\n');stream.flush();os.fsync(stream.fileno())
os.replace(temp,path);print('ISOLATED_STOP_HELPERS_RESULT='+json.dumps(report),flush=True)
