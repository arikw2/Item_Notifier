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
import com.arikw.itemnotifier.data.db.SearchWatch
import com.arikw.itemnotifier.data.db.TrackedItem
import com.arikw.itemnotifier.data.model.FoundProduct

object Notifier {

    private const val CHANNEL_ID = "stock_alerts"

    /** Keeps search-watch notification ids clear of tracked-item ids. */
    private const val WATCH_ID_BASE = 1_000_000

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
        val what = buildString {
            append(item.name)
            append(" — ")
            append(itemDescription(item))
        }
        post(
            context, item,
            notificationId = item.id.toInt() * 10,
            title = "Back in stock!",
            text = what,
            bigText = "${item.name}\n${itemDescription(item)} is available now. " +
                "Tap to open ${item.siteName ?: "the shop"}."
        )
    }

    /** Fires a deal notification (price drop or new promotion). */
    fun notifyDeal(context: Context, item: TrackedItem, dealText: String) {
        post(
            context, item,
            notificationId = item.id.toInt() * 10 + 1,
            title = dealText,
            text = "${item.name} — ${itemDescription(item)}",
            bigText = "${item.name}\n${itemDescription(item)}\n$dealText. " +
                "Tap to open ${item.siteName ?: "the shop"}."
        )
    }

    /** Fires when a search watch finds products it has never seen before. */
    fun notifyNewProducts(context: Context, watch: SearchWatch, products: List<FoundProduct>) {
        if (products.isEmpty()) return
        val first = products.first()
        val title = if (products.size == 1) "New arrival!"
        else "${products.size} new arrivals!"
        val text = buildString {
            append(first.name)
            if (products.size > 1) append(" +${products.size - 1} more")
        }
        val bigText = buildString {
            append("Your search \"${watch.query}\" on ${watch.siteName} has new results:\n")
            products.take(5).forEach { p ->
                append("• ${p.name}")
                p.price?.let { append(" — ${priceText(it, p.currency)}") }
                append('\n')
            }
            append("Tap to open.")
        }
        // A single find opens its product page; several open the search itself.
        val openUrl = if (products.size == 1) first.url else watch.searchUrl

        postUrl(
            context,
            notificationId = WATCH_ID_BASE + watch.id.toInt() * 10,
            url = openUrl,
            title = title,
            text = text,
            bigText = bigText,
        )
    }

    private fun priceText(price: Double, currency: String?): String {
        val symbol = when (currency) {
            null, "ILS", "NIS" -> "₪"
            "USD" -> "$"
            "EUR" -> "€"
            else -> "$currency "
        }
        return "$symbol${if (price == price.toLong().toDouble()) price.toLong() else "%.2f".format(price)}"
    }

    private fun itemDescription(item: TrackedItem): String = buildString {
        if (item.isWholeProduct) append("Product") else append("Size ${item.sizeLabel}")
        item.colorLabel?.let { append(" · $it") }
    }

    private fun post(
        context: Context,
        item: TrackedItem,
        notificationId: Int,
        title: String,
        text: String,
        bigText: String,
    ) = postUrl(context, notificationId, item.url, title, text, bigText)

    private fun postUrl(
        context: Context,
        notificationId: Int,
        url: String,
        title: String,
        text: String,
        bigText: String,
    ) {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openPage = PendingIntent.getActivity(
            context,
            notificationId,
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(openPage)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
