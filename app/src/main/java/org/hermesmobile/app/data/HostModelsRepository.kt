package org.hermesmobile.app.data

import org.hermesmobile.app.network.*

class HostModelsRepository {
    suspend fun read(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,refresh:Boolean=false,expectedHome:String?=null):HostModelInventory {
        GatewayCronInventory.requireProfile(target.profile)
        require(target.url==api.base.toString() && target.actor==tokens.provider+" · "+tokens.userId)
        require(tokens.expiresAt>System.currentTimeMillis()/1000)
        api.identity(tokens)
        val home=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome ?: throw HostModelsUnavailable()
        if(expectedHome!=null && home.identity()!=expectedHome) throw HostModelsChanged()
        val aux=api.modelAuxiliary(tokens,target.profile)
        val catalog=api.modelOptions(tokens,target.profile,refresh)
        val after=api.profiles(tokens).singleOrNull {it.name==target.profile}?.pluginHome
        if(after?.identity()!=home.identity()) throw HostModelsChanged()
        return HostModelInventory.parse(aux,catalog,target,home,refresh)
    }
}
