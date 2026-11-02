package org.hermesmobile.app

import kotlinx.serialization.json.*
import org.hermesmobile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class RuntimeChoiceTest {
    @Test fun overridesAndOptionsAppearOnEverySupportedTransport() {
        val choice=RuntimeChoice("chosen","provider",false,serviceTier="priority")
        for(transport in listOf("runs","session","compat")) {
            val c=Conversation("c","scope","api",pendingInput="Review",transport=transport)
            val body=TurnRequest.create(c,listOf(ChatMessage("u","c","user","Review")),"server-default",choice).body
            assertEquals("chosen",body.text("model"));assertEquals("provider",body.text("provider"))
            assertEquals(choice.fields()["model_options"],body["model_options"])
        }
    }
    @Test fun defaultDoesNotInventOverridesAndPerConnectionChoicesAreIsolated() {
        assertTrue(RuntimeChoice().fields().isEmpty())
        val chosen=RuntimeChoice("m","p",true,"high")
        val map=JsonObject(mapOf("a" to chosen.fields())).toString()
        assertEquals(chosen,RuntimeChoice.saved(map,"a"));assertEquals(RuntimeChoice(),RuntimeChoice.saved(map,"b"))
        assertEquals(RuntimeChoice(),RuntimeChoice.saved("broken","a"))
    }
    @Test fun malformedIdsAndContradictoryOptionsAreRefusedBeforeSending() {
        for(choice in listOf(RuntimeChoice("m\n"),RuntimeChoice("m",provider="p".repeat(81)),RuntimeChoice("m".repeat(257)),RuntimeChoice(provider="p"),RuntimeChoice("m",reasoning=false,effort="high"))) {
            try {choice.fields();fail("Invalid runtime choice")} catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun catalogAccountRestrictionsSurviveParsing() {
        val inventory=ModelInventory.parse(Json.parseToJsonElement("""{"providers":[{"slug":"nous","authenticated":true,"models":["free","paid"],"unavailable_models":["paid"]}]}""").jsonObject)
        assertEquals(setOf("paid"),inventory.providers.single().unavailableModels)
    }
}
