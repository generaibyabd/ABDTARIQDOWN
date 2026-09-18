package com.example.download

import android.content.Context
import com.example.data.db.DownloadHistoryEntity
import com.example.data.db.DownloadTaskEntity
import com.example.data.db.MediaType
import com.example.data.db.TaskStatus
import com.example.data.repository.DownloadRepository
import com.example.engine.SupportedPlatform
import com.example.settings.SettingsManager
import com.example.storage.CookieManager
import com.example.storage.MediaStorageHelper
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
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
import java.io.File
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
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (_: Exception) {}
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
        try {
            YoutubeDL.getInstance().destroyProcessById(taskId)
        } catch (_: Exception) {}
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        scope.launch {
            val task = repository.getTaskById(taskId)
            if (task != null) {
                if (task.filePath.isNotBlank()) {
                    MediaStorageHelper.deletePhysicalFile(context, task.filePath)
                }
                // Clean up any pending or partial staging files associated with this task
                try {
                    val sanitizedTitle = task.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(50).ifBlank { "media" }
                    val stagingDir = MediaStorageHelper.getStagingDirectory(context)
                    stagingDir.listFiles { _, name -> name.contains(sanitizedTitle) }?.forEach {
                        it.delete()
                    }
                } catch (_: Exception) {}
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

        val sanitizedTitle = task.title.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(50).ifBlank { "media" }
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

        try {
            var targetPathOrUri = ""
            var totalBytes = task.totalBytes

            if (task.mediaType == MediaType.PHOTO) {
                // Direct photo download via OkHttp streaming
                val pair = MediaStorageHelper.createMediaOutputStream(
                    context,
                    fileName,
                    mimeType,
                    task.mediaType
                )
                val outputStream = pair.first ?: throw Exception("Could not allocate media storage for photo")
                targetPathOrUri = pair.second

                outputStream.use { os ->
                    val request = Request.Builder().url(task.streamUrl).build()
                    val response = client.newCall(request).execute()
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        throw Exception("HTTP ${response.code} downloading photo")
                    }
                    val length = body.contentLength()
                    if (length > 0) totalBytes = length
                    body.byteStream().use { input ->
                        downloadStream(task, input, os, 0L, totalBytes)
                    }
                }
                MediaStorageHelper.finalizeMediaStoreItem(context, targetPathOrUri)
            } else {
                // Real Video/Audio download via YoutubeDL staged into private writable external storage
                val privateStagingDir = MediaStorageHelper.getStagingDirectory(context)
                val templateFile = File(privateStagingDir, "AB_${sanitizedTitle}_${System.currentTimeMillis()}.%(ext)s")
                val request = YoutubeDLRequest(task.originalUrl)
                request.addOption("-o", templateFile.absolutePath)

                // Pass login cookies if configured for platform
                val platform = SupportedPlatform.detect(task.originalUrl)
                val cookieManager = CookieManager.getInstance(context)
                val cookieFile = cookieManager.getCookieFileForPlatform(platform)
                if (cookieFile != null) {
                    request.addOption("--cookies", cookieFile.absolutePath)
                }

                if (task.mediaType == MediaType.AUDIO) {
                    if (task.formatId.isNotBlank() && task.formatId != "best" && task.formatId != "bestaudio") {
                        request.addOption("-f", task.formatId)
                    } else {
                        request.addOption("-f", "bestaudio/best")
                    }
                    request.addOption("-x")
                    request.addOption("--audio-format", "mp3")
                    request.addOption("--audio-quality", "0")
                } else {
                    if (task.formatId.isNotBlank() && task.formatId != "best") {
                        request.addOption("-f", "${task.formatId}+bestaudio/best/${task.formatId}")
                    } else {
                        request.addOption("-f", "bestvideo+bestaudio/best")
                    }
                }

                var detectedFilePath = ""
                var lastProgressTime = 0L

                YoutubeDL.getInstance().execute(request, task.id) { progress, etaInSeconds, line ->
                    if (!scope.isActive || pausedTasks.contains(task.id)) {
                        try {
                            YoutubeDL.getInstance().destroyProcessById(task.id)
                        } catch (_: Exception) {}
                        throw CancellationException()
                    }

                    // Extract speed from progress line
                    val speedMatch = Regex("at\\s+([\\d.]+\\s*[KMG]i?B/s)").find(line)
                    val speedText = speedMatch?.groupValues?.get(1)
                        ?: if (etaInSeconds > 0) "ETA: ${etaInSeconds}s" else "${progress.toInt()}%"

                    // Extract destination if reported by yt-dlp
                    val destMatch = Regex("\\[(?:download|ExtractAudio|ffmpeg|Merger)\\] (?:Destination:|Merging formats into)\\s+\"?([^\"]+)\"?").find(line)
                        ?: Regex("\\[(?:download|ExtractAudio|ffmpeg|Merger)\\] Destination:\\s+(.+)").find(line)
                    if (destMatch != null) {
                        detectedFilePath = destMatch.groupValues[1].trim()
                    }

                    val now = System.currentTimeMillis()
                    if (now - lastProgressTime >= 400 || progress >= 100f) {
                        lastProgressTime = now
                        val normalizedProgress = (progress / 100f).coerceIn(0f, 1f)
                        val downloaded = if (totalBytes > 0) (normalizedProgress * totalBytes).toLong() else 0L

                        scope.launch {
                            repository.updateProgress(task.id, normalizedProgress, speedText, downloaded, totalBytes)
                        }
                        DownloadNotificationHelper.showNotification(
                            context,
                            task.title,
                            progress.toInt(),
                            speedText,
                            isPaused = false
                        )
                    }
                }

                // If yt-dlp didn't output Destination line, find the file created in privateStagingDir
                if (detectedFilePath.isBlank() || !File(detectedFilePath).exists()) {
                    val matchingFiles = privateStagingDir.listFiles { _, name ->
                        name.startsWith("AB_${sanitizedTitle}_") && !name.endsWith(".part") && !name.endsWith(".ytdl")
                    }
                    val newest = matchingFiles?.maxByOrNull { it.lastModified() }
                    if (newest != null && newest.exists()) {
                        detectedFilePath = newest.absolutePath
                    }
                }

                val stagedFile = File(detectedFilePath)
                if (!stagedFile.exists()) {
                    throw Exception("Downloaded file could not be found in staging directory")
                }

                totalBytes = stagedFile.length()
                val actualExt = stagedFile.extension.lowercase()
                val resolvedMimeType = when (actualExt) {
                    "mp4" -> "video/mp4"
                    "mkv" -> "video/x-matroska"
                    "webm" -> if (task.mediaType == MediaType.AUDIO) "audio/webm" else "video/webm"
                    "mp3" -> "audio/mpeg"
                    "m4a" -> "audio/mp4"
                    "opus" -> "audio/opus"
                    "ogg" -> "audio/ogg"
                    "wav" -> "audio/wav"
                    "flac" -> "audio/flac"
                    else -> mimeType
                }

                // Move finished file into public storage via MediaStore
                targetPathOrUri = MediaStorageHelper.publishFileToMediaStore(
                    context = context,
                    sourceFile = stagedFile,
                    mediaType = task.mediaType,
                    mimeType = resolvedMimeType
                )
            }

            // Update to completed
            val completedTask = task.copy(
                status = TaskStatus.COMPLETED,
                progress = 1.0f,
                speedText = "Completed",
                downloadedBytes = totalBytes,
                totalBytes = totalBytes,
                filePath = targetPathOrUri,
                mimeType = if (task.mimeType.isNotBlank()) task.mimeType else mimeType,
                completedAt = System.currentTimeMillis()
            )
            repository.updateTask(completedTask)

            // Record in History
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
            val rawMsg = e.localizedMessage ?: "Download failed"
            val lower = rawMsg.lowercase()
            val isLoginOrRateLimit = lower.contains("login required") ||
                    lower.contains("rate-limit") ||
                    lower.contains("rate limit") ||
                    lower.contains("sign in") ||
                    lower.contains("log in") ||
                    lower.contains("checkpoint_required") ||
                    lower.contains("confirm you're not a robot") ||
                    lower.contains("confirm you’re not a robot") ||
                    lower.contains("confirm you're not a bot") ||
                    lower.contains("confirm you’re not a bot") ||
                    lower.contains("redirected to login") ||
                    lower.contains("private account") ||
                    lower.contains("429")

            val platform = SupportedPlatform.detect(task.originalUrl)
            val friendlyMsg = if (isLoginOrRateLimit && (platform == SupportedPlatform.INSTAGRAM || platform == SupportedPlatform.TIKTOK || platform == SupportedPlatform.TWITTER)) {
                val cookieManager = CookieManager.getInstance(context)
                if (!cookieManager.hasCookiesForPlatform(platform)) {
                    "This content may require login. You can add your ${platform.displayName} cookies in Settings to fix this."
                } else {
                    rawMsg
                }
            } else {
                rawMsg
            }

            repository.updateTask(
                task.copy(
                    status = TaskStatus.FAILED,
                    errorMessage = friendlyMsg
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
                    status = "Failed: $friendlyMsg"
                )
            )
        } finally {
            activeJobs.remove(task.id)
            MediaStorageHelper.cleanStagingDirectory(context)
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
