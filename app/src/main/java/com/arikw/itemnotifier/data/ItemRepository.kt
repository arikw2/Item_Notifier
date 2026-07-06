package com.arikw.itemnotifier.data

import android.content.Context
import com.arikw.itemnotifier.data.db.AppDatabase
import com.arikw.itemnotifier.data.db.StockStatus
import com.arikw.itemnotifier.data.db.TrackedItem
import com.arikw.itemnotifier.data.model.ProductSnapshot
import com.arikw.itemnotifier.data.network.TerminalXClient
import com.arikw.itemnotifier.notifications.Notifier
import kotlinx.coroutines.flow.Flow

class ItemRepository(
    private val context: Context,
    private val client: TerminalXClient = TerminalXClient(),
) {
    private val dao = AppDatabase.get(context).trackedItemDao()

    fun observeItems(): Flow<List<TrackedItem>> = dao.observeAll()

    suspend fun fetchProduct(url: String): ProductSnapshot = client.fetchProduct(url)

    suspend fun addItems(items: List<TrackedItem>) = dao.insertAll(items)

    suspend fun deleteItem(id: Long) = dao.delete(id)

    /**
     * Re-checks every tracked item and fires a notification for each item whose
     * size just came back in stock. Items sharing a product URL are checked
     * with a single page fetch. Returns the number of items that flipped to
     * in-stock during this run.
     */
    suspend fun checkAllAndNotify(): Int {
        val items = dao.getAll()
        if (items.isEmpty()) return 0

        val now = System.currentTimeMillis()
        var newlyInStock = 0

        for ((url, group) in items.groupBy { it.url }) {
            val snapshot = try {
                client.fetchProduct(url)
            } catch (e: Exception) {
                group.forEach { item ->
                    dao.update(item.copy(lastStatus = StockStatus.ERROR, lastCheckedAt = now))
                }
                continue
            }

            for (item in group) {
                val variant = snapshot.variantFor(item.sizeIndex, item.sizeLabel, item.colorIndex)
                val newStatus = when (variant?.inStock) {
                    true -> StockStatus.IN_STOCK
                    false -> StockStatus.OUT_OF_STOCK
                    null -> StockStatus.UNKNOWN
                }

                var updated = item.copy(
                    lastStatus = newStatus,
                    lastCheckedAt = now,
                    name = snapshot.name,
                    imageUrl = snapshot.imageUrl ?: item.imageUrl,
                )

                val cameBackInStock =
                    newStatus == StockStatus.IN_STOCK && item.lastStatus != StockStatus.IN_STOCK
                if (cameBackInStock) {
                    Notifier.notifyInStock(context, updated)
                    updated = updated.copy(lastNotifiedAt = now)
                    newlyInStock++
                }

                dao.update(updated)
            }
        }
        return newlyInStock
    }
}
