package com.abdeveloper.abdownloader.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.abdeveloper.abdownloader.data.db.MediaType
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object MediaStorageHelper {

    private const val BASE_FOLDER = "ABDownloader_Media"
    const val FOLDER_VIDEOS = "$BASE_FOLDER/Videos"
    const val FOLDER_AUDIOS = "$BASE_FOLDER/Audios"
    const val FOLDER_PHOTOS = "$BASE_FOLDER/Photos"

    fun getSubfolderName(mediaType: MediaType): String {
        return when (mediaType) {
            MediaType.VIDEO -> FOLDER_VIDEOS
            MediaType.AUDIO -> FOLDER_AUDIOS
            MediaType.PHOTO -> FOLDER_PHOTOS
            MediaType.PLAYLIST -> FOLDER_VIDEOS
        }
    }

    /**
     * Prepares target output stream via MediaStore (API 29+) or public external storage.
     */
    fun createMediaOutputStream(
        context: Context,
        fileName: String,
        mimeType: String,
        mediaType: MediaType
    ): Pair<OutputStream?, String> {
        val subfolder = getSubfolderName(mediaType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subfolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            var uri = try {
                resolver.insert(contentUri, values)
            } catch (_: Exception) { null }

            if (uri == null) {
                val fallbackUri = when (mediaType) {
                    MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    MediaType.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    MediaType.PLAYLIST -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                uri = try {
                    resolver.insert(fallbackUri, values)
                } catch (_: Exception) { null }
            }

            if (uri != null) {
                val os = resolver.openOutputStream(uri)
                return Pair(os, uri.toString())
            }
        }

        // Fallback for older APIs or if MediaStore insert didn't succeed
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, subfolder)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, fileName)
        return Pair(FileOutputStream(targetFile), targetFile.absolutePath)
    }

    /**
     * App-specific private staging directory, always writable across all Android versions
     * without needing Scoped Storage or MANAGE_EXTERNAL_STORAGE permissions.
     */
    fun getStagingDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "staging")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Cleans up orphaned or lingering files in private staging older than [maxAgeMillis].
     */
    fun cleanStagingDirectory(context: Context, maxAgeMillis: Long = 24 * 60 * 60 * 1000L) {
        try {
            val stagingDir = getStagingDirectory(context)
            val now = System.currentTimeMillis()
            stagingDir.listFiles()?.forEach { file ->
                if (file.isFile && (now - file.lastModified() > maxAgeMillis)) {
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Moves/copies a finished file from private staging to public MediaStore storage under Downloads/ABDownloader_Media/...
     * Deletes the source staging file on completion and returns the final content:// URI (or path).
     */
    fun publishFileToMediaStore(
        context: Context,
        sourceFile: File,
        mediaType: MediaType,
        mimeType: String
    ): String {
        if (!sourceFile.exists()) {
            throw java.io.FileNotFoundException("Staged source file not found: ${sourceFile.absolutePath}")
        }

        val fileName = sourceFile.name
        val subfolder = getSubfolderName(mediaType)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subfolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            var uri = try {
                resolver.insert(contentUri, values)
            } catch (_: Exception) { null }

            if (uri == null) {
                val fallbackUri = when (mediaType) {
                    MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                    MediaType.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    MediaType.PLAYLIST -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                }
                uri = try {
                    resolver.insert(fallbackUri, values)
                } catch (_: Exception) { null }
            }

            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        sourceFile.inputStream().use { inputStream ->
                            inputStream.copyTo(outputStream, bufferSize = 64 * 1024)
                        }
                        outputStream.flush()
                    } ?: throw Exception("Failed to open output stream for MediaStore uri: $uri")

                    finalizeMediaStoreItem(context, uri.toString())

                    // Delete the redundant private staging file
                    try {
                        sourceFile.delete()
                    } catch (_: Exception) {}

                    return uri.toString()
                } catch (e: Exception) {
                    try {
                        resolver.delete(uri, null, null)
                    } catch (_: Exception) {}
                    throw e
                }
            }
        }

        // Fallback for older APIs (< 29) or if MediaStore insert was unavailable
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, subfolder)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, fileName)
        sourceFile.inputStream().use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 64 * 1024)
            }
        }
        try {
            sourceFile.delete()
        } catch (_: Exception) {}

        scanMediaFile(context, targetFile.absolutePath, mimeType)
        return targetFile.absolutePath
    }

    fun getTargetMediaFolder(mediaType: MediaType): File {
        val subfolder = getSubfolderName(mediaType)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, subfolder)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
    }

    fun scanMediaFile(context: Context, filePath: String, mimeType: String) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(filePath),
                arrayOf(mimeType),
                null
            )
        } catch (_: Exception) {}
    }

    /**
     * Marks pending file as complete in MediaStore so device media players and gallery index it.
     */
    fun finalizeMediaStoreItem(context: Context, pathOrUri: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && pathOrUri.startsWith("content://")) {
            val resolver = context.contentResolver
            val uri = Uri.parse(pathOrUri)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            try {
                resolver.update(uri, values, null, null)
            } catch (_: Exception) {}
        }
    }

    /**
     * Deletes physical file given path or content Uri.
     */
    fun deletePhysicalFile(context: Context, pathOrUri: String): Boolean {
        return try {
            if (pathOrUri.startsWith("content://")) {
                context.contentResolver.delete(Uri.parse(pathOrUri), null, null) > 0
            } else {
                val file = File(pathOrUri)
                if (file.exists()) file.delete() else true
            }
        } catch (_: Exception) {
            false
        }
    }
}
