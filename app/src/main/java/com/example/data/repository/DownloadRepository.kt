package com.example.data.repository

import com.example.data.db.DownloadDao
import com.example.data.db.DownloadHistoryEntity
import com.example.data.db.DownloadTaskEntity
import com.example.data.db.HistoryDao
import com.example.data.db.TaskStatus
import kotlinx.coroutines.flow.Flow

class DownloadRepository(
    private val downloadDao: DownloadDao,
    private val historyDao: HistoryDao
) {
    val topLevelTasks: Flow<List<DownloadTaskEntity>> = downloadDao.getAllTopLevelTasks()
    val completedTasks: Flow<List<DownloadTaskEntity>> = downloadDao.getCompletedTasks()
    val uncompletedTasks: Flow<List<DownloadTaskEntity>> = downloadDao.getUncompletedTasks()
    val uncompletedCount: Flow<Int> = downloadDao.getUncompletedCount()
    val historyItems: Flow<List<DownloadHistoryEntity>> = historyDao.getAllHistory()

    fun getPlaylistSubTasks(playlistId: String): Flow<List<DownloadTaskEntity>> {
        return downloadDao.getPlaylistSubTasks(playlistId)
    }

    suspend fun getPlaylistSubTasksSync(playlistId: String): List<DownloadTaskEntity> {
        return downloadDao.getPlaylistSubTasksSync(playlistId)
    }

    suspend fun getTaskById(id: String): DownloadTaskEntity? {
        return downloadDao.getTaskById(id)
    }

    fun observeTaskById(id: String): Flow<DownloadTaskEntity?> {
        return downloadDao.observeTaskById(id)
    }

    suspend fun insertTask(task: DownloadTaskEntity) {
        downloadDao.insertTask(task)
    }

    suspend fun insertTasks(tasks: List<DownloadTaskEntity>) {
        downloadDao.insertTasks(tasks)
    }

    suspend fun updateTask(task: DownloadTaskEntity) {
        downloadDao.updateTask(task)
    }

    suspend fun updateTaskStatus(id: String, status: TaskStatus) {
        downloadDao.updateTaskStatus(id, status)
    }

    suspend fun updateProgress(id: String, progress: Float, speed: String, downloaded: Long, total: Long) {
        downloadDao.updateProgress(id, progress, speed, downloaded, total)
    }

    suspend fun deleteTask(id: String, playlistId: String? = null) {
        downloadDao.deleteTaskById(id)
        if (playlistId != null) {
            downloadDao.deletePlaylistSubTasks(playlistId)
        }
    }

    suspend fun deleteTasks(ids: List<String>) {
        downloadDao.deleteTasksByIds(ids)
    }

    suspend fun addHistory(history: DownloadHistoryEntity) {
        historyDao.insertHistory(history)
    }

    suspend fun clearHistory() {
        historyDao.clearAllHistory()
    }
}
