package org.hermesmobile.app

import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.junit.runner.Description
import org.junit.runner.Result
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Explicit AndroidJUnitRunner listener; records stack metadata, never thread names or values. */
class DeviceTestDiagnosticsListener:RunListener() {
    private data class Case(val number:Int,val owner:String,val method:String,val thread:Long,val elapsed:Long,val uptime:Long,val nano:Long)
    private val executor=Executors.newSingleThreadScheduledExecutor {r->Thread(r,"signal-grove-test-diagnostics").apply {isDaemon=true}}
    private val sequence=AtomicInteger()
    private val snapshots=AtomicInteger()
    private var pending:List<ScheduledFuture<*>> = emptyList()
    @Volatile private var active:Case?=null
    private lateinit var directory:File
    private var lease:PowerManager.WakeLock?=null
    private var leaseRequested=false
    private var leaseError:String?=null
    private var delayMs=10_000L
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val power get()=context.getSystemService(PowerManager::class.java)
    private fun safely(block:()->Unit) {try {block()} catch(error:Exception) {Log.w("DeviceTestDiagnostics",error.javaClass.simpleName)}}
    private fun identifier(value:String?,fallback:String)=value?.substringBefore('[')?.takeIf {it.matches(Regex("[A-Za-z0-9_.$]{1,256}"))} ?: fallback
    private fun write(name:String,data:JsonObject) {
        val bytes=data.toString().toByteArray(Charsets.UTF_8);require(bytes.size<=262144)
        val temporary=File(directory,"$name.part");temporary.writeBytes(bytes);check(temporary.renameTo(File(directory,name)))
    }
    private fun state(c:Case?,phase:String)=buildJsonObject {
        put("schema",1);put("phase",phase);put("scope","instrumentation metadata only")
        put("elapsed_ms",SystemClock.elapsedRealtime());put("uptime_ms",SystemClock.uptimeMillis())
        put("interactive",power?.isInteractive==true);put("cpu_lease_requested",leaseRequested);put("cpu_lease_held",lease?.isHeld==true)
        leaseError?.let {put("cpu_lease_error_class",it)}
        if(c!=null) {
            put("case",c.number);put("class",c.owner);put("method",c.method)
            val elapsed=SystemClock.elapsedRealtime()-c.elapsed;val awake=SystemClock.uptimeMillis()-c.uptime
            put("case_elapsed_ms",elapsed);put("case_uptime_ms",awake);put("case_nano_ms",(System.nanoTime()-c.nano)/1_000_000)
            put("elapsed_minus_uptime_ms",(elapsed-awake).coerceAtLeast(0))
        }
    }
    override fun testRunStarted(description:Description)=safely {
        require(context.packageName.endsWith(".debug"))
        val root=File(context.cacheDir,"device-test-diagnostics").canonicalFile
        require(root.path.startsWith(context.cacheDir.canonicalPath+File.separator))
        directory=File(root,"run-${SystemClock.elapsedRealtimeNanos()}");check(directory.mkdirs())
        val args=InstrumentationRegistry.getArguments()
        delayMs=(args.getString("signal_grove.snapshot_delay_seconds")?.toLongOrNull() ?: 10L).coerceIn(1,60)*1000
        leaseRequested=args.getString("signal_grove.keep_cpu_awake")=="true"
        if(leaseRequested) try {
            lease=checkNotNull(power).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SignalGrove:TestDiagnostics").apply {setReferenceCounted(false);acquire(120_000L)}
        } catch(error:Exception) {leaseError=error.javaClass.simpleName}
        write("run-start.json",JsonObject(state(null,"run_start")+mapOf("snapshot_delay_ms" to JsonPrimitive(delayMs),"cpu_lease_limit_ms" to JsonPrimitive(120_000))))
    }
    override fun testStarted(description:Description)=safely {
        pending.forEach {it.cancel(false)}
        val c=Case(sequence.incrementAndGet(),identifier(description.className,"unknown_class"),identifier(description.methodName,"unknown_method"),Thread.currentThread().id,SystemClock.elapsedRealtime(),SystemClock.uptimeMillis(),System.nanoTime())
        active=c;write("case-${c.number}-start.json",state(c,"case_start"))
        pending=listOf(delayMs,(delayMs*3).coerceAtMost(120_000L)).map {delay->executor.schedule({safely {snapshot(c,"deadline")}},delay,TimeUnit.MILLISECONDS)}
    }
    private fun snapshot(c:Case,reason:String) {
        if(active!==c) return
        val ordinal=snapshots.incrementAndGet();if(ordinal>32) return
        val observed=Thread.getAllStackTraces().entries.sortedBy {if(it.key.id==c.thread) 0 else if(it.key.id==1L) 1 else 2}
        fun payload(threads:Int,frames:Int)=JsonObject(state(c,reason)+mapOf("threads_truncated" to JsonPrimitive(observed.size>threads),"threads" to buildJsonArray {
            for((thread,stack) in observed.take(threads)) add(buildJsonObject {
                put("id",thread.id);put("state",thread.state.name);put("frames_truncated",stack.size>frames)
                put("frames",buildJsonArray {for(frame in stack.take(frames)) add(buildJsonObject {
                    put("class",frame.className.take(128));put("method",frame.methodName.take(64))
                    frame.fileName?.let {put("file",it.substringAfterLast('/').substringAfterLast('\\').take(64))}
                    put("line",frame.lineNumber)
                })})
            })
        }))
        var data=payload(32,24);if(data.toString().toByteArray(Charsets.UTF_8).size>262144) data=payload(8,16)
        if(active===c) write("snapshot-${c.number}-$ordinal-$reason.json",data)
    }
    override fun testFailure(failure:Failure)=safely {active?.let {c->executor.execute {safely {snapshot(c,"failure")}}}}
    override fun testFinished(description:Description)=safely {
        active?.let {write("case-${it.number}-finish.json",state(it,"case_finish"))}
        active=null;pending.forEach {it.cancel(false)};pending=emptyList()
    }
    override fun testRunFinished(result:Result)=safely {
        active=null;pending.forEach {it.cancel(false)};executor.shutdown()
        val heldBeforeRelease=lease?.isHeld==true
        try {if(heldBeforeRelease) lease?.release()} catch(error:Exception) {leaseError=error.javaClass.simpleName}
        write("run-finish.json",JsonObject(state(null,"run_finish")+mapOf("tests" to JsonPrimitive(result.runCount),"failures" to JsonPrimitive(result.failureCount),"ignored" to JsonPrimitive(result.ignoreCount),"cpu_lease_held_before_release" to JsonPrimitive(heldBeforeRelease),"snapshots" to JsonPrimitive(snapshots.get().coerceAtMost(32)))))
    }
}
