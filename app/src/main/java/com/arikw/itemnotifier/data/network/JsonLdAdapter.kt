package com.arikw.itemnotifier.data.network

import com.arikw.itemnotifier.data.model.ProductSnapshot
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

/**
 * Last-resort adapter for shops the app has no specific support for: most
 * e-commerce pages embed a schema.org Product as JSON-LD for search engines.
 * Gives whole-product availability and price (no per-size breakdown).
 */
object JsonLdAdapter : SiteAdapter {

    override suspend fun fetch(url: String): ProductSnapshot {
        val host = runCatching { URI(url).host }.getOrNull().orEmpty().removePrefix("www.")
        return JsonLdParser.parse(Http.getText(url), siteName = host.ifEmpty { "shop" })
    }
}

object JsonLdParser {

    private val SCRIPT_REGEX = Regex(
        """<script[^>]*type\s*=\s*["']application/ld\+json["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    fun parse(html: String, siteName: String): ProductSnapshot {
        for (match in SCRIPT_REGEX.findAll(html)) {
            val product = findProduct(match.groupValues[1].trim()) ?: continue
            return toSnapshot(product, siteName)
        }
        throw ProductParseException(
            "Couldn't find product data on this page. Supported best: Terminal X " +
                "and Shopify shops (product links containing /products/…)."
        )
    }

    /** JSON-LD may be an object, an array, or an @graph — search all of them. */
    private fun findProduct(raw: String): JSONObject? {
        val candidates = mutableListOf<JSONObject>()
        runCatching {
            when {
                raw.startsWith("[") -> {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { candidates += it }
                }
                else -> candidates += JSONObject(raw)
            }
        }.getOrElse { return null }

        val queue = ArrayDeque(candidates)
        while (queue.isNotEmpty()) {
            val obj = queue.removeFirst()
            if (isProductType(obj.opt("@type"))) return obj
            obj.optJSONArray("@graph")?.let { graph ->
                for (i in 0 until graph.length()) graph.optJSONObject(i)?.let { queue += it }
            }
        }
        return null
    }

    private fun isProductType(type: Any?): Boolean = when (type) {
        is String -> type.equals("Product", ignoreCase = true)
        is JSONArray -> (0 until type.length()).any {
            type.optString(it).equals("Product", ignoreCase = true)
        }
        else -> false
    }

    private fun toSnapshot(product: JSONObject, siteName: String): ProductSnapshot {
        val offer = firstOffer(product.opt("offers"))
        val availability = offer?.optString("availability").orEmpty()
        val available = when {
            availability.contains("InStock", ignoreCase = true) -> true
            availability.contains("OutOfStock", ignoreCase = true) -> false
            availability.contains("SoldOut", ignoreCase = true) -> false
            else -> null
        }
        val price = offer?.let { parsePrice(it.opt("price") ?: it.opt("lowPrice")) }

        return ProductSnapshot(
            parentSku = product.optString("sku").takeIf { it.isNotEmpty() },
            name = product.optString("name").takeIf { it.isNotEmpty() }
                ?: throw ProductParseException("Product JSON-LD has no name"),
            imageUrl = firstImage(product.opt("image")),
            colors = emptyList(),
            sizes = emptyList(),
            variants = emptyList(),
            siteName = siteName,
            price = price,
            currency = offer?.optString("priceCurrency")?.takeIf { it.isNotEmpty() },
            productAvailable = available,
        )
    }

    private fun firstOffer(offers: Any?): JSONObject? = when (offers) {
        is JSONObject ->
            if (offers.optString("@type").equals("AggregateOffer", ignoreCase = true) &&
                offers.has("offers")
            ) firstOffer(offers.opt("offers")) ?: offers
            else offers
        is JSONArray -> offers.optJSONObject(0)
        else -> null
    }

    private fun firstImage(image: Any?): String? = when (image) {
        is String -> image.takeIf { it.startsWith("http") }
        is JSONArray -> image.optString(0).takeIf { it.startsWith("http") }
        is JSONObject -> image.optString("url").takeIf { it.startsWith("http") }
        else -> null
    }

    private fun parsePrice(price: Any?): Double? = when (price) {
        is Number -> price.toDouble()
        is String -> price.replace(",", "").toDoubleOrNull()
        else -> null
    }
}
