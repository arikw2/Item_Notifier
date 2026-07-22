package com.arikw.itemnotifier

import com.arikw.itemnotifier.data.model.QueryMatcher
import com.arikw.itemnotifier.data.network.ShopifySearchParser
import com.arikw.itemnotifier.data.network.TerminalXSearchParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryMatcherTest {

    @Test
    fun `all words must appear`() {
        assertTrue(QueryMatcher.matches("novablast", "נעלי ריצה NOVABLAST 5 MEN"))
        assertTrue(QueryMatcher.matches("novablast 5", "נעלי ריצה NOVABLAST 5 MEN"))
        assertFalse(QueryMatcher.matches("novablast 6", "נעלי ריצה NOVABLAST 5 MEN"))
    }

    @Test
    fun `bare numbers do not match inside skus`() {
        // "6" appears inside the SKU digits but this is still a Novablast 5.
        assertFalse(
            QueryMatcher.matches("novablast 6", "נעלי ריצה NOVABLAST 5 MEN", "R323060001")
        )
        assertTrue(
            QueryMatcher.matches("novablast 6", "נעלי ריצה 1011C446 NOVABLAST 6 MEN")
        )
    }

    @Test
    fun `matching is case-insensitive and works on handles`() {
        assertTrue(QueryMatcher.matches("NOVABLAST", "asics-novablast-6-black", null))
    }
}

/** Runs against a trimmed copy of a real Terminal X search results page. */
class TerminalXSearchParserTest {

    private val html: String by lazy {
        javaClass.classLoader!!
            .getResourceAsStream("tx_search_fixture.html")!!
            .readBytes()
            .decodeToString()
    }

    @Test
    fun `parses search result products`() {
        val products = TerminalXSearchParser.parse(html)
        assertEquals(5, products.size)

        val first = products.first()
        assertEquals("R323060001", first.key)
        assertTrue(first.name.contains("NOVABLAST 5 MEN"))
        assertEquals(
            "https://www.terminalx.com/default-category/r323060001",
            first.url
        )
        assertEquals(699.9, first.price!!, 0.001)
    }

    @Test
    fun `novablast 6 watch would not fire on these results`() {
        val products = TerminalXSearchParser.parse(html)
        assertTrue(products.none { QueryMatcher.matches("novablast 6", it.name, it.key) })
        assertEquals(5, products.count { QueryMatcher.matches("novablast 5", it.name, it.key) })
    }
}

class ShopifySearchParserTest {

    @Test
    fun `extracts distinct product handles from any theme markup`() {
        val html = """
            <a href="/products/asics-novablast-6">x</a>
            <a href="/collections/sale/products/asics-novablast-6?variant=1">dup</a>
            <img src="/products/other-shoe.js">
            <a href="/products/third-one/">y</a>
        """.trimIndent()
        assertEquals(
            listOf("asics-novablast-6", "other-shoe", "third-one"),
            ShopifySearchParser.extractHandles(html)
        )
    }

    @Test
    fun `no handles on unsupported pages`() {
        assertTrue(ShopifySearchParser.extractHandles("<html>no products</html>").isEmpty())
    }
}
