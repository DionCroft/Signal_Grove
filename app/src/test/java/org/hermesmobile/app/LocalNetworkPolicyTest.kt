package org.hermesmobile.app

import java.net.InetAddress
import okhttp3.Dns
import org.hermesmobile.app.network.*
import org.junit.Assert.*
import org.junit.Test

class LocalNetworkPolicyTest {
    @Test fun deniedPermissionBlocksLiteralLanAndPrivateDnsAnswersBeforeConnection() {
        val denied=PermissionAwareDns({false},Dns {listOf(InetAddress.getByName("192.168.20.4"))})
        listOf("192.168.1.2","10.2.3.4","172.20.0.1","169.254.2.3","100.64.0.1","fd00::1","fe80::1","hermes.local").forEach {host ->
            assertThrows(LocalNetworkPermissionException::class.java) {denied.checkHost(host)}
        }
        assertThrows(LocalNetworkPermissionException::class.java) {denied.lookup("private.example")}
    }
    @Test fun publicAndLoopbackConnectionsRemainAvailableAndGrantIsReadForEachLookup() {
        var granted=false
        val dns=PermissionAwareDns({granted},Dns {listOf(InetAddress.getByName("10.0.0.2"))})
        listOf("9.9.9.9","2606:4700:4700::1111","127.0.0.1","::1","public.example").forEach {dns.checkHost(it)}
        val public=PermissionAwareDns({false},Dns {listOf(InetAddress.getByName("9.9.9.9"))})
        assertEquals(1,public.lookup("public.example").size)
        granted=true;assertEquals(1,dns.lookup("private.example").size)
        granted=false;assertThrows(LocalNetworkPermissionException::class.java) {dns.lookup("private.example")}
        assertTrue(connectionError(LocalNetworkPermissionException()).contains("Settings"))
    }
}
