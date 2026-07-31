package com.synapse.lantransfer.data.model

/**
 * Represents a discovered peer on the local network via mDNS/NSD.
 */
data class Peer(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
    val discoveredAt: Long = System.currentTimeMillis()
) {
    /** Returns host:port format for TCP connections */
    val fullAddress: String get() = "$address:$port"
}
