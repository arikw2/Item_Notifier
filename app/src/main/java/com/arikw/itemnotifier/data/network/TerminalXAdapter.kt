package com.arikw.itemnotifier.data.network

import com.arikw.itemnotifier.data.model.ProductSnapshot

/**
 * Terminal X (terminalx.com). The site is a server-side-rendered React app:
 * every product page embeds the full product state (variants, sizes, per-size
 * stock status, prices, promo badges) in a `window.__INITIAL_STATE__` JSON blob.
 */
object TerminalXAdapter : SiteAdapter {
    override suspend fun fetch(url: String): ProductSnapshot =
        TerminalXParser.parse(Http.getText(url))
}
