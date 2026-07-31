package com.synapse.lantransfer.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Direction of a file transfer.
 */
enum class TransferDirection {
    SEND,
    RECEIVE
}

/**
 * Status of a file transfer.
 */
enum class TransferStatus {
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * Room entity representing a completed or failed file transfer.
 * Persisted to the local database for history display.
 */
@Entity(tableName = "transfers")
data class TransferRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val fileSize: Long,
    val direction: TransferDirection,
    val status: TransferStatus,
    val peerName: String,
    val peerAddress: String,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

/**
 * Aggregated transfer statistics for the history screen.
 */
data class TransferStats(
    val sentCount: Int,
    val receivedCount: Int,
    val completedCount: Int
)
