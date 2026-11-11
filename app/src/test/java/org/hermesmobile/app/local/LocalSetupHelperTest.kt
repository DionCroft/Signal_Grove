package org.hermesmobile.app.local

import org.junit.Assert.*
import org.junit.Test

class LocalSetupHelperTest {
    @Test fun observationsReduceSensitiveAddressesToEnums() {
        val observation = LocalHelpObservations.capture(false, "https://private-user:private-pass@host.invalid/profile-secret?token=hidden", true, true, 42)
        assertEquals(LocalAddressKind.HTTPS, observation.address)
        val prompt = LocalSetupHelper.messages(LocalHelpTopic.AUTH, observation, false).joinToString {it.text}
        for (value in listOf("private-user", "private-pass", "host.invalid", "profile-secret", "hidden")) assertFalse(prompt.contains(value))
        assertTrue(prompt.contains("42 epoch milliseconds")); assertTrue(prompt.contains("No server readiness or authentication was checked"))
    }
    @Test fun everyTopicIsBoundedVersionedAndNamesItsExactSources() {
        for (topic in LocalHelpTopic.entries) {
            val messages = LocalSetupHelper.messages(topic, null, false)
            assertEquals(listOf("system", "user"), messages.map {it.role})
            assertTrue(messages.sumOf {it.text.toByteArray().size} < 8192)
            assertTrue(messages.last().text.contains(LocalSetupHelper.VERSION))
            assertTrue(messages.last().text.contains("No phone observations were shared"))
            assertEquals(listOf("S1", "S2"), LocalSetupHelper.sources(topic).map {it.id})
            LocalSetupHelper.sources(topic).forEach {assertTrue(messages.last().text.contains(it.body.en))}
        }
    }
    @Test fun languageDoesNotAlterFactsOrSourceRevision() {
        val en = LocalSetupHelper.messages(LocalHelpTopic.NETWORK, null, false)
        val zh = LocalSetupHelper.messages(LocalHelpTopic.NETWORK, null, true)
        LocalSetupHelper.sources(LocalHelpTopic.NETWORK).forEach {assertTrue(en.last().text.contains(it.body.en)); assertTrue(zh.last().text.contains(it.body.en))}
        assertTrue(zh.last().text.contains("只用简体中文")); assertTrue(en.last().text.contains("at most 80 words"))
        assertTrue(en.first().text.endsWith("Answer in English.")); assertTrue(zh.first().text.endsWith("Answer in Simplified Chinese."))
        assertTrue(en.last().text.contains("do not enable Funnel")); assertTrue(en.first().text.contains("Never request secrets"))
    }
    @Test fun addressKindsAndMissingReadinessRemainHonest() {
        assertEquals(LocalAddressKind.UNCONFIGURED, LocalHelpObservations.capture(true, "", false, false, 0).address)
        assertEquals(LocalAddressKind.OTHER, LocalHelpObservations.capture(false, "http://host.invalid", true, false, 0).address)
        val prompt = LocalSetupHelper.messages(LocalHelpTopic.PROJECT, null, false).last().text
        assertTrue(prompt.contains("does not copy files")); assertTrue(prompt.contains("Server state is unknown"))
    }
    @Test fun formatGuardRejectsTheObservedInventedSettingAndWrongLanguage() {
        assertFalse(LocalSetupHelper.accepts("Set API_ENABLED=true. [S1]", LocalHelpTopic.AUTH, false))
        assertTrue(LocalSetupHelper.accepts("Check API_SERVER_ENABLED=true in the intended profile. [S1]", LocalHelpTopic.AUTH, false))
        assertFalse(LocalSetupHelper.accepts("Check Tailscale Serve. [S1]", LocalHelpTopic.NETWORK, true))
        assertTrue(LocalSetupHelper.accepts("检查 Tailscale Serve 并保持 TLS 验证。[S1]", LocalHelpTopic.NETWORK, true))
    }
    @Test fun unsupportedLinksCommandsAndCitationsAreWithheld() {
        for (candidate in listOf("Visit https://untrusted.invalid/ [S1]", "Run `disable_tls` [S1]", "Advice [S9]", "Advice without sources", "```sh\nanything\n``` [S1]"))
            assertFalse(candidate, LocalSetupHelper.accepts(candidate, LocalHelpTopic.NETWORK, false))
        assertTrue(LocalSetupHelper.accepts("Inspect `tailscale serve status` before changing mappings. [S1]", LocalHelpTopic.NETWORK, false))
    }
}
