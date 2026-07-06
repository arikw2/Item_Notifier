package com.arikw.itemnotifier.data.network

import com.arikw.itemnotifier.data.model.ColorOption
import com.arikw.itemnotifier.data.model.ProductSnapshot
import com.arikw.itemnotifier.data.model.SizeOption
import com.arikw.itemnotifier.data.model.Variant
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

/** Thrown when a page doesn't look like a Terminal X product page. */
class ProductParseException(message: String, cause: Throwable? = null) :
    IOException(message, cause)

object TerminalXParser {

    private const val STATE_MARKER = "window.__INITIAL_STATE__"

    /**
     * Extracts product data from a Terminal X product page.
     *
     * The page embeds a `window.__INITIAL_STATE__ = {...}` script. The blob is
     * almost pure JSON, except embedded third-party snippets are written as JS
     * string concatenations (`"<"+"script>"`) so the browser doesn't terminate
     * the surrounding script tag early — we undo that before parsing.
     */
    fun parse(html: String): ProductSnapshot {
        val markerIdx = html.indexOf(STATE_MARKER)
        if (markerIdx < 0) {
            throw ProductParseException(
                "Page has no $STATE_MARKER — not a Terminal X product page?"
            )
        }
        val braceStart = html.indexOf('{', markerIdx)
        val scriptEnd = html.indexOf("</script>", braceStart)
        if (braceStart < 0 || scriptEnd < 0) {
            throw ProductParseException("Malformed state script block")
        }
        val blob = html.substring(braceStart, scriptEnd)
            .trim()
            .removeSuffix(";")
            .replace("\"+\"", "")

        val root = try {
            JSONObject(blob)
        } catch (e: Exception) {
            throw ProductParseException("Failed to parse state JSON", e)
        }

        val mainProduct = root.optJSONObject("productPageStoreData")
            ?.optJSONObject("data")
            ?.optJSONObject("mainProduct")
            ?: throw ProductParseException(
                "No product data on page — is this a product URL (not a category/search page)?"
            )

        return parseMainProduct(mainProduct, html)
    }

    private fun parseMainProduct(mainProduct: JSONObject, html: String): ProductSnapshot {
        val colors = mutableListOf<ColorOption>()
        val sizes = mutableListOf<SizeOption>()
        mainProduct.optJSONArray("configurable_options")?.forEachObject { option ->
            val code = option.optString("attribute_code")
            option.optJSONArray("values")?.forEachObject { value ->
                val label = value.optString("label")
                val index = value.optInt("value_index", -1)
                if (label.isNotEmpty() && index >= 0) {
                    when (code) {
                        "color" -> colors += ColorOption(index, label)
                        "size" -> sizes += SizeOption(index, label)
                    }
                }
            }
        }

        val variants = mutableListOf<Variant>()
        var parentName: String? = null
        var promoText: String? = null
        mainProduct.optJSONArray("variants")?.forEachObject { variant ->
            val product = variant.optJSONObject("product") ?: return@forEachObject
            val sku = product.optString("sku")
            if (sku.isEmpty()) return@forEachObject

            var colorIndex: Int? = null
            var colorLabel: String? = null
            var sizeIndex: Int? = null
            var sizeLabel: String? = null
            variant.optJSONArray("attributes")?.forEachObject { attr ->
                when (attr.optString("code")) {
                    "color" -> {
                        colorIndex = attr.optInt("value_index", -1).takeIf { it >= 0 }
                        colorLabel = attr.optString("label").takeIf { it.isNotEmpty() }
                    }
                    "size" -> {
                        sizeIndex = attr.optInt("value_index", -1).takeIf { it >= 0 }
                        sizeLabel = attr.optString("label").takeIf { it.isNotEmpty() }
                    }
                }
            }

            val inStock = product.optString("stock_status2") == "IN_STOCK"
            val (final, regular, _) = priceRange(product)
            variants += Variant(
                sku, colorIndex, colorLabel, sizeIndex, sizeLabel, inStock,
                price = final,
                compareAtPrice = regular?.takeIf { final != null && it > final },
            )

            if (parentName == null) {
                parentName = product.optJSONObject("parent_product")
                    ?.optJSONObject("product")
                    ?.optString("name")
                    ?.takeIf { it.isNotEmpty() }
            }
            if (promoText == null) {
                promoText = product.optJSONObject("state_stampa")
                    ?.optString("text")
                    ?.takeIf { it.isNotEmpty() }
            }
        }

        val name = mainProduct.optString("name").takeIf { it.isNotEmpty() && it != "null" }
            ?: parentName
            ?: ogTitle(html)
            ?: mainProduct.optString("sku").takeIf { it.isNotEmpty() }
            ?: "Unknown product"

        val imageUrl = mainProduct.optJSONObject("image")
            ?.optString("url")
            ?.takeIf { it.startsWith("http") }

        val (final, regular, currency) = priceRange(mainProduct)
        return ProductSnapshot(
            parentSku = mainProduct.optString("sku").takeIf { it.isNotEmpty() },
            name = name,
            imageUrl = imageUrl,
            colors = colors,
            sizes = sizes,
            variants = variants,
            siteName = "Terminal X",
            price = final ?: variants.firstNotNullOfOrNull { it.price },
            compareAtPrice = regular?.takeIf { final != null && it > final },
            currency = currency ?: "ILS",
            promoText = promoText,
        )
    }

    /** Reads Magento's price_range: (final price, regular price, currency). */
    private fun priceRange(obj: JSONObject): Triple<Double?, Double?, String?> {
        val minimum = obj.optJSONObject("price_range")
            ?.optJSONObject("minimum_price")
            ?: return Triple(null, null, null)
        val final = minimum.optJSONObject("final_price")
        val regular = minimum.optJSONObject("regular_price")
        return Triple(
            final?.optDouble("value")?.takeIf { !it.isNaN() },
            regular?.optDouble("value")?.takeIf { !it.isNaN() },
            final?.optString("currency")?.takeIf { it.isNotEmpty() },
        )
    }

    private fun ogTitle(html: String): String? {
        val regex = Regex("""property="og:title"\s+content="([^"]+)"""")
        return regex.find(html)?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
    }

    private inline fun JSONArray.forEachObject(action: (JSONObject) -> Unit) {
        for (i in 0 until length()) {
            optJSONObject(i)?.let(action)
        }
    }
}
