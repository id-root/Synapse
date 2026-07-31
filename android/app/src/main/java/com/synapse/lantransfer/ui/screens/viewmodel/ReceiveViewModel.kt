package com.synapse.lantransfer.ui.screens.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.lantransfer.data.model.Peer
import com.synapse.lantransfer.data.model.TransferState
import com.synapse.lantransfer.data.service.BackgroundTransferService
import com.synapse.lantransfer.data.service.TransferManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Receive screen.
 * Manages peer discovery, connection, and receive progress.
 */
class ReceiveViewModel(application: Application) : AndroidViewModel(application) {

    private val transferManager = TransferManager.getInstance(application)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _hasScanned = MutableStateFlow(false)
    val hasScanned: StateFlow<Boolean> = _hasScanned.asStateFlow()

    val discoveredPeers: StateFlow<List<Peer>> =
        transferManager.discoveryService.discoveredPeers

    private val _connectingTo = MutableStateFlow<String?>(null)
    val connectingTo: StateFlow<String?> = _connectingTo.asStateFlow()

    val transferState: StateFlow<TransferState> = transferManager.transferState

    /**
     * Start scanning for Synapse peers on the LAN via mDNS.
     */
    fun startScan() {
        if (_isScanning.value) return

        _isScanning.value = true
        _hasScanned.value = false

        transferManager.startDiscovery()

        // Auto-stop scanning after a timeout
        viewModelScope.launch {
            delay(5000)
            if (_isScanning.value) {
                stopScan()
            }
        }
    }

    /**
     * Stop scanning for peers.
     */
    fun stopScan() {
        transferManager.stopDiscovery()
        _isScanning.value = false
        _hasScanned.value = true
    }

    /**
     * Connect to a discovered peer and start receiving files.
     */
    fun connectToPeer(peer: Peer) {
        if (_connectingTo.value != null) return

        _connectingTo.value = peer.fullAddress

        BackgroundTransferService.start(
            getApplication()
        )

        transferManager.startReceiving(peer)

        // Watch for completion to reset connecting state without leaking coroutines
        viewModelScope.launch {
            transferManager.transferState.first { state ->
                state is TransferState.Completed || state is TransferState.Error || state is TransferState.Idle
            }
            _connectingTo.value = null
            BackgroundTransferService.stop(getApplication())
        }
    }

    /**
     * Cancel the current receive operation.
     */
    fun cancelReceive() {
        transferManager.cancelReceive()
        _connectingTo.value = null
        BackgroundTransferService.stop(getApplication())
    }

    private val prefs = com.synapse.lantransfer.data.local.PreferencesManager(application)
    val autoAccept: StateFlow<Boolean> = prefs.autoAccept.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    private val _pendingPeerRequest = MutableStateFlow<Peer?>(null)
    val pendingPeerRequest: StateFlow<Peer?> = _pendingPeerRequest.asStateFlow()

    fun requestAccept(peer: Peer) {
        _pendingPeerRequest.value = peer
    }

    fun confirmAccept() {
        val peer = _pendingPeerRequest.value
        _pendingPeerRequest.value = null
        if (peer != null) {
            connectToPeer(peer)
        }
    }

    fun declineAccept() {
        _pendingPeerRequest.value = null
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        cancelReceive()
    }
}
