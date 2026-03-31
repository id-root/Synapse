package com.synapse.lantransfer.ui.screens.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.lantransfer.data.local.PreferencesManager
import com.synapse.lantransfer.data.model.SelectedFile
import com.synapse.lantransfer.data.model.TransferState
import com.synapse.lantransfer.data.repository.FileRepository
import com.synapse.lantransfer.data.service.TransferForegroundService
import com.synapse.lantransfer.data.service.TransferManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Send screen.
 * Manages file selection, broadcasting state, and transfer progress.
 */
class SendViewModel(application: Application) : AndroidViewModel(application) {

    private val fileRepo = FileRepository(application)
    private val transferManager = TransferManager(application)
    private val prefs = PreferencesManager(application)

    private val _selectedFiles = MutableStateFlow<List<SelectedFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedFile>> = _selectedFiles.asStateFlow()

    private val _isBroadcasting = MutableStateFlow(false)
    val isBroadcasting: StateFlow<Boolean> = _isBroadcasting.asStateFlow()

    private val _senderPort = MutableStateFlow<Int?>(null)
    val senderPort: StateFlow<Int?> = _senderPort.asStateFlow()

    val transferState: StateFlow<TransferState> = transferManager.transferState

    /**
     * Add files from the file picker to the selected list.
     */
    fun addFiles(uris: List<Uri>) {
        val newFiles = fileRepo.resolveFiles(uris)
        val existingNames = _selectedFiles.value.map { it.name }.toSet()
        _selectedFiles.value = _selectedFiles.value + newFiles.filter { it.name !in existingNames }
    }

    /**
     * Remove a file from the selected list by index.
     */
    fun removeFile(index: Int) {
        val newList = _selectedFiles.value.filterIndexed { i, _ -> i != index }
        _selectedFiles.value = newList
        if (newList.isEmpty() && _isBroadcasting.value) {
            stopBroadcasting()
        }
    }

    /**
     * Start broadcasting files on the LAN.
     * Registers the mDNS service and waits for receivers.
     */
    fun startBroadcasting() {
        if (_isBroadcasting.value || _selectedFiles.value.isEmpty()) return

        viewModelScope.launch {
            try {
                val uris = _selectedFiles.value.map { Uri.parse(it.uri) }
                val deviceName = prefs.deviceName.first()
                val port = transferManager.startSending(uris, deviceName)
                _senderPort.value = port
                _isBroadcasting.value = true

                // Start foreground service
                TransferForegroundService.start(
                    getApplication(),
                    "Broadcasting on port $port"
                )
            } catch (e: Exception) {
                _isBroadcasting.value = false
            }
        }
    }

    /**
     * Stop broadcasting and close the sender.
     */
    fun stopBroadcasting() {
        transferManager.stopSending()
        _isBroadcasting.value = false
        _senderPort.value = null
        TransferForegroundService.stop(getApplication())
    }

    override fun onCleared() {
        super.onCleared()
        stopBroadcasting()
        transferManager.destroy()
    }
}
