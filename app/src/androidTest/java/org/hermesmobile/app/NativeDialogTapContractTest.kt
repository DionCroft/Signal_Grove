package org.hermesmobile.app

import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.test.espresso.UiController
import androidx.test.espresso.action.Tapper
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

/** Scripted controller boundary checks only; these never claim real Android input acceptance. */
class NativeDialogTapContractTest {
    private class Scripted(private val mode:String):UiController {
        val actions=mutableListOf<Int>()
        override fun injectMotionEvent(event:MotionEvent):Boolean {
            actions+=event.actionMasked
            if(mode=="throw") throw IllegalStateException("private-value-sentinel")
            if(mode=="long" && event.actionMasked==MotionEvent.ACTION_DOWN) Thread.sleep(ViewConfiguration.getLongPressTimeout().toLong()+30)
            return !(mode=="down-false" && event.actionMasked==MotionEvent.ACTION_DOWN || mode=="up-false" && event.actionMasked==MotionEvent.ACTION_UP)
        }
        override fun injectMotionEventSequence(events:Iterable<MotionEvent>):Boolean=error("No sequence expected")
        override fun injectKeyEvent(event:KeyEvent):Boolean=error("No key expected")
        override fun injectString(value:String):Boolean=error("No text expected")
        override fun loopMainThreadUntilIdle()=Unit
        override fun loopMainThreadForAtLeast(delay:Long) {Thread.sleep(delay.coerceAtMost(1000))}
    }
    private fun attempt()=SingleNativeTapAttempt(floatArrayOf(50f,60f),floatArrayOf(1f,1f))
    private fun events(tap:SingleNativeTapAttempt)=tap.report().getValue("events").jsonArray.map {it.jsonObject}
    @Test fun aSuccessfulSingleTapHasExactlyOneDownAndOneUp() {
        val controller=Scripted("ok");val tap=attempt();tap.send(controller);tap.requireSuccess()
        assertEquals(Tapper.Status.SUCCESS,tap.status);assertEquals(listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP),controller.actions)
        assertEquals(listOf("DOWN","UP"),events(tap).map {it.getValue("action").jsonPrimitive.content})
        assertTrue(events(tap).all {it["accepted"]==JsonPrimitive(true)});assertFalse(tap.retryBlocked)
    }
    @Test fun aFailedDownBlocksEspressosInternalRetryBeforeASecondDispatch() {
        val controller=Scripted("down-false");val tap=attempt()
        assertTrue(runCatching {tap.send(controller)}.exceptionOrNull() is IllegalStateException)
        assertEquals(listOf(MotionEvent.ACTION_DOWN),controller.actions);assertTrue(tap.retryBlocked);assertNull(tap.status)
        assertEquals(1,events(tap).size);assertEquals(JsonPrimitive(false),events(tap).single()["accepted"])
        assertTrue(runCatching {tap.requireSuccess()}.isFailure)
    }
    @Test fun aFailedUpRetainsFailureAndOnlyAllowsTheLibrarysCancelCleanup() {
        val controller=Scripted("up-false");val tap=attempt();tap.send(controller)
        assertEquals(Tapper.Status.FAILURE,tap.status);assertTrue(runCatching {tap.requireSuccess()}.isFailure)
        assertEquals(listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL),controller.actions)
        assertEquals(listOf(true,false,true),events(tap).map {it.getValue("accepted").jsonPrimitive.boolean});assertFalse(tap.retryBlocked)
    }
    @Test fun aLongPressWarningIsRecordedAndNeverAcceptedOrRetried() {
        val controller=Scripted("long");val tap=attempt();tap.send(controller)
        assertEquals(Tapper.Status.WARNING,tap.status);assertTrue(runCatching {tap.requireSuccess()}.isFailure)
        assertEquals(listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP),controller.actions);assertFalse(tap.retryBlocked)
        assertTrue(events(tap).first().getValue("dispatchMillis").jsonPrimitive.long>=ViewConfiguration.getLongPressTimeout())
    }
    @Test fun failedDispatchRecordsOnlyAClosedErrorCategoryAndCannotBeReused() {
        val controller=Scripted("throw");val tap=attempt();val failure=runCatching {tap.send(controller)}.exceptionOrNull()
        assertTrue(failure is IllegalStateException);assertEquals(1,controller.actions.size);assertFalse(tap.report().toString().contains("private-value-sentinel"))
        assertEquals(JsonPrimitive("IllegalStateException"),tap.report()["errorType"]);assertEquals(JsonNull,events(tap).single()["accepted"])
        assertTrue(runCatching {tap.send(controller)}.isFailure);assertEquals(1,controller.actions.size)
    }
    @Test fun successfulDispatchCannotBeReusedToSendASecondTap() {
        val controller=Scripted("ok");val tap=attempt();tap.send(controller)
        assertTrue(runCatching {tap.send(controller)}.isFailure);assertEquals(listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP),controller.actions)
    }
}
