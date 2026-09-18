package com.abdeveloper.abdownloader.ui.viewmodels

import android.app.Activity
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abdeveloper.abdownloader.ABDownloaderApplication
import com.abdeveloper.abdownloader.ads.AdMobManager
import com.abdeveloper.abdownloader.data.db.DownloadHistoryEntity
import com.abdeveloper.abdownloader.data.db.DownloadTaskEntity
import com.abdeveloper.abdownloader.data.db.MediaType
import com.abdeveloper.abdownloader.data.db.TaskStatus
import com.abdeveloper.abdownloader.data.repository.DownloadRepository
import com.abdeveloper.abdownloader.download.DownloadEngine
import com.abdeveloper.abdownloader.engine.EngineOrchestrator
import com.abdeveloper.abdownloader.engine.EngineUpdateManager
import com.abdeveloper.abdownloader.engine.EngineUpdateState
import com.abdeveloper.abdownloader.engine.ExtractedMedia
import com.abdeveloper.abdownloader.engine.ExtractionResult
import com.abdeveloper.abdownloader.engine.FormatOption
import com.abdeveloper.abdownloader.engine.SupportedPlatform
import com.abdeveloper.abdownloader.settings.AppThemeMode
import com.abdeveloper.abdownloader.settings.SettingsManager
import com.abdeveloper.abdownloader.storage.MediaStorageHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class SubScreen {
    UNCOMPLETED_LIST,
    HISTORY,
    APP_INFO,
    PRIVACY_POLICY,
    TERMS_OF_USE
}

