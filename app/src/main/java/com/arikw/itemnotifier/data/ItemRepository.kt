package com.arikw.itemnotifier.data

import android.content.Context
import com.arikw.itemnotifier.data.db.AppDatabase
import com.arikw.itemnotifier.data.db.SearchWatch
import com.arikw.itemnotifier.data.db.StockStatus
import com.arikw.itemnotifier.data.db.TrackedItem
import com.arikw.itemnotifier.data.model.ProductSnapshot
import com.arikw.itemnotifier.data.model.SearchOutcome
import com.arikw.itemnotifier.data.network.SearchClient
import com.arikw.itemnotifier.data.network.SiteRegistry
import com.arikw.itemnotifier.notifications.Notifier
import kotlinx.coroutines.flow.Flow

class ItemRepository(private val context: Context) {

    private val dao = AppDatabase.get(context).trackedItemDao()
    private val watchDao = AppDatabase.get(context).searchWatchDao()

    fun observeItems(): Flow<List<TrackedItem>> = dao.observeAll()

    suspend fun fetchProduct(url: String): ProductSnapshot =
        SiteRegistry.adapterFor(url).fetch(url)

    suspend fun addItems(items: List<TrackedItem>) = dao.insertAll(items)

    suspend fun deleteItem(id: Long) = dao.delete(id)

    fun observeWatches(): Flow<List<SearchWatch>> = watchDao.observeAll()

    suspend fun previewSearch(kind: String, host: String, query: String): SearchOutcome =
        SearchClient.search(kind, host, query)

    suspend fun addWatch(watch: SearchWatch) = watchDao.insert(watch)

    suspend fun deleteWatch(id: Long) = watchDao.delete(id)

    /**
     * Re-checks every tracked item; notifies on restocks, price drops and new
     * promo badges. Items sharing a product URL are checked with a single page
     * fetch. Returns the number of items that flipped to in-stock during this run.
     */
    suspend fun checkAllAndNotify(): Int {
        val newlyInStock = checkTrackedItems()
        checkSearchWatches()
        return newlyInStock
    }

    private suspend fun checkTrackedItems(): Int {
        val items = dao.getAll()
        if (items.isEmpty()) return 0

        val now = System.currentTimeMillis()
        var newlyInStock = 0

        for ((url, group) in items.groupBy { it.url }) {
            val snapshot = try {
                fetchProduct(url)
            } catch (e: Exception) {
                group.forEach { item ->
                    dao.update(item.copy(lastStatus = StockStatus.ERROR, lastCheckedAt = now))
                }
                continue
            }

            for (item in group) {
                dao.update(checkOne(item, snapshot, now).also { updated ->
                    if (updated.lastStatus == StockStatus.IN_STOCK &&
                        item.lastStatus != StockStatus.IN_STOCK
                    ) newlyInStock++
                })
            }
        }
        return newlyInStock
    }

    /** Re-runs every saved search and notifies on first-seen matching products. */
    private suspend fun checkSearchWatches() {
        val now = System.currentTimeMillis()
        for (watch in watchDao.getAll()) {
            val known = watch.knownKeySet()
            val outcome = try {
                SearchClient.search(watch.siteKind, watch.siteHost, watch.query, skipKeys = known)
            } catch (e: Exception) {
                watchDao.update(watch.copy(lastCheckedAt = now, lastError = true))
                continue
            }

            val newMatches = outcome.matches.filter { it.key !in known }
            if (newMatches.isNotEmpty()) {
                Notifier.notifyNewProducts(context, watch, newMatches)
            }

            watchDao.update(
                watch.copy(
                    // Remember everything seen (matching or not) so fuzzy search
                    // noise is never re-inspected and never re-alerts.
                    knownKeys = SearchWatch.encodeKeys(known + outcome.seenKeys),
                    lastCheckedAt = now,
                    lastMatchCount = outcome.matches.size,
                    lastError = false,
                )
            )
        }
    }

    private fun checkOne(item: TrackedItem, snapshot: ProductSnapshot, now: Long): TrackedItem {
        val variant = if (item.isWholeProduct) null
        else snapshot.variantFor(item.sizeIndex, item.sizeLabel, item.colorIndex)

        val inStock: Boolean? = if (item.isWholeProduct) snapshot.anyAvailable()
        else variant?.inStock

        val newStatus = when (inStock) {
            true -> StockStatus.IN_STOCK
            false -> StockStatus.OUT_OF_STOCK
            null -> StockStatus.UNKNOWN
        }
        val newPrice = variant?.price ?: snapshot.price
        val newWasPrice = variant?.compareAtPrice ?: snapshot.compareAtPrice

        var updated = item.copy(
            lastStatus = newStatus,
            lastCheckedAt = now,
            name = snapshot.name,
            imageUrl = snapshot.imageUrl ?: item.imageUrl,
            siteName = snapshot.siteName,
            lastPrice = newPrice ?: item.lastPrice,
            lastWasPrice = newWasPrice,
            currency = snapshot.currency ?: item.currency,
            promoText = snapshot.promoText,
        )

        val cameBackInStock =
            newStatus == StockStatus.IN_STOCK && item.lastStatus != StockStatus.IN_STOCK
        // Deal alerts only for buyable items, and never on the very first check.
        val priceDropped = newStatus == StockStatus.IN_STOCK &&
            !cameBackInStock &&
            newPrice != null && item.lastPrice != null &&
            newPrice < item.lastPrice - 0.005
        val newPromo = newStatus == StockStatus.IN_STOCK &&
            !cameBackInStock && !priceDropped &&
            item.lastCheckedAt != null &&
            snapshot.promoText != null && snapshot.promoText != item.promoText

        when {
            cameBackInStock -> {
                Notifier.notifyInStock(context, updated)
                updated = updated.copy(lastNotifiedAt = now)
            }
            priceDropped -> {
                Notifier.notifyDeal(
                    context, updated,
                    "Price drop: ${formatPrice(item.lastPrice!!, updated.currency)} → " +
                        formatPrice(newPrice!!, updated.currency)
                )
                updated = updated.copy(lastNotifiedAt = now)
            }
            newPromo -> {
                Notifier.notifyDeal(context, updated, "New promotion: ${snapshot.promoText}")
                updated = updated.copy(lastNotifiedAt = now)
            }
        }
        return updated
    }

    companion object {
        fun formatPrice(price: Double, currency: String?): String {
            val symbol = when (currency) {
                null, "ILS", "NIS" -> "₪"
                "USD" -> "$"
                "EUR" -> "€"
                else -> "$currency "
            }
            return if (price == price.toLong().toDouble()) {
                "$symbol${price.toLong()}"
            } else {
                "$symbol${"%.2f".format(price)}"
            }
        }
    }
}
