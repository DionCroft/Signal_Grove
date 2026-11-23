"""Stage the reviewed admission changes for the exact owner Windows broker."""
from argparse import ArgumentParser
import ast
import hashlib
import json
from pathlib import Path

SOURCE_HASHES = {
    "worker.py": "50aeb1f1f8e7e5ee93e46bae20e1954d889e75dcdd29c5d6e49be3a0b2c237a4",
    "host_daemon.py": "26875800aba972b8c1fcb4571f1d6b4fc79c955987d6681ce4975ee7b179f4b1",
}


def replace(source, old, new):
    if source.count(old) != 1:
        raise ValueError("Expected exactly one admitted source anchor")
    return source.replace(old, new)


def stage_broker(source):
    source = replace(source, "def step():", "def step(reviewed_lookup=None):")
    anchor = '''  d.execute("UPDATE requests SET state='claimed' WHERE id=? AND state='ready'",(row[0],))
 try:
  data=json.loads(row[1]);worker.start(data)
'''
    return replace(source, anchor, '''  data=json.loads(row[1])
  admission=None
  if data.get('action')=='reviewed_start':
   try:
    if reviewed_lookup is None:raise ValueError('Reviewed operator unavailable')
    admission=reviewed_lookup(data['id'])
    with admission.claim(data):
     claim=d.execute("UPDATE requests SET state='claimed' WHERE id=? AND state='ready'",(row[0],))
     if claim.rowcount!=1:raise ValueError('Reviewed queue claim changed')
     d.commit()
   except Exception:
    d.rollback()
    # No launcher has been called. Preserve the original ID and hold any
    # claim whose database outcome cannot be proved.
    changed=d.execute("UPDATE requests SET state='failed' WHERE id=? AND state='ready'",(row[0],))
    if changed.rowcount!=1:raise
    return
  else:
   d.execute("UPDATE requests SET state='claimed' WHERE id=? AND state='ready'",(row[0],))
 try:
  if admission is None:worker.start(data)
  else:worker.start(data,admission=admission)
''').replace("def main():", "def main(reviewed_lookup=None):").replace(
        "if ready:step()", "if ready:step(reviewed_lookup)")


