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
    /** Selected size option label, e.g. "44". */
    val sizeLabel: String,
    /** Selected size option value_index (more stable than label matching). */
    val sizeIndex: Int?,
    val lastStatus: StockStatus = StockStatus.UNKNOWN,
    val lastCheckedAt: Long? = null,
    val lastNotifiedAt: Long? = null,
    val createdAt: Long,
)
