package org.hermesmobile.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isRoot

/** Text editors in these flows live in a dialog, not the background activity window. */
fun closeDialogKeyboard() {onView(isRoot()).inRoot(isDialog()).perform(closeSoftKeyboard())}
