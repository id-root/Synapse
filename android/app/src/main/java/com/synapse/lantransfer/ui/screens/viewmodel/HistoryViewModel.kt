package com.synapse.lantransfer.ui.screens.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.synapse.lantransfer.data.model.TransferRecord
import com.synapse.lantransfer.data.model.TransferStats
import com.synapse.lantransfer.data.repository.TransferRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the History screen.
 * Provides reactive access to transfer history from the Room database.
 */
class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TransferRepository(application)

    /** Reactive flow of all transfer records. */
    val entries: Flow<List<TransferRecord>> = repository.history

    /** Reactive flow of aggregated stats. */
    val stats: Flow<TransferStats> = repository.stats

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // Loading state is managed by observing the flow
        _isLoading.value = false
    }

    /** Trigger a refresh (Room flows auto-update, so this is mostly a no-op). */
    fun refresh() {
        // Room Flow automatically emits new values when data changes.
        // This function exists for UI pull-to-refresh semantics.
    }
}
