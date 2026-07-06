package com.arikw.itemnotifier.data.model

/** One selectable color of a product. */
data class ColorOption(
    val valueIndex: Int,
    val label: String,
)

/** One selectable size of a product. */
data class SizeOption(
    val valueIndex: Int,
    val label: String,
)

/** A concrete purchasable variant (color + size combination) and its live state. */
data class Variant(
    val sku: String,
    val colorIndex: Int?,
    val colorLabel: String?,
    val sizeIndex: Int?,
    val sizeLabel: String?,
    val inStock: Boolean,
    /** Current price of this variant, in major currency units (e.g. 399.90). */
    val price: Double? = null,
    /** Pre-sale price when the variant is discounted; null when not on sale. */
    val compareAtPrice: Double? = null,
)

/** Everything we could parse from a product page, on any supported site. */
data class ProductSnapshot(
    val parentSku: String?,
    val name: String,
    val imageUrl: String?,
    val colors: List<ColorOption>,
    val sizes: List<SizeOption>,
    val variants: List<Variant>,
    /** Display name of the shop this came from, e.g. "Terminal X" or "fox.co.il". */
    val siteName: String,
    /** Product-level price, used when a variant has no price of its own. */
    val price: Double? = null,
    val compareAtPrice: Double? = null,
    /** ISO currency code when the site reports one; Israeli shops are ILS. */
    val currency: String? = null,
    /** Promotional badge on the product, e.g. "LAST CALL" or "30% הנחה". */
    val promoText: String? = null,
    /** Product-level availability for sites that don't expose per-size variants. */
    val productAvailable: Boolean? = null,
) {
    /**
     * Finds the variant matching a tracked size (and color, when one was chosen).
     * Matches by value_index first and falls back to the size label, so tracking
     * survives the site re-indexing its attribute options.
     */
    fun variantFor(sizeIndex: Int?, sizeLabel: String, colorIndex: Int?): Variant? =
        variants.firstOrNull { v ->
            val sizeMatches = (sizeIndex != null && v.sizeIndex == sizeIndex) ||
                v.sizeLabel.equals(sizeLabel, ignoreCase = true)
            val colorMatches = colorIndex == null || v.colorIndex == colorIndex
            sizeMatches && colorMatches
        }

    /** Sizes that exist for the given color, with their current availability. */
    fun sizesForColor(colorIndex: Int?): List<Pair<SizeOption, Boolean>> {
        val relevant = variants.filter { colorIndex == null || it.colorIndex == colorIndex }
        return sizes.map { size ->
            val variant = relevant.firstOrNull {
                it.sizeIndex == size.valueIndex || it.sizeLabel.equals(size.label, ignoreCase = true)
            }
            size to (variant?.inStock == true)
        }
    }

    /** Whole-product availability: any variant in stock, or the page-level flag. */
    fun anyAvailable(): Boolean? = when {
        variants.isNotEmpty() -> variants.any { it.inStock }
        else -> productAvailable
    }
}
