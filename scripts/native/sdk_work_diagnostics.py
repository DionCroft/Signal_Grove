"""Content-free observations for the generated SDK gateway fixture only.

These observations do not decide quiescence, change registries or cancel work.
Only code locations, interpreter thread identifiers and work states are retained. No locals,
arguments, object representations, task names or exception text are serialized.
"""
import asyncio
import os
import sys
import time
import types

MAX_THREADS=64
MAX_FRAMES=24
MAX_TASKS=64
MAX_AWAITS=24
MAX_FUTURES=256


def code_location(code,line,source):
    filename=code.co_filename
    prefix=os.fspath(source)+os.sep
    if filename.startswith(prefix):
        module=filename[len(prefix):].replace(os.sep,'/')
    else:
        # Absolute filenames can contain account or project identity.
        module='<outside-reviewed-sdk>'
    return dict(module=module,function=code.co_name,line=line)


def frame_stack(frame,source):
    rows=[]
    while frame is not None and len(rows)<MAX_FRAMES:
        rows.append(code_location(frame.f_code,frame.f_lineno,source));frame=frame.f_back
    return dict(frames=rows,truncated=frame is not None)


def await_chain(coroutine,source):
    rows=[];seen=set();current=coroutine
    while current is not None and len(rows)<MAX_AWAITS:
        if id(current) in seen:return dict(frames=rows,truncated=True,leaf='cycle')
        seen.add(id(current))
        if type(current) is types.CoroutineType:
            frame=current.cr_frame;next_value=current.cr_await
        elif type(current) is types.GeneratorType:
            frame=current.gi_frame;next_value=current.gi_yieldfrom
        else:
            # Do not invoke attributes, repr or iterators on awaitable objects.
            return dict(frames=rows,truncated=False,leaf='opaque-awaitable')
        if frame is not None:rows.append(code_location(frame.f_code,frame.f_lineno,source))
        current=next_value
    return dict(frames=rows,truncated=current is not None,leaf='bound' if current is not None else 'end')


def registry_states(registry,*,futures):
    lock=registry._lock
    if not lock.acquire(timeout=.02):return dict(state='LOCK_UNAVAILABLE')
    try:
        pending=tuple(registry._pending)
        if not futures:return dict(state='OBSERVED',pending=len(pending))
        rows=[dict(done=f.done(),running=f.running(),cancelled=f.cancelled()) for f in pending[:MAX_FUTURES]]
        return dict(state='OBSERVED',pending=len(pending),futures=rows,omitted=max(0,len(pending)-len(rows)))
    finally:lock.release()


def observe_work(runner,source,observed):
    """Called on the real runner loop after its status snapshot has returned."""
    if (type(observed) is not dict or observed.get('state')!='OBSERVED'
        or observed.get('busy') is not True or observed.get('contract')!='gateway_live_work_v1'):
        raise ValueError('An actual known-busy SDK observation is required')
    loop=asyncio.get_running_loop()
    frames=sys._current_frames()
    try:
        thread_count=len(frames)
        threads=[dict(ident=ident,**frame_stack(frame,source)) for ident,frame in sorted(frames.items())[:MAX_THREADS]]
    finally:frames.clear()
    tasks=tuple(asyncio.all_tasks(loop))
    task_rows=[dict(done=t.done(),cancelled=t.cancelled(),awaits=await_chain(t.get_coro(),source)) for t in tasks[:MAX_TASKS]]
    return dict(contract='generated_gateway_work_diagnostics_v1',pid=os.getpid(),monotonic=time.monotonic(),
        status_counters=dict(observed['counters']),executor=registry_states(runner._executor_work,futures=True),
        periodic=registry_states(runner._periodic_work,futures=False),threads=threads,tasks=task_rows,
        omitted_threads=max(0,thread_count-len(threads)),omitted_tasks=max(0,len(tasks)-len(task_rows)),
        profiler_enabled=sys.getprofile() is not None,acceptance_permission=False)
