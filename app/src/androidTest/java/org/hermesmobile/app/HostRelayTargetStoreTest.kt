package org.hermesmobile.app

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.ManagementTokens
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class HostRelayTargetStoreTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated():Context=object:ContextWrapper(context) {private val file="qa-relay-vault-"+UUID.randomUUID();override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(file,mode)}
    private fun saved(target:ManagementTarget)=buildJsonObject {put("url",target.url.trimEnd('/'));put("profile",target.profile);put("tokens",ManagementTokens("relay-vault-fixture","refresh-fixture","password",target.actor.substringAfter(" · "),4102444800).json())}
    private fun binding(target:ManagementTarget,key:String="fixture-receipt",home:String="a".repeat(64))=HostRelayUnlinkBinding.parse(ManagementOperation(key,target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"RelayUnlink","telegram",buildJsonObject {put("platform","telegram");put("home_id",home);put("account_count",1)}.toString(),"unknown","",1),target)
    private val account=HostRelayAccount("private-vault-account","Private vault 名称","@private-vault")
    private fun fixture():Triple<Context,SecureCredentialStore,ManagementTarget> {val c=isolated();val store=SecureCredentialStore(c);val config=ServerConfig("https://relay-vault.test/api/","relay-vault-inference-fixture");store.writeConfig(config);val t=ManagementTarget(config.scope,"https://relay-vault.test/dashboard/","worker","password · fixture");store.saveManagement(t.apiScope,saved(t));return Triple(c,store,t)}
    @Test fun encryptedTargetsSurviveRecreationAndNormalizeTheSavedBaseUrl() {
        val(c,store,t)=fixture();val b=binding(t);store.relayTargets.save(b,account);val raw=c.getSharedPreferences("credentials",0).getString("relay_targets_v1","")!!
        for(secret in listOf(account.id,account.name,account.handle,t.actor,t.url)) assertFalse(raw.contains(secret));assertEquals(account.id,SecureCredentialStore(c).relayTargets.read(b)!!.id)
        for(changed in listOf(t.copy(actor="password · other"),t.copy(profile="other"),t.copy(url="https://other.test/"),t.copy(apiScope="b".repeat(64)))) assertNull(store.relayTargets.read(binding(changed)))
        assertNull(store.relayTargets.read(binding(t,home="b".repeat(64))));assertNotNull(store.relayTargets.read(b));assertTrue(runCatching {store.relayTargets.save(b,HostRelayAccount("replacement-private-account",account.name,account.handle))}.isFailure);assertEquals(account.id,store.relayTargets.read(b)!!.id)
    }
    @Test fun changedSavedSignInCannotReadAnOldActorOrProfileTarget() {
        val(_,store,t)=fixture();val b=binding(t);store.relayTargets.save(b,account)
        for(changed in listOf(t.copy(actor="password · other"),t.copy(profile="other"),t.copy(url="https://other.test/"))) {store.saveManagement(t.apiScope,saved(changed));assertNull(store.relayTargets.read(b))}
        store.saveManagement(t.apiScope,saved(t));assertEquals(account.id,store.relayTargets.read(b)!!.id)
    }
    @Test fun clearUsesExactActorUrlProfileAndKeepsOtherEncryptedTargets() {
        val(_,store,t)=fixture();val b=binding(t);store.relayTargets.save(b,account);val other=t.copy(profile="other");store.saveManagement(t.apiScope,saved(other));val second=binding(other,"second");store.relayTargets.save(second,account);store.saveManagement(t.apiScope,saved(t));store.relayTargets.clear(t);assertNull(store.relayTargets.read(b));store.saveManagement(t.apiScope,saved(other));assertNotNull(store.relayTargets.read(second))
    }
    @Test fun managementSignOutAndCredentialForgetRemovePrivateTargets() {
        for(forget in listOf(false,true)) {val(_,store,t)=fixture();val b=binding(t);store.relayTargets.save(b,account);if(forget) store.forget(t.apiScope) else store.saveManagement(t.apiScope,null);assertNull(store.relayTargets.read(b));if(!forget) {store.saveManagement(t.apiScope,saved(t));assertNull(store.relayTargets.read(b))}}
    }
    @Test fun localLimitRefusesWithoutEvictingExistingReceiptTargets() {
        val(_,store,t)=fixture();for(i in 0..31) store.relayTargets.save(binding(t,"receipt-$i"),account)
        assertTrue(runCatching {store.relayTargets.save(binding(t,"overflow"),account)}.exceptionOrNull() is HostRelayPrivateFull);assertNotNull(store.relayTargets.read(binding(t,"receipt-0")));store.relayTargets.clear(t);store.relayTargets.save(binding(t,"after-clear"),account);assertNotNull(store.relayTargets.read(binding(t,"after-clear")))
    }
    @Test fun missingCredentialsCannotSaveOrRevealPrivateTargets() {val(_,store,t)=fixture();val b=binding(t);store.forget(t.apiScope);assertTrue(runCatching {store.relayTargets.save(b,account)}.isFailure);assertNull(store.relayTargets.read(b))}
    @Test fun corruptNewCacheCannotPreventRemovingManagementCredentials() {
        val(c,store,t)=fixture();c.getSharedPreferences("credentials",0).edit().putString("relay_targets_v1","invalid-fixture-ciphertext").commit();store.saveManagement(t.apiScope,null);assertNull(store.management(t.apiScope));assertNull(c.getSharedPreferences("credentials",0).getString("relay_targets_v1",null))
    }
}
