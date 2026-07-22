# Item Notifier

Android app that watches Israeli shop product pages and notifies you when the
size you want is back in stock — e.g. the ASICS Novablast 5 in size 44 on
Terminal X — when a tracked item's price drops or gets a promo badge, and when
a **new product** matching a saved search shows up (e.g. watch "novablast 6"
to hear the moment it lands).

## Supported shops

| Adapter | Shops | Granularity |
|---|---|---|
| **Terminal X** | terminalx.com | per size + color, price, promo badges |
| **Shopify** | Any Shopify shop — Fox, Foot Locker IL, Laline, Fox Home, and many other BuyMe-accepting stores (product links contain `/products/…`) | per size + color, price, sale price |
| **Generic (JSON-LD)** | Most other shops (schema.org Product metadata) | whole product, price |

## How it works

1. Fetches the product page you added (plain HTTPS GET, like a mobile browser).
   - Terminal X pages embed the full product state — every color/size variant
     with live stock status, prices and promo badges — in a
     `window.__INITIAL_STATE__` JSON blob.
   - Shopify shops serve machine-readable product JSON at
     `/products/<handle>.js` with per-variant `available` and prices.
   - Other shops: the schema.org `Product` JSON-LD embedded for search engines
     gives whole-product availability and price.
2. Compares against the last known state of what you track and notifies on:
   - **Restock** — a tracked size (or product) flips from out-of-stock to in-stock.
   - **Price drop** — the tracked item got cheaper since the last check.
   - **New promotion** — a promo badge (e.g. sale/LAST CALL) appeared.
   - **New arrival** — a saved search ("search watch") found a product it has
     never seen before whose name matches every word of the query.
   Tapping a notification opens the product page so you can buy it.

### Search watches

The "Watch search" tab saves a query per shop (Terminal X, Originals,
Foot Locker IL, Laline, Fox Home, or any Shopify shop by address). Each
background cycle re-runs the search: Terminal X results come from the
server-rendered search page; on Shopify shops the app reads the standard
`/search?q=…&type=product` page and resolves only never-seen-before handles
via `/products/<handle>.js`, so periodic checks stay cheap. Because shop
search is fuzzy (searching "novablast 6" also returns Novablast 5), a watch
only alerts on results containing **every** word of the query, with bare
numbers matched as standalone tokens. Everything visible when the watch is
created is treated as old news — only later arrivals alert.

Note: fox.co.il renders search results client-side, so search watches don't
work there (item tracking does). The app tells you at preview time.

Background checks run through WorkManager on a user-configurable interval
(15 min – 6 h, default 30 min), only when the network is up. A manual
"check now" button in the top bar re-checks everything immediately.

## Using the app

1. Open a product in the shop's site or app and copy its link — or use the
   system **Share** sheet and pick **Item Notifier**.
2. In the app, tap **+**, paste the link, and tap **Load product**.
3. Pick the color (when the product has several) and tap the size(s) you want to
   watch — struck-through sizes are the ones currently out of stock. On shops
   without per-size data you track the whole product instead.
4. Tap **Track this item**. You'll get notified on restock, price drops and
   new promotions.

Any number of items can be tracked; each row shows live status, price (with
pre-sale price when discounted) and promo badge, and items sharing a product
page are checked with a single request.

## Building

Requirements: JDK 17+, Android SDK (compileSdk 35). Then:

```bash
./gradlew :app:assembleDebug     # APK at app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest # parser tests against a captured real page
```

Install on a device with `adb install app/build/outputs/apk/debug/app-debug.apk`
(or open the project in Android Studio and press Run).

## Project layout

```
app/src/main/java/com/arikw/itemnotifier/
├── ItemNotifierApp.kt          # notification channel + periodic work scheduling
├── MainActivity.kt             # navigation, share-intent handling, permissions
├── data/
│   ├── ItemRepository.kt       # check-all-and-notify core logic
│   ├── Prefs.kt                # check-interval setting
│   ├── db/                     # Room: TrackedItem entity + DAO (+ migrations)
│   ├── model/Product.kt        # parsed product/variant models
│   └── network/                # site adapters: TerminalX, Shopify, JSON-LD
├── notifications/Notifier.kt   # restock + deal notifications
├── worker/StockCheckWorker.kt  # WorkManager periodic + one-shot jobs
└── ui/                         # Compose screens (item list, add item)
```

## Notes & limitations

- **Coupon codes can't be auto-validated.** Checking whether a coupon code
  works requires creating a cart and applying the code through each shop's
  checkout API — fragile, bot-protected, and against most shops' terms.
  Instead the app tracks what shops publish openly: price drops, sale
  (compare-at) prices and promo badges on the items you track.
- If a shop changes its page structure, its tracked items will show
  "Check failed" until the matching adapter is updated.
- Shopify's product JSON carries no currency; the app assumes ₪ (ILS), which
  is right for the Israeli shops it targets.
- WorkManager's minimum periodic interval is 15 minutes, and Android may defer
  checks in Doze mode. For the most reliable timing, exclude the app from
  battery optimization (Settings → Apps → Item Notifier → Battery → Unrestricted).
- Checks are polite: one page request per tracked product per cycle — roughly
  what a person refreshing the page would generate.
