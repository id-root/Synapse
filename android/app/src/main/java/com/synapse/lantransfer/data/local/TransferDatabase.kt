package com.synapse.lantransfer.data.local

import android.content.Context
import androidx.room.*
import com.synapse.lantransfer.data.model.TransferDirection
import com.synapse.lantransfer.data.model.TransferRecord
import com.synapse.lantransfer.data.model.TransferStatus

/**
 * Type converters for Room to handle enums.
 */
class Converters {
    @TypeConverter
    fun fromTransferDirection(value: TransferDirection): String = value.name

    @TypeConverter
    fun toTransferDirection(value: String): TransferDirection = TransferDirection.valueOf(value)

    @TypeConverter
    fun fromTransferStatus(value: TransferStatus): String = value.name

    @TypeConverter
    fun toTransferStatus(value: String): TransferStatus = TransferStatus.valueOf(value)
}

/**
 * Room database for persisting transfer history.
 * Uses a singleton pattern to avoid multiple database instances.
 */
@Database(
    entities = [TransferRecord::class],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TransferDatabase : RoomDatabase() {

    abstract fun transferDao(): TransferDao

    companion object {
        @Volatile
        private var INSTANCE: TransferDatabase? = null

        fun getInstance(context: Context): TransferDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TransferDatabase::class.java,
                    "synapse_transfers.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
