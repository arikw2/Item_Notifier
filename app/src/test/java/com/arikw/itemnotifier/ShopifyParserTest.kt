package com.arikw.itemnotifier

import com.arikw.itemnotifier.data.network.ProductParseException
import com.arikw.itemnotifier.data.network.ShopifyParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the parser against a real /products/<handle>.js payload captured from
 * fox.co.il (basic t-shirt, sizes XS-3XL, ₪39.90).
 */
class ShopifyParserTest {

    private val json: String by lazy {
        javaClass.classLoader!!
            .getResourceAsStream("shopify_product_fixture.json")!!
            .readBytes()
            .decodeToString()
    }

    @Test
    fun `parses title, price and image`() {
        val snapshot = ShopifyParser.parse(json, siteName = "fox.co.il")
        assertEquals("טישרט בייסיק צווארון עגול", snapshot.name)
        assertEquals("fox.co.il", snapshot.siteName)
        assertEquals(39.90, snapshot.price!!, 0.001)
        assertNull(snapshot.compareAtPrice)
        assertNotNull(snapshot.imageUrl)
        assertTrue(snapshot.imageUrl!!.startsWith("https://"))
    }

    @Test
    fun `parses size options from variants`() {
        val snapshot = ShopifyParser.parse(json, siteName = "fox.co.il")
        assertEquals(
            listOf("XS", "S", "M", "L", "XL", "2XL", "3XL"),
            snapshot.sizes.map { it.label }
        )
    }

    @Test
    fun `reports per-size availability and price`() {
        val snapshot = ShopifyParser.parse(json, siteName = "fox.co.il")
        val m = snapshot.variantFor(sizeIndex = "M".hashCode(), sizeLabel = "M", colorIndex = null)
        assertNotNull(m)
        assertTrue(m!!.inStock)
        assertEquals(39.90, m.price!!, 0.001)
    }

    @Test
    fun `matches by label alone`() {
        val snapshot = ShopifyParser.parse(json, siteName = "fox.co.il")
        val xl = snapshot.variantFor(sizeIndex = null, sizeLabel = "xl", colorIndex = null)
        assertNotNull(xl)
        assertEquals("XL", xl!!.sizeLabel)
    }

    @Test
    fun `whole product availability`() {
        val snapshot = ShopifyParser.parse(json, siteName = "fox.co.il")
        assertEquals(true, snapshot.anyAvailable())
    }

    @Test(expected = ProductParseException::class)
    fun `rejects non-product json`() {
        ShopifyParser.parse("""{"foo": "bar"}""", siteName = "x")
    }
}
