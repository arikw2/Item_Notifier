package com.arikw.itemnotifier

import com.arikw.itemnotifier.data.network.ProductParseException
import com.arikw.itemnotifier.data.network.TerminalXParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the parser against a trimmed copy of a real Terminal X product page
 * (ASICS Novablast 5 Men, captured 2026-07): sizes 40.5–46.5, only 40.5 in stock.
 */
class TerminalXParserTest {

    private val html: String by lazy {
        javaClass.classLoader!!
            .getResourceAsStream("product_page_fixture.html")!!
            .readBytes()
            .decodeToString()
    }

    @Test
    fun `parses product name and image`() {
        val snapshot = TerminalXParser.parse(html)
        assertTrue(snapshot.name.contains("NOVABLAST 5 MEN"))
        assertNotNull(snapshot.imageUrl)
        assertTrue(snapshot.imageUrl!!.startsWith("https://"))
        assertEquals("R323060001", snapshot.parentSku)
        assertEquals("Terminal X", snapshot.siteName)
    }

    @Test
    fun `parses price and promo badge`() {
        val snapshot = TerminalXParser.parse(html)
        assertEquals(699.9, snapshot.price!!, 0.001)
        assertEquals("ILS", snapshot.currency)
        assertEquals("LAST CALL", snapshot.promoText)
    }

    @Test
    fun `parses all size options`() {
        val snapshot = TerminalXParser.parse(html)
        val labels = snapshot.sizes.map { it.label }
        assertEquals(
            listOf("40.5", "41.5", "42", "42.5", "43.5", "44", "44.5", "45", "46", "46.5"),
            labels
        )
        // size "44" has Magento value_index 55 on the captured page
        assertEquals(55, snapshot.sizes.first { it.label == "44" }.valueIndex)
    }

    @Test
    fun `reports per-size stock status`() {
        val snapshot = TerminalXParser.parse(html)

        val size405 = snapshot.variantFor(sizeIndex = 1442, sizeLabel = "40.5", colorIndex = 4)
        assertNotNull(size405)
        assertTrue("40.5 should be in stock in the captured page", size405!!.inStock)

        val size44 = snapshot.variantFor(sizeIndex = 55, sizeLabel = "44", colorIndex = 4)
        assertNotNull(size44)
        assertFalse("44 should be out of stock in the captured page", size44!!.inStock)
    }

    @Test
    fun `matches variant by label when value index is missing`() {
        val snapshot = TerminalXParser.parse(html)
        val byLabel = snapshot.variantFor(sizeIndex = null, sizeLabel = "44", colorIndex = null)
        assertNotNull(byLabel)
        assertEquals("R32306000109", byLabel!!.sku)
    }

    @Test
    fun `sizesForColor pairs sizes with availability`() {
        val snapshot = TerminalXParser.parse(html)
        val pairs = snapshot.sizesForColor(4)
        assertEquals(10, pairs.size)
        assertEquals(1, pairs.count { (_, inStock) -> inStock })
    }

    @Test(expected = ProductParseException::class)
    fun `rejects pages without product state`() {
        TerminalXParser.parse("<html><body>hello</body></html>")
    }
}
