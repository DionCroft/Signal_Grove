package org.hermesmobile.app.data

import org.hermesmobile.app.network.*

class HostRelayRepository(private val channels:HostChannelsRepository) {
    suspend fun read(target:ManagementTarget,api:ManagementClient,tokens:ManagementTokens,expectedHome:String?=null):HostRelayInventory {
        val before=HostRelayScope.from(channels.read(target,api,tokens,expectedHome))
        // Official relay routes use the process home and declare no profile parameter.
        val raw=api.hostRelayAccounts(tokens)
        val after=HostRelayScope.from(channels.read(target,api,tokens,before.homeId))
        if(!before.same(after)) throw HostRelayChanged()
        return HostRelayInventory.parse(raw,before)
    }
    suspend fun portal(target:ManagementTarget,expected:HostRelayInventory,api:ManagementClient,tokens:ManagementTokens):HostRelayInventory {
        require(target==expected.owner && expected.manager==HostRelayManager.Portal && HostRelayPolicy.portal(expected.portalUrl))
        val current=read(target,api,tokens,expected.scope.homeId)
        if(!expected.samePortal(current)) throw HostRelayChanged()
        return current
    }
}
