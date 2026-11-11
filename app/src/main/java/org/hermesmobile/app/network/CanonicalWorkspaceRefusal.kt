package org.hermesmobile.app.network

import kotlinx.serialization.json.*

/** Closed advisory labels only. They neither prove an outcome nor release a receipt guard. */
internal enum class CanonicalWorkspaceRefusal { Busy, Compressing, Permission }

internal class CanonicalWorkspaceRefused private constructor(code:Int,val refusal:CanonicalWorkspaceRefusal):GatewayRpcException(code) {
    companion object {
        fun parse(code:Int,error:JsonObject):CanonicalWorkspaceRefused? {
            if(code!=4001) return null
            val data=error["data"] as? JsonObject ?: return null
            val reason=(data["reason"] as? JsonPrimitive)?.takeIf {it.isString}?.content ?: return null
            if(error["message"]!=JsonPrimitive(reason)) return null
            val field=data["field"]
            val absent=field==null || field==JsonNull
            val advice=when {
                reason=="session_busy" && absent->CanonicalWorkspaceRefusal.Busy
                reason=="session_busy" && field==JsonPrimitive("compression")->CanonicalWorkspaceRefusal.Compressing
                reason=="permission_denied" && absent->CanonicalWorkspaceRefusal.Permission
                else->return null
            }
            // Keep no raw reason/message/detail/path/credential from the response.
            return CanonicalWorkspaceRefused(code,advice)
        }
    }
}
