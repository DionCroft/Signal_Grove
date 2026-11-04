package org.hermesmobile.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class ManagementFixtureIsolationTest {
    @get:Rule val credentials=CredentialFixtureRule()
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as HermesApplication
    @Test fun historicalOriginIsRejectedWithoutClearingPendingWork()=runBlocking {
        val (record,port)=MockWebServer().use {old ->
            old.start()
            val target=ManagementTarget("a".repeat(64),old.url("dashboard/").toString(),"worker","password · retained-fixture")
            val row=ManagementOperation(UUID.randomUUID().toString(),target.resourceScope,target.apiScope,target.url,target.profile,target.actor,"Probe","retained-fixture","{}","unknown","",1)
            app.managementReady.await();app.database.dao().insertManagementOperation(row)
            row to old.port
        }
        val attempts=AtomicInteger()
        credentials.managementServer {attempt ->attempts.incrementAndGet();if(attempt==0) port else 0}.use {fresh ->
            assertTrue(attempts.get()>=2);assertNotEquals(port,fresh.port);assertEquals(0,fresh.requestCount)
            assertEquals(record,app.database.dao().managementOperation(record.key))
            assertTrue(app.database.dao().unresolvedManagementOperations(record.resourceScope).any {it.key==record.key})
        }
        assertEquals(record,app.database.dao().managementOperation(record.key))
    }
}
