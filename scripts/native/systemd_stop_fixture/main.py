from pathlib import Path
import json,os,signal,subprocess,sys,time

root=Path(sys.argv[1]);assert root.is_absolute() and root.is_dir()
def birth(pid):return Path('/proc',str(pid),'stat').read_text().rsplit(')',1)[1].split()[19]
def cgroup():return next(line[3:] for line in Path('/proc/self/cgroup').read_text().splitlines() if line.startswith('0::'))
def write(name,value):
    path=root/name;temp=root/(name+'.pending');assert not path.exists()
    with temp.open('x',encoding='utf8') as stream:json.dump(value,stream);stream.write('\n');stream.flush();os.fsync(stream.fileno())
    os.replace(temp,path)
def stop(signum,frame):
    home=Path(os.environ['HERMES_HOME']);marker=home/'.gateway-planned-stop.json'
    value=json.loads(marker.read_text(encoding='utf8')) if marker.is_file() else None
    write('stop-observed.json',dict(pid=os.getpid(),startTicks=birth(os.getpid()),signal=signum,marker=value,cgroup=cgroup()))
    raise SystemExit(0)
signal.signal(signal.SIGTERM,stop)
children=[subprocess.Popen([sys.executable,'-I','-B',str(Path(__file__).with_name('sentinel.py')),str(root)]) for _ in range(2)]
deadline=time.monotonic()+10
while not all((root/f'sentinel-{child.pid}.json').is_file() for child in children):
    assert time.monotonic()<deadline and all(child.poll() is None for child in children)
    time.sleep(.05)
write('ready.json',dict(pid=os.getpid(),startTicks=birth(os.getpid()),parent=os.getppid(),parentComm=Path('/proc',str(os.getppid()),'comm').read_text().strip(),cgroup=cgroup(),children=[dict(pid=child.pid,startTicks=birth(child.pid)) for child in children]))
while True:signal.pause()
