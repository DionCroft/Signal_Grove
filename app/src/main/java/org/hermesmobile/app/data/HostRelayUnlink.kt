package org.hermesmobile.app.data

import kotlinx.serialization.json.*

class HostRelayUnlinkUncertain:Exception()
class HostRelayUnlinkReadbackPending:Exception()
class HostRelayPrivateUnavailable:Exception()
class HostRelayPrivateFull:Exception()

class HostRelayUnlinkRequest(val expected:HostRelayInventory,val platform:String,val account:HostRelayAccount) {
    val row=expected.platforms.single {it.id==platform}
    init {
        require(expected.scope.homeId.matches(Regex("[a-f0-9]{64}")))
        require(expected.manager==HostRelayManager.Connector && HostChannelPolicy.id(platform) && addressable(account.id))
        require(row.accounts.singleOrNull {it.id==account.id}?.let {sameAccount(it,account)}==true)
    }
    fun same(current:HostRelayInventory):Boolean {
        val now=current.platforms.singleOrNull {it.id==platform} ?: return false
        return sameInstance(current) && row.botUrl==now.botUrl && now.accounts.singleOrNull {it.id==account.id}?.let {sameAccount(account,it)}==true
    }
    private fun sameInstance(current:HostRelayInventory)=expected.scope.same(current.scope) && current.manager==HostRelayManager.Connector
    /** Null means the selected authority/bot context changed; absence alone is not revocation. */
    fun observedAbsent(current:HostRelayInventory):Boolean? {
        val now=current.platforms.singleOrNull {it.id==platform} ?: return null
        return if(sameInstance(current) && row.botUrl==now.botUrl) now.accounts.none {it.id==account.id} else null
    }
    fun summary()=buildJsonObject {put("platform",platform);put("home_id",expected.scope.homeId);put("account_count",1)}
    fun acknowledge(raw:JsonObject) {require(raw["unlinked"]==JsonPrimitive(true))}
    override fun toString()="HostRelayUnlinkRequest(redacted)"
    companion object {
        fun addressable(value:String)=value.isNotBlank() && value==value.trim() && value.length<=512 && value !in setOf(".","..") && value.none {it.isISOControl() || it in "/\\?#"}
        fun sameAccount(a:HostRelayAccount,b:HostRelayAccount)=a.id==b.id && a.name==b.name && a.handle==b.handle
    }
}
class HostRelayUnlinkBinding private constructor(val receipt:ManagementOperation,val homeId:String) {
    fun owner()=buildJsonObject {put("key",receipt.key);put("api_scope",receipt.apiScope);put("url",receipt.url);put("profile",receipt.profile);put("actor",receipt.actor);put("action",receipt.action);put("platform",receipt.target);put("home_id",homeId)}
    override fun toString()="HostRelayUnlinkBinding(redacted)"
    companion object {
        fun parse(receipt:ManagementOperation,target:ManagementTarget):HostRelayUnlinkBinding {
            require(receipt.action=="RelayUnlink" && receipt.state in setOf("sending","unknown","acknowledged","reviewed") && receipt.resourceScope==target.resourceScope && receipt.apiScope==target.apiScope && receipt.url==target.url && receipt.profile==target.profile && receipt.actor==target.actor)
            require(HostChannelPolicy.id(receipt.target) && receipt.key.isNotBlank() && receipt.key.length<=128)
            val j=Json.parseToJsonElement(receipt.requestSummary).jsonObject
            require(j.keys==setOf("platform","home_id","account_count") && j["platform"]==JsonPrimitive(receipt.target) && j["account_count"]==JsonPrimitive(1))
            val home=(j["home_id"] as? JsonPrimitive)?.takeIf {it.isString}?.content ?: error("Missing home")
            require(home.matches(Regex("[a-f0-9]{64}")))
            return HostRelayUnlinkBinding(receipt,home)
        }
    }
}
interface HostRelayTargetStore {
    fun save(binding:HostRelayUnlinkBinding,account:HostRelayAccount)
    fun read(binding:HostRelayUnlinkBinding):HostRelayAccount?
    fun remove(binding:HostRelayUnlinkBinding)
    fun clear(target:ManagementTarget)
}
class HostRelayLocalTarget(val binding:HostRelayUnlinkBinding,val account:HostRelayAccount?) {
    override fun toString()="HostRelayLocalTarget(redacted)"
}
class HostRelayUnlinkOutcome(val inventory:HostRelayInventory,val receipt:ManagementOperation,val absent:Boolean?) {
    override fun toString()="HostRelayUnlinkOutcome(redacted)"
}
