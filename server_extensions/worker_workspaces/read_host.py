"""Explicit lifetime owner for existing opt-in Windows read sessions.

No import-time listener, enrollment, queue, project writes, content cache or
transport replay. The interactive host alone publishes endpoint projections.
"""
from dataclasses import dataclass,field
import threading
import time
from .broker_enrollment import EnrolledReadSession
from .local_pipe import IdentityRevertFailure,LocalReadPipe
from .workspace_provider import WorkspaceError


@dataclass(frozen=True)
class ReadHostStatus:
    scope_id:str=field(repr=False)
    state:str
    endpoint:object=field(default=None,repr=False)
    failure_code:str|None=None


class ReadHost:
    """Start explicitly on the owning broker; never restart a held session.

    Sessions have already passed explicit operator enrollment. Each gets one
    dedicated pipe thread, which rechecks its trusted context before accepting
    another connection. Accepting a new frame is not replaying an earlier frame;
    the same adapter retains its bounded duplicate-ID set throughout its life.
    Endpoint publication belongs on the normal host thread, never a thread whose
    impersonation failed to revert. No thread callback writes files or receipts.
    """
    def __init__(self,sessions,*,pipe_factory=LocalReadPipe):
        if not isinstance(sessions,(tuple,list)) or not 1<=len(sessions)<=8 or any(not isinstance(s,EnrolledReadSession) for s in sessions):raise WorkspaceError('INVALID')
        if len({s.scope for s in sessions})!=len(sessions):raise WorkspaceError('INVALID')
        self._sessions=tuple(sessions);self._factory=pipe_factory;self._lock=threading.Lock();self._stopping=threading.Event()
        self._started=False;self._closed=False;self._pipes={};self._threads={};self._failures={}

    def start(self):
        with self._lock:
            if self._started or self._closed:raise WorkspaceError('CHANGED')
            self._started=True
        try:
            for session in self._sessions:
                if self._stopping.is_set():raise WorkspaceError('UNAVAILABLE')
                session.checked_context();pipe=self._factory(session.adapter)
                with self._lock:
                    self._pipes[session.scope]=pipe
                    if self._closed:raise WorkspaceError('UNAVAILABLE')
                    thread=threading.Thread(target=self._serve,args=(session,pipe),name='SignalGrove-read-'+session.scope[:8],daemon=True)
                    self._threads[session.scope]=thread
                    try:thread.start()
                    except BaseException:
                        del self._threads[session.scope];raise
        except BaseException:
            self.close();raise
        return self

    def _serve(self,session,pipe):
        failure=None
        try:
            while not self._stopping.is_set():
                session.adapter._context()
                try:pipe.serve_one(timeout=3)
                except WorkspaceError as error:
                    # A bounded accept timeout or a disconnected client does not
                    # reissue any request. IDs/leases still belong to this instance.
                    if error.code not in {'UNAVAILABLE','NOT_FOUND','INVALID','BUSY','TOO_LARGE'}:raise
                    self._stopping.wait(.05) # Bound immediate transport-fault loops; never reissue a frame.
        except IdentityRevertFailure:failure='IDENTITY_FAILURE'
        except WorkspaceError as error:failure=error.code
        except BaseException:failure='UNAVAILABLE'
        finally:
            # Only in-memory state/native pipe cleanup on this dedicated thread.
            # In particular, never invoke an endpoint-file writer here.
            try:pipe.close()
            except BaseException:
                if failure is None:failure='UNAVAILABLE'
            session.close()
            with self._lock:
                if failure is not None and not self._closed:self._failures[session.scope]=failure

    def statuses(self):
        with self._lock:
            rows=[]
            for session in self._sessions:
                scope=session.scope;pipe=self._pipes.get(scope);thread=self._threads.get(scope);failure=self._failures.get(scope)
                if self._closed:state='closed'
                elif failure is not None:state='held'
                elif not self._started or pipe is None or thread is None:state='starting'
                elif thread.is_alive() and pipe.accepting:state='ready'
                elif thread.is_alive():state='starting'
                else:state='held';failure='UNAVAILABLE'
                rows.append(ReadHostStatus(scope,state,pipe.endpoint if state=='ready' else None,failure))
            return tuple(rows)

    def close(self):
        with self._lock:self._closed=True;self._stopping.set();pipes=tuple(self._pipes.values())
        errors=[]
        for session in self._sessions:session.close()
        for pipe in pipes:
            try:pipe.close()
            except BaseException as error:errors.append(error)
        if errors:raise WorkspaceError('UNAVAILABLE')

    def wait_closed(self,timeout=5):
        from .local_pipe import _deadline
        import time
        deadline=_deadline(timeout)
        with self._lock:threads=tuple(self._threads.values())
        for thread in threads:
            if thread is threading.current_thread():raise WorkspaceError('INVALID')
            thread.join(max(0,deadline-time.monotonic()))
        return all(not thread.is_alive() for thread in threads)

def encode_projection(statuses,*,device,observed):
    """Return bounded private endpoint metadata, never project contents or paths.

    The broker publishes this from its normal thread into its protected state
    directory. Readiness is a fresh observation, not proof of a later call.
    """
    from dataclasses import asdict
    import json
    rows=[dict(scope_id=s.scope_id,state=s.state,failure_code=s.failure_code,
               endpoint=asdict(s.endpoint) if s.endpoint is not None else None) for s in statuses]
    raw=json.dumps(dict(schema=1,device=device,observed=observed,sessions=rows),allow_nan=False,separators=(',',':')).encode('utf8')
    _projection(raw,device=device,now=observed)
    return raw


