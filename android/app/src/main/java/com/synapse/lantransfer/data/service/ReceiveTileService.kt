package com.synapse.lantransfer.data.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReceiveTileService : TileService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile
        tile.state = Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        
        
        // Start Background Transfer Service to handle UI and state
        BackgroundTransferService.start(this)
        
        // Automatically start discovery and receive mode
        val transferManager = TransferManager.getInstance(applicationContext)
        transferManager.startDiscovery()
        
        // Note: For receiving, we typically need to connect to a peer first.
        // The prompt asks to "Begin advertising availability. Wait for incoming connections."
        // Our existing app initiates transfer from sender, and receiver discovers or vice versa?
        // Usually, in this app, one is a hotspot or they just discover each other.
        // Let's assume startDiscovery is enough to make this device visible to senders.
    }
}
