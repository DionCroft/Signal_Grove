package org.hermesmobile.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProviderCodeStoreTest {
    @get:Rule val credentials=CredentialFixtureRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture():Pair<SecureCredentialStore,ProviderOAuthBinding> {
        val store=SecureCredentialStore(context);val config=ServerConfig("https://inference.example/","inference-provider-fixture",false);store.writeConfig(config);store.saveManagement(config.scope,buildJsonObject {put("fixture",true)})
        val t=ManagementTarget(config.scope,"https://hermes.example/prefix/","worker","password · fixture")
        val receipt=ManagementOperation("provider-cache-fixture",t.resourceScope,t.apiScope,t.url,t.profile,t.actor,"ProviderOAuthStart",ProviderOAuthFixture.provider,"{}","acknowledged",ProviderOAuthProtocol.start(ProviderOAuthFixture.start(),"a".repeat(64)).toString(),1)
        return store to ProviderOAuthBinding.parse(receipt,t)
    }
    @Test fun encryptedCodeSurvivesStoreRecreationButIsBoundToExactReceiptAndOwner() {
        val (store,b)=fixture();val now=System.currentTimeMillis();store.providerCodes.save(b,ProviderDeviceCode.parse(ProviderOAuthFixture.start(),now))
        val raw=context.getSharedPreferences("credentials",0).getString("provider_codes_v1","")!!;assertFalse(raw.contains(ProviderOAuthFixture.code));assertFalse(raw.contains(ProviderOAuthFixture.url));assertFalse(raw.contains("password"))
        val restored=SecureCredentialStore(context).providerCodes.read(b,now+1000)!!;assertEquals(ProviderOAuthFixture.code,restored.code);assertEquals(ProviderOAuthFixture.url,restored.url)
        val other=ProviderOAuthBinding(b.receipt.copy(actor="password · other"),b.homeId,b.sessionId);assertNull(store.providerCodes.read(other,now+1000));assertNull(store.providerCodes.read(ProviderOAuthBinding(b.receipt,b.homeId,"other_session_1234567"),now+1000));assertNotNull(store.providerCodes.read(b,now+1000))
    }
    @Test fun expiredCodeIsDeletedAndNeverRestored() {val (store,b)=fixture();val now=System.currentTimeMillis();store.providerCodes.save(b,ProviderDeviceCode.parse(ProviderOAuthFixture.start(),now));assertNull(store.providerCodes.read(b,now+900001));assertNull(store.providerCodes.read(b,now+1000))}
    @Test fun managementSignOutDeletesCodeBeforeFutureSignIn() {val (store,b)=fixture();val now=System.currentTimeMillis();store.providerCodes.save(b,ProviderDeviceCode.parse(ProviderOAuthFixture.start(),now));store.saveManagement(b.receipt.apiScope,null);store.saveManagement(b.receipt.apiScope,buildJsonObject {put("fixture",true)});assertNull(store.providerCodes.read(b,now+1000))}
    @Test fun forgettingInferenceCredentialAlsoDeletesCodeAndManagementCredentials() {val (store,b)=fixture();val now=System.currentTimeMillis();store.providerCodes.save(b,ProviderDeviceCode.parse(ProviderOAuthFixture.start(),now));store.forget(b.receipt.apiScope);assertNull(store.providerCodes.read(b,now+1000));assertNull(store.management(b.receipt.apiScope));assertNull(store.profile(b.receipt.apiScope))}
}
