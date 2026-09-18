package com.abdeveloper.abdownloader.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM download_history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<DownloadHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(item: DownloadHistoryEntity)

    @Query("DELETE FROM download_history")
    suspend fun clearAllHistory()

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteHistoryById(id: String)
}
