package com.arikw.itemnotifier.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class StockStatus {
    IN_STOCK,
    OUT_OF_STOCK,
    UNKNOWN,
    ERROR,
}

/** A single product-size the user wants to be notified about. */
@Entity(tableName = "tracked_items")
data class TrackedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Product page URL as the user added it (color query param preserved). */
    val url: String,
    val name: String,
    val imageUrl: String?,
    /** Selected color option value_index; null when the product has one color. */
    val colorIndex: Int?,
    val colorLabel: String?,
    /** Selected size option label, e.g. "44". Empty = track the whole product. */
    val sizeLabel: String,
    /** Selected size option value_index (more stable than label matching). */
    val sizeIndex: Int?,
    val lastStatus: StockStatus = StockStatus.UNKNOWN,
    val lastCheckedAt: Long? = null,
    val lastNotifiedAt: Long? = null,
    val createdAt: Long,
    /** Shop this item belongs to, e.g. "Terminal X" or "fox.co.il". */
    val siteName: String? = null,
    /** Price at the last successful check, for price-drop alerts. */
    val lastPrice: Double? = null,
    /** Pre-sale price at the last check; non-null means the item was on sale. */
    val lastWasPrice: Double? = null,
    val currency: String? = null,
    /** Promo badge at the last check, e.g. "LAST CALL"; new badges trigger alerts. */
    val promoText: String? = null,
) {
    /** True when tracking whole-product availability instead of one size. */
    val isWholeProduct: Boolean get() = sizeLabel.isEmpty()
}