class MainViewModel(
    private val app: ABDownloaderApplication = ABDownloaderApplication.instance
) : ViewModel() {

    private val repository: DownloadRepository = app.repository
    private val orchestrator: EngineOrchestrator = app.orchestrator
    val updateManager: EngineUpdateManager = app.updateManager
    val settingsManager: SettingsManager = app.settingsManager
    val cookieManager: com.abdeveloper.abdownloader.storage.CookieManager = app.cookieManager
    private val downloadEngine: DownloadEngine = app.downloadEngine

    val cookieState: StateFlow<Map<String, Boolean>> = cookieManager.cookieState

    private val _cookieActionFeedback = MutableStateFlow<String?>(null)
    val cookieActionFeedback: StateFlow<String?> = _cookieActionFeedback.asStateFlow()

    // Navigation & Screen States
    private val _selectedTab = MutableStateFlow(0) // 0 = Home, 1 = Downloads, 2 = Settings
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _currentSubScreen = MutableStateFlow<SubScreen?>(null)
    val currentSubScreen: StateFlow<SubScreen?> = _currentSubScreen.asStateFlow()

    // Home Tab URL Input & Validation
    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _validatedUrl = MutableStateFlow<String?>(null)
    val validatedUrl: StateFlow<String?> = _validatedUrl.asStateFlow()

    private val _detectedPlatform = MutableStateFlow<SupportedPlatform?>(null)
    val detectedPlatform: StateFlow<SupportedPlatform?> = _detectedPlatform.asStateFlow()

    // Extraction & Quality Picker Bottom Sheet
    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting.asStateFlow()

    private val _extractionError = MutableStateFlow<String?>(null)
    val extractionError: StateFlow<String?> = _extractionError.asStateFlow()

    private val _extractedMedia = MutableStateFlow<ExtractedMedia?>(null)
    val extractedMedia: StateFlow<ExtractedMedia?> = _extractedMedia.asStateFlow()

    private val _showQualitySheet = MutableStateFlow(false)
    val showQualitySheet: StateFlow<Boolean> = _showQualitySheet.asStateFlow()

    private val _showPlaylistSelector = MutableStateFlow(false)
    val showPlaylistSelector: StateFlow<Boolean> = _showPlaylistSelector.asStateFlow()

    private val _selectedQualityOption = MutableStateFlow<FormatOption?>(null)
    val selectedQualityOption: StateFlow<FormatOption?> = _selectedQualityOption.asStateFlow()

    private val _selectedPhotoIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPhotoIds: StateFlow<Set<String>> = _selectedPhotoIds.asStateFlow()

    private val _selectedPlaylistVideoIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedPlaylistVideoIds: StateFlow<Set<String>> = _selectedPlaylistVideoIds.asStateFlow()

    // Downloads List Data
    val completedTasks: StateFlow<List<DownloadTaskEntity>> = repository.completedTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uncompletedTasks: StateFlow<List<DownloadTaskEntity>> = repository.uncompletedTasks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uncompletedCount: StateFlow<Int> = repository.uncompletedCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val historyItems: StateFlow<List<DownloadHistoryEntity>> = repository.historyItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val themeMode: StateFlow<AppThemeMode> = settingsManager.themeMode
    val wifiOnly: StateFlow<Boolean> = settingsManager.wifiOnly
    val engineUpdateState: StateFlow<EngineUpdateState> = updateManager.updateState
    val lastUsedEngine: StateFlow<String> = orchestrator.lastUsedEngine
    val ytDlpVersion: String get() = orchestrator.ytDlpEngine.version
    val newPipeVersion: String get() = orchestrator.newPipeEngine.version

    // UI Expansion & Selection in Downloads
    private val _expandedPlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedPlaylistIds: StateFlow<Set<String>> = _expandedPlaylistIds.asStateFlow()

    private val _multiSelectMode = MutableStateFlow(false)
    val multiSelectMode: StateFlow<Boolean> = _multiSelectMode.asStateFlow()

    private val _selectedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedTaskIds: StateFlow<Set<String>> = _selectedTaskIds.asStateFlow()

    private val _detailsTask = MutableStateFlow<DownloadTaskEntity?>(null)
    val detailsTask: StateFlow<DownloadTaskEntity?> = _detailsTask.asStateFlow()

    private val _showDeleteConfirmDialog = MutableStateFlow(false)
    val showDeleteConfirmDialog: StateFlow<Boolean> = _showDeleteConfirmDialog.asStateFlow()

    private val _pendingDeleteIds = MutableStateFlow<List<String>>(emptyList())
    val pendingDeleteIds: StateFlow<List<String>> = _pendingDeleteIds.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTab.value = index
        _currentSubScreen.value = null
    }

    fun navigateToSubScreen(subScreen: SubScreen?) {
        _currentSubScreen.value = subScreen
    }

    fun onUrlInputChanged(newText: String) {
        _urlInput.value = newText
        val valid = orchestrator.validateSingleSupportedUrl(newText)
        _validatedUrl.value = valid
        _detectedPlatform.value = if (valid != null) SupportedPlatform.detect(valid) else null
        _extractionError.value = null
    }

    fun pasteFromClipboard(context: Context) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null && clipboard.hasPrimaryClip()) {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).text?.toString().orEmpty()
                onUrlInputChanged(text)
            }
        }
    }

    fun clearUrlInput() {
        _urlInput.value = ""
        _validatedUrl.value = null
        _detectedPlatform.value = null
        _extractionError.value = null
    }

    fun startExtraction(urlOverride: String? = null) {
        val target = urlOverride ?: _validatedUrl.value ?: return
        _isExtracting.value = true
        _extractionError.value = null
        _selectedQualityOption.value = null

        viewModelScope.launch {
            val result = orchestrator.resolveMedia(target)
            _isExtracting.value = false
            when (result) {
                is ExtractionResult.Success -> {
                    _extractedMedia.value = result.media
                    if (result.media.isPlaylist) {
                        _selectedPlaylistVideoIds.value = result.media.playlistVideos.map { it.id }.toSet()
                        _showPlaylistSelector.value = true
                    } else if (result.media.isPhotoPost) {
                        _selectedPhotoIds.value = result.media.photos.map { it.id }.toSet()
                        _showQualitySheet.value = true
                    } else {
                        // Preselect standard 1080p or highest available format
                        _selectedQualityOption.value = result.media.videoFormats.firstOrNull()
                            ?: result.media.audioFormats.firstOrNull()
                        _showQualitySheet.value = true
                    }
                }
                is ExtractionResult.Failure -> {
                    _extractionError.value = result.error
                }
            }
        }
    }

    fun selectQualityOption(option: FormatOption) {
        _selectedQualityOption.value = option
    }

    fun togglePhotoSelection(photoId: String) {
        val current = _selectedPhotoIds.value.toMutableSet()
        if (current.contains(photoId)) {
            current.remove(photoId)
        } else {
            current.add(photoId)
        }
        _selectedPhotoIds.value = current
    }

    fun togglePlaylistVideo(videoId: String) {
        val current = _selectedPlaylistVideoIds.value.toMutableSet()
        if (current.contains(videoId)) {
            current.remove(videoId)
        } else {
            current.add(videoId)
        }
        _selectedPlaylistVideoIds.value = current
    }

    fun toggleSelectAllPlaylist() {
        val all = _extractedMedia.value?.playlistVideos?.map { it.id }.orEmpty()
        if (_selectedPlaylistVideoIds.value.size == all.size) {
            _selectedPlaylistVideoIds.value = emptySet()
        } else {
            _selectedPlaylistVideoIds.value = all.toSet()
        }
    }

    fun dismissQualitySheet() {
        _showQualitySheet.value = false
    }

    fun dismissPlaylistSelector() {
        _showPlaylistSelector.value = false
    }

    fun openPlaylistQualitySheet() {
        _showPlaylistSelector.value = false
        _selectedQualityOption.value = _extractedMedia.value?.videoFormats?.firstOrNull()
            ?: _extractedMedia.value?.audioFormats?.firstOrNull()
        _showQualitySheet.value = true
    }

    fun confirmDownload(activity: Activity?) {
        val media = _extractedMedia.value ?: return

        // Interstitial ad shown after initiating download if preloaded (never blocks if not ready)
        AdMobManager.showInterstitialIfReady(activity) {
            // Proceed with download queueing
        }

        viewModelScope.launch {
            if (media.isPlaylist) {
                val selectedVideos = media.playlistVideos.filter { _selectedPlaylistVideoIds.value.contains(it.id) }
                if (selectedVideos.isEmpty()) return@launch

                val playlistId = "playlist_${UUID.randomUUID().toString().take(8)}"
                val quality = _selectedQualityOption.value?.qualityLabel ?: "1080p 60FPS"
                val streamUrl = _selectedQualityOption.value?.streamUrl ?: media.originalUrl

                // 1. Create root playlist task
                val rootTask = DownloadTaskEntity(
                    id = playlistId,
                    title = media.title,
                    originalUrl = media.originalUrl,
                    thumbnailUrl = media.thumbnailUrl,
                    platform = media.platform.displayName,
                    mediaType = MediaType.PLAYLIST,
                    selectedQuality = quality,
                    formatId = _selectedQualityOption.value?.id ?: "",
                    streamUrl = streamUrl,
                    status = TaskStatus.DOWNLOADING,
                    isPlaylistRoot = true,
                    playlistId = playlistId,
                    playlistTotalItems = selectedVideos.size,
                    playlistCompletedItems = 0,
                    engineUsed = media.engineUsed
                )
                repository.insertTask(rootTask)

                // 2. Create sub-tasks
                val subTasks = selectedVideos.map { item ->
                    DownloadTaskEntity(
                        id = "item_${UUID.randomUUID().toString().take(8)}",
                        title = item.title,
                        originalUrl = item.url,
                        thumbnailUrl = item.thumbnailUrl,
                        platform = media.platform.displayName,
                        mediaType = if (_selectedQualityOption.value?.isAudio == true) MediaType.AUDIO else MediaType.VIDEO,
                        selectedQuality = quality,
                        formatId = _selectedQualityOption.value?.id ?: "",
                        streamUrl = item.url,
                        status = TaskStatus.QUEUED,
                        isPlaylistRoot = false,
                        playlistId = playlistId,
                        playlistTitle = media.title,
                        engineUsed = media.engineUsed
                    )
                }
                repository.insertTasks(subTasks)
                downloadEngine.startOrResumeTask(rootTask)
            } else if (media.isPhotoPost) {
                val selectedPhotos = media.photos.filter { _selectedPhotoIds.value.contains(it.id) }
                for (photo in selectedPhotos) {
                    val taskId = "photo_${UUID.randomUUID().toString().take(8)}"
                    val task = DownloadTaskEntity(
                        id = taskId,
                        title = "${media.title} - Photo ${photo.index}",
                        originalUrl = media.originalUrl,
                        thumbnailUrl = photo.url,
                        platform = media.platform.displayName,
                        mediaType = MediaType.PHOTO,
                        selectedQuality = "Original",
                        formatId = photo.id,
                        streamUrl = photo.url,
                        status = TaskStatus.DOWNLOADING,
                        engineUsed = media.engineUsed
                    )
                    repository.insertTask(task)
                    downloadEngine.startOrResumeTask(task)
                }
            } else {
                val quality = _selectedQualityOption.value ?: return@launch
                val taskId = "media_${UUID.randomUUID().toString().take(8)}"
                val task = DownloadTaskEntity(
                    id = taskId,
                    title = media.title,
                    originalUrl = media.originalUrl,
                    thumbnailUrl = media.thumbnailUrl,
                    platform = media.platform.displayName,
                    mediaType = if (quality.isAudio) MediaType.AUDIO else MediaType.VIDEO,
                    selectedQuality = quality.qualityLabel,
                    formatId = quality.id,
                    streamUrl = quality.streamUrl.ifBlank { media.originalUrl },
                    status = TaskStatus.DOWNLOADING,
                    durationSeconds = media.durationSeconds,
                    engineUsed = media.engineUsed
                )
                repository.insertTask(task)
                downloadEngine.startOrResumeTask(task)
            }

            _showQualitySheet.value = false
            _urlInput.value = ""
            _validatedUrl.value = null
            _detectedPlatform.value = null
            _selectedTab.value = 1 // Switch to Downloads tab to view progress
        }
    }

    fun pauseTask(task: DownloadTaskEntity) {
        downloadEngine.pauseTask(task.id)
    }

    fun resumeTask(task: DownloadTaskEntity) {
        downloadEngine.startOrResumeTask(task)
    }

    fun toggleExpandPlaylist(playlistId: String) {
        val current = _expandedPlaylistIds.value.toMutableSet()
        if (current.contains(playlistId)) {
            current.remove(playlistId)
        } else {
            current.add(playlistId)
        }
        _expandedPlaylistIds.value = current
    }

    fun enterMultiSelect(initialId: String) {
        _multiSelectMode.value = true
        _selectedTaskIds.value = setOf(initialId)
    }

    fun exitMultiSelect() {
        _multiSelectMode.value = false
        _selectedTaskIds.value = emptySet()
    }

    fun toggleTaskSelection(id: String) {
        val current = _selectedTaskIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedTaskIds.value = current
        if (current.isEmpty()) {
            _multiSelectMode.value = false
        }
    }

    fun selectAllTasks(allIds: List<String>) {
        if (_selectedTaskIds.value.size == allIds.size) {
            _selectedTaskIds.value = emptySet()
            _multiSelectMode.value = false
        } else {
            _selectedTaskIds.value = allIds.toSet()
        }
    }

    fun promptDeleteTasks(ids: List<String>) {
        _pendingDeleteIds.value = ids
        _showDeleteConfirmDialog.value = true
    }

    fun dismissDeleteDialog() {
        _showDeleteConfirmDialog.value = false
        _pendingDeleteIds.value = emptyList()
    }

    fun confirmDeleteTasks() {
        val ids = _pendingDeleteIds.value
        viewModelScope.launch {
            for (id in ids) {
                val task = repository.getTaskById(id)
                if (task != null) {
                    if (task.filePath.isNotBlank()) {
                        MediaStorageHelper.deletePhysicalFile(app, task.filePath)
                    }
                    repository.deleteTask(task.id, task.playlistId)
                }
            }
            exitMultiSelect()
            dismissDeleteDialog()
        }
    }

    fun showDetails(task: DownloadTaskEntity) {
        _detailsTask.value = task
    }

    fun dismissDetails() {
        _detailsTask.value = null
    }

    fun openMediaFile(context: Context, task: DownloadTaskEntity) {
        if (task.filePath.isBlank()) return
        try {
            val uri = if (task.filePath.startsWith("content://")) {
                Uri.parse(task.filePath)
            } else {
                val file = File(task.filePath)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }

            val mime = when {
                task.mimeType.isNotBlank() -> task.mimeType
                task.mediaType == MediaType.VIDEO -> "video/*"
                task.mediaType == MediaType.AUDIO -> "audio/*"
                task.mediaType == MediaType.PHOTO -> "image/*"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun shareMediaFile(context: Context, task: DownloadTaskEntity) {
        if (task.filePath.isBlank()) return
        try {
            val uri = if (task.filePath.startsWith("content://")) {
                Uri.parse(task.filePath)
            } else {
                val file = File(task.filePath)
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }

            val mime = when {
                task.mimeType.isNotBlank() -> task.mimeType
                task.mediaType == MediaType.VIDEO -> "video/*"
                task.mediaType == MediaType.AUDIO -> "audio/*"
                task.mediaType == MediaType.PHOTO -> "image/*"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "${task.title} - Downloaded via ABDownloader")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share Media"))
        } catch (_: Exception) {}
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            updateManager.checkManual()
        }
    }

    fun applyEngineUpdate() {
        viewModelScope.launch {
            updateManager.applyUpdate()
        }
    }

    fun dismissEngineUpdate() {
        updateManager.dismissUpdate()
    }

    fun setThemeMode(mode: AppThemeMode) {
        settingsManager.setThemeMode(mode)
    }

    fun setWifiOnly(enabled: Boolean) {
        settingsManager.setWifiOnly(enabled)
    }

    fun importCookies(platformKey: String, uri: Uri) {
        viewModelScope.launch {
            val success = cookieManager.importCookies(platformKey, uri)
            val displayName = when (platformKey.lowercase()) {
                "instagram" -> "Instagram"
                "tiktok" -> "TikTok"
                "twitter" -> "X (Twitter)"
                else -> platformKey.replaceFirstChar { it.uppercase() }
            }
            _cookieActionFeedback.value = if (success) {
                "Cookies loaded successfully for $displayName"
            } else {
                "Failed to read cookies file for $displayName"
            }
        }
    }

    fun removeCookies(platformKey: String) {
        viewModelScope.launch {
            cookieManager.removeCookies(platformKey)
            val displayName = when (platformKey.lowercase()) {
                "instagram" -> "Instagram"
                "tiktok" -> "TikTok"
                "twitter" -> "X (Twitter)"
                else -> platformKey.replaceFirstChar { it.uppercase() }
            }
            _cookieActionFeedback.value = "Cookies removed for $displayName"
        }
    }

    fun clearCookieFeedback() {
        _cookieActionFeedback.value = null
    }
}
