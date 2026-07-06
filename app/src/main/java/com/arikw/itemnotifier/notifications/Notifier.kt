package com.arikw.itemnotifier.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arikw.itemnotifier.R
import com.arikw.itemnotifier.data.db.TrackedItem

object Notifier {

    private const val CHANNEL_ID = "stock_alerts"

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Stock alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies when a tracked item is back in stock"
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    /** Fires a "back in stock" notification; tapping it opens the product page. */
    fun notifyInStock(context: Context, item: TrackedItem) {
        if (context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) {
            return
        }

        val openPage = PendingIntent.getActivity(
            context,
            item.id.toInt(),
            Intent(Intent.ACTION_VIEW, Uri.parse(item.url)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val sizeText = buildString {
            append("Size ${item.sizeLabel}")
            item.colorLabel?.let { append(" · $it") }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle("Back in stock!")
            .setContentText("${item.name} — $sizeText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${item.name}\n$sizeText is available now. Tap to open Terminal X.")
            )
            .setContentIntent(openPage)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(item.id.toInt(), notification)
    }
}
