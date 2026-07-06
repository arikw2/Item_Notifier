package com.arikw.itemnotifier.data.network

import com.arikw.itemnotifier.data.model.ProductSnapshot
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches Terminal X product pages. The site is a server-side-rendered React app:
 * every product page embeds the full product state (variants, sizes, per-size
 * stock status) in a `window.__INITIAL_STATE__` JSON blob, which is what we parse.
 */
class TerminalXClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun fetchProduct(url: String): ProductSnapshot = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header(
                "Accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
            )
            .header("Accept-Language", "he-IL,he;q=0.9,en-US;q=0.8,en;q=0.7")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} for $url")
            }
            val html = response.body?.string()
                ?: throw IOException("Empty response body for $url")
            TerminalXParser.parse(html)
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.0.0 Mobile Safari/537.36"
    }
}
