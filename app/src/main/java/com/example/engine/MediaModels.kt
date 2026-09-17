package com.example.engine

enum class SupportedPlatform(val displayName: String, val badgeColor: Long) {
    YOUTUBE("YouTube", 0xFFFF0000),
    INSTAGRAM("Instagram", 0xFFE1306C),
    TIKTOK("TikTok", 0xFF00F2FE),
    TWITTER("Twitter / X", 0xFF1DA1F2),
    FACEBOOK("Facebook", 0xFF1877F2),
    REDDIT("Reddit", 0xFFFF4500),
    SOUNDCLOUD("SoundCloud", 0xFFFF5500),
    VIMEO("Vimeo", 0xFF1AB7EA),
    GENERIC("Web Media", 0xFF3785D2);

    companion object {
        fun detect(url: String): SupportedPlatform {
            val lower = url.lowercase().trim()
            return when {
                lower.contains("youtube.com") || lower.contains("youtu.be") -> YOUTUBE
                lower.contains("instagram.com") || lower.contains("instagr.am") -> INSTAGRAM
                lower.contains("tiktok.com") -> TIKTOK
                lower.contains("twitter.com") || lower.contains("x.com") -> TWITTER
                lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com") -> FACEBOOK
                lower.contains("reddit.com") || lower.contains("redd.it") -> REDDIT
                lower.contains("soundcloud.com") -> SOUNDCLOUD
                lower.contains("vimeo.com") -> VIMEO
                else -> GENERIC
            }
        }
    }
}

data class FormatOption(
    val id: String,
    val qualityLabel: String,
    val resolution: String = "",
    val fps: Int = 30,
    val bitrateKbps: Int = 0,
    val ext: String = "mp4",
    val filesizeBytes: Long = 0L,
    val isAudio: Boolean = false,
    val streamUrl: String
)

data class PhotoOption(
    val id: String,
    val url: String,
    val previewUrl: String = url,
    val index: Int = 0,
    val isSelected: Boolean = true
)

data class PlaylistItemOption(
    val id: String,
    val title: String,
    val url: String,
    val durationFormatted: String = "",
    val thumbnailUrl: String = "",
    val isSelected: Boolean = true,
    val index: Int = 0
)

data class ExtractedMedia(
    val title: String,
    val thumbnailUrl: String,
    val durationSeconds: Long = 0L,
    val platform: SupportedPlatform,
    val originalUrl: String,
    val videoFormats: List<FormatOption> = emptyList(),
    val audioFormats: List<FormatOption> = emptyList(),
    val photos: List<PhotoOption> = emptyList(),
    val playlistVideos: List<PlaylistItemOption> = emptyList(),
    val isPlaylist: Boolean = false,
    val isPhotoPost: Boolean = false,
    val engineUsed: String
)

sealed interface ExtractionResult {
    data class Success(val media: ExtractedMedia) : ExtractionResult
    data class Failure(val error: String) : ExtractionResult
}
