package com.abdeveloper.abdownloader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TaskStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class MediaType {
    VIDEO,
    AUDIO,
    PHOTO,
    PLAYLIST
}

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val originalUrl: String,
    val thumbnailUrl: String,
    val platform: String,
    val mediaType: MediaType,
    val selectedQuality: String,
    val formatId: String = "",
    val streamUrl: String,
    val status: TaskStatus = TaskStatus.QUEUED,
    val progress: Float = 0f,
    val speedText: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String = "",
    val mimeType: String = "",
    val durationSeconds: Long = 0L,
    val playlistId: String? = null,
    val playlistTitle: String? = null,
    val isPlaylistRoot: Boolean = false,
    val playlistTotalItems: Int = 0,
    val playlistCompletedItems: Int = 0,
    val engineUsed: String = "YtDlpEngine",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L
)

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val originalUrl: String,
    val thumbnailUrl: String,
    val platform: String,
    val mediaType: MediaType,
    val formatChosen: String,
    val engineUsed: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "Completed"
)
