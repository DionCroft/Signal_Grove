package org.hermesmobile.app.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import okhttp3.Dns
import java.io.IOException
import java.net.InetAddress

class LocalNetworkPermissionException : IOException("Allow local network access in Settings to reach this server.")
object LocalNetworkAccess {
    const val PERMISSION="android.permission.ACCESS_LOCAL_NETWORK"
    fun granted(context:Context)=Build.VERSION.SDK_INT<37 || ContextCompat.checkSelfPermission(context,PERMISSION)==PackageManager.PERMISSION_GRANTED
}
/** Loopback is process/device-local; broad LAN permission is only needed for LAN routes. */
class PermissionAwareDns(private val allowed:()->Boolean,private val delegate:Dns=Dns.SYSTEM):Dns {
    override fun lookup(hostname:String):List<InetAddress> {
        checkHost(hostname)
        return delegate.lookup(hostname).also {addresses -> if(!allowed() && addresses.any(::isLocalRoute)) throw LocalNetworkPermissionException()}
    }
    fun checkHost(host:String) {
        if(allowed()) return
        if(host.endsWith(".local",ignoreCase=true) || host.equals("local",ignoreCase=true)) throw LocalNetworkPermissionException()
        // Numeric addresses bypass OkHttp DNS. Never resolve arbitrary names on the caller thread.
        if(host.contains(':') || host.all {it.isDigit() || it=='.'}) {
            val address=runCatching {InetAddress.getByName(host)}.getOrNull()
            if(address!=null && isLocalRoute(address)) throw LocalNetworkPermissionException()
        }
    }
    companion object {
        fun isLocalRoute(address:InetAddress):Boolean {
            if(address.isLoopbackAddress) return false
            val bytes=address.address
            val first=bytes[0].toInt() and 255
            return address.isSiteLocalAddress || address.isLinkLocalAddress || address.isMulticastAddress || address.isAnyLocalAddress ||
                (bytes.size==16 && first and 254==252) ||
                (bytes.size==4 && first==100 && (bytes[1].toInt() and 255) in 64..127)
        }
    }
}
