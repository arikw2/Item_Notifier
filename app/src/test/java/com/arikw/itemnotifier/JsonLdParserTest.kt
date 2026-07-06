package com.arikw.itemnotifier

import com.arikw.itemnotifier.data.network.JsonLdParser
import com.arikw.itemnotifier.data.network.ProductParseException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsonLdParserTest {

    private val html: String by lazy {
        javaClass.classLoader!!
            .getResourceAsStream("jsonld_page_fixture.html")!!
            .readBytes()
            .decodeToString()
    }

    @Test
    fun `parses schema-org product, skipping non-product blocks`() {
        val snapshot = JsonLdParser.parse(html, siteName = "example.com")
        assertEquals("Cool Sneaker Limited", snapshot.name)
        assertEquals("SNK-123", snapshot.parentSku)
        assertEquals("https://cdn.example.com/sneaker.jpg", snapshot.imageUrl)
        assertEquals(349.90, snapshot.price!!, 0.001)
        assertEquals("ILS", snapshot.currency)
        assertEquals(false, snapshot.productAvailable)
        assertEquals(false, snapshot.anyAvailable())
        assertNull(snapshot.compareAtPrice)
    }

    @Test(expected = ProductParseException::class)
    fun `rejects pages without product json-ld`() {
        JsonLdParser.parse("<html><body>nothing here</body></html>", siteName = "x")
    }
}
