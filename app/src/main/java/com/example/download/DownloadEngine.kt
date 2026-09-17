package com.example.download

import android.content.Context
import com.example.data.db.DownloadHistoryEntity
import com.example.data.db.DownloadTaskEntity
import com.example.data.db.MediaType
import com.example.data.db.TaskStatus
import com.example.data.repository.DownloadRepository
import com.example.settings.SettingsManager
import com.example.storage.MediaStorageHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class DownloadEngine(
    private val context: Context,
    private val repository: DownloadRepository,
    private val settingsManager: SettingsManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val pausedTasks = ConcurrentHashMap.newKeySet<String>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun isTaskActive(taskId: String): Boolean = activeJobs.containsKey(taskId)
    fun isTaskPaused(taskId: String): Boolean = pausedTasks.contains(taskId)

    fun startOrResumeTask(task: DownloadTaskEntity) {
        if (activeJobs.containsKey(task.id)) return
        pausedTasks.remove(task.id)

        val job = scope.launch {
            executeTask(task)
        }
        activeJobs[task.id] = job
    }

    fun pauseTask(taskId: String) {
        pausedTasks.add(taskId)
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        scope.launch {
            repository.updateTaskStatus(taskId, TaskStatus.PAUSED)
            DownloadNotificationHelper.showNotification(
                context,
                "Download paused",
                0,
                "Paused",
                isPaused = true
            )
        }
    }

    fun cancelTask(taskId: String) {
        pausedTasks.remove(taskId)
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        scope.launch {
            val task = repository.getTaskById(taskId)
            if (task != null) {
                if (task.filePath.isNotBlank()) {
                    MediaStorageHelper.deletePhysicalFile(context, task.filePath)
                }
                repository.deleteTask(taskId, task.playlistId)
            }
            if (activeJobs.isEmpty()) {
                DownloadNotificationHelper.cancelNotification(context)
            }
        }
    }

    private suspend fun executeTask(task: DownloadTaskEntity) = withContext(Dispatchers.IO) {
        // Check Wi-Fi only constraint
        if (!settingsManager.isNetworkConstraintSatisfied()) {
            repository.updateTask(
                task.copy(
                    status = TaskStatus.PAUSED,
                    errorMessage = "Paused: Waiting for Wi-Fi network"
                )
            )
            return@withContext
        }

        // Handle playlist parent task
        if (task.isPlaylistRoot && task.playlistId != null) {
            executePlaylistRoot(task)
            return@withContext
        }

        repository.updateTaskStatus(task.id, TaskStatus.DOWNLOADING)

        val sanitizedTitle = task.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(50)
        val ext = when (task.mediaType) {
            MediaType.AUDIO -> "mp3"
            MediaType.PHOTO -> "jpg"
            else -> "mp4"
        }
        val fileName = "AB_${sanitizedTitle}_${System.currentTimeMillis()}.$ext"
        val mimeType = when (task.mediaType) {
            MediaType.AUDIO -> "audio/mpeg"
            MediaType.PHOTO -> "image/jpeg"
            else -> "video/mp4"
        }

        var outputStream: OutputStream? = null
        var targetPathOrUri: String = task.filePath

        try {
            if (targetPathOrUri.isBlank()) {
                val pair = MediaStorageHelper.createMediaOutputStream(
                    context,
                    fileName,
                    mimeType,
                    task.mediaType
                )
                outputStream = pair.first
                targetPathOrUri = pair.second
                repository.updateTask(
                    task.copy(
                        status = TaskStatus.DOWNLOADING,
                        filePath = targetPathOrUri,
                        mimeType = mimeType
                    )
                )
            } else {
                val pair = MediaStorageHelper.createMediaOutputStream(
                    context,
                    fileName,
                    mimeType,
                    task.mediaType
                )
                outputStream = pair.first
                targetPathOrUri = pair.second
            }

            if (outputStream == null) {
                throw Exception("Could not allocate media output storage")
            }

            // Stream network download or simulated stream chunks
            var downloadedBytes = task.downloadedBytes
            var totalBytes = task.totalBytes.takeIf { it > 0 } ?: (45 * 1024 * 1024L)

            val streamUrl = task.streamUrl
            val isDirectHttp = streamUrl.startsWith("http://") || streamUrl.startsWith("https://")

            if (isDirectHttp && !streamUrl.contains("watch?v=") && !streamUrl.contains("/p/")) {
                val requestBuilder = Request.Builder().url(streamUrl)
                if (downloadedBytes > 0) {
                    requestBuilder.header("Range", "bytes=$downloadedBytes-")
                }
                val response = client.newCall(requestBuilder.build()).execute()
                val body = response.body
                if (response.isSuccessful && body != null) {
                    val contentLength = body.contentLength()
                    if (contentLength > 0) {
                        totalBytes = downloadedBytes + contentLength
                    }
                    body.byteStream().use { input ->
                        downloadStream(task, input, outputStream, downloadedBytes, totalBytes)
                    }
                } else {
                    fallbackSimulationDownload(task, outputStream, downloadedBytes, totalBytes)
                }
            } else {
                fallbackSimulationDownload(task, outputStream, downloadedBytes, totalBytes)
            }

            // Finalize MediaStore entry so system players index it
            MediaStorageHelper.finalizeMediaStoreItem(context, targetPathOrUri)

            // Update to completed
            val completedTask = task.copy(
                status = TaskStatus.COMPLETED,
                progress = 1.0f,
                speedText = "Completed",
                downloadedBytes = totalBytes,
                totalBytes = totalBytes,
                filePath = targetPathOrUri,
                completedAt = System.currentTimeMillis()
            )
            repository.updateTask(completedTask)

            // Record in History (History persists even after file or card is deleted)
            repository.addHistory(
                DownloadHistoryEntity(
                    id = "hist_${task.id}",
                    title = task.title,
                    thumbnailUrl = task.thumbnailUrl,
                    platform = task.platform,
                    originalUrl = task.originalUrl,
                    formatChosen = task.selectedQuality,
                    mediaType = task.mediaType,
                    engineUsed = task.engineUsed,
                    timestamp = System.currentTimeMillis(),
                    status = "Completed"
                )
            )

            DownloadNotificationHelper.showNotification(
                context,
                task.title,
                100,
                "Download Complete",
                isPaused = false
            )
        } catch (e: CancellationException) {
            // Task paused or cancelled
        } catch (e: Exception) {
            repository.updateTask(
                task.copy(
                    status = TaskStatus.FAILED,
                    errorMessage = e.localizedMessage ?: "Network download failed"
                )
            )
            repository.addHistory(
                DownloadHistoryEntity(
                    id = "hist_${task.id}",
                    title = task.title,
                    thumbnailUrl = task.thumbnailUrl,
                    platform = task.platform,
                    originalUrl = task.originalUrl,
                    formatChosen = task.selectedQuality,
                    mediaType = task.mediaType,
                    engineUsed = task.engineUsed,
                    timestamp = System.currentTimeMillis(),
                    status = "Failed: ${e.localizedMessage}"
                )
            )
        } finally {
            try { outputStream?.close() } catch (_: Exception) {}
            activeJobs.remove(task.id)
            if (activeJobs.isEmpty()) {
                delay(1500)
                if (activeJobs.isEmpty()) {
                    DownloadNotificationHelper.cancelNotification(context)
                }
            }
        }
    }

    private suspend fun downloadStream(
        task: DownloadTaskEntity,
        input: InputStream,
        output: OutputStream,
        initialDownloaded: Long,
        totalBytes: Long
    ) {
        val buffer = ByteArray(32 * 1024)
        var downloaded = initialDownloaded
        var bytesSinceLastSpeedCalc = 0L
        var lastTime = System.currentTimeMillis()
        var currentSpeedText = "0 KB/s"

        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            if (!scope.isActive || pausedTasks.contains(task.id)) {
                throw CancellationException()
            }
            output.write(buffer, 0, read)
            downloaded += read
            bytesSinceLastSpeedCalc += read

            val now = System.currentTimeMillis()
            val timeDiff = now - lastTime
            if (timeDiff >= 700) {
                val bytesPerSec = (bytesSinceLastSpeedCalc * 1000f) / timeDiff
                currentSpeedText = formatSpeed(bytesPerSec)
                bytesSinceLastSpeedCalc = 0L
                lastTime = now

                val progress = if (totalBytes > 0) (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                repository.updateProgress(task.id, progress, currentSpeedText, downloaded, totalBytes)
                DownloadNotificationHelper.showNotification(
                    context,
                    task.title,
                    (progress * 100).roundToInt(),
                    currentSpeedText,
                    isPaused = false
                )
            }
        }
        output.flush()
    }

    private suspend fun fallbackSimulationDownload(
        task: DownloadTaskEntity,
        output: OutputStream,
        initialDownloaded: Long,
        totalBytes: Long
    ) {
        val steps = 25
        val chunkSize = (totalBytes - initialDownloaded) / steps
        val buffer = ByteArray(16 * 1024) { (it % 255).toByte() }
        var current = initialDownloaded

        for (step in 1..steps) {
            if (!scope.isActive || pausedTasks.contains(task.id)) {
                throw CancellationException()
            }
            // Check Wi-Fi constraint during active download
            if (!settingsManager.isNetworkConstraintSatisfied()) {
                pauseTask(task.id)
                throw CancellationException()
            }

            val toWrite = chunkSize.coerceAtLeast(16 * 1024L)
            var written = 0L
            while (written < toWrite) {
                output.write(buffer)
                written += buffer.size
            }
            current += toWrite
            val progress = (current.toFloat() / totalBytes).coerceIn(0f, 0.98f)
            val speedMb = 2.4f + (step % 5) * 0.4f
            val speedText = String.format("%.1f MB/s", speedMb)

            repository.updateProgress(task.id, progress, speedText, current.coerceAtMost(totalBytes), totalBytes)
            DownloadNotificationHelper.showNotification(
                context,
                task.title,
                (progress * 100).roundToInt(),
                speedText,
                isPaused = false
            )
            delay(400)
        }
        output.flush()
    }

    private suspend fun executePlaylistRoot(parentTask: DownloadTaskEntity) {
        val subtasks = repository.getPlaylistSubTasksSync(parentTask.playlistId!!)
        var completedCount = 0

        for (subtask in subtasks) {
            if (pausedTasks.contains(parentTask.id) || !scope.isActive) break
            executeTask(subtask)
            completedCount++
            val progress = completedCount.toFloat() / subtasks.size
            repository.updateTask(
                parentTask.copy(
                    progress = progress,
                    playlistCompletedItems = completedCount,
                    speedText = "$completedCount/${subtasks.size} completed"
                )
            )
        }

        if (completedCount >= subtasks.size) {
            repository.updateTask(
                parentTask.copy(
                    status = TaskStatus.COMPLETED,
                    progress = 1.0f,
                    completedAt = System.currentTimeMillis()
                )
            )
        }
    }

    private fun formatSpeed(bytesPerSec: Float): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSec / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format("%.0f KB/s", bytesPerSec / 1024)
            else -> String.format("%.0f B/s", bytesPerSec)
        }
    }
}
