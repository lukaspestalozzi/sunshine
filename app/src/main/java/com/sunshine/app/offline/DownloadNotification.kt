package com.sunshine.app.offline

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.sunshine.app.R

/** The progress notification of region downloads (offline-regions spec, "Background download"). */
object DownloadNotification {
    const val ID = 1
    private const val CHANNEL = "offline_downloads"
    private const val FULL = 100

    fun build(
        context: Context,
        percent: Int,
    ): Notification {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.offline_notification_channel), NotificationManager.IMPORTANCE_LOW),
        )
        return Notification
            .Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_offline)
            .setContentTitle(context.getString(R.string.offline_notification_title))
            .setContentText("$percent %")
            .setProgress(FULL, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    /** Shows [percent]; nothing when the user refused notifications (the download still runs). */
    fun update(
        context: Context,
        percent: Int,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        context.getSystemService(NotificationManager::class.java).notify(ID, build(context, percent))
    }

    fun cancel(context: Context) = context.getSystemService(NotificationManager::class.java).cancel(ID)
}
