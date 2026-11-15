package org.hermesmobile.app

import android.graphics.Rect
import android.os.Looper
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.hermesmobile.app.ui.AppRouteEvent
import org.hermesmobile.app.ui.UiRouteObservation
import org.json.JSONArray
import org.json.JSONObject

/** Bounded content-free callback/route observations; injects no action or input. */
internal class AppRouteTrace(private val activity:ComponentActivity) {
    private val started=SystemClock.uptimeMillis()
    private val entries=JSONArray()
    private var omitted=0
    private var observationFailures=0
    @Volatile private var stage="fixture-ready"
    private var closed=false
    private var restored=false
    private fun frame():JSONObject {
        val view=activity.window.decorView;val visible=Rect();view.getWindowVisibleDisplayFrame(visible)
        val insets=ViewCompat.getRootWindowInsets(view)
        return JSONObject().put("attached",view.isAttachedToWindow).put("focused",view.hasWindowFocus())
            .put("width",view.width).put("height",view.height)
            .put("visible",JSONArray(listOf(visible.left,visible.top,visible.right,visible.bottom)))
            .put("deliveredImeBottomPx",insets?.getInsets(WindowInsetsCompat.Type.ime())?.bottom ?: -1)
            .put("deliveredImeVisible",insets?.isVisible(WindowInsetsCompat.Type.ime()) ?: JSONObject.NULL)
    }
    private val sink:(AppRouteEvent,Int,String)->Unit={event,owner,route->
        try {
            if(entries.length()<128) entries.put(JSONObject().put("event",event.name).put("stage",stage)
                .put("owner",owner).put("route",route).put("observedOffsetMs",SystemClock.uptimeMillis()-started)
                .put("frame",frame()))
            else omitted=(omitted+1).coerceAtMost(10000)
        } catch(_:Exception) {observationFailures=(observationFailures+1).coerceAtMost(10000)}
    }
    init {
        check(BuildConfig.DEBUG && Looper.myLooper()==Looper.getMainLooper())
        check(UiRouteObservation.observer==null) {"Another fixture owns route observation"}
        UiRouteObservation.observer=sink
    }
    fun mark(value:String) {
        require(value in setOf("home-new-chat","after-home-new-chat","demo-exercise","route-contract"))
        stage=value
    }
    fun close() {
        check(Looper.myLooper()==Looper.getMainLooper())
        if(!closed) {
            if(UiRouteObservation.observer===sink) {UiRouteObservation.observer=null;restored=true}
            closed=true
        }
    }
    fun snapshot(caseName:String):JSONObject {
        require(caseName.matches(Regex("[A-Za-z0-9_]{1,160}")));check(closed)
        return JSONObject().put("schema",1).put("atomic",false).put("case",caseName)
            .put("originalObserverRestored",restored).put("observationFailures",observationFailures)
            .put("omitted",omitted).put("entries",entries)
    }
}
