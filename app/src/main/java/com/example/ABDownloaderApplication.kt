package com.example

import android.app.Application
import com.example.ads.AdMobManager
import com.example.data.db.AppDatabase
import com.example.data.repository.DownloadRepository
import com.example.download.DownloadEngine
import com.example.download.DownloadNotificationHelper
import com.example.engine.EngineOrchestrator
import com.example.engine.EngineUpdateManager
import com.example.settings.SettingsManager
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
        orchestrator = EngineOrchestrator()
        updateManager = EngineUpdateManager(this, orchestrator)
        downloadEngine = DownloadEngine(this, repository, settingsManager)

        DownloadNotificationHelper.createNotificationChannel(this)
        AdMobManager.initialize(this)

        // Silent once-per-day check on launch
        CoroutineScope(Dispatchers.IO).launch {
            updateManager.checkOnLaunchSilently()
        }
    }

    companion object {
        lateinit var instance: ABDownloaderApplication
            private set
    }
}
