package org.hermesmobile.app.local

import org.junit.Assert.*
import org.junit.Test

class LocalSetupHelperTest {
    @Test fun observationsReduceSensitiveAddressesToEnums() {
        val observation = LocalHelpObservations.capture(false, "https://private-user:private-pass@host.invalid/profile-secret?token=hidden", true, true, 42)
        assertEquals(LocalAddressKind.HTTPS, observation.address)
        val prompt = LocalSetupHelper.messages(LocalHelpTopic.AUTH, observation).joinToString {it.text}
        for(value in listOf("private-user", "private-pass", "host.invalid", "profile-secret", "hidden")) assertFalse(prompt.contains(value))
        assertTrue(prompt.contains("42 epoch milliseconds")); assertTrue(prompt.contains("No server readiness or authentication was checked"))
    }
    @Test fun everyTopicHasBoundedVersionedChoicesWithKnownSourceAndLocation() {
        for(topic in LocalHelpTopic.entries) {
            val messages = LocalSetupHelper.messages(topic, null)
            assertEquals(listOf("system", "user"), messages.map {it.role})
            assertTrue(messages.sumOf {it.text.toByteArray().size} < 8192)
            assertTrue(messages.last().text.contains(LocalSetupHelper.VERSION))
            val choices = LocalSetupHelper.checks(topic)
            assertTrue(choices.size in 3..6); assertEquals(choices.size, choices.map {it.id}.distinct().size)
            choices.forEach {
                assertTrue(messages.last().text.contains(it.id)); assertTrue(messages.last().text.contains(it.word.en))
                assertTrue(LocalSetupHelper.sources(topic).any {source -> source.id == it.sourceId})
                assertNotEquals(LocalHelpScope.REFERENCE, it.scope)
            }
        }
    }
    @Test fun selectedIdsResolveToTheSameReviewedWordsInEveryUILanguage() {
        val prompt = LocalSetupHelper.messages(LocalHelpTopic.NETWORK, null)
        assertTrue(prompt.first().text.contains("Return a JSON array"))
        val selected = LocalSetupHelper.selection("[\"phone.tailnet\",\"host.serve\"]", LocalHelpTopic.NETWORK)!!
        selected.forEach {assertTrue(it.word.en.isNotBlank()); assertTrue(it.word.zh.isNotBlank()); assertTrue(it.word.pin.isNotBlank())}
        assertEquals(listOf("phone.tailnet", "host.serve"), selected.map {it.id})
    }
    @Test fun observationsFilterAlreadyDisabledDemoWithoutClaimingReadiness() {
        val observed = LocalHelpObservations.capture(false, "", true, true, 7)
        assertFalse(LocalSetupHelper.checks(LocalHelpTopic.FIRST_SETUP, observed).any {it.id == "phone.demo"})
        assertNull(LocalSetupHelper.selection("[\"phone.demo\"]", LocalHelpTopic.FIRST_SETUP, observed))
        assertTrue(LocalSetupHelper.checks(LocalHelpTopic.FIRST_SETUP).any {it.id == "phone.demo"})
        assertTrue(LocalSetupHelper.messages(LocalHelpTopic.FIRST_SETUP, null).last().text.contains("Server state is unknown"))
    }
    @Test fun generatedCommandsAndInventedSettingsCannotBecomeDisplayedGuidance() {
        for(text in listOf("Set API_ENABLED=true. [S1]", "Run tailscale status on both phone and host [S1]", "```json\n[\"host.api\"]\n```", "[\"host.api\"] and run a command"))
            assertNull(text, LocalSetupHelper.selection(text, LocalHelpTopic.AUTH))
        val selected = LocalSetupHelper.selection("[\"host.api\"]", LocalHelpTopic.AUTH)!!
        assertEquals("host.api", selected.single().id); assertEquals(LocalHelpScope.HERMES_HOST, selected.single().scope)
    }
    @Test fun crossTopicUnknownDuplicateOversizedAndNonStringSelectionsAreRejected() {
        for(text in listOf("[\"host.api\"]", "[\"host.serve\",\"host.serve\"]", "[]", "{}", "[5]", "[\"unknown\"]", "[\"phone.tailnet\",\"host.tailscale\",\"host.serve\",\"host.private\"]", " ".repeat(1025)))
            assertNull(text, LocalSetupHelper.selection(text, LocalHelpTopic.NETWORK))
    }
    @Test fun hostCommandsAlwaysRenderAsHostChecksAndPhoneUsesAndroidApps() {
        val network = LocalSetupHelper.checks(LocalHelpTopic.NETWORK)
        network.filter {it.word.en.contains("tailscale status") || it.word.en.contains("tailscale serve status")}.forEach {assertEquals(LocalHelpScope.HERMES_HOST, it.scope)}
        val phone = network.single {it.id == "phone.tailnet"}
        assertEquals(LocalHelpScope.PHONE_APP, phone.scope); assertTrue(phone.word.en.contains("Android Tailscale app"))
    }
    @Test fun selectionKeepsRequestedOrderAndExactReviewedObjects() {
        val allowed = LocalSetupHelper.checks(LocalHelpTopic.PROJECT).associateBy {it.id}
        val selected = LocalSetupHelper.selection(" [\"worker.project_files\", \"phone.project_details\"] ", LocalHelpTopic.PROJECT)!!
        assertEquals(listOf(allowed.getValue("worker.project_files"), allowed.getValue("phone.project_details")), selected)
        assertEquals(LocalHelpScope.EXECUTION_WORKER, selected.first().scope)
    }
}