def stage_worker(source):
    source = replace(source, "from contextlib import contextmanager", "from contextlib import ExitStack,contextmanager")
    source = replace(source, "def start(data):", """def start(data,*,admission=None):
 if (data.get('action')=='reviewed_start') != (admission is not None):
  raise PermissionError('Reviewed native admission required')""")
    anchor = "  directory=root/'.agentic/dispatcher/jobs'/ident;directory.mkdir(parents=True,exist_ok=False)\n"
    source = replace(source, anchor, '''  directory=root/'.agentic/dispatcher/jobs'/ident
  if admission is not None:
   from threading import Thread
   receipt={'id':ident,'project':bound['id'],'directory':str(directory),
            'state':'dispatching','pid':None,'started':None,'reviewed':True}
   atomic(STATE/(ident+'.json'),receipt)
   Thread(target=run_reviewed,args=(ident,data,admission),daemon=True).start()
   return {'id':ident,'state':'dispatching','project':bound['id']}
  directory.mkdir(parents=True,exist_ok=False)
''')
    source = replace(source, "def run(ident):", '''def run_reviewed(ident,data,admission):
 try:run(ident,data=data,admission=admission)
 except Exception:
  receipt=load(ident)
  if receipt['state'] in {'dispatching','running'}:
   receipt.update(state='unknown' if admission.launch_attempted else 'failed',
                  reason='Reviewed worker launch did not settle; inspect the original receipt.')
   atomic(STATE/(ident+'.json'),receipt)

def run(ident,*,data=None,admission=None):''')
    source = replace(source, " r=load(ident);directory=pathlib.Path(r['directory']);data=json.loads((directory/'request.json').read_text())",
                     ''' r=load(ident);directory=pathlib.Path(r['directory'])
 if (r.get('reviewed') is True) != (admission is not None):
  raise PermissionError('Reviewed launch belongs to the owning broker')
 if admission is None:data=json.loads((directory/'request.json').read_text())''')
    before = """ root=project(data['project'])['root'];safety_config()
 if root!=data['root'] or project(data['project'])!=data.get('binding'):raise ValueError('Frozen host project changed')
"""
    after = """ root=project(data['project'])['root'];safety_config()
 if project(data['project'])!=data.get('binding'):raise ValueError('Frozen host project changed')
 with ExitStack() as files:
  if admission is not None:
   checkout=files.enter_context(admission.launch(data))
   if checkout.root!=root or directory!=pathlib.Path(root)/'.agentic/dispatcher/jobs'/ident:
    raise ValueError('Approved launch directory changed')
   if (directory/'cancel.requested').exists() or (STATE/(ident+'.cancel')).exists():
    r.update(state='cancelled');atomic(STATE/(ident+'.json'),r);return
   directory=files.enter_context(admission.job_directory(checkout))
   data={**data,'root':root,'directory':str(directory)}
   with (directory/'request.json').open('x',encoding='utf-8') as request_file:
    json.dump(data,request_file)
  elif root!=data['root']:raise ValueError('Frozen host project changed')
"""
    line = " with (directory/'stdout.jsonl').open('wb') as out,(directory/'stderr.log').open('wb') as err:\n"
    start = source.index(before)
    end = source.index(line, start)
    validation = source[start + len(before):end]
    source = replace(source, before + validation + line, after +
                     "".join(" " + ln for ln in validation.splitlines(keepends=True)) +
                     "  mode='xb' if admission is not None else 'wb'\n"
                     "  out=files.enter_context((directory/'stdout.jsonl').open(mode))\n"
                     "  err=files.enter_context((directory/'stderr.log').open(mode))\n")
    opener = "  p=subprocess.Popen(command(root,data['profile'],data['scope']),cwd=root,env=env,stdin=subprocess.PIPE,stdout=out,stderr=err,creationflags=subprocess.CREATE_NO_WINDOW)"
    source = replace(source, opener, """  launch_input=subprocess.PIPE
  if admission is not None:
   launch_input=files.enter_context(tempfile.TemporaryFile(mode='w+b',dir=directory))
   launch_input.write(json.dumps(data).encode('utf-8'))
   launch_input.seek(0)
   admission.launch_attempted=True
  p=subprocess.Popen(command(root,data['profile'],data['scope']),cwd=root,env=env,
                     stdin=launch_input,stdout=out,stderr=err,creationflags=subprocess.CREATE_NO_WINDOW)""")
    return replace(source, """  try:p.communicate(json.dumps(data).encode('utf-8'),timeout=570)
  except subprocess.TimeoutExpired:
   sys.path.insert(0,str(RT));from agent.deadline import kill_process_tree
   kill_process_tree(p.pid);r.update(state='unknown',reason='Host stage deadline; inspect partial work before retrying.');settle(r,data);return
""", """ try:p.communicate(None if admission is not None else json.dumps(data).encode('utf-8'),timeout=570)
 except subprocess.TimeoutExpired:
  sys.path.insert(0,str(RT));from agent.deadline import kill_process_tree
  kill_process_tree(p.pid);r.update(state='unknown',reason='Host stage deadline; inspect partial work before retrying.');settle(r,data);return
""")


def main():
    parser = ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    source, output = args.source.resolve(strict=True), args.output.resolve()
    if output.exists() or output == source or output.is_relative_to(source):
        raise ValueError("Use a fresh external candidate directory")
    files = {}
    for name, expected in SOURCE_HASHES.items():
        original = (source / name).read_bytes()
        if hashlib.sha256(original).hexdigest() != expected:
            raise ValueError(f"Unreviewed owner source: {name}")
        text = original.decode("utf-8").replace("\r\n", "\n")
        changed = (stage_worker if name == "worker.py" else stage_broker)(text)
        ast.parse(changed, filename=name)
        files[name] = changed.encode("utf-8")
    output.mkdir()
    for name, data in files.items():
        (output / name).write_bytes(data)
    manifest = {name: hashlib.sha256(data).hexdigest() for name, data in files.items()}
    (output / "source-manifest.json").write_text(json.dumps({"input": SOURCE_HASHES, "candidate": manifest}) + "\n",
                                                encoding="utf-8")
    print(json.dumps(manifest))


if __name__ == "__main__":
    main()
