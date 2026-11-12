"""One bounded private SSH helper call. Operator-created argv only; never retries."""
import asyncio
from .coordinator import MAX_REPLY, ReadRefused, decode, encode


class SingleCallSsh:
    def __init__(self, argv, *, timeout=30.0):
        if (type(argv) is not tuple or not 2 <= len(argv) <= 24
                or any(type(a) is not str or not a or len(a)>4096 or '\0' in a or '\n' in a or '\r' in a for a in argv)
                or type(timeout) not in (int,float) or not 0.05 <= timeout <= 30):
            raise ReadRefused('INVALID')
        self._argv = argv
        self._timeout = timeout

    def __repr__(self): return 'SingleCallSsh(redacted)'

    async def __call__(self, payload):
        if (type(payload) is not dict or payload.get('action') not in ('workspace_status','workspace_read')
                or set(payload) != ({'action','scope_id'} if payload['action']=='workspace_status'
                                    else {'action','scope_id','frame'})):
            raise ReadRefused('INVALID')
        raw = encode(payload); reading = payload.get('action') == 'workspace_read'
        submitted = False; process = None; tasks = []
        async def bounded(stream, maximum):
            chunks=[]; total=0
            while True:
                data=await stream.read(min(16384,maximum-total+1))
                if not data: return b''.join(chunks)
                total += len(data)
                if total > maximum: raise ReadRefused('TOO_LARGE',request_submitted=submitted)
                chunks.append(data)
        async def exchange():
            nonlocal submitted, process, tasks
            process=await asyncio.create_subprocess_exec(*self._argv,stdin=asyncio.subprocess.PIPE,
                    stdout=asyncio.subprocess.PIPE,stderr=asyncio.subprocess.PIPE,limit=16384)
            # A failed write/drain can have reached the remote helper already.
            submitted=reading
            process.stdin.write(raw); await process.stdin.drain(); process.stdin.close()
            tasks=[asyncio.create_task(bounded(process.stdout,MAX_REPLY)),
                   asyncio.create_task(bounded(process.stderr,8192)),asyncio.create_task(process.wait())]
            output, _, code=await asyncio.gather(*tasks)
            if code != 0: raise ReadRefused('UNAVAILABLE',request_submitted=submitted)
            try:
                envelope=decode(output)
                if (set(envelope)!={'ok','result'} or envelope['ok'] is not True
                        or type(envelope['result']) is not dict):
                    raise ReadRefused('UNAVAILABLE',request_submitted=submitted)
                return envelope['result']
            except ReadRefused as exc: raise ReadRefused(exc.code,request_submitted=submitted) from None
        try: return await asyncio.wait_for(exchange(),timeout=self._timeout)
        except asyncio.CancelledError: raise
        except ReadRefused: raise
        except Exception: raise ReadRefused('UNAVAILABLE',request_submitted=submitted) from None
        finally:
            for task in tasks:
                if not task.done(): task.cancel()
            if process is not None and process.returncode is None:
                try: process.kill()
                except ProcessLookupError: pass
                try: await asyncio.wait_for(process.wait(),timeout=3)
                except (asyncio.TimeoutError,ProcessLookupError): pass
            if tasks: await asyncio.gather(*tasks,return_exceptions=True)
