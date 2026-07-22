package com.arikw.itemnotifier.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray

/** Search kinds a watch can run. */
object SearchKind {
    const val TERMINALX = "TERMINALX"
    const val SHOPIFY = "SHOPIFY"
}

/**
 * A saved shop search. The app re-runs it in the background and notifies when
 * a product that matches the query shows up for the first time — e.g. watch
 * "novablast 6" to hear the moment it lands.
 */
@Entity(tableName = "search_watches")
data class SearchWatch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    /** One of [SearchKind]. */
    val siteKind: String,
    /** Shop host, e.g. "www.terminalx.com" or "originals.co.il". */
    val siteHost: String,
    /** Display name, e.g. "Terminal X". */
    val siteName: String,
    /** Browser-facing search URL, opened when the user taps the watch. */
    val searchUrl: String,
    /** JSON array of product keys already seen — these never alert again. */
    val knownKeys: String = "[]",
    val lastCheckedAt: Long? = null,
    /** How many matching products the last check saw. */
    val lastMatchCount: Int? = null,
    val lastError: Boolean = false,
    val createdAt: Long,
) {
    fun knownKeySet(): Set<String> {
        val arr = runCatching { JSONArray(knownKeys) }.getOrNull() ?: return emptySet()
        return buildSet { for (i in 0 until arr.length()) add(arr.optString(i)) }
    }

    companion object {
        fun encodeKeys(keys: Collection<String>): String {
            val arr = JSONArray()
            keys.forEach { arr.put(it) }
            return arr.toString()
        }
    }
}
