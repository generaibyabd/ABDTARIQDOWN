package com.example.engine

import android.content.Context
import com.example.ABDownloaderApplication
import com.example.storage.CookieManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class YtDlpEngine(
    private val context: Context = ABDownloaderApplication.instance
) : ExtractionEngine {

    override val name: String = "yt-dlp"
    override var version: String = try {
        YoutubeDL.getInstance().version(context) ?: "2025.02.19"
    } catch (_: Exception) {
        "2025.02.19"
    }
    override val isBundled: Boolean = true

    override suspend fun supportsUrl(url: String): Boolean = true

    override suspend fun runSelfTest(): Boolean = withContext(Dispatchers.IO) {
        try {
            val ver = YoutubeDL.getInstance().version(context)
            !ver.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun extract(url: String): ExtractionResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val platform = SupportedPlatform.detect(cleanUrl)
        val cookieManager = CookieManager.getInstance(context)

        try {
            // Detect playlist URLs
            val isPlaylistUrl = cleanUrl.contains("playlist?list=") ||
                    (cleanUrl.contains("&list=") && !cleanUrl.contains("watch?v="))

            if (isPlaylistUrl) {
                return@withContext extractPlaylist(cleanUrl, platform)
            }

            // Real single media extraction via YoutubeDL
            val request = YoutubeDLRequest(cleanUrl)
            request.addOption("--no-playlist")

            // Pass login cookies if configured for platform
            val cookieFile = cookieManager.getCookieFileForPlatform(platform)
            if (cookieFile != null) {
                request.addOption("--cookies", cookieFile.absolutePath)
            }

            val videoInfo: VideoInfo = YoutubeDL.getInstance().getInfo(request)

            val title = videoInfo.title?.ifBlank { null }
                ?: videoInfo.fulltitle?.ifBlank { null }
                ?: "${platform.displayName} Media"

            val thumbnail = videoInfo.thumbnail.orEmpty()
            val durationSeconds = videoInfo.duration.toLong()

            val rawFormats: List<VideoFormat> = videoInfo.formats ?: emptyList()

            // 1. Separate candidates
            val rawVideoCandidates = mutableListOf<VideoCandidate>()
            val rawAudioCandidates = mutableListOf<AudioCandidate>()

            for (fmt in rawFormats) {
                val formatId = fmt.formatId ?: continue
                val ext = fmt.ext?.lowercase() ?: "mp4"
                val vcodec = fmt.vcodec
                val acodec = fmt.acodec

                val hasVideo = vcodec != null && vcodec != "none"
                val hasAudio = acodec != null && acodec != "none"
                val isAudioOnly = !hasVideo && hasAudio
                val isVideoOnly = hasVideo && !hasAudio
                val isMuxed = hasVideo && hasAudio

                val filesize = if (fmt.fileSize > 0) fmt.fileSize else fmt.fileSizeApproximate
                val bitrate = if (fmt.tbr > 0) fmt.tbr else fmt.abr

                if (isAudioOnly) {
                    val rawBitrate = when {
                        fmt.abr > 0 -> fmt.abr
                        fmt.tbr > 0 -> fmt.tbr
                        else -> 128
                    }
                    val roundedKbps = (((rawBitrate + 16) / 32) * 32).coerceAtLeast(64)
                    rawAudioCandidates.add(
                        AudioCandidate(
                            fmt = fmt,
                            formatId = formatId,
                            ext = ext,
                            rawBitrate = rawBitrate,
                            roundedKbps = roundedKbps,
                            filesize = filesize
                        )
                    )
                } else if (isMuxed || isVideoOnly) {
                    val effectiveHeight = when {
                        fmt.height > 0 -> fmt.height
                        videoInfo.height > 0 -> videoInfo.height
                        else -> {
                            val note = fmt.formatNote ?: fmt.format ?: ""
                            Regex("(\\d{3,4})p?").find(note)?.groupValues?.get(1)?.toIntOrNull() ?: 720
                        }
                    }
                    val fpsTier = if (fmt.fps > 30) fmt.fps.toInt() else 0
                    rawVideoCandidates.add(
                        VideoCandidate(
                            fmt = fmt,
                            formatId = formatId,
                            ext = ext,
                            effectiveHeight = effectiveHeight,
                            fpsTier = fpsTier,
                            bitrate = bitrate,
                            filesize = filesize
                        )
                    )
                }
            }

            // 2. Video quality tier grouping & container preference (prefer mp4, keep single best per tier)
            val videosByTier = rawVideoCandidates.groupBy { "${it.effectiveHeight}_${it.fpsTier}" }
            val cleanVideoOptions = videosByTier.mapNotNull { (_, candidates) ->
                // Within each tier key, prefer mp4: if at least one candidate has ext == "mp4", discard non-mp4
                val hasMp4 = candidates.any { it.ext == "mp4" }
                val containerFiltered = if (hasMp4) {
                    candidates.filter { it.ext == "mp4" }
                } else {
                    candidates
                }

                // Within each tier key, keep only single best candidate (highest bitrate, then largest filesize)
                val best = containerFiltered.maxWithOrNull(
                    compareBy<VideoCandidate> { it.bitrate }.thenBy { it.filesize }
                ) ?: return@mapNotNull null

                val fmt = best.fmt
                val height = best.effectiveHeight
                val fpsTier = best.fpsTier
                val ext = best.ext

                // Label without FPS suffix when fpsTier == 0, and never saying "(Video Only)"
                val baseLabel = if (fpsTier > 0) "${height}p ${fpsTier}FPS" else "${height}p"
                val label = "$baseLabel ($ext)"

                FormatOption(
                    id = best.formatId,
                    qualityLabel = label,
                    resolution = if (fmt.width > 0 && height > 0) "${fmt.width}x$height" else "${height}p",
                    fps = fmt.fps,
                    bitrateKbps = best.bitrate,
                    ext = ext,
                    filesizeBytes = best.filesize,
                    isAudio = false,
                    streamUrl = fmt.url ?: cleanUrl
                )
            }.sortedWith(
                compareByDescending<FormatOption> {
                    Regex("(\\d+)p").find(it.qualityLabel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                }.thenByDescending { it.fps }
            )

            // 3. Audio quality tier grouping & container preference (prefer m4a/mp3 over webm/opus, keep single best per tier)
            val audiosByTier = rawAudioCandidates.groupBy { it.roundedKbps }
            val cleanAudioOptions = audiosByTier.mapNotNull { (tierKbps, candidates) ->
                // Prefer m4a / mp3 over webm / opus for consistent playback compatibility
                val hasCompatible = candidates.any { it.ext == "m4a" || it.ext == "mp3" }
                val containerFiltered = if (hasCompatible) {
                    candidates.filter { it.ext == "m4a" || it.ext == "mp3" }
                } else {
                    candidates
                }

                val best = containerFiltered.maxWithOrNull(
                    compareBy<AudioCandidate> { it.rawBitrate }.thenBy { it.filesize }
                ) ?: return@mapNotNull null

                val fmt = best.fmt
                val ext = best.ext
                val label = "$tierKbps kbps ($ext)"

                FormatOption(
                    id = best.formatId,
                    qualityLabel = label,
                    resolution = "Audio",
                    fps = 0,
                    bitrateKbps = tierKbps,
                    ext = ext,
                    filesizeBytes = best.filesize,
                    isAudio = true,
                    streamUrl = fmt.url ?: cleanUrl
                )
            }.sortedByDescending { it.bitrateKbps }

            // Fallback for direct media streams or simple sites where no specific format list was extracted:
            val finalVideos = if (cleanVideoOptions.isEmpty() && cleanAudioOptions.isEmpty()) {
                listOf(
                    FormatOption(
                        id = "best",
                        qualityLabel = "Best Quality (${videoInfo.ext ?: "mp4"})",
                        resolution = videoInfo.resolution ?: "Standard",
                        fps = 30,
                        bitrateKbps = 0,
                        ext = videoInfo.ext ?: "mp4",
                        filesizeBytes = if (videoInfo.fileSize > 0) videoInfo.fileSize else videoInfo.fileSizeApproximate,
                        isAudio = false,
                        streamUrl = videoInfo.url ?: cleanUrl
                    )
                )
            } else cleanVideoOptions

            val finalAudios = if (cleanAudioOptions.isEmpty() && finalVideos.isNotEmpty()) {
                listOf(
                    FormatOption(
                        id = "bestaudio",
                        qualityLabel = "Best Audio (mp3)",
                        resolution = "Audio",
                        fps = 0,
                        bitrateKbps = 320,
                        ext = "mp3",
                        filesizeBytes = 0L,
                        isAudio = true,
                        streamUrl = cleanUrl
                    )
                )
            } else cleanAudioOptions

            ExtractionResult.Success(
                ExtractedMedia(
                    title = title,
                    thumbnailUrl = thumbnail,
                    durationSeconds = durationSeconds,
                    platform = platform,
                    originalUrl = cleanUrl,
                    videoFormats = finalVideos,
                    audioFormats = finalAudios,
                    photos = emptyList(),
                    playlistVideos = emptyList(),
                    isPlaylist = false,
                    isPhotoPost = false,
                    engineUsed = name
                )
            )
        } catch (e: Exception) {
            val friendlyError = formatFriendlyError(e, platform, cookieManager)
            ExtractionResult.Failure(friendlyError)
        }
    }

    private suspend fun extractPlaylist(url: String, platform: SupportedPlatform): ExtractionResult = withContext(Dispatchers.IO) {
        val cookieManager = CookieManager.getInstance(context)
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--flat-playlist")
            request.addOption("-J")

            val cookieFile = cookieManager.getCookieFileForPlatform(platform)
            if (cookieFile != null) {
                request.addOption("--cookies", cookieFile.absolutePath)
            }

            val response = YoutubeDL.getInstance().execute(request)
            val json = JSONObject(response.out)

            val playlistTitle = json.optString("title").ifBlank { "${platform.displayName} Playlist" }
            val entries = json.optJSONArray("entries") ?: JSONArray()
            val playlistItems = mutableListOf<PlaylistItemOption>()

            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                val itemId = item.optString("id", "item_$i")
                val itemTitle = item.optString("title", "Item ${i + 1}")
                val itemUrl = item.optString("url").ifBlank {
                    if (platform == SupportedPlatform.YOUTUBE) "https://www.youtube.com/watch?v=$itemId" else url
                }
                val itemDurationSec = item.optLong("duration", 0L)
                val durFormatted = if (itemDurationSec > 0) {
                    val m = itemDurationSec / 60
                    val s = itemDurationSec % 60
                    String.format("%d:%02d", m, s)
                } else ""
                val itemThumb = item.optString("thumbnail").ifBlank {
                    if (platform == SupportedPlatform.YOUTUBE) "https://img.youtube.com/vi/$itemId/mqdefault.jpg" else ""
                }

                playlistItems.add(
                    PlaylistItemOption(
                        id = itemId,
                        title = itemTitle,
                        url = itemUrl,
                        durationFormatted = durFormatted,
                        thumbnailUrl = itemThumb,
                        isSelected = true,
                        index = i + 1
                    )
                )
            }

            if (playlistItems.isEmpty()) {
                return@withContext ExtractionResult.Failure("No playlist videos found at this URL")
            }

            ExtractionResult.Success(
                ExtractedMedia(
                    title = playlistTitle,
                    thumbnailUrl = playlistItems.firstOrNull()?.thumbnailUrl.orEmpty(),
                    durationSeconds = 0L,
                    platform = platform,
                    originalUrl = url,
                    videoFormats = listOf(
                        FormatOption("best", "Best Video (mp4)", "Auto", 30, 0, "mp4", 0L, false, url),
                        FormatOption("bestaudio", "Best Audio (mp3)", "Audio", 0, 320, "mp3", 0L, true, url)
                    ),
                    audioFormats = emptyList(),
                    photos = emptyList(),
                    playlistVideos = playlistItems,
                    isPlaylist = true,
                    isPhotoPost = false,
                    engineUsed = name
                )
            )
        } catch (e: Exception) {
            val friendlyError = formatFriendlyError(e, platform, cookieManager)
            ExtractionResult.Failure(friendlyError)
        }
    }

    private fun formatFriendlyError(e: Throwable, platform: SupportedPlatform, cookieManager: CookieManager): String {
        val rawMessage = e.localizedMessage ?: e.message ?: "Failed to extract streams"
        val lower = rawMessage.lowercase()

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

        if (isLoginOrRateLimit && (platform == SupportedPlatform.INSTAGRAM || platform == SupportedPlatform.TIKTOK || platform == SupportedPlatform.TWITTER)) {
            if (!cookieManager.hasCookiesForPlatform(platform)) {
                return "This content may require login. You can add your ${platform.displayName} cookies in Settings to fix this."
            }
        }

        return "yt-dlp extraction error: $rawMessage"
    }

    private data class VideoCandidate(
        val fmt: VideoFormat,
        val formatId: String,
        val ext: String,
        val effectiveHeight: Int,
        val fpsTier: Int,
        val bitrate: Int,
        val filesize: Long
    )

    private data class AudioCandidate(
        val fmt: VideoFormat,
        val formatId: String,
        val ext: String,
        val rawBitrate: Int,
        val roundedKbps: Int,
        val filesize: Long
    )
}
