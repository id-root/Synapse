package com.synapse.lantransfer.ui.screens.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.synapse.lantransfer.data.local.PreferencesManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Settings screen.
 * Loads and saves user preferences via DataStore.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    val deviceName: StateFlow<String> = prefs.deviceName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.defaultDeviceName())

    val downloadDir: StateFlow<String> = prefs.downloadDir
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.defaultDownloadDir())

    val autoAccept: StateFlow<Boolean> = prefs.autoAccept
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _saveState = MutableStateFlow(SaveState.IDLE)
    val saveState: StateFlow<SaveState> = _saveState.asStateFlow()

    /**
     * Save current settings to DataStore.
     */
    fun saveSettings(name: String, dir: String, auto: Boolean) {
        if (_saveState.value == SaveState.SAVING) return

        viewModelScope.launch {
            _saveState.value = SaveState.SAVING
            prefs.setDeviceName(name)
            prefs.setDownloadDir(dir)
            prefs.setAutoAccept(auto)
            _saveState.value = SaveState.SAVED

            kotlinx.coroutines.delay(2000)
            _saveState.value = SaveState.IDLE
        }
    }

    fun updateDownloadDir(dir: String) {
        viewModelScope.launch {
            prefs.setDownloadDir(dir)
        }
    }

    enum class SaveState {
        IDLE, SAVING, SAVED
    }
}
