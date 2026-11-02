"""Validate the committed SHA-256 verification policy before a CI build."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

NS='https://schema.gradle.org/dependency-verification'

def check(text:str)->tuple[int,int]:
    root=ET.fromstring(text)
    if root.tag!=f'{{{NS}}}verification-metadata':raise ValueError('Unexpected verification schema.')
    config=root.find(f'{{{NS}}}configuration')
    if config is None or config.findtext(f'{{{NS}}}verify-metadata')!='true':raise ValueError('Metadata verification must remain enabled.')
    if root.findall(f'.//{{{NS}}}trusted-artifacts'):raise ValueError('Blanket artifact trust is prohibited.')
    components=root.findall(f'{{{NS}}}components/{{{NS}}}component')
    if not components:raise ValueError('Verification components are missing.')
    seen=set();count=0
    for component in components:
        identity=tuple(component.get(key,'') for key in ('group','name','version'))
        if not all(identity) or identity in seen:raise ValueError('Invalid or duplicate component identity.')
        seen.add(identity);names=set()
        artifacts=component.findall(f'{{{NS}}}artifact')
        if not artifacts:raise ValueError('Component artifacts are missing.')
        for artifact in artifacts:
            name=artifact.get('name','')
            if not name or name in names:raise ValueError('Invalid or duplicate artifact identity.')
            names.add(name)
            hashes=artifact.findall(f'{{{NS}}}sha256')
            if not hashes:raise ValueError('Every artifact requires SHA-256.')
            for checksum in hashes:
                values=[checksum.get('value','')]+[node.get('value','') for node in checksum.findall(f'{{{NS}}}also-trust')]
                if any(not re.fullmatch('[a-f0-9]{64}',value) or value=='0'*64 for value in values):raise ValueError('Invalid SHA-256 value.')
            count+=1
    return len(components),count

if __name__=='__main__':
    try:
        components,artifacts=check((Path(__file__).resolve().parents[1]/'gradle/verification-metadata.xml').read_text(encoding='utf-8'))
        print(f'Checksum policy passed: {components} components, {artifacts} artifacts, metadata verified, no blanket artifact trust.')
    except (OSError,ValueError,ET.ParseError) as exc:
        print(f'Checksum policy rejected: {exc}',file=sys.stderr);sys.exit(1)