def _projection(raw,*,device,now):
    import json,socket
    from dataclasses import fields
    from .broker_protocol import _unique
    from .local_pipe import PipeEndpoint,_endpoint,_SCOPE,_CODES
    if (type(raw) is not bytes or not 1<=len(raw)<=32768 or not isinstance(device,str)
        or device!=socket.gethostname().upper() or type(now) not in (int,float) or not 0<now<=10**12):raise WorkspaceError('INVALID')
    try:
        value=json.loads(raw.decode('utf8'),object_pairs_hook=_unique,parse_constant=lambda _:(_ for _ in ()).throw(ValueError()))
        if not isinstance(value,dict) or set(value)!={'schema','device','observed','sessions'} or type(value['schema']) is not int or value['schema']!=1 or value['device']!=device:raise ValueError()
        observed=value['observed']
        if type(observed) not in (int,float) or not 0<observed<=10**12 or not 0<=now-observed<=10:raise WorkspaceError('UNAVAILABLE')
        rows=value['sessions']
        if not isinstance(rows,list) or not 1<=len(rows)<=8:raise ValueError()
        clean={};endpoint_fields={f.name for f in fields(PipeEndpoint)}
        for row in rows:
            if not isinstance(row,dict) or set(row)!={'scope_id','state','failure_code','endpoint'}:raise ValueError()
            scope,state,failure,endpoint=(row[k] for k in ['scope_id','state','failure_code','endpoint'])
            if not isinstance(scope,str) or not _SCOPE.fullmatch(scope) or scope in clean or state not in {'starting','ready','held','closed'}:raise ValueError()
            if failure is not None and (not isinstance(failure,str) or failure not in _CODES|{'IDENTITY_FAILURE'}):raise ValueError()
            if state=='held' and failure is None:raise ValueError()
            if state in {'starting','ready'} and failure is not None:raise ValueError()
            if state=='ready':
                if not isinstance(endpoint,dict) or set(endpoint)!=endpoint_fields:raise ValueError()
                endpoint=_endpoint(PipeEndpoint(**endpoint))
                if endpoint.scope_id!=scope:raise ValueError()
            elif endpoint is not None:raise ValueError()
            clean[scope]=ReadHostStatus(scope,state,endpoint,failure)
        return clean
    except WorkspaceError:raise
    except (ValueError,UnicodeError,KeyError,TypeError,RecursionError):raise WorkspaceError('INVALID') from None


def select_projection(raw,*,scope_id,device,now):
    """Scope comes from the authenticated coordinator binding, not phone JSON."""
    from .local_pipe import _SCOPE
    if not isinstance(scope_id,str) or not _SCOPE.fullmatch(scope_id):raise WorkspaceError('INVALID')
    rows=_projection(raw,device=device,now=now)
    if scope_id not in rows:raise WorkspaceError('DENIED')
    return rows[scope_id]


def call_projected(raw_projection,raw_request,*,scope_id,device,now,timeout=3):
    """One caller-bound submission; all native identity/no-replay checks remain.

    Endpoint projection age alone does not authenticate a live worker. call_once
    verifies the actual local process token, start time and session before sending.
    """
    from .broker_protocol import decode_frame
    from .local_pipe import call_once,ReadTransportError
    try:
        frame=decode_frame(raw_request)
        if frame['payload'].get('scope_id')!=scope_id:raise WorkspaceError('DENIED')
        status=select_projection(raw_projection,scope_id=scope_id,device=device,now=now)
        if status.state!='ready' or status.endpoint is None:raise WorkspaceError('UNAVAILABLE')
        return call_once(status.endpoint,raw_request,timeout=timeout)
    except WorkspaceError as error:raise ReadTransportError(error.code,request_submitted=False) from None

def call_current_projected(read_projection,raw_request,*,scope_id,device,timeout=3):
    """Wait only for fresh starting state, then admit at most one frame.

    The trusted loader rechecks caller policy before returning protected bytes.
    Held/closed, stale data and changed instances never resume automatically.
    Readiness observation, native connection and I/O share one absolute deadline.
    """
    from .broker_protocol import decode_frame
    from .local_pipe import _deadline,call_once,ReadTransportError
    try:
        deadline=_deadline(timeout)
        if not callable(read_projection):raise WorkspaceError('INVALID')
        frame=decode_frame(raw_request)
        if frame['payload'].get('scope_id')!=scope_id:raise WorkspaceError('DENIED')
        waiting=threading.Event()
        while True:
            if time.monotonic()>=deadline:raise WorkspaceError('UNAVAILABLE')
            status=select_projection(read_projection(),scope_id=scope_id,device=device,now=time.time())
            if time.monotonic()>=deadline:raise WorkspaceError('UNAVAILABLE')
            if status.state=='ready':
                if frame['worker_instance']!=status.endpoint.worker_instance:raise WorkspaceError('CHANGED')
                break
            if status.state!='starting':raise WorkspaceError('UNAVAILABLE')
            waiting.wait(min(.05,max(0.0,deadline-time.monotonic())))
    except WorkspaceError as error:raise ReadTransportError(error.code,request_submitted=False) from None
    except Exception:raise ReadTransportError('UNAVAILABLE',request_submitted=False) from None
    # Once native admission begins, preserve its exact submission uncertainty.
    return call_once(status.endpoint,raw_request,timeout=timeout,deadline=deadline)
