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
    fun instanceAction(action:String)=action in setOf("RelayLinkStart","RelayUnlink")
    fun project(rows:List<ManagementOperation>,apiScope:String,resourceScope:String,dashboardUrl:String=""):List<ManagementOperation> {
        if(resourceScope.isBlank()) return emptyList()
        return rows.mapNotNull {row ->
            val pending=row.state in setOf("sending","unknown")
            val sameResource=row.resourceScope==resourceScope
            val instance=dashboardUrl.isNotBlank() && row.url==dashboardUrl && pending && (instanceAction(row.action) || row.action==SharedInstancePending)
            if(!sameResource && !instance) null
            else if(sameResource && row.apiScope==apiScope) row
            else if(pending) row.copy(resourceScope=if(instance) "" else row.resourceScope,apiScope="",profile=if(instance) "" else row.profile,actor="",action=if(instance) SharedInstancePending else SharedPending,target="",requestSummary="",resultSummary="",createdAt=0)
            else null
        }
    }
}
