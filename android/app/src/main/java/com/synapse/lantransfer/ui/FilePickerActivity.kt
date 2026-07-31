package com.synapse.lantransfer.ui

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.synapse.lantransfer.data.local.PreferencesManager
import com.synapse.lantransfer.data.service.BackgroundTransferService
import com.synapse.lantransfer.data.service.TransferManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FilePickerActivity : ComponentActivity() {

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            uris.forEach { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.e("FilePickerActivity", "Failed to take persistable permission for $uri", e)
                }
            }
            startTransfer(uris)
        } else {
            finish() // User cancelled
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Launch file picker immediately
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    private fun startTransfer(uris: List<Uri>) {
        val prefs = PreferencesManager(applicationContext)
        val transferManager = TransferManager.getInstance(applicationContext)

        lifecycleScope.launch {
            try {
                val deviceName = prefs.deviceName.first()
                
                // Start background service to show overlay & notification
                BackgroundTransferService.start(applicationContext)
                
                // Initiate send
                transferManager.startSending(uris, deviceName, isOneOff = true)
                
            } catch (e: Exception) {
                Log.e("FilePickerActivity", "Failed to start transfer", e)
            } finally {
                finish()
            }
        }
    }
}
