from pathlib import Path
import json,os,signal,sys

root=Path(sys.argv[1]);pid=os.getpid()
def write(suffix,value):
    path=root/f'sentinel-{pid}{suffix}';temp=path.with_suffix(path.suffix+'.pending');assert not path.exists()
    with temp.open('x',encoding='utf8') as stream:json.dump(value,stream);stream.write('\n');stream.flush();os.fsync(stream.fileno())
    os.replace(temp,path)
def term(signum,frame):write('.term.json',dict(pid=pid,signal=signum))
signal.signal(signal.SIGTERM,term)
write('.json',dict(pid=pid,parent=os.getppid(),startTicks=Path('/proc/self/stat').read_text().rsplit(')',1)[1].split()[19],cgroup=next(line[3:] for line in Path('/proc/self/cgroup').read_text().splitlines() if line.startswith('0::'))))
while True:signal.pause()
