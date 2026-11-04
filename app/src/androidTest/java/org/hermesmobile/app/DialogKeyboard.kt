package org.hermesmobile.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Root
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
