"""Closed renderer-to-native catalogs; read-only until a fixed stage runs.

No listener, CLI, service, credential, grant discovery or automatic action. The
external operator supplies reviewed policy/launcher hashes and actual authority.
Existing native work/backup/PM/protected-ancestry approval gates still apply.
"""
from __future__ import annotations

from dataclasses import dataclass, field, replace, asdict
import hashlib
import os
from pathlib import Path, PurePosixPath
import re
import sys

from plan_pi_runtime import render, COMMAND_DIRECTORY
from native_file_publication import (DirectoryAnchor,DirectoryCreation,FileImage,FileBinding,ModeBinding,
    FILE_ROLES,MODE_ROLES,STAGE_ROLES,NativeAncestry,anchor,observe,digest,_hash,_path,_open_directory,_image,MAX_FILE)
from cutover_journal import Refused,Unknown

FIXTURE_LAYOUT=(('account_home','/home/fixture'),('fleet_root','/home/fixture/Projects/fleet'),
                ('generation','fixture-v1'),('source_name','runtime-source'))
CREATE_ROLES=frozenset({'source_gateway','source_serve','source_client','source_server','policy','gateway_link','serve_link'})


@dataclass(frozen=True)
class PolicyReview:
    authority:str
    devices:tuple[str,...]
    sha256:str

    def __post_init__(self):
        if (type(self.authority) is not str or not 1<=len(self.authority)<=512 or any(c in self.authority for c in '\0\r\n')
                or type(self.devices) is not tuple or not 1<=len(self.devices)<=16
                or any(type(d) is not str or not re.fullmatch(r'[A-Z0-9][A-Z0-9_-]{0,63}',d) for d in self.devices)
                or len(set(self.devices))!=len(self.devices) or not _hash(self.sha256)):
            raise ValueError('Reviewed authority, fixed devices and exact policy hash required')


@dataclass(frozen=True)
class TargetSpec:
    role:str
    path:str
    kind:str
    value:bytes|str=field(repr=False)

    def metadata(self):return dict(role=self.role,path=self.path,kind=self.kind,
        payload_sha256=hashlib.sha256(self.value).hexdigest() if type(self.value) is bytes else None,
        target=self.value if type(self.value) is str else None)


@dataclass(frozen=True)
class RenderedCatalog:
    layout:tuple[tuple[str,str],...]
    policy_review:PolicyReview
    policy_bytes:bytes=field(repr=False)
    launcher_sha256:str
    owned_root:str|None=None

    def __post_init__(self):
        if (type(self.layout) is not tuple or any(type(p) is not tuple or len(p)!=2 for p in self.layout)
                or len(dict(self.layout))!=len(self.layout) or tuple(sorted(self.layout))!=self.layout
                or type(self.policy_review) is not PolicyReview or type(self.policy_bytes) is not bytes
                or not 1<=len(self.policy_bytes)<=32*1024 or hashlib.sha256(self.policy_bytes).hexdigest()!=self.policy_review.sha256
                or not _hash(self.launcher_sha256)):
            raise ValueError('Closed renderer layout and exact reviewed payload hashes required')
        packet=render(dict(self.layout));account=PurePosixPath(packet['layout']['account_home']);fleet=PurePosixPath(packet['layout']['fleet_root'])
        if fleet.parent!=account/'Projects':raise ValueError('The named fleet must be a dedicated child of the owner Projects directory')
        if self.owned_root is not None and (self.layout!=FIXTURE_LAYOUT or not _path(self.owned_root)
                or not re.fullmatch(r'signal-grove-catalog-[a-f0-9]{32}',PurePosixPath(self.owned_root).name)):
            raise ValueError('Owned fixtures require their exact separate virtual layout and unique private root')

    @classmethod
    def for_layout(cls,layout,policy_review,policy_bytes,launcher_sha256):
        if type(layout) is not dict:raise ValueError('Closed layout mapping required')
        return cls(tuple(sorted(layout.items())),policy_review,policy_bytes,launcher_sha256)

    @classmethod
    def for_owned_fixture(cls,root,policy_review,policy_bytes,launcher_sha256):
        return cls(FIXTURE_LAYOUT,policy_review,policy_bytes,launcher_sha256,str(root))

    @property
    def scope(self):return 'owned_fixture_only' if self.owned_root is not None else 'production_review_only'

    def packet(self):
        packet=render(dict(self.layout))
        if self.owned_root is None:return packet
        replacement=self.owned_root+'/account'
        def rewrite(value):
            if type(value) is str:return value.replace('/home/fixture',replacement)
            if type(value) is list:return [rewrite(v) for v in value]
            if type(value) is dict:return {rewrite(k):rewrite(v) for k,v in value.items()}
            return value
        packet=rewrite(packet);packet['state']='OWNED_RENDERED_FIXTURE_ONLY';return packet

    def specs(self):
        p=self.packet();fleet=p['layout']['fleet_root'];gen=p['layout']['generation'];commands=p['command_directory']
        sources=(('source_gateway',f'scripts/systemd/{gen}/hermes-gateway.service.conf'),
                 ('source_serve',f'scripts/systemd/{gen}/hermes-serve.service.conf'),
                 ('source_client',f'scripts/bin/{gen}/hermes-client'),('source_server',f'scripts/bin/{gen}/hermes-serve'))
        result=[TargetSpec(role,fleet+'/'+name,'file',p['source_artifacts'][name].encode()) for role,name in sources]
        result.extend(TargetSpec(role,commands+'/'+name,'file',p['deployed_wrappers'][commands+'/'+name].encode())
            for role,name in (('runtime_client','hermes-client'),('runtime_server','hermes-serve')))
        result.append(TargetSpec('policy',p['policy_path'],'file',self.policy_bytes))
        units=p['layout']['account_home']+'/.config/systemd/user'
        for role,name in (('gateway_link','hermes-gateway.service'),('serve_link','hermes-serve.service')):
            path=units+'/'+name+'.d/100-signal-grove.conf';result.append(TargetSpec(role,path,'link',p['proposed_dropin_links'][path]))
        path=p['layout']['account_home']+'/.local/bin/hermes';result.append(TargetSpec('client_link',path,'link',p['proposed_user_command_link'][path]))
        if tuple(s.role for s in result)!=tuple(FILE_ROLES) or any(PurePosixPath(s.path).name!=FILE_ROLES[s.role][0] for s in result):
            raise Refused('Renderer outputs no longer match the fixed native role catalog')
        return tuple(result)

    def mode_paths(self):
        p=self.packet();account=p['layout']['account_home'];fleet=p['layout']['fleet_root']
        paths=(account+'/.config',account+'/.local/bin',account+'/Projects',fleet,
               p['canonical_home']+'/runtime-generations',fleet+'/scripts/bin',fleet+'/scripts/systemd')
        return tuple(zip(MODE_ROLES,paths))

    def metadata(self):return dict(scope=self.scope,layout=self.layout,owned_root=self.owned_root,
        policy_review=asdict(self.policy_review),launcher_sha256=self.launcher_sha256,
        targets=[s.metadata() for s in self.specs()],mode_paths=self.mode_paths())
    @property
    def sha256(self):return digest(self.metadata())


