package com.example.storage

import android.content.Context
import android.net.Uri
import com.example.engine.SupportedPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class CookieManager(private val context: Context) {

    private val cookiesDir: File
        get() = File(context.filesDir, "cookies").apply {
            if (!exists()) {
                mkdirs()
            }
        }

    private val _cookieState = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val cookieState: StateFlow<Map<String, Boolean>> = _cookieState.asStateFlow()

    init {
        refreshState()
    }

    fun refreshState() {
        val state = mutableMapOf<String, Boolean>()
        for (platformKey in PLATFORMS) {
            val file = getCookieFile(platformKey)
            state[platformKey] = file.exists() && file.length() > 0
        }
        _cookieState.value = state
    }

    fun getCookieFile(platformKey: String): File {
        return File(cookiesDir, "${platformKey.lowercase()}_cookies.txt")
    }

    fun getCookieFileForPlatform(platform: SupportedPlatform): File? {
        val key = when (platform) {
            SupportedPlatform.INSTAGRAM -> "instagram"
            SupportedPlatform.TIKTOK -> "tiktok"
            SupportedPlatform.TWITTER -> "twitter"
            SupportedPlatform.YOUTUBE -> "youtube"
            else -> null
        } ?: return null

        val file = getCookieFile(key)
        return if (file.exists() && file.length() > 0) file else null
    }

    fun hasCookiesForPlatform(platform: SupportedPlatform): Boolean {
        return getCookieFileForPlatform(platform) != null
    }

    fun importCookies(platformKey: String, uri: Uri): Boolean {
        return try {
            val targetFile = getCookieFile(platformKey)
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            refreshState()
            targetFile.exists() && targetFile.length() > 0
        } catch (_: Exception) {
            false
        }
    }

    fun removeCookies(platformKey: String): Boolean {
        return try {
            val file = getCookieFile(platformKey)
            val deleted = if (file.exists()) file.delete() else true
            refreshState()
            deleted
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        val PLATFORMS = listOf("instagram", "tiktok", "twitter")

        @Volatile
        private var INSTANCE: CookieManager? = null

        fun getInstance(context: Context): CookieManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CookieManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
