package com.abdeveloper.abdownloader

import android.app.Application
import com.abdeveloper.abdownloader.ads.AdMobManager
import com.abdeveloper.abdownloader.data.db.AppDatabase
import com.abdeveloper.abdownloader.data.repository.DownloadRepository
import com.abdeveloper.abdownloader.download.DownloadEngine
import com.abdeveloper.abdownloader.download.DownloadNotificationHelper
import com.abdeveloper.abdownloader.engine.EngineOrchestrator
import com.abdeveloper.abdownloader.engine.EngineUpdateManager
import com.abdeveloper.abdownloader.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ABDownloaderApplication : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var repository: DownloadRepository
        private set
    lateinit var settingsManager: SettingsManager
        private set
    lateinit var cookieManager: com.abdeveloper.abdownloader.storage.CookieManager
        private set
    lateinit var orchestrator: EngineOrchestrator
        private set
    lateinit var updateManager: EngineUpdateManager
        private set
    lateinit var downloadEngine: DownloadEngine
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        database = AppDatabase.getInstance(this)
        repository = DownloadRepository(database.downloadDao(), database.historyDao())
        settingsManager = SettingsManager(this)
        cookieManager = com.abdeveloper.abdownloader.storage.CookieManager.getInstance(this)

        // Initialize real yt-dlp & FFmpeg engine
        try {
            com.yausername.youtubedl_android.YoutubeDL.getInstance().init(this)
            com.yausername.ffmpeg.FFmpeg.getInstance().init(this)
            try {
                com.yausername.aria2c.Aria2c.getInstance().init(this)
            } catch (_: Throwable) {}
        } catch (e: Exception) {
            android.util.Log.e("ABDownloader", "Failed to initialize yt-dlp engine", e)
        }

        orchestrator = EngineOrchestrator()
        updateManager = EngineUpdateManager(this, orchestrator)
        downloadEngine = DownloadEngine(this, repository, settingsManager)

        DownloadNotificationHelper.createNotificationChannel(this)
        AdMobManager.initialize(this)

        // Silent once-per-day check on launch and clean orphaned staging files
        CoroutineScope(Dispatchers.IO).launch {
            com.abdeveloper.abdownloader.storage.MediaStorageHelper.cleanStagingDirectory(this@ABDownloaderApplication)
            updateManager.checkOnLaunchSilently()
        }
    }

    companion object {
        lateinit var instance: ABDownloaderApplication
            private set
    }
}
