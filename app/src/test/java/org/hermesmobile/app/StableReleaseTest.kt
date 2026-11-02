package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class StableReleaseTest {
    @Test fun taskBackupRoundTripsUnicodeNotesDatesPriorityAndCompletionOnly() {
        val rows=listOf(LocalTask("task-one","整理计划 🌿","2026-12-01",2,true,"Lǜsè notes\nKeep privately"))
        val bytes=TaskTransfer.encode(rows)
        assertEquals(rows,TaskTransfer.decode(bytes))
        val root=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        assertEquals(setOf("format","version","tasks"),root.keys)
        assertFalse(root.containsKey("token"));assertFalse(root.containsKey("server"))
    }
    @Test fun invalidImportIsRejectedBeforeAnyRowsCanBeApplied() {
        val good=TaskTransfer.encode(listOf(LocalTask("t","Task"))).toString(Charsets.UTF_8)
        val bad=listOf(good.replace("\"version\":1","\"version\":2"),good.replace("\"priority\":1","\"priority\":7"),good.replace("\"dueDate\":\"\"","\"dueDate\":\"2026-02-30\""),"{}","[]",good.replace("\"completed\":false","\"completed\":\"false\""))
        bad.forEach {try {TaskTransfer.decode(it.toByteArray());fail(it.take(30))} catch(_:Exception) {}}
        try {TaskTransfer.decode(("[".repeat(10000)+"]".repeat(10000)).toByteArray());fail()} catch(_:IllegalArgumentException) {}
        try {TaskTransfer.decode(ByteArray(TaskTransfer.MAX_BYTES+1));fail()} catch(_:IllegalArgumentException) {}
        try {TaskTransfer.decode(byteArrayOf(0xc3.toByte(),0x28));fail()} catch(_:java.nio.charset.CharacterCodingException) {}
        try {TaskTransfer.encode(listOf(LocalTask("same","One"),LocalTask("same","Two")));fail()} catch(_:IllegalArgumentException) {}
    }
    @Test fun changedChoicesAndInvisibleOrMissingConsentCannotAuthorizeExecution() {
        fun request(prompt: String,choices: List<String>)=ControlProtocol.prompt(buildJsonObject {put("prompt_id","p");put("description",prompt);put("choices",JsonArray(choices.map {JsonPrimitive(it)}))},"r","approval",1)
        val before=request("Read file",listOf("once","deny")).stored("s")
        assertFalse(ControlProtocol.matches(before,request("Read file",listOf("deny"))))
        for(r in listOf(request("",listOf("once","deny")),request("Read file",listOf("once","deny","bad\u202Echoice")))) {
            assertTrue(r.limited)
            try {ControlProtocol.responseBody(r.stored("s"),"once");fail()} catch(_:IllegalArgumentException) {}
            assertEquals("deny",ControlProtocol.responseBody(r.stored("s"),"deny").text("choice"))
        }
    }
    @Test fun malformedBearerCharactersFailWithAGenericMessage() {
        for(token in listOf("abc\u0001secret","你好","abc secret")) {
            try {OkHttpHermesApiClient.validateUrl(ServerConfig("https://fixture.example/",token),false);fail()} catch(e:IllegalArgumentException) {assertEquals("Enter your Hermes bearer token.",e.message)}
        }
    }
}