def _original_bytes(parent,role,expected):
    fd=_open_directory(parent);opened=None
    try:
        if _image(fd,FILE_ROLES[role][0],parent.uid)!=expected:raise Unknown('Original entry changed before retention')
        opened=os.open(FILE_ROLES[role][0],os.O_RDONLY|os.O_NOFOLLOW|os.O_NONBLOCK|os.O_CLOEXEC,dir_fd=fd)
        info=os.fstat(opened)
        if (info.st_dev,info.st_ino)!=(expected.device,expected.inode):raise Unknown('Original entry identity changed')
        data=os.pread(opened,MAX_FILE+1,0)
        if len(data)!=expected.size or hashlib.sha256(data).hexdigest()!=expected.sha256 or _image(fd,FILE_ROLES[role][0],parent.uid)!=expected:
            raise Unknown('Original entry changed during bounded retention')
        return data
    finally:
        if opened is not None:os.close(opened)
        os.close(fd)


@dataclass(frozen=True)
class CapturedCatalog:
    definition:RenderedCatalog
    bindings:tuple[FileBinding,...]
    modes:tuple[ModeBinding,...]
    command_parent:DirectoryAnchor
    launcher_image:FileImage
    original_values:tuple[tuple[str,bytes|str|None],...]=field(repr=False)

    def capture_ancestry(self,reviewed_capture_sha256):
        if reviewed_capture_sha256!=self.sha256:raise Refused('Exact reviewed renderer capture required for ancestry')
        packet=self.definition.packet()
        source=packet['canonical_home']+'/runtime-generations/'+packet['layout']['generation']+'/'+packet['layout']['source_name']
        endpoints=tuple(sorted({str(Path(s.path).parent) for s in self.definition.specs()}|{source,self.command_parent.path}))
        creations=tuple(dict.fromkeys(b.parent for b in self.bindings if type(b.parent) is DirectoryCreation))
        return NativeAncestry.capture(self.definition.scope,self.definition.owned_root or '/',endpoints,self.modes,creations,self.sha256)

    def for_stage(self,stage):
        if stage=='secure_modes':return self.modes
        if stage not in STAGE_ROLES or stage.startswith('rollback_'):raise ValueError('Forward renderer stage required; recovery needs reviewed actual effect inspection')
        return tuple(b for b in self.bindings if b.role in STAGE_ROLES[stage])

    def assert_launcher(self):
        if observe(self.command_parent,'client_link')!=self.launcher_image:raise Refused('Reviewed SDK launcher changed')

    @property
    def sha256(self):return digest(dict(definition=self.definition.sha256,bindings=[b.evidence() for b in self.bindings],
        modes=[b.evidence() for b in self.modes],command_parent=asdict(self.command_parent),launcher_image=asdict(self.launcher_image)))


