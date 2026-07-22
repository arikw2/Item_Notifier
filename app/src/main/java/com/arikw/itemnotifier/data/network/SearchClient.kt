package com.arikw.itemnotifier.data.network

import android.net.Uri
import com.arikw.itemnotifier.data.db.SearchKind
import com.arikw.itemnotifier.data.model.FoundProduct
import com.arikw.itemnotifier.data.model.QueryMatcher
import com.arikw.itemnotifier.data.model.SearchOutcome
import org.json.JSONObject

/** Runs a saved search against a shop and reports the products it finds. */
object SearchClient {

    /** How many not-yet-known Shopify products we resolve per run. */
    private const val SHOPIFY_RESOLVE_LIMIT = 8

    /** The URL a human opens for this search. */
    fun searchUrlFor(kind: String, host: String, query: String): String {
        val q = Uri.encode(query)
        return when (kind) {
            SearchKind.TERMINALX -> "https://www.terminalx.com/catalogsearch/result?q=$q"
            else -> "https://$host/search?q=$q&type=product"
        }
    }

    /**
     * Runs the search. [skipKeys] are products already seen — Shopify watches
     * skip re-resolving them so periodic checks stay cheap.
     */
    suspend fun search(
        kind: String,
        host: String,
        query: String,
        skipKeys: Set<String> = emptySet(),
    ): SearchOutcome = when (kind) {
        SearchKind.TERMINALX -> terminalXSearch(query)
        else -> shopifySearch(host, query, skipKeys)
    }

    private suspend fun terminalXSearch(query: String): SearchOutcome {
        val html = Http.getText(searchUrlFor(SearchKind.TERMINALX, "", query))
        val all = TerminalXSearchParser.parse(html)
        return SearchOutcome(
            matches = all.filter { QueryMatcher.matches(query, it.name, it.key) },
            seenKeys = all.map { it.key },
        )
    }

    private suspend fun shopifySearch(
        host: String,
        query: String,
        skipKeys: Set<String>,
    ): SearchOutcome {
        val html = Http.getText(searchUrlFor(SearchKind.SHOPIFY, host, query))
        val handles = ShopifySearchParser.extractHandles(html)
        if (handles.isEmpty()) {
            throw ProductParseException(
                "This shop's search page doesn't list products in a readable way."
            )
        }

        // Resolve titles/prices only for products we haven't seen before.
        val matches = mutableListOf<FoundProduct>()
        for (handle in handles.filter { it !in skipKeys }.take(SHOPIFY_RESOLVE_LIMIT)) {
            val snapshot = runCatching {
                ShopifyParser.parse(
                    Http.getText(
                        "https://$host/products/$handle.js",
                        accept = "application/json,*/*;q=0.8"
                    ),
                    siteName = host,
                )
            }.getOrNull() ?: continue

            if (QueryMatcher.matches(query, snapshot.name, handle)) {
                matches += FoundProduct(
                    key = handle,
                    name = snapshot.name,
                    url = "https://$host/products/$handle",
                    imageUrl = snapshot.imageUrl,
                    price = snapshot.price,
                    currency = snapshot.currency,
                    available = snapshot.anyAvailable(),
                )
            }
        }
        return SearchOutcome(matches = matches, seenKeys = handles)
    }
}

/** Parses Terminal X's server-rendered search results page. */
object TerminalXSearchParser {

    fun parse(html: String): List<FoundProduct> {
        val state = TerminalXParser.extractState(html)
        val items = state.optJSONObject("listingAndSearchStoreData")
            ?.optJSONObject("data")
            ?.optJSONObject("listing")
            ?.optJSONObject("products")
            ?.optJSONArray("items")
            ?: throw ProductParseException("No search results data on page")

        val products = mutableListOf<FoundProduct>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val sku = item.optString("sku")
            if (sku.isEmpty()) continue

            val name = productName(item) ?: sku
            val price = item.optJSONObject("price_range")
                ?.optJSONObject("minimum_price")
                ?.optJSONObject("final_price")
            products += FoundProduct(
                key = sku,
                name = name,
                url = "https://www.terminalx.com/default-category/${sku.lowercase()}",
                imageUrl = imageUrl(item),
                price = price?.optDouble("value")?.takeIf { !it.isNaN() },
                currency = price?.optString("currency")?.takeIf { it.isNotEmpty() } ?: "ILS",
                available = when (item.optString("stock_status2")) {
                    "IN_STOCK" -> true
                    "OUT_OF_STOCK" -> false
                    else -> null
                },
            )
        }
        return products
    }

    /** Listing items keep the display name nested inside the first variant. */
    private fun productName(item: JSONObject): String? {
        item.optJSONArray("variants")?.let { variants ->
            for (i in 0 until variants.length()) {
                variants.optJSONObject(i)
                    ?.optJSONObject("product")
                    ?.optJSONObject("parent_product")
                    ?.optJSONObject("product")
                    ?.optString("name")
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { return it }
            }
        }
        return item.optJSONObject("image")
            ?.optString("label")
            ?.takeIf { it.isNotEmpty() && it != "null" }
    }

    private fun imageUrl(item: JSONObject): String? {
        for (key in listOf("small_image", "image", "thumbnail")) {
            item.optJSONObject(key)
                ?.optString("url")
                ?.takeIf { it.startsWith("http") }
                ?.let { return it }
        }
        return null
    }
}

/** Extracts product handles from any Shopify theme's search results HTML. */
object ShopifySearchParser {

    private val HANDLE_REGEX = Regex("""/products/([a-zA-Z0-9][a-zA-Z0-9%_-]*)""")

    fun extractHandles(html: String): List<String> =
        HANDLE_REGEX.findAll(html)
            .map { it.groupValues[1].removeSuffix(".js").removeSuffix(".json") }
            .distinct()
            .take(24)
            .toList()
}
