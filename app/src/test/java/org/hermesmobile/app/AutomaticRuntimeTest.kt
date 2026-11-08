package org.hermesmobile.app

import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class AutomaticRuntimeTest {
    private val now=1_800_000_000_000L
    private fun runtime(model:String="active",provider:String="local",time:Long=now,status:String="ready",state:String="ready",verified:Boolean=true)=buildJsonObject {
        put("object","signalgrove.model_runtime");put("schema_version",1);put("observed_at",time);put("status",status)
        put("models",buildJsonArray {add(buildJsonObject {put("model",model);put("provider",provider);put("state",state);put("verified",verified)})})
    }
    private fun catalog(model:String="active",provider:String="local",authenticated:Boolean=true,reasoning:Boolean=true)=buildJsonObject {
        put("model","saved-offline");put("provider","saved-provider")
        put("providers",buildJsonArray {add(buildJsonObject {
            put("slug",provider);put("authenticated",authenticated);put("models",buildJsonArray {add(model)})
            put("capabilities",buildJsonObject {put(model,buildJsonObject {put("reasoning",reasoning);put("fast",false)})})
        })})
    }
    private fun caps(runtime:Boolean=true)=HermesCapabilities("hermes-agent",buildJsonObject {
        put("model_options",true);put("model_runtime_status_v1",runtime)
    },buildJsonObject {
        if(runtime) put(DiscoveryResource.RuntimeModels.endpoint,buildJsonObject {put("method","GET");put("path","/"+DiscoveryResource.RuntimeModels.path)})
        put(DiscoveryResource.Models.endpoint,buildJsonObject {put("method","GET");put("path","/"+DiscoveryResource.Models.path)})
    })
    private fun refused(block:()->Unit) {assertTrue(runCatching(block).exceptionOrNull() is AutomaticModelUnavailable)}

    @Test fun absentChoiceFollowsServingWhileExplicitLegacyChoicesRemainExplicit() {
        assertTrue(RuntimeChoice.saved("{}","a").followServing)
        assertTrue(RuntimeChoice.saved("broken","a").followServing)
        assertEquals(RuntimeChoice(),RuntimeChoice.saved("""{"a":{}}""","a"))
        val explicit=RuntimeChoice("saved","provider",true,"high")
        assertEquals(explicit,RuntimeChoice.saved(JsonObject(mapOf("a" to explicit.storedFields())).toString(),"a"))
        assertTrue(RuntimeChoice.saved(JsonObject(mapOf("a" to RuntimeChoice.automatic().storedFields())).toString(),"a").followServing)
    }
    @Test fun unresolvedAutomaticChoiceCannotLeakIntoAnyRequestBody() {
        assertTrue(runCatching {RuntimeChoice.automatic().fields()}.isFailure)
        for(t in listOf("runs","session","compat")) {
            assertTrue(runCatching {TurnRequest.create(Conversation("c","s","api",pendingInput="fixture",transport=t),emptyList(),"configured",RuntimeChoice.automatic())}.isFailure)
        }
        assertFalse(RuntimeChoice.automatic().storedFields().containsKey("model"))
        assertFalse(RuntimeChoice.automatic().storedFields().containsKey("provider"))
    }
    @Test fun freshServingPairWinsOverConfiguredDefaultAndIsResolvedPerTurn() {
        val a=AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(),catalog(),now)
        val b=AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime("changed","other"),catalog("changed","other"),now)
        assertEquals(RuntimeChoice("active","local"),a);assertEquals(RuntimeChoice("changed","other"),b)
        assertFalse(a.followServing);assertFalse(b.followServing)
    }
    @Test fun staleFutureOrUnverifiedObservationsCannotSelectTheSavedOfflineDefault() {
        for(time in listOf(now-AutomaticRuntime.MAX_AGE_MS,now+30_001)) refused {AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(time=time),catalog(),now)}
        refused {AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(status="unavailable",state="offline",verified=false),catalog(),now)}
        refused {AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(status="unverified",state="unknown",verified=false),catalog(),now)}
    }
    @Test fun ambiguousServingModelsCannotPickTheFirstEntry() {
        val source=runtime();val rows=source["models"]!!.jsonArray
        val j=JsonObject(source+mapOf("status" to JsonPrimitive("ambiguous"),"models" to JsonArray(rows+runtime("second","other")["models"]!!.jsonArray)))
        refused {AutomaticRuntime.resolve(RuntimeChoice.automatic(),j,catalog(),now)}
    }
    @Test fun providerModelAndAccountMustMatchTheAdvertisedCatalogExactly() {
        for(j in listOf(catalog("other"),catalog(provider="other"),catalog(authenticated=false)))
            refused {AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(),j,now)}
        val j=catalog();val p=j["providers"]!!.jsonArray.single().jsonObject
        val unavailable=JsonObject(j+("providers" to buildJsonArray {add(JsonObject(p+("unavailable_models" to buildJsonArray {add("active")})))}))
        refused {AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(),unavailable,now)}
    }
    @Test fun knownConflictingSessionLockIsHeldWithoutChangingItsChoice() {
        val lock=RuntimeChoice("locked","local")
        assertTrue(runCatching {AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(),catalog(),now,lock)}.exceptionOrNull() is AutomaticModelConflict)
        assertEquals(RuntimeChoice("locked","local"),lock)
        assertEquals(RuntimeChoice("active","local"),AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(),catalog(),now,RuntimeChoice("active","local")))
    }
    @Test fun automaticOptionsArePreservedOnlyWhenOfferedForTheResolvedModel() {
        val choice=RuntimeChoice(reasoning=true,effort="high",followServing=true)
        assertEquals(choice.copy(model="active",provider="local",followServing=false),AutomaticRuntime.resolve(choice,runtime(),catalog(),now))
        refused {AutomaticRuntime.resolve(choice,runtime(),catalog(reasoning=false),now)}
        refused {AutomaticRuntime.resolve(RuntimeChoice(serviceTier="priority",followServing=true),runtime(),catalog(),now)}
    }
    @Test fun automaticWireFieldsUseTheVerifiedPairOnEverySupportedTransport() {
        val resolved=AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(),catalog(),now)
        for(t in listOf("runs","session","compat")) {
            val body=TurnRequest.create(Conversation("c","s","api",pendingInput="fixture",transport=t),emptyList(),"offline",resolved).body
            assertEquals("active",body.text("model"));assertEquals("local",body.text("provider"))
            assertFalse(body.containsKey("_signal_grove_model_mode"))
        }
    }
    @Test fun aPendingRequestRemainsFrozenWhenTheServingModelChanges() {
        val root=Files.createTempDirectory("serving-model-freeze").toFile()
        try {
            val store=TurnRequestStore(root);val c=Conversation("c","scope","api",pendingInput="fixture",transport="runs")
            val first=TurnRequest.create(c,emptyList(),"offline",AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(),catalog(),now))
            val hash=store.freeze("scope","c","one-key",first)
            val next=AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime("new"),catalog("new"),now)
            assertEquals("new",next.model)
            assertEquals(first,store.read("scope","c","one-key",hash))
            assertTrue(runCatching {store.freeze("scope","c","one-key",TurnRequest.create(c,emptyList(),"offline",next))}.isFailure)
        } finally {check(root.canonicalFile.parentFile==java.io.File(System.getProperty("java.io.tmpdir")).canonicalFile);root.deleteRecursively()}
    }
    @Test fun legacyServersRemainServerManagedWithoutInventedModelOrProvider()=runBlocking {
        val calls=mutableListOf<DiscoveryResource>()
        val api=object:FakeHermesApiClient() {override suspend fun discovery(resource:DiscoveryResource,refreshModels:Boolean):JsonObject {calls+=resource;error("No extension advertised")}}
        val result=AutomaticRuntime.beforeNewTurn(RuntimeChoice.automatic(),caps(false),api,{true},{now})
        assertTrue(result.choice.isDefault);assertTrue(result.choice.fields().isEmpty());assertTrue(calls.isEmpty())
    }
    @Test fun scopeChangeAfterTheFirstReadCannotReadAnotherCatalogOrFreezeAChoice()=runBlocking {
        var same=true;val calls=mutableListOf<DiscoveryResource>()
        val api=object:FakeHermesApiClient() {override suspend fun discovery(resource:DiscoveryResource,refreshModels:Boolean):JsonObject {calls+=resource;same=false;return runtime()}}
        assertTrue(runCatching {AutomaticRuntime.beforeNewTurn(RuntimeChoice.automatic(),caps(),api,{same},{now})}.exceptionOrNull() is CancellationException)
        assertEquals(listOf(DiscoveryResource.RuntimeModels),calls)
    }
    @Test fun manualSelectionsDoNotTriggerAutomaticDiscovery()=runBlocking {
        val chosen=RuntimeChoice("manual","explicit")
        val api=object:FakeHermesApiClient() {override suspend fun discovery(resource:DiscoveryResource,refreshModels:Boolean):JsonObject=error("Unexpected discovery")}
        assertEquals(chosen,AutomaticRuntime.beforeNewTurn(chosen,caps(),api,{true},{now}).choice)
    }
    @Test fun metadataReadsAreConservativeAndNeverRequestAFullProviderProbe()=runBlocking {
        val calls=mutableListOf<Pair<DiscoveryResource,Boolean>>()
        val api=object:FakeHermesApiClient() {override suspend fun discovery(resource:DiscoveryResource,refreshModels:Boolean):JsonObject {
            calls+=resource to refreshModels;return if(resource==DiscoveryResource.RuntimeModels) runtime() else catalog()
        }}
        val result=AutomaticRuntime.beforeNewTurn(RuntimeChoice.automatic(),caps(),api,{true},{now})
        assertEquals(RuntimeChoice("active","local"),result.choice)
        assertEquals(listOf(DiscoveryResource.RuntimeModels to false,DiscoveryResource.Models to false),calls)
    }
    @Test fun localModelsOfferHermesReasoningPresetsAndNoInventedServiceTier() {
        val p=RuntimePresets.forModel(ModelInventory.parse(catalog()),"active","local")
        assertEquals(listOf("minimal","low","medium","high","xhigh","max","ultra"),p.efforts)
        assertTrue(p.reasoning);assertTrue(p.canDisable);assertTrue(p.tiers.isEmpty())
    }
    @Test fun tierAndMandatoryReasoningMetadataControlTheirPickers() {
        val j=catalog();val p=j["providers"]!!.jsonArray.single().jsonObject
        val changed=JsonObject(j+("providers" to buildJsonArray {add(JsonObject(p+("capabilities" to buildJsonObject {
            put("active",buildJsonObject {put("reasoning",true);put("can_disable_reasoning",false);put("fast",true);put("ultrafast",true);put("daybreak",true)})
        })))}))
        val options=RuntimePresets.forModel(ModelInventory.parse(changed),"active","local")
        assertFalse(options.canDisable);assertEquals(listOf("priority","ultrafast","daybreak"),options.tiers)
        assertFalse(options.permits(RuntimeChoice(reasoning=false)));assertTrue(options.permits(RuntimeChoice(serviceTier="priority")))
    }

    @Test fun duplicateProviderRowsAndUntypedAuthenticationCannotAuthorizeAutomaticSelection() {
        val j=catalog();val provider=j["providers"]!!.jsonArray.single().jsonObject
        val duplicate=JsonObject(j+("providers" to JsonArray(listOf(provider,provider))))
        refused {AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(),duplicate,now)}
        val untyped=JsonObject(j+("providers" to JsonArray(listOf(JsonObject(provider+("authenticated" to JsonPrimitive("true")))))))
        refused {AutomaticRuntime.resolve(RuntimeChoice.automatic(),runtime(),untyped,now)}
    }
    @Test fun automaticOptionsRoundTripLocallyWithoutSavingTheObservedModel() {
        val choice=RuntimeChoice(reasoning=true,effort="high",followServing=true)
        val stored=choice.storedFields()
        assertEquals(choice,RuntimeChoice.saved(JsonObject(mapOf("scope" to stored)).toString(),"scope"))
        assertFalse(stored.containsKey("model"));assertFalse(stored.containsKey("provider"))
        assertEquals(JsonPrimitive("serving"),stored["_signal_grove_model_mode"])
    }
}
