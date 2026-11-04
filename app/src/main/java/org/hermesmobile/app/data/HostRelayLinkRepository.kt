package org.hermesmobile.app.data

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.network.*
import java.util.UUID

class HostRelayLinkRepository(private val dao:HermesDao,private val ready:Deferred<Unit>,private val relay:HostRelayRepository,private val codes:HostRelayCodeStore) {
    private suspend fun original(target:ManagementTarget,key:String):HostRelayLinkBinding {ready.await();return HostRelayLinkBinding.parse(dao.managementOperation(key) ?: throw HostRelayCodeUnavailable(),target)}
    suspend fun execute(target:ManagementTarget,request:HostRelayLinkRequest,api:ManagementClient,tokens:ManagementTokens):HostRelayLinkObservation {
        ready.await();require(target==request.expected.owner)
        if(dao.unresolvedManagementOperations(target.resourceScope).isNotEmpty()) throw ManagementOutcomePending()
        val before=relay.read(target,api,tokens,request.expected.scope.homeId);if(!request.same(before)) throw HostRelayChanged()
        try {codes.beforeStart(target,System.currentTimeMillis())} catch(e:CancellationException) {throw e} catch(e:HostRelayCodeFull) {throw e} catch(e:Exception) {throw HostRelayCodeUnavailable()}
        val record=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"RelayLinkStart",request.platform,request.summary().toString(),"sending","",System.currentTimeMillis())
        try {
            dao.claimManagementOperation(record);currentCoroutineContext().ensureActive()
            val raw=try {api.startHostRelayLink(tokens,request.platform)} catch(e:HostRelayLinkRefused) {
                withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",buildJsonObject {put("acknowledged",true);put("code_issued",false);put("status","not_issued");put("portal_required",e.portal)}.toString())}
                return HostRelayLinkObservation(original(target,record.key),null,false,false)
            }
            request.acknowledge(raw)
            withContext(NonCancellable) {dao.finishManagementOperation(record.key,"acknowledged",buildJsonObject {put("acknowledged",true);put("code_issued",true);put("status","unverified")}.toString())}
            val binding=original(target,record.key);val code=HostRelayCode.from(raw,request,System.currentTimeMillis())
            withContext(NonCancellable+Dispatchers.IO) {runCatching {if(code.expiresAt>System.currentTimeMillis()) codes.save(binding,code,System.currentTimeMillis())}}
            val after=relay.read(target,api,tokens,binding.homeId);if(!request.same(after)) throw HostRelayChanged()
            val stored=try {codes.read(binding,System.currentTimeMillis())} catch(e:Exception) {null}
            return HostRelayLinkObservation(binding,stored,stored!=null,true)
        } catch(e:Exception) {
            val state=withContext(NonCancellable) {dao.finishManagementOperation(record.key,"unknown","");dao.managementOperation(record.key)?.state}
            if(e is CancellationException) throw e
            if(state=="unknown") throw HostRelayLinkUncertain()
            if(state=="acknowledged") throw HostRelayLinkReadbackPending()
            throw e
        }
    }
    suspend fun local(target:ManagementTarget,key:String):HostRelayLinkObservation {
        val binding=original(target,key)
        val available=if(binding.issued && binding.status in setOf("unverified","pending")) try {codes.read(binding,System.currentTimeMillis())!=null} catch(e:Exception) {false} else false
        return HostRelayLinkObservation(binding,null,available,false)
    }
    suspend fun poll(target:ManagementTarget,key:String,api:ManagementClient,tokens:ManagementTokens):HostRelayLinkObservation {
        val binding=original(target,key)
        if(!binding.issued || binding.status !in setOf("unverified","pending")) return HostRelayLinkObservation(binding,null,false,false)
        val before=relay.read(target,api,tokens,binding.homeId);if(!binding.same(before)) throw HostRelayChanged()
        val code=try {codes.read(binding,System.currentTimeMillis())} catch(e:Exception) {throw HostRelayCodeUnavailable()} ?: throw HostRelayCodeUnavailable()
        if(before.platforms.single {it.id==binding.receipt.target}.botUrl!=code.originalBot) throw HostRelayChanged()
        val raw=api.hostRelayLinkState(tokens,code.code);val status=HostRelayLinkPolicy.text(raw,"state",32);require(status in setOf("pending","inactive","refused"))
        val after=relay.read(target,api,tokens,binding.homeId)
        if(!binding.same(after) || after.platforms.single {it.id==binding.receipt.target}.botUrl!=code.originalBot) throw HostRelayChanged()
        val reason=(raw["reason"] as? JsonPrimitive)?.takeIf {it.isString && it.content.length<=4096 && it.content.none(Char::isISOControl)}?.content
        withContext(NonCancellable) {dao.observeRelayCodeState(key,status)}
        val current=original(target,key)
        if(current.status!="pending") {withContext(NonCancellable+Dispatchers.IO) {runCatching {codes.remove(current)}};return HostRelayLinkObservation(current,null,false,true,reason)}
        val cached=try {codes.read(current,System.currentTimeMillis())} catch(e:Exception) {null}
        if(cached==null || !cached.same(code)) throw HostRelayCodeUnavailable()
        return HostRelayLinkObservation(current,cached,true,true,reason)
    }
}
