package com.synapse.lantransfer.data.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.synapse.lantransfer.data.local.PreferencesManager
import com.synapse.lantransfer.data.local.TransferDatabase
import com.synapse.lantransfer.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * Orchestrates file transfers — coordinates LanService, DiscoveryService,
 * database logging, and state management.
 *
 * Exposes a single [transferState] flow that the UI observes.
 */
class TransferManager(private val context: Context) {

    companion object {
        private const val TAG = "TransferManager"
    }

    private val lanService = LanService(context)
    val discoveryService = DiscoveryService(context)
    private val prefs = PreferencesManager(context)
    private val db = TransferDatabase.getInstance(context)
    private val dao = db.transferDao()

    private val _transferState = MutableStateFlow<TransferState>(TransferState.Idle)
    val transferState: StateFlow<TransferState> = _transferState.asStateFlow()

    private var senderSession: SenderSession? = null
    private var senderJob: Job? = null
    private var receiverJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ======================== SEND ========================

    /**
     * Start broadcasting files to the LAN.
     * Sets up the TLS server and registers the mDNS service.
     */
    suspend fun startSending(uris: List<Uri>, deviceName: String): Int {
        if (_transferState.value is TransferState.Sending) {
            Log.w(TAG, "Already sending")
            return ((_transferState.value as? TransferState.Sending)?.port ?: 0)
        }

        val session = lanService.startSender(
            uris = uris,
            onProgress = { progress ->
                _transferState.value = TransferState.Sending(
                    port = senderSession?.port ?: 0,
                    progress = progress
                )
            },
            onPeerConnected = { addr ->
                Log.d(TAG, "Peer connected: $addr")
            },
            onComplete = { fileName, peerName ->
                scope.launch {
                    // Log to history
                    dao.insert(
                        TransferRecord(
                            fileName = fileName,
                            fileSize = 0, // Will be updated
                            direction = TransferDirection.SEND,
                            status = TransferStatus.COMPLETED,
                            peerName = peerName,
                            peerAddress = ""
                        )
                    )
                    _transferState.value = TransferState.Completed(fileName)
                    delay(3000)
                    if (_transferState.value is TransferState.Completed) {
                        _transferState.value = TransferState.Idle
                    }
                }
            },
            onError = { error ->
                scope.launch {
                    _transferState.value = TransferState.Error(error)
                    delay(3000)
                    _transferState.value = TransferState.Idle
                }
            }
        )

        senderSession = session
        _transferState.value = TransferState.Sending(port = session.port)

        // Register mDNS so peers can discover us
        discoveryService.registerService(session.port, deviceName)

        // Start accept loop in background
        senderJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    session.acceptAndTransfer()
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Accept loop error: ${e.message}")
                    }
                    break
                }
            }
        }

        return session.port
    }

    /**
     * Stop broadcasting and close the sender.
     */
    fun stopSending() {
        senderJob?.cancel()
        senderJob = null
        senderSession?.stop()
        senderSession = null
        discoveryService.unregisterService()
        _transferState.value = TransferState.Idle
    }

    // ======================== RECEIVE ========================

    /**
     * Connect to a discovered peer and download their shared files.
     */
    fun startReceiving(peer: Peer) {
        if (receiverJob?.isActive == true) {
            Log.w(TAG, "Already receiving")
            return
        }

        _transferState.value = TransferState.Receiving(peerAddress = peer.fullAddress)

        receiverJob = scope.launch {
            val downloadDir = prefs.downloadDir.first()
            val deviceName = prefs.deviceName.first()

            try {
                lanService.receiveFrom(
                    address = peer.address,
                    port = peer.port,
                    downloadDir = downloadDir,
                    deviceName = deviceName,
                    onProgress = { progress ->
                        _transferState.value = TransferState.Receiving(
                            peerAddress = peer.fullAddress,
                            progress = progress
                        )
                    },
                    onComplete = { fileName ->
                        scope.launch {
                            dao.insert(
                                TransferRecord(
                                    fileName = fileName,
                                    fileSize = 0,
                                    direction = TransferDirection.RECEIVE,
                                    status = TransferStatus.COMPLETED,
                                    peerName = peer.name,
                                    peerAddress = peer.fullAddress
                                )
                            )
                            _transferState.value = TransferState.Completed(fileName)
                            delay(3000)
                            if (_transferState.value is TransferState.Completed) {
                                _transferState.value = TransferState.Idle
                            }
                        }
                    },
                    onError = { error ->
                        scope.launch {
                            dao.insert(
                                TransferRecord(
                                    fileName = "Unknown",
                                    fileSize = 0,
                                    direction = TransferDirection.RECEIVE,
                                    status = TransferStatus.FAILED,
                                    peerName = peer.name,
                                    peerAddress = peer.fullAddress,
                                    errorMessage = error
                                )
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Receive failed: ${e.message}", e)
                _transferState.value = TransferState.Error(e.message ?: "Receive failed")
                delay(3000)
                _transferState.value = TransferState.Idle
            }
        }
    }

    /**
     * Cancel an active receive operation.
     */
    fun cancelReceive() {
        lanService.cancelReceive()
        receiverJob?.cancel()
        receiverJob = null
        _transferState.value = TransferState.Idle
    }

    // ======================== DISCOVERY ========================

    fun startDiscovery() = discoveryService.startDiscovery()
    fun stopDiscovery() = discoveryService.stopDiscovery()

    // ======================== CLEANUP ========================

    fun destroy() {
        stopSending()
        cancelReceive()
        discoveryService.destroy()
        scope.cancel()
    }
}
