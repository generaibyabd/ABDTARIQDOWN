package com.example.engine

import android.content.Context
import com.example.ABDownloaderApplication
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
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
        try {
            val cleanUrl = url.trim()
            val platform = SupportedPlatform.detect(cleanUrl)

            // Detect playlist URLs
            val isPlaylistUrl = cleanUrl.contains("playlist?list=") ||
                    (cleanUrl.contains("&list=") && !cleanUrl.contains("watch?v="))

            if (isPlaylistUrl) {
                return@withContext extractPlaylist(cleanUrl, platform)
            }

            // Real single media extraction via YoutubeDL
            val request = YoutubeDLRequest(cleanUrl)
            request.addOption("--no-playlist")
            val videoInfo: VideoInfo = YoutubeDL.getInstance().getInfo(request)

            val title = videoInfo.title?.ifBlank { null }
                ?: videoInfo.fulltitle?.ifBlank { null }
                ?: "${platform.displayName} Media"

            val thumbnail = videoInfo.thumbnail.orEmpty()
            val durationSeconds = videoInfo.duration.toLong()

            val rawFormats = videoInfo.formats ?: emptyList()
            val videoOptions = mutableListOf<FormatOption>()
            val audioOptions = mutableListOf<FormatOption>()

            for (fmt in rawFormats) {
                val formatId = fmt.formatId ?: continue
                val ext = fmt.ext ?: "mp4"
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
                    val abrStr = if (fmt.abr > 0) "${fmt.abr} kbps" else if (fmt.tbr > 0) "${fmt.tbr} kbps" else "Audio"
                    val label = "$abrStr ($ext)"
                    audioOptions.add(
                        FormatOption(
                            id = formatId,
                            qualityLabel = label,
                            resolution = "Audio",
                            fps = 0,
                            bitrateKbps = bitrate,
                            ext = ext,
                            filesizeBytes = filesize,
                            isAudio = true,
                            streamUrl = fmt.url ?: cleanUrl
                        )
                    )
                } else if (isMuxed || isVideoOnly) {
                    val resLabel = if (fmt.height > 0) "${fmt.height}p" else (fmt.formatNote ?: fmt.format ?: "Video")
                    val fpsStr = if (fmt.fps > 30) " ${fmt.fps}fps" else ""
                    val noteStr = if (isVideoOnly) " (Video Only)" else ""
                    val label = "$resLabel$fpsStr ($ext)$noteStr"
                    videoOptions.add(
                        FormatOption(
                            id = formatId,
                            qualityLabel = label,
                            resolution = if (fmt.width > 0 && fmt.height > 0) "${fmt.width}x${fmt.height}" else resLabel,
                            fps = fmt.fps,
                            bitrateKbps = bitrate,
                            ext = ext,
                            filesizeBytes = filesize,
                            isAudio = false,
                            streamUrl = fmt.url ?: cleanUrl
                        )
                    )
                }
            }

            // Deduplicate formats
            val distinctVideos = videoOptions.distinctBy { "${it.resolution}_${it.fps}_${it.ext}_${it.id}" }
                .sortedByDescending { it.bitrateKbps }
            val distinctAudios = audioOptions.distinctBy { "${it.bitrateKbps}_${it.ext}_${it.id}" }
                .sortedByDescending { it.bitrateKbps }

            // If no specific formats were parsed (e.g. direct media stream or simple site):
            val finalVideos = if (distinctVideos.isEmpty() && distinctAudios.isEmpty()) {
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
            } else distinctVideos

            val finalAudios = if (distinctAudios.isEmpty() && finalVideos.isNotEmpty()) {
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
            } else distinctAudios

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
            ExtractionResult.Failure("yt-dlp extraction error: ${e.localizedMessage ?: "Failed to extract streams"}")
        }
    }

    private suspend fun extractPlaylist(url: String, platform: SupportedPlatform): ExtractionResult = withContext(Dispatchers.IO) {
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("--flat-playlist")
            request.addOption("-J")
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
            ExtractionResult.Failure("Playlist extraction error: ${e.localizedMessage ?: "Unknown error"}")
        }
    }
}
