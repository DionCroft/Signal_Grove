package org.hermesmobile.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Root
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.view.View
import org.hamcrest.Matcher
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import org.hamcrest.Description
import org.hamcrest.TypeSafeMatcher

/** Nested reviews can leave an editor dialog behind the foreground dialog. Match actual focus. */
fun closeDialogKeyboard() {
    val focusedDialog=object:TypeSafeMatcher<Root>() {
        override fun describeTo(description:Description) {description.appendText("a dialog with window focus")}
        override fun matchesSafely(root:Root)=isDialog().matches(root) && root.decorView.hasWindowFocus()
    }
    onView(isRoot()).inRoot(focusedDialog).perform(closeSoftKeyboard())
}

/** Default Espresso root selection can choose a stale activity behind the foreground window. */
fun closeFocusedKeyboard() {
    val focusedWindow=object:TypeSafeMatcher<Root>() {
        override fun describeTo(description:Description) {description.appendText("an app window with focus")}
        override fun matchesSafely(root:Root)=root.decorView.hasWindowFocus()
    }
    onView(isRoot()).inRoot(focusedWindow).perform(closeSoftKeyboard())
}

private fun focusedAppDialog()=object:TypeSafeMatcher<Root>() {
    override fun describeTo(description:Description) {description.appendText("the focused app dialog")}
    override fun matchesSafely(root:Root)=isDialog().matches(root) && root.decorView.hasWindowFocus()
}

fun focusedDialogKeyboardVisible():Boolean {
    var visible=false
    onView(isRoot()).inRoot(focusedAppDialog()).check {view,error->
        if(error!=null) throw error
        visible=ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime())==true
    }
    return visible
}

/** Android input dispatch must respect the IME window, unlike a direct Compose-view click. */
fun tapFocusedDialogWindowPoint(x:Float,y:Float,action:NativeDialogTapAction,id:String=java.util.UUID.randomUUID().toString()) {
    onView(isRoot()).inRoot(focusedAppDialog()).perform(object:ViewAction {
        override fun getConstraints():Matcher<View> = isRoot()
        override fun getDescription()="one Android-input tap on a known app-dialog control"
        override fun perform(controller:UiController,view:View) {
            dispatchOneDialogTap(controller,view,x,y,action,id)
        }
    })
}


/** The real Android IME boundary, in screen coordinates, for a focused dialog. */
data class FocusedDialogViewport(val visible:android.graphics.Rect,val screenOffsetX:Int,val screenOffsetY:Int,val imeBottom:Int,val displayHeight:Int,val keyboardVisible:Boolean=true)

/** Sequential native-window/inset observations, not an atomic keyboard state. */
data class DialogImeDisagreement(val nativeImePresent:Boolean,val nativeImeTopPx:Int,
    val deliveredImeVisible:Boolean,val deliveredImeBottomPx:Int,
    val readSeparationMs:Long,val readSeparationCapped:Boolean)

fun focusedDialogViewport(requireKeyboard:Boolean=true,onIncoherentIme:((DialogImeDisagreement)->Unit)?=null):FocusedDialogViewport? {
    var nativeSampleAt=0L
    val nativeIme=withInteractiveWindows {windows->
        val bounds=windows.filter {it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD}.map {item->android.graphics.Rect().also {item.getBoundsInScreen(it)}}.filter {!it.isEmpty}
        bounds.minOfOrNull {it.top}.also {if(onIncoherentIme!=null) nativeSampleAt=android.os.SystemClock.uptimeMillis()}
    }
    if(requireKeyboard && nativeIme==null) return null // Insets can precede the native IME window.
    var result:FocusedDialogViewport?=null
    onView(isRoot()).inRoot(focusedAppDialog()).check {view,error->
        if(error!=null) throw error
        val frame=android.graphics.Rect();view.getWindowVisibleDisplayFrame(frame)
        val screen=IntArray(2);val window=IntArray(2);view.getLocationOnScreen(screen);view.getLocationInWindow(window)
        val insets=checkNotNull(ViewCompat.getRootWindowInsets(view))
        val keyboardVisible=insets.isVisible(WindowInsetsCompat.Type.ime())
        if(requireKeyboard) check(keyboardVisible) {"The real dialog keyboard must remain visible"}
        // A general action can be checked with or without an IME, but not during
        // disagreement between native windows and delivered insets.
        if(keyboardVisible!=(nativeIme!=null)) {
            // Failure-path metadata only: no new window query, waiting, input
            // or acceptance. Observer errors cannot turn null into readiness.
            onIncoherentIme?.let {observer ->
                val elapsed=(android.os.SystemClock.uptimeMillis()-nativeSampleAt).coerceAtLeast(0)
                val sample=DialogImeDisagreement(nativeIme!=null,nativeIme?.coerceIn(-100000,100000) ?: -1,
                    keyboardVisible,insets.getInsets(WindowInsetsCompat.Type.ime()).bottom.coerceIn(0,100000),
                    elapsed.coerceAtMost(10000),elapsed>10000)
                runCatching {observer(sample)}
            }
            return@check
        }
        val ime=if(keyboardVisible) insets.getInsets(WindowInsetsCompat.Type.ime()).bottom else 0
        val metrics=android.util.DisplayMetrics()
        @Suppress("DEPRECATION")
        view.display.getRealMetrics(metrics)
        // Platform fitting can consume the local inset; the actual native IME is mandatory.
        check(ime>=0 && metrics.heightPixels>ime)
        if(keyboardVisible) {
            val top=checkNotNull(nativeIme);check(top in 1 until metrics.heightPixels)
            frame.bottom=minOf(frame.bottom,metrics.heightPixels-ime,top)
        }
        result=FocusedDialogViewport(frame,screen[0]-window[0],screen[1]-window[1],ime,metrics.heightPixels,keyboardVisible)
    }
    return if(requireKeyboard) checkNotNull(result) else result
}


private fun <T> withInteractiveWindows(read:(List<android.view.accessibility.AccessibilityWindowInfo>)->T):T {
    val automation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
    val info=automation.serviceInfo;val oldFlags=info.flags
    try {
        info.flags=oldFlags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo=info
        automation.waitForIdle(100,3000)
        return read(automation.windows)
    } finally {info.flags=oldFlags;automation.serviceInfo=info}
}

/** Native accessibility coordinates independently check the Compose-to-screen conversion. */
fun focusedDialogCaptionScreenBounds(text:String,description:String):android.graphics.Rect=withInteractiveWindows {windows->
    val target=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.packageName
    val matches=mutableListOf<android.graphics.Rect>();var visited=0
    fun visit(node:android.view.accessibility.AccessibilityNodeInfo,depth:Int) {
        check(++visited<=1000 && depth<=40) {"Unexpected accessibility tree size"}
        if(node.text?.toString()==text || node.contentDescription?.toString()==description) {
            val bounds=android.graphics.Rect();node.getBoundsInScreen(bounds)
            if(!bounds.isEmpty) matches+=bounds
        }
        for(i in 0 until node.childCount) node.getChild(i)?.let {visit(it,depth+1)}
    }
    windows.filter {it.type==android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION}.forEach {window->
        window.root?.takeIf {it.packageName?.toString()==target}?.let {visit(it,0)}
    }
    check(matches.size in 1..4) {"A bounded native footer caption is required: "+matches}
    android.graphics.Rect(matches.first()).also {union->matches.drop(1).forEach {union.union(it)}}
}
