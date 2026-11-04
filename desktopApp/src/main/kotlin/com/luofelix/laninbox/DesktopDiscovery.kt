package com.luofelix.laninbox

import com.luofelix.laninbox.core.logging.AppLogger
import com.luofelix.laninbox.core.network.LAN_SERVICE_TYPE
import com.luofelix.laninbox.core.network.SECURE_PORT
import com.luofelix.laninbox.core.network.privateLanAuthority
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal suspend fun advertiseDesktop(
    bindAddress: String,
    name: String,
    fingerprint: String,
    logger: AppLogger,
    onAddress: (String?) -> Unit,
) = withContext(Dispatchers.IO) {
    val active = mutableMapOf<Inet4Address, JmDNS>()
    try {
        while (true) {
            val addresses = try {
                privateAddresses().filter { bindAddress == "0.0.0.0" || it.hostAddress == bindAddress }
            } catch (_: java.net.SocketException) {
                logger.warn("Device discovery could not enumerate network interfaces")
                delay(5_000)
                continue
            }
            val removed = active.keys - addresses.toSet()
            removed.forEach { address -> active.remove(address)?.let { runCatching(it::close) } }
            for (address in addresses - active.keys) {
                val mdns = try {
                    JmDNS.create(address)
                } catch (_: Exception) {
                    logger.info("Device discovery could not bind a local interface")
                    continue
                }
                try {
                    mdns.registerService(ServiceInfo.create(
                        "${LAN_SERVICE_TYPE}local.", name, SECURE_PORT, 0, 0,
                        mapOf("pin" to fingerprint),
                    ))
                    active[address] = mdns
                } catch (_: Exception) {
                    runCatching(mdns::close)
                    logger.info("Device discovery could not publish the service")
                }
            }
            onAddress(addresses.firstOrNull()?.hostAddress)
            delay(5_000)
        }
    } finally {
        active.values.forEach { runCatching(it::close) }
    }
}

private fun privateAddresses(): List<Inet4Address> =
    NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
        .sortedBy { it.isVirtual }
        .flatMap { network -> network.inetAddresses.toList().filterIsInstance<Inet4Address>() }
        .filter { privateLanAuthority(it.hostAddress, SECURE_PORT) != null }
        .distinct()
