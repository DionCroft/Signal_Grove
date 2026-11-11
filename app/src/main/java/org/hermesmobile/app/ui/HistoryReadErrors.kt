package org.hermesmobile.app.ui

import org.hermesmobile.app.data.AppPreferences
import org.hermesmobile.app.network.HermesHttpException
import org.hermesmobile.app.network.HistoryReadChanged
import org.hermesmobile.app.network.HistoryReadInvalid
import org.hermesmobile.app.network.connectionError

/** Only the documented missing-session refusal establishes this read outcome. */
fun historyReadError(error:Throwable,preferences:AppPreferences):String =
    when {
        error is HermesHttpException && error.code==404 && error.serverCode=="session_not_found"->Words.HistoryUnavailable.localized(preferences)
        error is HistoryReadChanged->Words.HistoryChanged.localized(preferences)
        error is HistoryReadInvalid->Words.HistoryInvalid.localized(preferences)
        else->connectionError(error)
    }
