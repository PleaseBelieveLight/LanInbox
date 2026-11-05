package com.luofelix.laninbox

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import com.luofelix.laninbox.core.network.LAN_SERVICE_TYPE
import com.luofelix.laninbox.core.network.SECURE_PORT
import com.luofelix.laninbox.core.network.isFingerprint
import com.luofelix.laninbox.core.network.privateLanAuthority
import java.net.Inet4Address
import kotlin.coroutines.resume
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

internal data class DiscoveredDesktop(val name: String, val address: String, val fingerprint: String)

@Suppress("DEPRECATION")
internal fun discoverDesktops(context: Context) = callbackFlow {
    val manager = context.getSystemService(NsdManager::class.java)
    if (manager == null) {
        trySend(emptyList<DiscoveredDesktop>())
        close()
        return@callbackFlow
    }
    val events = Channel<Pair<Boolean, NsdServiceInfo>>(32)
    val entries = mutableMapOf<String, DiscoveredDesktop>()
    val worker = launch {
        for ((found, service) in events) {
            val name = service.serviceName
            if (found) {
                val resolved = withTimeoutOrNull(4_000) { resolveDesktop(manager, service) }
                if (resolved != null) entries[name] = resolved
            } else {
                entries.remove(name)
            }
            trySend(entries.values.sortedBy { it.name })
        }
    }
    val listener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(type: String) = Unit
        override fun onDiscoveryStopped(type: String) = Unit
        override fun onStartDiscoveryFailed(type: String, error: Int) { close() }
        override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
        override fun onServiceFound(service: NsdServiceInfo) { events.trySend(true to service) }
        override fun onServiceLost(service: NsdServiceInfo) { events.trySend(false to service) }
    }
    trySend(emptyList<DiscoveredDesktop>())
    // Older Wi-Fi stacks filter multicast unless a foreground client holds this lock.
    val wifiLock = if (Build.VERSION.SDK_INT <= 33) {
        runCatching {
            context.applicationContext.getSystemService(WifiManager::class.java)
                ?.createMulticastLock("LanInbox discovery")?.apply { acquire() }
        }.getOrNull()
    } else null
    try {
        manager.discoverServices(LAN_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { runCatching { manager.stopServiceDiscovery(listener) } }
    } finally {
        runCatching { wifiLock?.release() }
        events.close()
        worker.cancel()
    }
}

@Suppress("DEPRECATION")
private suspend fun resolveDesktop(manager: NsdManager, service: NsdServiceInfo): DiscoveredDesktop? =
    suspendCancellableCoroutine { result ->
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, error: Int) { result.resume(null) }
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = (info.host as? Inet4Address)?.hostAddress
                val address = host?.let { privateLanAuthority("$it:${info.port}", SECURE_PORT) }
                val pin = info.attributes["pin"]?.decodeToString()?.takeIf(::isFingerprint)
                result.resume(if (address != null && pin != null) DiscoveredDesktop(info.serviceName, address, pin) else null)
            }
        }
        try {
            manager.resolveService(service, listener)
        } catch (_: RuntimeException) {
            result.resume(null)
        }
    }
