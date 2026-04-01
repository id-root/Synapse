package com.synapse.lantransfer.data.local

import androidx.room.*
import com.synapse.lantransfer.data.model.TransferRecord
import com.synapse.lantransfer.data.model.TransferStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for transfer history records.
 */
@Dao
interface TransferDao {

    @Query("SELECT * FROM transfers ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TransferRecord>>

    @Query("SELECT * FROM transfers ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int = 50): Flow<List<TransferRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TransferRecord): Long

    @Query("UPDATE transfers SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: Long, status: TransferStatus, errorMessage: String? = null)

    @Query("DELETE FROM transfers")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM transfers WHERE direction = 'SEND'")
    fun getSentCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transfers WHERE direction = 'RECEIVE'")
    fun getReceivedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transfers WHERE status = 'COMPLETED'")
    fun getCompletedCount(): Flow<Int>
}
