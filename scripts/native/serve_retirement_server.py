"""Finite synthetic HTTP protocol server for native retirement tests only.

No Hermes import, production login/home, tools or inference. The test controller
starts this only in a uniquely named, finite user-manager acceptance service.
"""
import http.server
import json
import os
from pathlib import Path
import socket
import sys
import threading

os.umask(0o077)
root=Path(sys.argv[1]);config=json.loads((root/'server-config.json').read_text(encoding='utf-8'))
state=dict(pid=os.getpid(),requests=[],prepared=False,committed=False,active_count=0)
lock=threading.Lock();permit='fixture_permit_'+'p'*40

def save():
    target=root/'observations.json';scratch=root/'observations.next'
    with scratch.open('w',encoding='utf-8') as out:json.dump(state,out);out.flush();os.fsync(out.fileno())
    scratch.chmod(0o600);os.replace(scratch,target)

class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version='HTTP/1.1'
    def log_message(self,*args):pass
    def reply(self,body,status=200):
        data=json.dumps(body).encode();self.send_response(status)
        self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)))
        self.end_headers();self.wfile.write(data);self.wfile.flush()
    def do_GET(self):self.handle_request(None)
    def do_POST(self):
        length=int(self.headers.get('Content-Length','0'))
        if not 0<=length<=4096:self.reply({'ok':False},400);return
        self.handle_request(json.loads(self.rfile.read(length)))
    def handle_request(self,body):
        if self.headers.get('Authorization')!='Bearer fixture_access':self.reply({'ok':False},401);return
        with lock:
            action=body.get('action') if isinstance(body,dict) else None
            state['requests'].append(dict(method=self.command,path=self.path,action=action));save()
            if self.command=='GET' and self.path=='/api/host/identity':
                self.reply(dict(ok=True,pid=os.getpid(),role='gateway' if config.get('mode')=='wrong-role' else 'serve',protocolVersion=1));return
            if self.command=='GET' and self.path=='/api/health/idle':
                self.reply(dict(ok=True,idle=config.get('mode')!='busy'));return
            if self.command=='POST' and self.path=='/api/health/retirement':
                if action=='prepare':
                    if config.get('mode')=='prepare-busy':self.reply(dict(ok=False,idle=False));return
                    state['prepared']=True;save();self.reply(dict(ok=True,idle=True,token='bad' if config.get('mode')=='invalid-permit' else permit));return
                if action=='commit' and state['prepared'] and body.get('token')==permit:
                    state['committed']=True;save()
                    if config.get('mode')=='drop-commit':
                        self.connection.shutdown(socket.SHUT_RDWR);self.connection.close();return
                    self.reply(dict(ok=True));return
            self.reply(dict(ok=False),400)

server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
state['port']=server.server_port;save()
server.serve_forever(poll_interval=0.1)
