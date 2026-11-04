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

class HostRelayCodeStoreTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated():Context=object:ContextWrapper(context) {private val file="qa-relay-codes-"+UUID.randomUUID();override fun getSharedPreferences(name:String,mode:Int)=super.getSharedPreferences(file,mode)}
    private fun saved(t:ManagementTarget)=buildJsonObject {put("url",t.url.trimEnd('/'));put("profile",t.profile);put("tokens",ManagementTokens("code-cache-fixture","refresh-fixture","password",t.actor.substringAfter(" · "),4102444800).json())}
    private fun binding(t:ManagementTarget,key:String="fixture-key",home:String="a".repeat(64),status:String="pending",issued:Boolean=true):HostRelayLinkBinding {
        val summary=buildJsonObject {put("platform","telegram");put("home_id",home);put("relay_modes",buildJsonObject {put("telegram",buildJsonArray {add(true);add(false)})})}
        val r=ManagementOperation(key,t.resourceScope,t.apiScope,t.url,t.profile,t.actor,"RelayLinkStart","telegram",summary.toString(),"acknowledged",buildJsonObject {put("acknowledged",true);put("code_issued",issued);put("status",if(issued) status else "not_issued")}.toString(),1)
        return HostRelayLinkBinding.parse(r,t)
    }
    private fun fixture():Triple<Context,SecureCredentialStore,ManagementTarget> {val c=isolated();val store=SecureCredentialStore(c);val config=ServerConfig("https://codes.test/inference/","code-cache-inference-fixture");store.writeConfig(config);val t=ManagementTarget(config.scope,"https://codes.test/dashboard/","worker","password · fixture");store.saveManagement(t.apiScope,saved(t));return Triple(c,store,t)}
    private fun code(now:Long)=HostRelayCode(HostRelayLinkFixture.code,HostRelayLinkFixture.bot,HostRelayLinkFixture.bot,now+900_000)
    @Test fun encryptedOriginalCodeSurvivesRecreationWithoutPlaintextOrBindingSubstitution() {
        val(c,store,t)=fixture();val b=binding(t);val now=System.currentTimeMillis();store.relayCodes.beforeStart(t,now);store.relayCodes.save(b,code(now),now)
        val raw=c.getSharedPreferences("credentials",0).getString("relay_codes_v1","")!!;for(v in listOf(HostRelayLinkFixture.code,HostRelayLinkFixture.bot,t.actor,t.url,"/link")) assertFalse(raw.contains(v))
        assertEquals(HostRelayLinkFixture.code,SecureCredentialStore(c).relayCodes.read(b,now)!!.code)
        for(other in listOf(t.copy(actor="password · other"),t.copy(profile="other"),t.copy(url="https://other.test/"),t.copy(apiScope="b".repeat(64)))) assertNull(store.relayCodes.read(binding(other),now))
        assertNull(store.relayCodes.read(binding(t,home="b".repeat(64)),now));assertNotNull(store.relayCodes.read(b,now))
    }
    @Test fun originalCapabilityAndExpiryCannotBeReplacedUnderItsReceiptKey() {
        val(_,store,t)=fixture();val b=binding(t);val now=System.currentTimeMillis();val original=code(now);store.relayCodes.save(b,original,now)
        for(value in listOf(HostRelayCode("OTHER",original.botUrl,original.originalBot,original.expiresAt),HostRelayCode(original.code,original.botUrl,original.originalBot,original.expiresAt+1),HostRelayCode(original.code,null,original.originalBot,original.expiresAt))) assertTrue(runCatching {store.relayCodes.save(b,value,now)}.isFailure)
        assertTrue(original.same(store.relayCodes.read(b,now)!!))
    }
    @Test fun expiredCopyIsPurgedAndCannotReturnAfterAClockRewind() {val(_,store,t)=fixture();val b=binding(t);val now=System.currentTimeMillis();store.relayCodes.save(b,code(now),now);assertNull(store.relayCodes.read(b,now+900001));assertNull(store.relayCodes.read(b,now))}
    @Test fun localAndGlobalLimitsRefuseWithoutEvictingOtherLiveCodes() {
        val(_,store,t)=fixture();val now=System.currentTimeMillis();for(profile in 0..3) {val owner=t.copy(profile="worker-$profile");store.saveManagement(t.apiScope,saved(owner));for(i in 0..31) store.relayCodes.save(binding(owner,"$profile-$i"),code(now),now);assertTrue(runCatching {store.relayCodes.beforeStart(owner,now)}.exceptionOrNull() is HostRelayCodeFull)}
        val fifth=t.copy(profile="fifth");store.saveManagement(t.apiScope,saved(fifth));assertTrue(runCatching {store.relayCodes.beforeStart(fifth,now)}.exceptionOrNull() is HostRelayCodeFull);val first=t.copy(profile="worker-0");store.saveManagement(t.apiScope,saved(first));assertNotNull(store.relayCodes.read(binding(first,"0-0"),now));store.relayCodes.clear(first);store.saveManagement(t.apiScope,saved(fifth));store.relayCodes.beforeStart(fifth,now)
    }
    @Test fun clearAffectsOnlyExactSignInAndDoesNotEraseOtherProfileCopies() {val(_,store,t)=fixture();val now=System.currentTimeMillis();store.relayCodes.save(binding(t),code(now),now);val other=t.copy(profile="other");store.saveManagement(t.apiScope,saved(other));store.relayCodes.save(binding(other,"other-key"),code(now),now);store.relayCodes.clear(t);store.saveManagement(t.apiScope,saved(t));assertNull(store.relayCodes.read(binding(t),now));store.saveManagement(t.apiScope,saved(other));assertNotNull(store.relayCodes.read(binding(other,"other-key"),now))}
    @Test fun signOutForgetAndCorruptCodeCacheCannotRestoreOrPreventCredentialRemoval() {
        for(mode in listOf("out","forget","corrupt")) {val(c,store,t)=fixture();val now=System.currentTimeMillis();val b=binding(t);store.relayCodes.save(b,code(now),now);if(mode=="corrupt") c.getSharedPreferences("credentials",0).edit().putString("relay_codes_v1","invalid-code-ciphertext").commit();if(mode=="forget") store.forget(t.apiScope) else store.saveManagement(t.apiScope,null);assertNull(store.management(t.apiScope));if(mode!="forget") {store.saveManagement(t.apiScope,saved(t));assertNull(store.relayCodes.read(b,now))}}
    }
    @Test fun changedSavedActorUrlOrProfileCannotReadOldOriginalCode() {val(_,store,t)=fixture();val now=System.currentTimeMillis();val b=binding(t);store.relayCodes.save(b,code(now),now);for(changed in listOf(t.copy(actor="password · other"),t.copy(profile="other"),t.copy(url="https://other.test/"))) {store.saveManagement(t.apiScope,saved(changed));assertNull(store.relayCodes.read(b,now))};store.saveManagement(t.apiScope,saved(t));assertNotNull(store.relayCodes.read(b,now))}
    @Test fun nonpendingOrNotIssuedBindingsCannotExposeSavedCode() {val(_,store,t)=fixture();val now=System.currentTimeMillis();store.relayCodes.save(binding(t),code(now),now);for(b in listOf(binding(t,status="inactive"),binding(t,status="refused"),binding(t,issued=false))) assertNull(store.relayCodes.read(b,now))}
}
