package com.synapse.lantransfer.data.service

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import com.synapse.lantransfer.data.model.Peer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Handles mDNS/NSD service discovery and registration on the local network.
 * Uses Android's NsdManager which implements mDNS under the hood.
 *
 * Service type: "_synapse._tcp." — matches the Go backend's zeroconf registration.
 */
class DiscoveryService(private val context: Context) {

    companion object {
        private const val TAG = "DiscoveryService"
        const val SERVICE_TYPE = "_synapse._tcp."
        const val SERVICE_NAME_PREFIX = "synapse-"
    }

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    private val _discoveredPeers = MutableStateFlow<List<Peer>>(emptyList())
    val discoveredPeers: StateFlow<List<Peer>> = _discoveredPeers.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val peerMap = mutableMapOf<String, Peer>()

    /**
     * Start discovering Synapse peers on the local network.
     * Acquires a multicast lock to receive mDNS packets.
     */
    fun startDiscovery() {
        if (_isDiscovering.value) {
            Log.w(TAG, "Discovery already in progress")
            return
        }

        // Acquire multicast lock so we receive mDNS packets
        multicastLock = wifiManager.createMulticastLock("synapse_discovery").apply {
            setReferenceCounted(true)
            acquire()
        }
        Log.d(TAG, "Multicast lock acquired")

        peerMap.clear()
        _discoveredPeers.value = emptyList()

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                Log.d(TAG, "Discovery started for $serviceType")
                _isDiscovering.value = true
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${serviceInfo.serviceName}")
                // Resolve the service to get IP and port
                resolveService(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${serviceInfo.serviceName}")
                peerMap.remove(serviceInfo.serviceName)
                _discoveredPeers.value = peerMap.values.toList()
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "Discovery stopped")
                _isDiscovering.value = false
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: error $errorCode")
                _isDiscovering.value = false
                releaseMulticastLock()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery stop failed: error $errorCode")
            }
        }

        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun resolveService(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Resolve failed for ${si.serviceName}: error $errorCode")
            }

            override fun onServiceResolved(si: NsdServiceInfo) {
                val host = si.host?.hostAddress ?: return
                val port = si.port
                val name = si.serviceName
                    .removePrefix(SERVICE_NAME_PREFIX)
                    .removeSuffix("-synapse") // Go format: "hostname-synapse"
                    .ifEmpty { si.serviceName }

                Log.d(TAG, "Resolved: $name @ $host:$port")

                val peer = Peer(
                    id = "${host}:${port}",
                    name = name,
                    address = host,
                    port = port
                )
                peerMap[si.serviceName] = peer
                _discoveredPeers.value = peerMap.values.toList()
            }
        })
    }

    /**
     * Stop discovering peers and release resources.
     */
    fun stopDiscovery() {
        try {
            discoveryListener?.let {
                nsdManager.stopServiceDiscovery(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping discovery: ${e.message}")
        }
        discoveryListener = null
        releaseMulticastLock()
        _isDiscovering.value = false
    }

    /**
     * Register this device as a Synapse sender on the network.
     * Other devices running startDiscovery() will find it.
     */
    fun registerService(port: Int, deviceName: String) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "$deviceName-synapse"
            serviceType = SERVICE_TYPE
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(si: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${si.serviceName} on port $port")
            }

            override fun onRegistrationFailed(si: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: error $errorCode")
            }

            override fun onServiceUnregistered(si: NsdServiceInfo) {
                Log.d(TAG, "Service unregistered: ${si.serviceName}")
            }

            override fun onUnregistrationFailed(si: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Unregistration failed: error $errorCode")
            }
        }

        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
    }

    /**
     * Unregister this device's Synapse service from the network.
     */
    fun unregisterService() {
        try {
            registrationListener?.let {
                nsdManager.unregisterService(it)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering service: ${e.message}")
        }
        registrationListener = null
    }

    private fun releaseMulticastLock() {
        try {
            multicastLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing multicast lock: ${e.message}")
        }
        multicastLock = null
    }

    /**
     * Clean up all resources. Call when the service is no longer needed.
     */
    fun destroy() {
        stopDiscovery()
        unregisterService()
    }
}
