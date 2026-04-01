package com.synapse.lantransfer.data.local

import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File

/** DataStore instance scoped to the application context. */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "synapse_settings")

/**
 * Manages user preferences using Jetpack DataStore.
 * Provides reactive flows for all settings and suspend functions for mutations.
 */
class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_DEVICE_NAME = stringPreferencesKey("device_name")
        private val KEY_DOWNLOAD_DIR = stringPreferencesKey("download_dir")
        private val KEY_AUTO_ACCEPT = booleanPreferencesKey("auto_accept")

        /** Default download directory */
        fun defaultDownloadDir(): String {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            return File(downloads, "Synapse").absolutePath
        }

        /** Default device name based on the device model */
        fun defaultDeviceName(): String {
            val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
            val model = Build.MODEL
            return if (model.startsWith(manufacturer, ignoreCase = true)) {
                model
            } else {
                "$manufacturer $model"
            }
        }
    }

    /** Reactive flow of the device name setting. */
    val deviceName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DEVICE_NAME] ?: defaultDeviceName()
    }

    /** Reactive flow of the download directory setting. */
    val downloadDir: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_DOWNLOAD_DIR] ?: defaultDownloadDir()
    }

    /** Reactive flow of the auto-accept setting. */
    val autoAccept: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_AUTO_ACCEPT] ?: false
    }

    suspend fun setDeviceName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEVICE_NAME] = name
        }
    }

    suspend fun setDownloadDir(dir: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DOWNLOAD_DIR] = dir
        }
    }

    suspend fun setAutoAccept(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_ACCEPT] = enabled
        }
    }
}
