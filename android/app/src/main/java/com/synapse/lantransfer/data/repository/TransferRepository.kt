package com.synapse.lantransfer.data.repository

import android.content.Context
import com.synapse.lantransfer.data.local.TransferDatabase
import com.synapse.lantransfer.data.model.TransferDirection
import com.synapse.lantransfer.data.model.TransferRecord
import com.synapse.lantransfer.data.model.TransferStats
import com.synapse.lantransfer.data.model.TransferStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Business logic layer over the transfer history DAO.
 * Provides reactive flows and convenience methods.
 */
class TransferRepository(context: Context) {

    private val dao = TransferDatabase.getInstance(context).transferDao()

    /** Reactive list of all transfer records, newest first. */
    val history: Flow<List<TransferRecord>> = dao.getAll()

    /** Aggregated transfer statistics as a reactive flow. */
    val stats: Flow<TransferStats> = combine(
        dao.getSentCount(),
        dao.getReceivedCount(),
        dao.getCompletedCount()
    ) { sent, received, completed ->
        TransferStats(
            sentCount = sent,
            receivedCount = received,
            completedCount = completed
        )
    }

    /** Insert a new transfer record. Returns the generated ID. */
    suspend fun logTransfer(
        fileName: String,
        fileSize: Long,
        direction: TransferDirection,
        status: TransferStatus,
        peerName: String,
        peerAddress: String,
        errorMessage: String? = null
    ): Long {
        return dao.insert(
            TransferRecord(
                fileName = fileName,
                fileSize = fileSize,
                direction = direction,
                status = status,
                peerName = peerName,
                peerAddress = peerAddress,
                errorMessage = errorMessage
            )
        )
    }

    /** Update the status of an existing transfer. */
    suspend fun updateStatus(id: Long, status: TransferStatus, errorMessage: String? = null) {
        dao.updateStatus(id, status, errorMessage)
    }

    /** Clear all transfer history. */
    suspend fun clearHistory() {
        dao.deleteAll()
    }
}
