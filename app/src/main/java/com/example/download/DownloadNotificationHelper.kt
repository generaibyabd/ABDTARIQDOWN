package com.example.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

object DownloadNotificationHelper {

    const val CHANNEL_ID = "abdownloader_channel"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Downloads"
            val descriptionText = "Notifications for active downloads and progress"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun buildDownloadNotification(
        context: Context,
        title: String,
        progressPercent: Int,
        speedText: String,
        isPaused: Boolean
    ): NotificationCompat.Builder {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val status = if (isPaused) "Paused (Waiting for Wi-Fi or User)" else "$progressPercent% • $speedText"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("ABDownloader: $title")
            .setContentText(status)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(!isPaused)
            .setContentIntent(pendingIntent)
            .setProgress(100, progressPercent, progressPercent == 0)
    }

    fun showNotification(
        context: Context,
        title: String,
        progressPercent: Int,
        speedText: String,
        isPaused: Boolean
    ) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(
                NOTIFICATION_ID,
                buildDownloadNotification(context, title, progressPercent, speedText, isPaused).build()
            )
        } catch (_: Exception) {}
    }

    fun cancelNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }
}
