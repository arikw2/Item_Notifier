package com.arikw.itemnotifier.data.model

/** A product found by a shop search. */
data class FoundProduct(
    /** Stable identity within the shop: Terminal X SKU or Shopify handle. */
    val key: String,
    val name: String,
    val url: String,
    val imageUrl: String? = null,
    val price: Double? = null,
    val currency: String? = null,
    val available: Boolean? = null,
)

/** Result of running a shop search once. */
data class SearchOutcome(
    /** Products that match the watch query (see [QueryMatcher]). */
    val matches: List<FoundProduct>,
    /** Every product key seen on the results page, matching or not. */
    val seenKeys: List<String>,
)

/**
 * Shop search engines are fuzzy — searching "novablast 6" also returns
 * Novablast 5 colorways. A watch only alerts on results whose name (or key)
 * really contains every word of the query.
 */
object QueryMatcher {

    fun matches(query: String, vararg texts: String?): Boolean {
        val haystack = texts.filterNotNull().joinToString(" ").lowercase()
        return tokens(query).all { token ->
            if (token.all { it.isDigit() }) {
                // Bare numbers ("6") must stand alone: the 6 inside SKU
                // "R323060001" must not make Novablast 5 match "novablast 6".
                Regex("(?<![0-9])${Regex.escape(token)}(?![0-9])")
                    .containsMatchIn(haystack)
            } else {
                haystack.contains(token)
            }
        }
    }

    private fun tokens(query: String): List<String> =
        query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
}
