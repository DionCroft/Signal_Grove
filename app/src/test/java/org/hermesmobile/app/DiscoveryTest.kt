package org.hermesmobile.app

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class DiscoveryTest {
    @Test fun discoveryRetainsProfilePrefixAndBearerAndOnlyExplicitlyRefreshesModels()=runBlocking {
        MockWebServer().use {server ->
            val api=OkHttpHermesApiClient(ServerConfig(server.url("p/work/").toString(),"fixture",true),true)
            for(resource in DiscoveryResource.entries) {
                server.enqueue(MockResponse().setBody("{}"));api.discovery(resource)
                val request=server.takeRequest();assertEquals("/p/work/"+resource.path,request.path)
                assertEquals("GET",request.method);assertEquals("Bearer fixture",request.getHeader("Authorization"))
            }
            server.enqueue(MockResponse().setBody("{}"));api.discovery(DiscoveryResource.Models,true)
            assertEquals("/p/work/api/model/options?refresh=1",server.takeRequest().path)
        }
    }
    @Test fun readinessUsesThePayloadAndInventoryKeepsUnauthenticatedProvidersVisible() {
        assertEquals("degraded",readinessStatus(Json.parseToJsonElement("""{"status":"degraded","readiness":{"status":"degraded"}}""").jsonObject))
        assertEquals("unknown",readinessStatus(JsonObject(emptyMap())))
        val inventory=ModelInventory.parse(Json.parseToJsonElement("""{"model":"saved","provider":"p","providers":[{"slug":"p","name":"Provider","authenticated":false,"models":["saved","saved"],"warning":"Sign in on Hermes"}]}""").jsonObject)
        assertEquals("saved",inventory.model);assertEquals(false,inventory.providers.single().authenticated)
        assertEquals(listOf("saved"),inventory.providers.single().models)
    }
}
