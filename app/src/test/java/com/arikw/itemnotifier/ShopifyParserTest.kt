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

    @Test
    fun `option-less products expose no fake Default Title size`() {
        // Shopify products without options carry a placeholder "Title" axis
        // with a single "Default Title" value (seen live on arosport.co.il).
        val json = """
            {
              "id": 1, "title": "משקפי שמש NIKE FIRE", "handle": "nike-fire",
              "available": true, "price": 39600, "compare_at_price": 44000,
              "featured_image": "//cdn.shopify.com/x.jpg",
              "options": [{"name": "Title", "position": 1, "values": ["Default Title"]}],
              "variants": [{
                "id": 11, "title": "Default Title", "option1": "Default Title",
                "option2": null, "available": true, "price": 39600,
                "compare_at_price": 44000
              }]
            }
        """.trimIndent()
        val snapshot = ShopifyParser.parse(json, siteName = "arosport.co.il")
        assertTrue(snapshot.sizes.isEmpty())
        assertEquals(true, snapshot.anyAvailable())
        assertEquals(396.0, snapshot.price!!, 0.001)
        assertEquals(440.0, snapshot.compareAtPrice!!, 0.001)
    }

    @Test
    fun `hebrew size option axis is detected`() {
        // Mirrors arosport.co.il running shoes: מידה (size) + מידת רוחב (width).
        val json = """
            {
              "id": 2, "title": "נעלי ריצה הוקה בונדי 9", "handle": "hoka-bondi-9",
              "available": true, "price": 90000,
              "options": [
                {"name": "מידה", "position": 1, "values": ["36", "37.5"]},
                {"name": "מידת רוחב", "position": 2, "values": ["רוחב רגיל"]}
              ],
              "variants": [
                {"id": 21, "title": "36 / רוחב רגיל", "option1": "36",
                 "option2": "רוחב רגיל", "available": false, "price": 90000},
                {"id": 22, "title": "37.5 / רוחב רגיל", "option1": "37.5",
                 "option2": "רוחב רגיל", "available": true, "price": 90000}
              ]
            }
        """.trimIndent()
        val snapshot = ShopifyParser.parse(json, siteName = "arosport.co.il")
        assertEquals(listOf("36", "37.5"), snapshot.sizes.map { it.label })
        val v36 = snapshot.variantFor(sizeIndex = null, sizeLabel = "36", colorIndex = null)
        assertEquals(false, v36!!.inStock)
        val v375 = snapshot.variantFor(sizeIndex = null, sizeLabel = "37.5", colorIndex = null)
        assertEquals(true, v375!!.inStock)
    }
}
