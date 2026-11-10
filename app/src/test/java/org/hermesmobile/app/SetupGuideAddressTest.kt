package org.hermesmobile.app

import org.hermesmobile.app.ui.SetupGuideContent
import org.junit.Assert.*
import org.junit.Test

class SetupGuideAddressTest {
    @Test fun savedAddressKeepsHttpsPortAndEncodedProfilePrefix() {
        assertEquals("https://pi.example.ts.net:8444/p/work%20profile/",SetupGuideContent.savedApi("https://pi.example.ts.net:8444/p/work%20profile/"))
        assertEquals("https://[fd7a:115c:a1e0::1]:8444/p/work/",SetupGuideContent.savedApi("https://[fd7a:115c:a1e0::1]:8444/p/work/"))
    }
    @Test fun credentialsQueryAndFragmentNeverAppearEvenWhenEmpty() {
        for(url in listOf("https://user:password@host.test/","https://@host.test/","https://host.test/?token=private","https://host.test/?","https://host.test/#private","https://host.test/#")) assertNull(url,SetupGuideContent.savedApi(url))
    }
    @Test fun untrustedDisplayInputsUseOnlyTheStaticPlaceholder() {
        for(url in listOf("","http://pi.test/","javascript:alert(1)","https://host.test/\nsecret","https://host.test/with space","https://host.test/\u202esecret","https://host.test/"+"x".repeat(2048))) {
            assertNull(SetupGuideContent.savedApi(url))
            assertEquals("https://your-host.your-tailnet.ts.net/",SetupGuideContent.example(3,false,url))
        }
    }
    @Test fun savedAddressCannotChangeHostCommandsAndExistingAgentKeepsReadInstructions() {
        for(step in listOf(0,1,2,4,8)) assertEquals(SetupGuideContent.example(step,false,""),SetupGuideContent.example(step,false,"https://pi.test/p/work/"))
        assertEquals("tailscale status\ntailscale serve status",SetupGuideContent.example(2,false,"https://pi.test/"))
        assertEquals("tailscale serve status",SetupGuideContent.example(4,false,"https://pi.test/"))
        assertFalse(SetupGuideContent.example(0,false,"")!!.contains("install.sh"))
        assertTrue(SetupGuideContent.example(0,true,"")!!.contains("hermes setup"))
    }
}
