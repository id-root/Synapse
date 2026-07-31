package com.synapse.lantransfer.data.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import com.synapse.lantransfer.data.model.TransferState
import com.synapse.lantransfer.ui.components.DynamicIslandOverlay
import com.synapse.lantransfer.util.NotificationManager
import com.synapse.lantransfer.util.OverlayPermissionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class BackgroundTransferService : Service() {

    companion object {
        const val ACTION_START = "com.synapse.lantransfer.ACTION_START_BACKGROUND_TRANSFER"
        const val ACTION_STOP = "com.synapse.lantransfer.ACTION_STOP_BACKGROUND_TRANSFER"

        fun start(context: Context) {
            val intent = Intent(context, BackgroundTransferService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BackgroundTransferService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private lateinit var notificationManager: NotificationManager
    private var dynamicIslandOverlay: DynamicIslandOverlay? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        notificationManager = NotificationManager(this)
        
        if (OverlayPermissionManager.hasOverlayPermission(this)) {
            dynamicIslandOverlay = DynamicIslandOverlay(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val notification = notificationManager.buildNotification(
                    "Synapse Transfer",
                    "Preparing transfer..."
                )
                startForeground(NotificationManager.NOTIFICATION_ID, notification)
                
                observeTransferState()
            }
            ACTION_STOP -> {
                stopTransfer()
            }
        }
        return START_NOT_STICKY
    }

    private fun observeTransferState() {
        val transferManager = TransferManager.getInstance(applicationContext)
        
        serviceScope.launch {
            transferManager.transferState.collectLatest { state ->
                when (state) {
                    is TransferState.Idle -> {
                        // Transfer stopped or hasn't started yet
                        dynamicIslandOverlay?.hide()
                    }
                    is TransferState.Preparing -> {
                        val title = "Synapse Transfer"
                        val text = "Preparing..."
                        notificationManager.updateNotification(title, text, null)
                        showOrUpdateIsland(state) {}
                    }
                    is TransferState.Discovering -> {
                        val title = "Synapse Transfer"
                        val text = "Scanning for peers..."
                        notificationManager.updateNotification(title, text, null)
                        showOrUpdateIsland(state) {
                            transferManager.stopDiscovery()
                            stopTransfer()
                        }
                    }
                    is TransferState.Sending -> {
                        val title = "Sending File"
                        val text = "${state.progress?.percent ?: 0}% complete"
                        notificationManager.updateNotification(title, text, state.progress?.percent)
                        
                        showOrUpdateIsland(state) {
                            transferManager.stopSending()
                            stopTransfer()
                        }
                    }
                    is TransferState.Receiving -> {
                        val title = "Receiving File"
                        val text = "${state.progress?.percent ?: 0}% complete"
                        notificationManager.updateNotification(title, text, state.progress?.percent)
                        
                        showOrUpdateIsland(state) {
                            transferManager.cancelReceive()
                            stopTransfer()
                        }
                    }
                    is TransferState.Completed -> {
                        notificationManager.updateNotification("Transfer Complete", state.fileName, 100)
                        showOrUpdateIsland(state) {}
                        
                        serviceScope.launch {
                            delay(4000) // Keep it visible for 4s
                            stopTransfer()
                        }
                    }
                    is TransferState.Error -> {
                        notificationManager.updateNotification("Transfer Failed", state.message, 0)
                        showOrUpdateIsland(state) {}
                        
                        serviceScope.launch {
                            delay(4000)
                            stopTransfer()
                        }
                    }
                }
            }
        }

        // Auto-connect to first discovered peer if triggered from Quick Settings (background mode)
        serviceScope.launch {
            transferManager.discoveryService.discoveredPeers.collectLatest { peers ->
                if (transferManager.transferState.value is TransferState.Discovering) {
                    peers.firstOrNull()?.let { peer ->
                        transferManager.stopDiscovery()
                        transferManager.startReceiving(peer)
                    }
                }
            }
        }
    }
    
    private fun showOrUpdateIsland(state: TransferState, onCancel: () -> Unit) {
        if (dynamicIslandOverlay == null && OverlayPermissionManager.hasOverlayPermission(this)) {
            dynamicIslandOverlay = DynamicIslandOverlay(this)
        }
        
        try {
            dynamicIslandOverlay?.let { overlay ->
                overlay.show(state, onCancel)
                overlay.updateState(state, onCancel)
            }
        } catch (e: Exception) {
            Log.e("BackgroundTransfer", "Failed to update overlay", e)
        }
    }

    private fun stopTransfer() {
        dynamicIslandOverlay?.hide()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        dynamicIslandOverlay?.hide()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
