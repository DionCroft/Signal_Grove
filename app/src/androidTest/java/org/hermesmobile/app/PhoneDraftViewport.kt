package org.hermesmobile.app

import android.os.SystemClock
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import java.io.File
import java.util.UUID
import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.ui.Words
import org.hermesmobile.app.ui.localized
import org.json.JSONArray
import org.json.JSONObject

internal data class PhoneDraftGeometry(
    val viewport:FocusedDialogViewport,
    val controls:List<Rect>,
    val captions:List<android.graphics.Rect>
)
internal data class PhoneDraftCancelObservation(
    val firstCandidate:PhoneDraftGeometry,
    val beforeTap:PhoneDraftGeometry,
    val preTapResamples:Int
)

/** Re-observe the complete footer; one native Cancel tap only, inside one deadline. */
internal fun ComposeContentTestRule.cancelPhoneDraftOnce(
    preferences:AppPreferences,
    keyboardExpected:Boolean?=null,
    beforePreTapObservation:(()->Unit)?=null
):PhoneDraftCancelObservation {
    val id=UUID.randomUUID().toString()
    val tags=listOf("phone-draft-confirm" to Words.PhoneDraftConfirm,"phone-draft-cancel" to Words.Cancel)
    val started=SystemClock.uptimeMillis();val deadline=started+5000
    var previous:PhoneDraftGeometry?=null;var stableAt=started
    var ready:PhoneDraftGeometry?=null;var firstCandidate:PhoneDraftGeometry?=null
    var hookUsed=false;var tapIntent=false;var dispatched=false;var resamples=0;var primary:Throwable?=null
    val samples=JSONArray()
    fun encode(value:PhoneDraftGeometry?):JSONObject {
        val out=JSONObject().put("ready",value!=null)
        value?.let {g->
            out.put("keyboardVisible",g.viewport.keyboardVisible)
                .put("visible",JSONArray(listOf(g.viewport.visible.left,g.viewport.visible.top,g.viewport.visible.right,g.viewport.visible.bottom)))
                .put("offset",JSONArray(listOf(g.viewport.screenOffsetX,g.viewport.screenOffsetY)))
                .put("controls",JSONArray(g.controls.map {b->JSONArray(listOf(b.left,b.top,b.right,b.bottom))}))
                .put("captions",JSONArray(g.captions.map {b->JSONArray(listOf(b.left,b.top,b.right,b.bottom))}))
        }
        return out
    }
    fun geometry():PhoneDraftGeometry? {
        val viewport=focusedDialogViewport(requireKeyboard=false) ?: return null
        val controls=tags.map {(tag,_)->onNodeWithTag(tag).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().boundsInWindow}
        val captions=tags.map {(_,word)->val text=word.localized(preferences);focusedDialogCaptionScreenBounds(text,text)}
        // Accessibility/insets/Compose are sequential reads. Reject a torn sample.
        val fresh=focusedDialogViewport(requireKeyboard=false) ?: return null
        val freshControls=tags.map {(tag,_)->onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow}
        val value=if(viewport==fresh && controls==freshControls) PhoneDraftGeometry(fresh,controls,captions) else null
        if(samples.length()<16) samples.put(encode(value))
        return value
    }
    fun contained(g:PhoneDraftGeometry):Boolean {
        val v=g.viewport
        return (keyboardExpected==null || v.keyboardVisible==keyboardExpected) &&
            g.controls.all {b->b.left+v.screenOffsetX>=v.visible.left && b.right+v.screenOffsetX<=v.visible.right &&
                b.top+v.screenOffsetY>=v.visible.top && b.bottom+v.screenOffsetY<=v.visible.bottom} &&
            g.captions.all {v.visible.contains(it)}
    }
    try {
        waitUntil(5000) {
            if(SystemClock.uptimeMillis()>=deadline) return@waitUntil false
            val value=geometry();val now=SystemClock.uptimeMillis()
            if(value==null || !contained(value) || value!=previous) {previous=value;stableAt=now;false}
            else if(now-stableAt<250) false
            else {
                if(firstCandidate==null) firstCandidate=value
                if(!hookUsed) {hookUsed=true;beforePreTapObservation?.invoke()}
                val fresh=geometry()
                if(fresh!=value || fresh==null || !contained(fresh)) {
                    resamples++;previous=fresh;stableAt=SystemClock.uptimeMillis();false
                } else {ready=fresh;true}
            }
        }
        val checked=checkNotNull(ready)
        check(SystemClock.uptimeMillis()<deadline) {"Phone-draft observation deadline expired before input"}
        // Full control and native caption visibility remain mandatory.
        check(contained(checked))
        val cancel=checked.controls.last().center
        tapIntent=true
        tapFocusedDialogWindowPoint(cancel.x,cancel.y,NativeDialogTapAction.PhoneDraftCancel,id)
        dispatched=true
        return PhoneDraftCancelObservation(checkNotNull(firstCandidate),checked,resamples)
    } catch(error:Throwable) {primary=error;throw error}
    finally {
        val result=runCatching {
            val text=JSONObject().put("schema",1).put("atomic",false).put("nativeTapIntent",tapIntent).put("nativeTapReturned",dispatched)
                .put("preTapResamples",resamples).put("elapsedMs",SystemClock.uptimeMillis()-started)
                .put("firstCandidate",encode(firstCandidate)).put("beforeTap",encode(ready)).put("samples",samples).toString()
            check(text.toByteArray().size<=65536)
            val app=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
            val file=File(app.getExternalFilesDir(null),"qa/phone-draft-viewport-$id.json")
            file.parentFile!!.mkdirs();file.writeText(text)
        }
        if(primary==null) result.getOrThrow()
    }
}
