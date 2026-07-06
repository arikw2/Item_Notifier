package com.arikw.itemnotifier.data.model

/** One selectable color of a product (Terminal X "color" configurable option). */
data class ColorOption(
    val valueIndex: Int,
    val label: String,
)

/** One selectable size of a product (Terminal X "size" configurable option). */
data class SizeOption(
    val valueIndex: Int,
    val label: String,
)

/** A concrete purchasable variant (color + size combination) and its live stock state. */
data class Variant(
    val sku: String,
    val colorIndex: Int?,
    val colorLabel: String?,
    val sizeIndex: Int?,
    val sizeLabel: String?,
    val inStock: Boolean,
)

/** Everything we could parse from a Terminal X product page. */
data class ProductSnapshot(
    val parentSku: String?,
    val name: String,
    val imageUrl: String?,
    val colors: List<ColorOption>,
    val sizes: List<SizeOption>,
    val variants: List<Variant>,
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
}
