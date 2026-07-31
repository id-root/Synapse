package com.synapse.lantransfer.data.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.synapse.lantransfer.data.local.PreferencesManager
import com.synapse.lantransfer.data.local.TransferDatabase
import com.synapse.lantransfer.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Orchestrates file transfers — coordinates LanService, DiscoveryService,
 * database logging, and state management.
 *
 * Exposes a single [transferState] flow that the UI observes.
 */
class TransferManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "TransferManager"

        @Volatile
        private var INSTANCE: TransferManager? = null

        fun getInstance(context: Context): TransferManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TransferManager(context.applicationContext).also { INSTANCE = it }
            }
        }
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
    /** Guards all _transferState write operations to prevent TOCTOU races. */
    private val stateMutex = Mutex()

    // ======================== SEND ========================

    /**
     * Start broadcasting files to the LAN.
     * Sets up the TLS server and registers the mDNS service.
     */
    suspend fun startSending(uris: List<Uri>, deviceName: String, isOneOff: Boolean = false): Int {
        if (senderJob?.isActive == true) {
            Log.w(TAG, "Already sending")
            return ((_transferState.value as? TransferState.Sending)?.port ?: 0)
        }

        _transferState.value = TransferState.Preparing
        delay(800)

        val session = lanService.startSender(
            uris = uris,
            onProgress = { progress ->
                scope.launch {
                    stateMutex.withLock {
                        _transferState.value = TransferState.Sending(
                            port = senderSession?.port ?: 0,
                            progress = progress
                        )
                    }
                }
            },
            onPeerConnected = { addr ->
                Log.d(TAG, "Peer connected: $addr")
            },
            onComplete = { fileName, peerName, fileSize ->
                scope.launch {
                    // Log to history
                    dao.insert(
                        TransferRecord(
                            fileName = fileName,
                            fileSize = fileSize,
                            direction = TransferDirection.SEND,
                            status = TransferStatus.COMPLETED,
                            peerName = peerName,
                            peerAddress = ""
                        )
                    )
                    stateMutex.withLock { _transferState.value = TransferState.Completed(fileName) }
                    delay(3000)
                    
                    if (isOneOff) {
                        stopSending()
                    } else {
                        stateMutex.withLock {
                            _transferState.update { currentState ->
                                if (currentState is TransferState.Completed) TransferState.Idle else currentState
                            }
                        }
                    }
                }
            },
            onError = { error ->
                scope.launch {
                    stateMutex.withLock { _transferState.value = TransferState.Error(error) }
                    delay(3000)
                    stateMutex.withLock {
                        _transferState.update { currentState ->
                            if (currentState is TransferState.Error) TransferState.Idle else currentState
                        }
                    }
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
                        scope.launch {
                            stateMutex.withLock {
                                _transferState.value = TransferState.Receiving(
                                    peerAddress = peer.fullAddress,
                                    progress = progress
                                )
                            }
                        }
                    },
                    onComplete = { fileName, fileSize ->
                        scope.launch {
                            dao.insert(
                                TransferRecord(
                                    fileName = fileName,
                                    fileSize = fileSize,
                                    direction = TransferDirection.RECEIVE,
                                    status = TransferStatus.COMPLETED,
                                    peerName = peer.name,
                                    peerAddress = peer.fullAddress
                                )
                            )
                            stateMutex.withLock { _transferState.value = TransferState.Completed(fileName) }
                            delay(3000)
                            stateMutex.withLock {
                                _transferState.update { currentState ->
                                    if (currentState is TransferState.Completed) TransferState.Idle else currentState
                                }
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
                            stateMutex.withLock { _transferState.value = TransferState.Error(error) }
                            delay(3000)
                            stateMutex.withLock {
                                _transferState.update { currentState ->
                                    if (currentState is TransferState.Error) TransferState.Idle else currentState
                                }
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Receive failed: ${e.message}", e)
                _transferState.value = TransferState.Error(e.message ?: "Receive failed")
                delay(3000)
                _transferState.update { currentState ->
                    if (currentState is TransferState.Error) TransferState.Idle else currentState
                }
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

    fun startDiscovery() {
        discoveryService.startDiscovery()
        if (_transferState.value is TransferState.Idle) {
            _transferState.value = TransferState.Discovering
        }
    }
    
    fun stopDiscovery() {
        discoveryService.stopDiscovery()
        if (_transferState.value is TransferState.Discovering) {
            _transferState.value = TransferState.Idle
        }
    }

    // ======================== CLEANUP ========================

    fun destroy() {
        stopSending()
        cancelReceive()
        discoveryService.destroy()
    }
}
