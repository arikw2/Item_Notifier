package com.arikw.itemnotifier.data.network

import com.arikw.itemnotifier.data.model.ProductSnapshot
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** A shop (or shop platform) the app knows how to read stock data from. */
interface SiteAdapter {
    /** Fetches and parses the product a URL points at. */
    suspend fun fetch(url: String): ProductSnapshot
}

/** Picks the right adapter for a product URL. */
object SiteRegistry {

    fun adapterFor(url: String): SiteAdapter {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host.orEmpty().removePrefix("www.")
        val path = uri?.path.orEmpty()
        return when {
            host.endsWith("terminalx.com") -> TerminalXAdapter
            // Shopify convention: product pages live under /products/<handle>.
            // Covers Fox, Foot Locker IL, Laline, Fox Home and thousands of others.
            path.contains("/products/") -> ShopifyAdapter
            // Anything else: try the schema.org Product JSON-LD most shops embed.
            else -> JsonLdAdapter
        }
    }
}

/** Shared OkHttp client; pages are fetched with mobile-browser headers. */
internal object Http {

    const val USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun getText(url: String, accept: String = "text/html,*/*;q=0.8"): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", accept)
                .header("Accept-Language", "he-IL,he;q=0.9,en-US;q=0.8,en;q=0.7")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code} for $url")
                }
                response.body?.string() ?: throw IOException("Empty response body for $url")
            }
        }
}
