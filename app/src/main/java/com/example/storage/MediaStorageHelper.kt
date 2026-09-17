package com.example.storage

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.db.MediaType
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
            val contentUri = when (mediaType) {
                MediaType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                MediaType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                MediaType.PHOTO -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                MediaType.PLAYLIST -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subfolder")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val uri = resolver.insert(contentUri, values)
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
