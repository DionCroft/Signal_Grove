"""Owned directory/journal fixture; non-filesystem transaction gates are synthetic."""
from pathlib import Path
import importlib.util
import json
import hashlib
import os
import sys
import uuid


def build(parent):
    # Tests use the source module's own exact class identities, independently of
    # earlier unittest files that load different reviewed module generations.
    scripts=Path(__file__).resolve().parents[1]
    modules={}
    for name in ('cutover_journal','cutover_coordinator','plan_pi_runtime','native_file_publication','rendered_publication_catalog','native_operational_paths'):
        spec=importlib.util.spec_from_file_location(name,scripts/(name+'.py'));m=importlib.util.module_from_spec(spec);sys.modules[name]=m;spec.loader.exec_module(m);modules[name]=m
    native=modules['native_file_publication'];renderer=modules['rendered_publication_catalog'];ops=modules['native_operational_paths']
    root=Path(parent)/('signal-grove-catalog-'+uuid.uuid4().hex);root.mkdir(mode=0o700)
    account=root/'account';fleet=account/'Projects/fleet';policy=json.dumps(dict(schema=1,authority='fixture-authority',grants=[dict(subject='fixture-owner',profile_id=str(fleet/'hermes'),device='FIXTURE',project_ids=['fixture'])]),separators=(',',':')).encode()
    launcher=b'#!/bin/sh\nexit 0\n';definition=renderer.RenderedCatalog.for_owned_fixture(root,renderer.PolicyReview('fixture-authority',('FIXTURE',),hashlib.sha256(policy).hexdigest()),policy,hashlib.sha256(launcher).hexdigest())
    packet=definition.packet();commands=Path(packet['command_directory']);commands.mkdir(parents=True,mode=0o700);(commands/'hermes').write_bytes(launcher);(commands/'hermes').chmod(0o755)
    source=commands.parent/packet['layout']['source_name'];source.mkdir(mode=0o700)
    client=account/'.local/bin';client.mkdir(parents=True,mode=0o700);(client/'hermes').write_bytes(b'original-cli');(client/'hermes').chmod(0o755)
    (account/'.config/systemd/user').mkdir(parents=True,mode=0o700)
    (account/'.local/state').mkdir(mode=0o700)
    for _,path in definition.mode_paths():
        p=Path(path);p.mkdir(parents=True,exist_ok=True,mode=0o700);p.chmod(0o775)
    operation=root/'operation';operation.mkdir(mode=0o700);paths={}
    for role in ops.ROLES:
        p=source if role=='new_source' else Path(packet['canonical_home']) if role=='hermes_home' else operation/role
        p.mkdir(mode=0o700,exist_ok=True);paths[role]=p
    captured=renderer.capture_native(definition,definition.sha256);publication=captured.capture_ancestry(captured.sha256)
    catalog=ops.OperationalDirectories(tuple((role,native.anchor(paths[role])) for role in ops.ROLES))
    guard=ops.NativeOperationalPaths.capture(publication,publication.sha256,catalog,catalog.sha256)
    return dict(root=root,paths=paths,captured=captured,publication=publication,catalog=catalog,guard=guard,modules=modules)
