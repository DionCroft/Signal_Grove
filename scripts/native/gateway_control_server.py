"""Synthetic bounded Unix control wire; no SDK, gateway or project state."""
from pathlib import Path
import json
import os
import socket
import sys
import time


def run(root,address,mode):
    root=Path(root);os.umask(0o077)
    observation=dict(pid=os.getpid(),accepted=0,requests=[])
    def save():
        with (root/'observations.tmp').open('w') as out:json.dump(observation,out);out.flush();os.fsync(out.fileno())
        os.replace(root/'observations.tmp',root/'observations.json')
    with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as server:
        server.bind(bytes.fromhex(address));server.listen(4);save()
        while True:
            connection,_=server.accept();observation['accepted']+=1;save()
            with connection:
                raw=bytearray()
                while len(raw)<=65536 and not raw.endswith(b'\n'):
                    block=connection.recv(1024)
                    if not block:break
                    raw.extend(block)
                if not raw:continue
                request=json.loads(raw);observation['requests'].append(request);save()
                result=dict(protocol=1,pid=os.getpid(),kind='gateway',fixture=True)
                reply=dict(ok=True,protocol=1,id=request['id'],result=result)
                if mode=='wrong_id':reply['id']='0'*32
                if mode=='error':reply=dict(ok=False,protocol=1,id=request['id'],error='fixture-private-error')
                encoded=json.dumps(reply).encode()+b'\n'
                if mode=='oversized':encoded=b'x'*(512*1024+1)
                if mode=='duplicate':encoded=b'{"ok":true,"ok":true,"protocol":1,"id":"'+request['id'].encode()+b'","result":{}}\n'
                if mode=='truncated':encoded=encoded[:-1]
                if mode=='two_frames':encoded+=encoded
                if mode=='slow':time.sleep(.4)
                if mode=='drop':continue
                try:connection.sendall(encoded)
                except OSError:pass


if __name__=='__main__':run(*sys.argv[1:])
