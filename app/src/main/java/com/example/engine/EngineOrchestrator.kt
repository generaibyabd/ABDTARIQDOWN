package com.example.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.regex.Pattern

data class EngineDiagnosticLog(
    val url: String,
    val platform: SupportedPlatform,
    val engineUsed: String,
    val success: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

class EngineOrchestrator(
    val newPipeEngine: NewPipeEngine = NewPipeEngine(),
    val ytDlpEngine: YtDlpEngine = YtDlpEngine()
) {
    private val _diagnosticLogs = MutableStateFlow<List<EngineDiagnosticLog>>(emptyList())
    val diagnosticLogs: StateFlow<List<EngineDiagnosticLog>> = _diagnosticLogs.asStateFlow()

    private val _lastUsedEngine = MutableStateFlow("NewPipeExtractor")
    val lastUsedEngine: StateFlow<String> = _lastUsedEngine.asStateFlow()

    /**
     * Validates if the given text contains EXACTLY ONE valid, supported link.
     * Returns the cleaned single URL if valid, or null if invalid, empty, or multi-link.
     */
    fun validateSingleSupportedUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Check if multiple URLs are present (e.g. separated by spaces, newlines, or multiple http/https)
        val urlCount = countUrls(trimmed)
        if (urlCount != 1) return null

        val singleUrl = extractFirstUrl(trimmed) ?: return null
        val lower = singleUrl.lowercase()

        // Check if from supported platforms or general media URL
        val isSupported = lower.contains("youtube.com") ||
                lower.contains("youtu.be") ||
                lower.contains("instagram.com") ||
                lower.contains("instagr.am") ||
                lower.contains("tiktok.com") ||
                lower.contains("twitter.com") ||
                lower.contains("x.com") ||
                lower.contains("facebook.com") ||
                lower.contains("fb.watch") ||
                lower.contains("reddit.com") ||
                lower.contains("redd.it") ||
                lower.contains("soundcloud.com") ||
                lower.contains("vimeo.com") ||
                lower.endsWith(".mp4") ||
                lower.endsWith(".m3u8") ||
                lower.endsWith(".mp3")

        return if (isSupported) singleUrl else null
    }

    private fun countUrls(text: String): Int {
        val pattern = Pattern.compile("https?://\\S+")
        val matcher = pattern.matcher(text)
        var count = 0
        while (matcher.find()) {
            count++
        }
        return count
    }

    private fun extractFirstUrl(text: String): String? {
        val pattern = Pattern.compile("https?://\\S+")
        val matcher = pattern.matcher(text)
        return if (matcher.find()) matcher.group(0) else null
    }

    /**
     * Resolves metadata with automatic dual-engine fallback:
     * - YouTube: NewPipeEngine first -> YtDlpEngine fallback
     * - Non-YouTube: YtDlpEngine
     */
    suspend fun resolveMedia(url: String): ExtractionResult {
        val platform = SupportedPlatform.detect(url)

        if (platform == SupportedPlatform.YOUTUBE) {
            // 1. Primary fast attempt for YouTube: NewPipeEngine
            val primaryResult = newPipeEngine.extract(url)
            if (primaryResult is ExtractionResult.Success) {
                recordDiagnostic(url, platform, newPipeEngine.name, true)
                _lastUsedEngine.value = newPipeEngine.name
                return primaryResult
            }

            // 2. Automatic fallback to YtDlpEngine
            val fallbackResult = ytDlpEngine.extract(url)
            if (fallbackResult is ExtractionResult.Success) {
                recordDiagnostic(url, platform, ytDlpEngine.name, true)
                _lastUsedEngine.value = ytDlpEngine.name
                return fallbackResult
            }

            // If both failed, report error
            recordDiagnostic(url, platform, "DualEngineFailed", false)
            return fallbackResult
        } else {
            // Non-YouTube platforms route through YtDlpEngine
            val result = ytDlpEngine.extract(url)
            if (result is ExtractionResult.Success) {
                recordDiagnostic(url, platform, ytDlpEngine.name, true)
                _lastUsedEngine.value = ytDlpEngine.name
                return result
            }

            // If ytDlp fails on SoundCloud, try NewPipe as fallback
            if (platform == SupportedPlatform.SOUNDCLOUD) {
                val soundcloudFallback = newPipeEngine.extract(url)
                if (soundcloudFallback is ExtractionResult.Success) {
                    recordDiagnostic(url, platform, newPipeEngine.name, true)
                    _lastUsedEngine.value = newPipeEngine.name
                    return soundcloudFallback
                }
            }

            recordDiagnostic(url, platform, ytDlpEngine.name, false)
            return result
        }
    }

    private fun recordDiagnostic(url: String, platform: SupportedPlatform, engineName: String, success: Boolean) {
        val log = EngineDiagnosticLog(url, platform, engineName, success)
        val current = _diagnosticLogs.value.toMutableList()
        current.add(0, log)
        if (current.size > 50) current.removeAt(current.lastIndex)
        _diagnosticLogs.value = current
    }
}
