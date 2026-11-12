package org.hermesmobile.app

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.UiController
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.Tapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import java.io.File
import java.util.UUID

/** Fixed test actions only; no captions, paths, credentials or view text enter diagnostics. */
enum class NativeDialogTapAction(val wire:String) {
    WorkspaceReview("workspace-review"),WorkspaceCancel("workspace-cancel"),HistoryMore("history-more"),HistoryClose("history-close"),
    KanbanRead("kanban-read"),KanbanCancel("kanban-cancel"),PhoneDraftCancel("phone-draft-cancel")
}

/** Espresso's MotionEvents.sendDown can retry internally. Refuse before a second DOWN reaches Android. */
internal class SingleNativeTapAttempt(private val point:FloatArray,private val precision:FloatArray) {
    init {require(point.size==2 && precision.size==2 && (point+precision).all {it.isFinite()})}
    private val events=mutableListOf<JsonObject>()
    private var down=false;private var up=false;private var cancelled=false
    var retryBlocked=false;private set
    var status:Tapper.Status?=null;private set
    private var errorType:String?=null
    private val started=SystemClock.uptimeMillis()
    private var elapsed=0L
    private fun failure(error:Throwable)=when(error) {
        is androidx.test.espresso.InjectEventSecurityException->"InjectEventSecurityException"
        is androidx.test.espresso.PerformException->"PerformException"
        is IllegalStateException->"IllegalStateException"
        else->"Other"
    }
    fun send(controller:UiController) {
        check(!down && status==null && errorType==null) {"A native tap attempt cannot be reused"}
        val bounded=object:UiController by controller {
            override fun injectMotionEvent(event:MotionEvent):Boolean {
                val action=event.actionMasked
                val allowed=when(action) {
                    MotionEvent.ACTION_DOWN->!down
                    MotionEvent.ACTION_UP->down && !up && !cancelled
                    MotionEvent.ACTION_CANCEL->down && !cancelled
                    else->false
                }
                if(!allowed || events.size>=3 || event.pointerCount!=1) {
                    retryBlocked=true;error("Additional or unexpected native input refused")
                }
                when(action) {MotionEvent.ACTION_DOWN->down=true;MotionEvent.ACTION_UP->up=true;MotionEvent.ACTION_CANCEL->cancelled=true}
                val begin=SystemClock.uptimeMillis();var accepted:Boolean?=null;var thrown:String?=null
                val eventTime=event.eventTime;val downTime=event.downTime;val x=event.x;val y=event.y
                try {return controller.injectMotionEvent(event).also {accepted=it}}
                catch(error:Throwable) {thrown=failure(error);throw error}
                finally {events+=buildJsonObject {
                    put("action",when(action) {MotionEvent.ACTION_DOWN->"DOWN";MotionEvent.ACTION_UP->"UP";else->"CANCEL"})
                    put("x",x);put("y",y);put("eventOffsetMillis",eventTime-started);put("downOffsetMillis",downTime-started)
                    put("dispatchOffsetMillis",begin-started);put("dispatchMillis",SystemClock.uptimeMillis()-begin)
                    put("accepted",accepted?.let(::JsonPrimitive) ?: JsonNull);put("errorType",thrown?.let(::JsonPrimitive) ?: JsonNull)
                }}
            }
            override fun injectMotionEventSequence(events:Iterable<MotionEvent>):Boolean=error("Native input sequence refused")
            override fun injectKeyEvent(event:KeyEvent):Boolean=error("Unrelated native key input refused")
            override fun injectString(value:String):Boolean=error("Unrelated native text input refused")
        }
        try {status=Tap.SINGLE.sendTap(bounded,point,precision)}
        catch(error:Throwable) {errorType=failure(error);throw error}
        finally {elapsed=SystemClock.uptimeMillis()-started}
    }
    fun requireSuccess() {check(status==Tapper.Status.SUCCESS) {"Native tap status: ${status?.name ?: "NO_RESULT"}; no retry performed"}}
    fun report()=buildJsonObject {
        put("status",status?.name?.let(::JsonPrimitive) ?: JsonNull);put("errorType",errorType?.let(::JsonPrimitive) ?: JsonNull)
        put("retryBlocked",retryBlocked);put("elapsedMillis",elapsed);put("tapTimeoutMillis",ViewConfiguration.getTapTimeout())
        put("longPressTimeoutMillis",ViewConfiguration.getLongPressTimeout());put("events",JsonArray(events.toList()))
    }
}

private fun nativeTapWindow(view:View)=buildJsonObject {
    val screen=IntArray(2);val window=IntArray(2);view.getLocationOnScreen(screen);view.getLocationInWindow(window)
    val frame=Rect();view.getWindowVisibleDisplayFrame(frame)
    put("focused",view.hasWindowFocus());put("attached",view.isAttachedToWindow);put("shown",view.isShown)
    put("width",view.width);put("height",view.height)
    put("screenX",screen[0]);put("screenY",screen[1]);put("windowX",window[0]);put("windowY",window[1])
    put("frameLeft",frame.left);put("frameTop",frame.top);put("frameRight",frame.right);put("frameBottom",frame.bottom)
    put("imeVisible",ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime())?.let(::JsonPrimitive) ?: JsonNull)
}

internal fun dispatchOneDialogTap(controller:UiController,view:View,x:Float,y:Float,action:NativeDialogTapAction,id:String) {
    require(x.isFinite() && y.isFinite() && UUID.fromString(id).toString()==id)
    val screen=IntArray(2);val window=IntArray(2);view.getLocationOnScreen(screen);view.getLocationInWindow(window)
    val point=floatArrayOf(x+screen[0]-window[0],y+screen[1]-window[1])
    val attempt=SingleNativeTapAttempt(point,Press.FINGER.describePrecision());val before=nativeTapWindow(view);var primary:Throwable?=null
    try {attempt.send(controller);attempt.requireSuccess();controller.loopMainThreadUntilIdle()}
    catch(error:Throwable) {primary=error;throw error}
    finally {
        try {writeNativeTapDiagnostic("native-dialog-tap-$id",buildJsonObject {
            put("schema",1);put("backend","native-controller");put("id",id);put("action",action.wire);put("api",Build.VERSION.SDK_INT)
            put("windowX",x);put("windowY",y);put("screenX",point[0]);put("screenY",point[1])
            put("before",before);put("after",nativeTapWindow(view));put("dispatch",attempt.report())
        })} catch(diagnostic:Throwable) {if(primary!=null) primary.addSuppressed(diagnostic) else throw diagnostic}
    }
}

internal fun writeNativeTapDiagnostic(name:String,report:JsonObject) {
    require(Regex("(?:native-dialog-tap|workspace-cancel-observation|workspace-review-observation)-[0-9a-f-]{36}").matches(name))
    val context=InstrumentationRegistry.getInstrumentation().targetContext
    val directory=File(checkNotNull(context.getExternalFilesDir(null)),"qa");check(directory.isDirectory || directory.mkdirs())
    val output=File(directory,"$name.json");val pending=File(directory,"$name.pending");check(!output.exists() && !pending.exists())
    val text=report.toString();check(text.length<=8192)
    pending.writeText(text);check(pending.renameTo(output))
    android.util.Log.i("SignalGroveNativeTap",text)
}
