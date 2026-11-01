"""Review Git-tracked/staged text and prohibited artifacts without printing matching values."""
from pathlib import Path
import re, subprocess, sys
root=Path(__file__).resolve().parents[1]
paths=subprocess.check_output(['git','ls-files','-z','--cached'],cwd=root).decode().split('\0')
bad=[]
for name in filter(None,paths):
    path=root/name
    if not path.is_file(): continue
    if path.name in {'local.properties','.env'} or path.suffix in {'.keystore','.jks','.log','.db'} or any(p in {'.gradle','build','.local'} for p in path.parts):
        bad.append((name,'private/generated file'));continue
    if path.suffix in {'.jar','.png','.webp'}: continue
    text=path.read_text(encoding='utf-8',errors='replace')
    for pattern in [r'gh[pousr]_[A-Za-z0-9]{30,}',r'github_pat_[A-Za-z0-9_]{40,}',r'sk-[A-Za-z0-9]{30,}',r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',r'(?i)(?:bearer|token)\s*[:=]\s*["\'][A-Za-z0-9_-]{40,}']:
        if re.search(pattern,text): bad.append((name,'possible credential'));break
if bad:
    for name,reason in bad: print(f'{name}: {reason}')
    sys.exit(1)
print(f'Sensitive-file and credential pattern review passed for {len(list(filter(None,paths)))} tracked files.')
