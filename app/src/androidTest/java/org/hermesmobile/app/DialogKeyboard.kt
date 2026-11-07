package org.hermesmobile.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Root
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.Tap
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.Tapper
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
fun tapFocusedDialogWindowPoint(x:Float,y:Float) {
    onView(isRoot()).inRoot(focusedAppDialog()).perform(object:ViewAction {
        override fun getConstraints():Matcher<View> = isRoot()
        override fun getDescription()="one Android-input tap on a known app-dialog control"
        override fun perform(controller:UiController,view:View) {
            val screen=IntArray(2);val window=IntArray(2)
            view.getLocationOnScreen(screen);view.getLocationInWindow(window)
            val point=floatArrayOf(x+screen[0]-window[0],y+screen[1]-window[1])
            check(Tap.SINGLE.sendTap(controller,point,Press.FINGER.describePrecision())==Tapper.Status.SUCCESS)
            controller.loopMainThreadUntilIdle()
        }
    })
}