def capture_native(definition,reviewed_definition_sha256):
    """Read actual names/bytes; no installation, chmod, grant or service effect.

    All seven current modes must match the reviewed 0775 state. Forward parent
    bindings carry the planned 0755 postimage, which the native actor must later
    prove. This does not claim full protected ancestry or live authentication.
    """
    if sys.platform!='linux' or type(definition) is not RenderedCatalog or definition.sha256!=reviewed_definition_sha256:
        raise Refused('Exact reviewed definition and native Linux observations required')
    uid=os.geteuid()
    if definition.owned_root is not None:
        owned=anchor(definition.owned_root)
        if owned.uid!=uid or owned.mode!=0o700:raise Refused('Owned catalog root must be private and native')
    from server_extensions.coordinator_workspaces.operator_policy import PolicyDocument,_rules
    try:_rules(PolicyDocument(definition.policy_bytes,definition.policy_review.sha256),definition.policy_review.authority,definition.policy_review.devices)
    except Exception:raise Refused('Canonical operator policy did not validate') from None
    modes=tuple(ModeBinding(role,anchor(path)) for role,path in definition.mode_paths())
    if any(b.directory.uid!=uid for b in modes):raise Refused('Named directory owner differs from the operator')
    by_path={b.directory.path:b.directory for b in modes}
    def parent(path):
        actual=anchor(path)
        if actual.uid!=uid:raise Refused('Renderer parent is not owned by the native operator')
        if actual.path in by_path:
            if actual!=by_path[actual.path]:raise Refused('Named native directory changed during capture')
            return actual,replace(actual,mode=0o755)
        if actual.mode not in (0o700,0o755):raise Refused('Unreviewed writable parent refused')
        return actual,actual
    packet=definition.packet();command_actual,command_planned=parent(packet['command_directory'])
    launcher=observe(command_actual,'client_link')
    if launcher.kind!='file' or launcher.mode!=0o755 or launcher.sha256!=definition.launcher_sha256:
        raise Refused('SDK-published launcher identity differs from the reviewed bytes')
    bindings=[];originals=[]
    for spec in definition.specs():
        directory=Path(spec.path).parent
        try:directory.lstat()
        except FileNotFoundError:
            if spec.role not in CREATE_ROLES:raise Refused('Required SDK or stable-command parent is absent')
            _,outer=parent(directory.parent);planned=DirectoryCreation(outer,directory.name);before=FileImage('absent');value=None
        else:
            actual,planned=parent(directory)
            if spec.role=='policy' and actual.mode!=0o700:raise Refused('Policy parent must already be private or explicitly new')
            before=observe(actual,spec.role)
            if spec.role in tuple(FILE_ROLES)[:6] and before.kind!='absent' and (
                    before.kind!='file' or before.mode!=FILE_ROLES[spec.role][1]
                    or before.sha256!=hashlib.sha256(spec.value).hexdigest()):
                raise Refused('The new generation target contains different source; preserve it for review')
            if spec.role in ('gateway_link','serve_link') and before.kind!='absent':
                raise Refused('The proposed new drop-in name is already in use')
            if spec.role=='policy' and before.kind not in ('absent','file'):raise Refused('Existing protected policy must be a regular file')
            if spec.role=='client_link' and before.kind=='file' and before.mode!=0o755:raise Refused('Original CLI mode requires a separately supported exact recovery plan')
            if spec.role=='client_link' and before.kind=='link' and not _path(before.target):raise Refused('Original CLI requires a separately supported exact recovery plan')
            value=_original_bytes(actual,spec.role,before) if before.kind=='file' and spec.role in ('policy','client_link') else before.target if before.kind=='link' else None
            if spec.role=='policy' and before.kind=='file':
                if before.mode!=0o600:raise Refused('Existing protected policy mode is unsafe')
                try:_rules(PolicyDocument(value,before.sha256),definition.policy_review.authority,definition.policy_review.devices)
                except Exception:raise Refused('Original policy cannot be safely preserved under the current schema') from None
        bindings.append(FileBinding(spec.role,planned,before,spec.kind,spec.value));originals.append((spec.role,value))
    captured=CapturedCatalog(definition,tuple(bindings),modes,command_planned,launcher,tuple(originals));captured.assert_launcher();return captured
