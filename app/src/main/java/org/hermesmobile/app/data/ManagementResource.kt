package org.hermesmobile.app.data

import java.security.MessageDigest

/** Exact canonical dashboard URL (including prefix) and profile; independent of inference credentials. */
fun managementResourceScope(url:String,profile:String):String=MessageDigest.getInstance("SHA-256")
    .digest(("management-v2\u0000"+url+"\u0000"+profile).toByteArray(Charsets.UTF_8))
    .joinToString("") {"%02x".format(it)}

/** Foreign receipt details stay private even while a collector is catching up with a connection switch. */
object ManagementReceiptVisibility {
    const val SharedPending="SharedPending"
    const val SharedInstancePending="SharedInstancePending"
    const val SharedProjectPending="SharedProjectPending"
    internal const val SharedCronTimingPending="SharedCronTimingPending"
    internal const val SharedCronRunOncePending="SharedCronRunOncePending"
    internal const val SharedCronProjectPending="SharedCronProjectPending"
    fun instanceAction(action:String)=action in setOf("RelayLinkStart","RelayUnlink","CustomEndpointProbe") || HostKanbanBoardAction.receipt(action) || HostKanbanTaskAction.receipt(action) || HostKanbanStatusChange.receipt(action)
    fun project(rows:List<ManagementOperation>,apiScope:String,resourceScope:String,dashboardUrl:String="",projectHome:String="",actor:String=""):List<ManagementOperation> {
        if(resourceScope.isBlank()) return emptyList()
        return rows.mapNotNull {row ->
            val pending=row.state in setOf("sending","unknown")
            val sameResource=row.resourceScope==resourceScope
            val instance=dashboardUrl.isNotBlank() && row.url==dashboardUrl && pending && (instanceAction(row.action) || row.action==SharedInstancePending)
            val sameStore=dashboardUrl.isNotBlank() && row.url==dashboardUrl && pending && projectHome.isNotBlank() && row.projectHome==projectHome
            if(!sameResource && !instance && !sameStore) null
            else if(row.action in setOf(CronTimingProtocol.Action, SharedCronTimingPending, CronRunOnceProtocol.Action,
                    SharedCronRunOncePending, CronProjectProtocol.Action, SharedCronProjectPending)) {
                if(sameResource && row.apiScope==apiScope && actor.isNotBlank() && row.actor==actor &&
                    projectHome.isNotBlank() && row.projectHome==projectHome) row
                else if(pending) row.copy(apiScope="",actor="",
                    action=when (row.action) {
                        CronRunOnceProtocol.Action, SharedCronRunOncePending -> SharedCronRunOncePending
                        CronProjectProtocol.Action, SharedCronProjectPending -> SharedCronProjectPending
                        else -> SharedCronTimingPending
                    },
                    target="",requestSummary="",resultSummary="",createdAt=0)
                else null
            }
            else if(sameResource && row.apiScope==apiScope) row
            else if(pending) row.copy(resourceScope=if(instance || sameStore) "" else row.resourceScope,apiScope="",profile=if(instance || sameStore) "" else row.profile,actor="",action=if(instance) SharedInstancePending else if(sameStore) SharedProjectPending else SharedPending,target="",requestSummary="",resultSummary="",createdAt=0)
            else null
        }
    }
}
