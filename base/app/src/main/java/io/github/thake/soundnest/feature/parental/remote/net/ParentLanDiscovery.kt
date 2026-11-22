/* SPDX-License-Identifier: GPL-3.0-only */
package io.github.thake.soundnest.feature.parental.remote.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import io.github.thake.soundnest.feature.parental.remote.settings.PairedDevicesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address

data class DiscoveredChildEndpoint(
    val serviceName: String,
    val childId: String,
    val deviceName: String,
    val host: String,
    val port: Int,
    val pairingOpen: Boolean,
)

/**
 * mDNS (`_soundnest-ctrl._tcp.`) discovery used by the Parent Device to locate Child Devices
 * on the same Wi-Fi network and keep paired child IP/port endpoints fresh.
 */
object ParentLanDiscovery {
    private val main = Handler(Looper.getMainLooper())
    private var nsd: NsdManager? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var appContext: Context? = null
    private var users = 0
    private val waiting = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    private val _discovered = MutableStateFlow<Map<String, DiscoveredChildEndpoint>>(emptyMap())
    val discovered: StateFlow<Map<String, DiscoveredChildEndpoint>> = _discovered.asStateFlow()

    fun start(context: Context) {
        if (users++ > 0) return
        val app = context.applicationContext
        appContext = app
        val m = runCatching { app.getSystemService(Context.NSD_SERVICE) as? NsdManager }.getOrNull()
        if (m == null) {
            users = 0
            return
        }
        nsd = m
        _discovered.value = emptyMap()
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                main.post {
                    if (listener === this) {
                        listener = null
                        users = 0
                    }
                }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post {
                    waiting.addLast(info)
                    resolveNext()
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post {
                    _discovered.value = _discovered.value.filterValues { it.serviceName != info.serviceName }
                }
            }
        }
        listener = l
        runCatching {
            m.discoverServices(RemoteProtocol.MDNS_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l)
        }.onFailure {
            listener = null
            users = 0
        }
    }

    fun stop() {
        if (users == 0 || --users > 0) return
        listener?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        listener = null
        waiting.clear()
        resolving = false
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving || listener == null) return
        val info = waiting.removeFirstOrNull() ?: return
        val m = nsd ?: return
        resolving = true
        val done = { main.post { resolving = false; resolveNext() }; Unit }
        runCatching {
            m.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = done()
                override fun onServiceResolved(resolved: NsdServiceInfo) {
                    onResolved(resolved)
                    done()
                }
            })
        }.onFailure { done() }
    }

    @Suppress("DEPRECATION")
    private fun onResolved(s: NsdServiceInfo) {
        val addrs = if (Build.VERSION.SDK_INT >= 34) s.hostAddresses else listOfNotNull(s.host)
        val ipv4 = addrs.filterIsInstance<Inet4Address>().firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?: return
        val host = ipv4.hostAddress ?: return
        val childId = s.attributes["id"]?.let { String(it, Charsets.UTF_8) }?.trim().orEmpty()
        if (childId.isEmpty()) return
        val name = s.attributes["name"]?.let { String(it, Charsets.UTF_8) }?.trim().orEmpty().ifEmpty { s.serviceName }
        val pairOpen = s.attributes["pair"]?.let { String(it, Charsets.UTF_8) } == "1"
        val endpoint = DiscoveredChildEndpoint(
            serviceName = s.serviceName,
            childId = childId,
            deviceName = name,
            host = host,
            port = s.port,
            pairingOpen = pairOpen,
        )
        main.post {
            _discovered.value = _discovered.value + (childId to endpoint)
            appContext?.let { ctx ->
                PairedDevicesStore.updateChildEndpoint(ctx, childId, host, s.port, name)
            }
        }
    }
}
