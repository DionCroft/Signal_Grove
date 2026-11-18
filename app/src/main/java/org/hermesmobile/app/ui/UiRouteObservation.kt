package org.hermesmobile.app.ui

import org.hermesmobile.app.BuildConfig

/** Optional debug-fixture observation; never navigation, input, persistence or telemetry. */
internal enum class AppRouteEvent { HomeNewChat,HomeNewChatCleared,NavigationRequested,NavigationReturned,RouteChanged,CompositionEntered,CompositionDisposed }

internal object UiRouteObservation {
    @Volatile var observer:((AppRouteEvent,Int,String)->Unit)?=null
    fun record(event:AppRouteEvent,owner:Int,route:String?) {
        if(BuildConfig.DEBUG) {
            val sink=observer ?: return
            val safe=route?.takeIf {it in setOf("Home","Chat","Tasks","Automation","Voice","Settings","ServerDetails")} ?: "Other"
            // An observation failure cannot consume input or change actual navigation.
            runCatching {sink(event,owner,safe)}
        }
    }
}
