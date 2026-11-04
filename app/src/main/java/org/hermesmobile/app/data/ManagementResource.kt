package org.hermesmobile.app.data

import java.security.MessageDigest

/** Exact canonical dashboard URL (including prefix) and profile; independent of inference credentials. */
fun managementResourceScope(url:String,profile:String):String=MessageDigest.getInstance("SHA-256")
    .digest(("management-v2\u0000"+url+"\u0000"+profile).toByteArray(Charsets.UTF_8))
    .joinToString("") {"%02x".format(it)}

/** Foreign receipt details stay private even while a collector is catching up with a connection switch. */
object ManagementReceiptVisibility {
    const val SharedPending="SharedPending"
    fun project(rows:List<ManagementOperation>,apiScope:String,resourceScope:String):List<ManagementOperation> =
        rows.filter {resourceScope.isNotBlank() && it.resourceScope==resourceScope}.mapNotNull {row ->
            if(row.apiScope==apiScope) row
            else if(row.state in setOf("sending","unknown")) row.copy(apiScope="",actor="",action=SharedPending,target="",requestSummary="",resultSummary="",createdAt=0)
            else null
        }
}
