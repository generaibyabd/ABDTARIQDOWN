package com.abdeveloper.abdownloader.engine

import android.content.Context
import com.abdeveloper.abdownloader.ABDownloaderApplication
import com.abdeveloper.abdownloader.storage.CookieManager
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
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

            // Real single media / photo post extraction via raw JSON dump
            val jsonRequest = YoutubeDLRequest(cleanUrl)
            jsonRequest.addOption("--no-playlist")
            jsonRequest.addOption("-j") // dump single-line JSON to stdout, don't download

            // Pass login cookies if configured for platform
            val cookieFile = cookieManager.getCookieFileForPlatform(platform)
            if (cookieFile != null) {
                jsonRequest.addOption("--cookies", cookieFile.absolutePath)
            }

            val rawJsonResponse = YoutubeDL.getInstance().execute(jsonRequest).out
            val jsonLines = rawJsonResponse.trim().lineSequence()
                .map { it.trim() }
                .filter { it.startsWith("{") && it.endsWith("}") }
                .toList()

            val jsonObjects = jsonLines.mapNotNull { line ->
                try {
                    JSONObject(line)
                } catch (_: Exception) {
                    null
                }
            }

            val rootJson = jsonObjects.lastOrNull()
                ?: JSONObject(rawJsonResponse.trim().lineSequence().last { it.isNotBlank() })

            val title = rootJson.optString("title").ifBlank {
                rootJson.optString("fulltitle")
            }.ifBlank { "${platform.displayName} Media" }

            var thumbnail = rootJson.optString("thumbnail")
            val durationSeconds = rootJson.optLong("duration", 0L)

            // Step 1: Detect photo/image posts across TikTok slideshows, Instagram carousels, Twitter photos, etc.
            val detectedPhotos = extractPhotos(rootJson, jsonObjects, platform)

            // Step 2: Parse raw formats for video and audio
            val rawFormatsJson = rootJson.optJSONArray("formats")
            val rawVideoCandidates = mutableListOf<VideoCandidate>()
            val rawAudioCandidates = mutableListOf<AudioCandidate>()

            if (rawFormatsJson != null) {
                for (i in 0 until rawFormatsJson.length()) {
                    val fmt = rawFormatsJson.optJSONObject(i) ?: continue
                    val formatId = fmt.optString("format_id").ifBlank { continue }
                    val ext = fmt.optString("ext", "mp4").lowercase()
                    val vcodec = fmt.optString("vcodec", "")
                    val acodec = fmt.optString("acodec", "")

                    val hasVideo = vcodec.isNotBlank() && vcodec != "none"
                    val hasAudio = acodec.isNotBlank() && acodec != "none"
                    val isAudioOnly = !hasVideo && hasAudio
                    val isVideoOnly = hasVideo && !hasAudio
                    val isMuxed = hasVideo && hasAudio

                    val filesize = when {
                        fmt.optLong("filesize", 0L) > 0 -> fmt.optLong("filesize", 0L)
                        fmt.optLong("filesize_approx", 0L) > 0 -> fmt.optLong("filesize_approx", 0L)
                        else -> 0L
                    }
                    val tbr = fmt.optInt("tbr", 0)
                    val abr = fmt.optInt("abr", 0)
                    val bitrate = if (tbr > 0) tbr else abr
                    val streamUrl = fmt.optString("url").ifBlank { cleanUrl }

                    if (isAudioOnly) {
                        val rawBitrate = when {
                            abr > 0 -> abr
                            tbr > 0 -> tbr
                            else -> 128
                        }
                        val roundedKbps = (((rawBitrate + 16) / 32) * 32).coerceAtLeast(64)
                        rawAudioCandidates.add(
                            AudioCandidate(
                                formatId = formatId,
                                ext = ext,
                                rawBitrate = rawBitrate,
                                roundedKbps = roundedKbps,
                                filesize = filesize,
                                streamUrl = streamUrl
                            )
                        )
                    } else if (isMuxed || isVideoOnly) {
                        val h = fmt.optInt("height", 0)
                        val rootH = rootJson.optInt("height", 0)
                        val note = fmt.optString("format_note").ifBlank { fmt.optString("format") }
                        val effectiveHeight = when {
                            h > 0 -> h
                            rootH > 0 -> rootH
                            else -> Regex("(\\d{3,4})p?").find(note)?.groupValues?.get(1)?.toIntOrNull() ?: 720
                        }
                        val fpsVal = fmt.optInt("fps", 30)
                        val fpsTier = if (fpsVal > 30) fpsVal else 0
                        val width = fmt.optInt("width", 0)

                        rawVideoCandidates.add(
                            VideoCandidate(
                                formatId = formatId,
                                ext = ext,
                                effectiveHeight = effectiveHeight,
                                fpsTier = fpsTier,
                                bitrate = bitrate,
                                filesize = filesize,
                                width = width,
                                fps = fpsVal,
                                streamUrl = streamUrl
                            )
                        )
                    }
                }
            }

            // Step 3: Video quality tier grouping & container preference (prefer mp4, keep single best per tier)
            val videosByTier = rawVideoCandidates.groupBy { "${it.effectiveHeight}_${it.fpsTier}" }
            val cleanVideoOptions = videosByTier.mapNotNull { (_, candidates) ->
                val hasMp4 = candidates.any { it.ext == "mp4" }
                val containerFiltered = if (hasMp4) {
                    candidates.filter { it.ext == "mp4" }
                } else {
                    candidates
                }

                val best = containerFiltered.maxWithOrNull(
                    compareBy<VideoCandidate> { it.bitrate }.thenBy { it.filesize }
                ) ?: return@mapNotNull null

                val height = best.effectiveHeight
                val fpsTier = best.fpsTier
                val ext = best.ext

                val baseLabel = if (fpsTier > 0) "${height}p ${fpsTier}FPS" else "${height}p"
                val label = "$baseLabel ($ext)"

                FormatOption(
                    id = best.formatId,
                    qualityLabel = label,
                    resolution = if (best.width > 0 && height > 0) "${best.width}x$height" else "${height}p",
                    fps = best.fps,
                    bitrateKbps = best.bitrate,
                    ext = ext,
                    filesizeBytes = best.filesize,
                    isAudio = false,
                    streamUrl = best.streamUrl
                )
            }.sortedWith(
                compareByDescending<FormatOption> {
                    Regex("(\\d+)p").find(it.qualityLabel)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                }.thenByDescending { it.fps }
            )

            // Step 4: Audio quality tier grouping & container preference (prefer m4a/mp3 over webm/opus)
            val audiosByTier = rawAudioCandidates.groupBy { it.roundedKbps }
            val cleanAudioOptions = audiosByTier.mapNotNull { (tierKbps, candidates) ->
                val hasCompatible = candidates.any { it.ext == "m4a" || it.ext == "mp3" }
                val containerFiltered = if (hasCompatible) {
                    candidates.filter { it.ext == "m4a" || it.ext == "mp3" }
                } else {
                    candidates
                }

                val best = containerFiltered.maxWithOrNull(
                    compareBy<AudioCandidate> { it.rawBitrate }.thenBy { it.filesize }
                ) ?: return@mapNotNull null

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
                    streamUrl = best.streamUrl
                )
            }.sortedByDescending { it.bitrateKbps }

            // Step 5: Check if this is a Photo Post
            val isPhotoPost = detectedPhotos.isNotEmpty() && (
                cleanVideoOptions.isEmpty() ||
                platform == SupportedPlatform.TIKTOK ||
                platform == SupportedPlatform.INSTAGRAM
            )

            val finalVideos = if (isPhotoPost) {
                emptyList()
            } else if (cleanVideoOptions.isEmpty() && cleanAudioOptions.isEmpty()) {
                val directUrl = rootJson.optString("url")
                val rootExt = rootJson.optString("ext", "mp4").lowercase()
                val isExplicitImage = isImageUrl(directUrl) || rootExt in setOf("jpg", "jpeg", "png", "webp")
                if (directUrl.isNotBlank() && !isExplicitImage) {
                    listOf(
                        FormatOption(
                            id = "best",
                            qualityLabel = "Best Quality ($rootExt)",
                            resolution = rootJson.optString("resolution", "Standard"),
                            fps = 30,
                            bitrateKbps = 0,
                            ext = rootExt,
                            filesizeBytes = if (rootJson.optLong("filesize", 0L) > 0) rootJson.optLong("filesize", 0L) else rootJson.optLong("filesize_approx", 0L),
                            isAudio = false,
                            streamUrl = directUrl
                        )
                    )
                } else emptyList()
            } else cleanVideoOptions

            val finalAudios = if (!isPhotoPost && cleanAudioOptions.isEmpty() && finalVideos.isNotEmpty()) {
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

            if (thumbnail.isBlank() && detectedPhotos.isNotEmpty()) {
                thumbnail = detectedPhotos.first().url
            }

            // Step 6: Validate that media exists
            if (finalVideos.isEmpty() && finalAudios.isEmpty() && detectedPhotos.isEmpty()) {
                return@withContext ExtractionResult.Failure("No downloadable media found at this link")
            }

            ExtractionResult.Success(
                ExtractedMedia(
                    title = title,
                    thumbnailUrl = thumbnail,
                    durationSeconds = durationSeconds,
                    platform = platform,
                    originalUrl = cleanUrl,
                    videoFormats = finalVideos,
                    audioFormats = finalAudios,
                    photos = if (isPhotoPost) detectedPhotos else emptyList(),
                    playlistVideos = emptyList(),
                    isPlaylist = false,
                    isPhotoPost = isPhotoPost,
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

    private fun extractPhotos(
        rootJson: JSONObject,
        jsonObjects: List<JSONObject>,
        platform: SupportedPlatform
    ): List<PhotoOption> {
        val photoUrls = linkedSetOf<String>()

        // 1. TikTok slideshow mode: rootJson.optJSONArray("images") or image_post_info.images
        val tiktokImages = rootJson.optJSONArray("images")
            ?: rootJson.optJSONObject("image_post_info")?.optJSONArray("images")
        if (tiktokImages != null && tiktokImages.length() > 0) {
            for (i in 0 until tiktokImages.length()) {
                val item = tiktokImages.opt(i)
                val url = extractImageUrlFromAny(item)
                if (url.isNotBlank()) {
                    photoUrls.add(url)
                }
            }
        }

        // 2. Multi-line JSON output (yt-dlp dumped multiple entries, e.g. Instagram carousel / tweet photos)
        if (photoUrls.isEmpty() && jsonObjects.size > 1) {
            for (obj in jsonObjects) {
                val url = extractImageUrlFromObject(obj)
                if (url.isNotBlank()) {
                    photoUrls.add(url)
                }
            }
        }

        // 3. rootJson.optJSONArray("entries") (Instagram carousel, Reddit gallery)
        if (photoUrls.isEmpty()) {
            val entries = rootJson.optJSONArray("entries")
            if (entries != null && entries.length() > 0) {
                for (i in 0 until entries.length()) {
                    val entryObj = entries.optJSONObject(i) ?: continue
                    val url = extractImageUrlFromObject(entryObj)
                    if (url.isNotBlank()) {
                        photoUrls.add(url)
                    }
                }
            }
        }

        // 4. Check if root is a single image or formats are strictly image formats
        if (photoUrls.isEmpty()) {
            val formats = rootJson.optJSONArray("formats")
            val isVideo = rootJson.optBoolean("is_video", true)
            val vcodec = rootJson.optString("vcodec", "")
            val ext = rootJson.optString("ext", "").lowercase()
            val isExplicitImageExt = ext in setOf("jpg", "jpeg", "png", "webp", "gif")

            val hasOnlyImageFormats = if (formats != null && formats.length() > 0) {
                var imageFmtCount = 0
                var videoFmtCount = 0
                for (i in 0 until formats.length()) {
                    val fmt = formats.optJSONObject(i) ?: continue
                    val fVcodec = fmt.optString("vcodec", "")
                    val fExt = fmt.optString("ext", "").lowercase()
                    if (fVcodec.isNotBlank() && fVcodec != "none") {
                        videoFmtCount++
                    } else if (fExt in setOf("jpg", "jpeg", "png", "webp")) {
                        imageFmtCount++
                    }
                }
                videoFmtCount == 0 && imageFmtCount > 0
            } else false

            if (isExplicitImageExt || hasOnlyImageFormats || (!isVideo && vcodec == "none")) {
                val bestUrl = extractImageUrlFromObject(rootJson)
                if (bestUrl.isNotBlank()) {
                    photoUrls.add(bestUrl)
                }
            }
        }

        return photoUrls.mapIndexed { index, url ->
            PhotoOption(
                id = "photo_${index + 1}",
                url = url,
                previewUrl = url,
                index = index + 1,
                isSelected = true
            )
        }
    }

    private fun extractImageUrlFromAny(item: Any?): String {
        return when (item) {
            is String -> item.trim()
            is JSONObject -> extractImageUrlFromObject(item)
            else -> ""
        }
    }

    private fun extractImageUrlFromObject(obj: JSONObject): String {
        // 1. Direct url
        val directUrl = obj.optString("url").trim()
        val ext = obj.optString("ext").lowercase()
        if (directUrl.isNotBlank() && (isImageUrl(directUrl) || ext in setOf("jpg", "jpeg", "png", "webp"))) {
            return directUrl
        }

        // 2. url_list array (TikTok)
        val urlList = obj.optJSONArray("url_list")
        if (urlList != null && urlList.length() > 0) {
            for (i in 0 until urlList.length()) {
                val u = urlList.optString(i).trim()
                if (u.isNotBlank()) return u
            }
        }

        // 3. image_url or display_url
        val imageUrl = obj.optString("image_url").trim()
        if (imageUrl.isNotBlank()) return imageUrl
        val displayUrl = obj.optString("display_url").trim()
        if (displayUrl.isNotBlank()) return displayUrl

        // 4. formats array (best image format)
        val formats = obj.optJSONArray("formats")
        if (formats != null && formats.length() > 0) {
            var bestFmtUrl = ""
            var maxRes = 0
            for (i in 0 until formats.length()) {
                val fmt = formats.optJSONObject(i) ?: continue
                val fUrl = fmt.optString("url").trim()
                val fExt = fmt.optString("ext").lowercase()
                val fVcodec = fmt.optString("vcodec", "")
                if (fUrl.isNotBlank() && (fExt in setOf("jpg", "jpeg", "png", "webp") || fVcodec == "none")) {
                    val height = fmt.optInt("height", 0)
                    val width = fmt.optInt("width", 0)
                    val res = height * width
                    if (res >= maxRes) {
                        maxRes = res
                        bestFmtUrl = fUrl
                    }
                }
            }
            if (bestFmtUrl.isNotBlank()) return bestFmtUrl
        }

        // 5. thumbnails array (highest resolution thumbnail)
        val thumbnails = obj.optJSONArray("thumbnails")
        if (thumbnails != null && thumbnails.length() > 0) {
            var bestThumbUrl = ""
            var maxPref = -1
            for (i in 0 until thumbnails.length()) {
                val t = thumbnails.optJSONObject(i) ?: continue
                val tUrl = t.optString("url").trim()
                val pref = t.optInt("preference", i)
                if (tUrl.isNotBlank() && pref >= maxPref) {
                    maxPref = pref
                    bestThumbUrl = tUrl
                }
            }
            if (bestThumbUrl.isNotBlank()) return bestThumbUrl
        }

        // 6. thumbnail string
        val thumbnail = obj.optString("thumbnail").trim()
        if (thumbnail.isNotBlank()) return thumbnail

        return if (directUrl.isNotBlank()) directUrl else ""
    }

    private fun isImageUrl(url: String): Boolean {
        val clean = url.lowercase().split("?").firstOrNull().orEmpty()
        return clean.endsWith(".jpg") || clean.endsWith(".jpeg") ||
                clean.endsWith(".png") || clean.endsWith(".webp") ||
                clean.endsWith(".gif")
    }

    private fun formatFriendlyError(e: Throwable, platform: SupportedPlatform, cookieManager: CookieManager): String {
        val rawMessage = e.localizedMessage ?: e.message ?: "Failed to extract media"
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
                return "This content may require login. You can log into your ${platform.displayName} account in Settings to fix this."
            }
        }

        return "yt-dlp extraction error: $rawMessage"
    }

    private data class VideoCandidate(
        val formatId: String,
        val ext: String,
        val effectiveHeight: Int,
        val fpsTier: Int,
        val bitrate: Int,
        val filesize: Long,
        val width: Int = 0,
        val fps: Int = 30,
        val streamUrl: String
    )

    private data class AudioCandidate(
        val formatId: String,
        val ext: String,
        val rawBitrate: Int,
        val roundedKbps: Int,
        val filesize: Long,
        val streamUrl: String
    )
}
