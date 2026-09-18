package com.abdeveloper.abdownloader.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks WHERE isPlaylistRoot = 1 OR playlistId IS NULL ORDER BY createdAt DESC")
    fun getAllTopLevelTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status = 'COMPLETED' AND (isPlaylistRoot = 1 OR playlistId IS NULL) ORDER BY completedAt DESC")
    fun getCompletedTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status != 'COMPLETED' AND (isPlaylistRoot = 1 OR playlistId IS NULL) ORDER BY createdAt DESC")
    fun getUncompletedTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status != 'COMPLETED' AND (isPlaylistRoot = 1 OR playlistId IS NULL)")
    fun getUncompletedCount(): Flow<Int>

    @Query("SELECT * FROM download_tasks WHERE playlistId = :playlistId AND isPlaylistRoot = 0 ORDER BY createdAt ASC")
    fun getPlaylistSubTasks(playlistId: String): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE playlistId = :playlistId AND isPlaylistRoot = 0 ORDER BY createdAt ASC")
    suspend fun getPlaylistSubTasksSync(playlistId: String): List<DownloadTaskEntity>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskById(id: String): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    fun observeTaskById(id: String): Flow<DownloadTaskEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: DownloadTaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<DownloadTaskEntity>)

    @Update
    suspend fun updateTask(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET status = :status WHERE id = :id")
    suspend fun updateTaskStatus(id: String, status: TaskStatus)

    @Query("UPDATE download_tasks SET progress = :progress, speedText = :speed, downloadedBytes = :downloaded, totalBytes = :total WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, speed: String, downloaded: Long, total: Long)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM download_tasks WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSubTasks(playlistId: String)

    @Query("DELETE FROM download_tasks WHERE id IN (:ids)")
    suspend fun deleteTasksByIds(ids: List<String>)
}
