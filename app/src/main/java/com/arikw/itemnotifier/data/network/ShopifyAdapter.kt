package com.arikw.itemnotifier.data.network

import com.arikw.itemnotifier.data.model.ColorOption
import com.arikw.itemnotifier.data.model.ProductSnapshot
import com.arikw.itemnotifier.data.model.SizeOption
import com.arikw.itemnotifier.data.model.Variant
import java.net.URI
import org.json.JSONObject

/**
 * Any Shopify-based shop — in Israel that includes Fox, Foot Locker, Laline,
 * Fox Home and many more BuyMe-accepting stores. Every Shopify product page
 * at /products/<handle> also serves machine-readable JSON at
 * /products/<handle>.js with per-variant availability and prices.
 */
object ShopifyAdapter : SiteAdapter {

    override suspend fun fetch(url: String): ProductSnapshot {
        val uri = URI(url)
        val handle = productHandle(uri.rawPath)
            ?: throw ProductParseException("No /products/<handle> in URL: $url")
        val jsonUrl = "${uri.scheme}://${uri.host}/products/$handle.js"
        val body = Http.getText(jsonUrl, accept = "application/json,*/*;q=0.8")
        return ShopifyParser.parse(body, siteName = uri.host.removePrefix("www."))
    }

    private fun productHandle(path: String): String? {
        val marker = "/products/"
        val idx = path.indexOf(marker)
        if (idx < 0) return null
        return path.substring(idx + marker.length)
            .substringBefore('/')
            .removeSuffix(".js")
            .removeSuffix(".json")
            .takeIf { it.isNotEmpty() }
    }
}

object ShopifyParser {

    /** Parses the /products/<handle>.js payload. */
    fun parse(json: String, siteName: String): ProductSnapshot {
        val product = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw ProductParseException("Not a Shopify product JSON", e)
        }
        val title = product.optString("title")
        if (title.isEmpty() || !product.has("variants")) {
            throw ProductParseException("Shopify JSON has no product data")
        }

        // Work out which option axis is the size and which is the color.
        var sizePos = -1
        var colorPos = -1
        val options = product.optJSONArray("options")
        if (options != null) {
            for (i in 0 until options.length()) {
                val opt = options.optJSONObject(i) ?: continue
                val optName = opt.optString("name")
                val pos = opt.optInt("position", i + 1)
                when {
                    sizePos < 0 && SIZE_NAMES.any { optName.contains(it, ignoreCase = true) } ->
                        sizePos = pos
                    colorPos < 0 && COLOR_NAMES.any { optName.contains(it, ignoreCase = true) } ->
                        colorPos = pos
                }
            }
            // Single-option products (a "Size"-like axis under any name) — but not
            // Shopify's placeholder "Title"/"Default Title" on option-less products.
            if (sizePos < 0 && colorPos < 0 && options.length() == 1 &&
                !options.optJSONObject(0)?.optString("name").equals("Title", ignoreCase = true)
            ) {
                sizePos = 1
            }
        }

        val sizes = mutableListOf<SizeOption>()
        val colors = mutableListOf<ColorOption>()
        val variants = mutableListOf<Variant>()

        product.optJSONArray("variants")?.let { arr ->
            for (i in 0 until arr.length()) {
                val v = arr.optJSONObject(i) ?: continue
                val sizeLabel = optionAt(v, sizePos)
                val colorLabel = optionAt(v, colorPos)
                // Shopify has no numeric option ids in this payload; a label
                // hash gives a stable index for matching across checks.
                val sizeIndex = sizeLabel?.hashCode()
                val colorIndex = colorLabel?.hashCode()

                if (sizeLabel != null && sizes.none { it.valueIndex == sizeIndex }) {
                    sizes += SizeOption(sizeIndex!!, sizeLabel)
                }
                if (colorLabel != null && colors.none { it.valueIndex == colorIndex }) {
                    colors += ColorOption(colorIndex!!, colorLabel)
                }

                variants += Variant(
                    sku = v.optLong("id").toString(),
                    colorIndex = colorIndex,
                    colorLabel = colorLabel,
                    sizeIndex = sizeIndex,
                    sizeLabel = sizeLabel,
                    inStock = v.optBoolean("available", false),
                    price = minorUnits(v, "price"),
                    compareAtPrice = minorUnits(v, "compare_at_price")?.takeIf { was ->
                        val now = minorUnits(v, "price")
                        now != null && was > now
                    },
                )
            }
        }

        val price = minorUnits(product, "price")
        val compareAt = minorUnits(product, "compare_at_price")
        return ProductSnapshot(
            parentSku = product.optLong("id").takeIf { it != 0L }?.toString(),
            name = title,
            imageUrl = product.optString("featured_image")
                .takeIf { it.isNotEmpty() && it != "null" }
                ?.let { if (it.startsWith("//")) "https:$it" else it },
            colors = colors,
            sizes = sizes,
            variants = variants,
            siteName = siteName,
            price = price,
            compareAtPrice = compareAt?.takeIf { price != null && it > price },
            // The .js payload has no currency field; the shops this app targets
            // are Israeli, so shekels is the sensible default.
            currency = "ILS",
            promoText = null,
            productAvailable = product.optBoolean("available", false),
        )
    }

    private fun optionAt(variant: JSONObject, position: Int): String? {
        if (position !in 1..3) return null
        return variant.optString("option$position")
            .takeIf { it.isNotEmpty() && it != "null" }
    }

    /** Shopify prices are integer minor units (agorot/cents): 3990 -> 39.90. */
    private fun minorUnits(obj: JSONObject, key: String): Double? {
        if (obj.isNull(key)) return null
        val value = obj.optLong(key, Long.MIN_VALUE)
        if (value == Long.MIN_VALUE) return null
        return value / 100.0
    }

    private val SIZE_NAMES = listOf("size", "מידה", "מידות")
    private val COLOR_NAMES = listOf("color", "colour", "צבע")
}
