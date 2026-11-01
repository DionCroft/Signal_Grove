package org.hermesmobile.app.network

import org.hermesmobile.app.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*

/** Explicit, deterministic demo. No production endpoint or credentials are used. */
class FakeHermesApiClient : HermesApiClient {
    override suspend fun capabilities() = HermesCapabilities("Demo", buildJsonObject { put("session_resources",true); put("session_chat",true); put("session_chat_streaming",true); put("run_stop",true) })
    override suspend fun sessions() = emptyList<RemoteSession>()
    override suspend fun createSession(id: String, title: String) = RemoteSession(id,title)
    override suspend fun messages(id: String) = emptyList<RemoteMessage>()
    override suspend fun jobs() = listOf(HermesJob("demo-job","Weekly reflection · Demo","Review completed local goals. This sample job never runs.","Every Sunday · example only","demo"))
    override suspend fun job(id: String) = jobs().first()
    override suspend fun submitRun(session: String, input: String, key: String) = "demo-run"
    override suspend fun run(id: String) = buildJsonObject { put("status","completed") }
    override suspend fun stop(id: String) = Unit
    override suspend fun approve(approval: Approval, choice: String) = Unit
    override fun stream(path: String, body: JsonObject?, key: String) = flow {
        emit(StreamUpdate(status="running",runId="demo-run"))
        val text="This is **Demo mode**. Your message stays on this device.\n\nConnect your Hermes server in Settings to start a real conversation.\n\n```text\nYour agent. Your infrastructure.\n```"
        for(chunk in text.chunked(9)) { delay(35); emit(StreamUpdate(delta=chunk)) }
        emit(StreamUpdate(status="completed"))
    }
}


/** Fail closed when real mode has no valid configuration. Never substitute demo data. */
class UnavailableHermesApiClient : HermesApiClient {
    private fun unavailable(): Nothing = throw java.io.IOException("Server is not configured")
    override suspend fun capabilities(): HermesCapabilities = unavailable()
    override suspend fun sessions(): List<RemoteSession> = unavailable()
    override suspend fun createSession(id: String, title: String): RemoteSession = unavailable()
    override suspend fun messages(id: String): List<RemoteMessage> = unavailable()
    override suspend fun jobs(): List<HermesJob> = unavailable()
    override suspend fun job(id: String): HermesJob = unavailable()
    override suspend fun submitRun(session: String, input: String, key: String): String = unavailable()
    override suspend fun run(id: String): JsonObject = unavailable()
    override suspend fun stop(id: String): Unit = unavailable()
    override suspend fun approve(approval: Approval, choice: String): Unit = unavailable()
    override fun stream(path: String, body: JsonObject?, key: String) = flow<StreamUpdate> { unavailable() }
}
