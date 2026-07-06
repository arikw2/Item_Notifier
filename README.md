# Item Notifier

Android app that watches [Terminal X](https://www.terminalx.com) product pages and
notifies you when the size you want is back in stock — e.g. the ASICS Novablast 5
in size 44.

## How it works

Terminal X product pages are server-side rendered and embed the complete product
state — every color/size variant and its live stock status — in a
`window.__INITIAL_STATE__` JSON blob. The app:

1. Fetches the product page you added (plain HTTPS GET, like a mobile browser).
2. Extracts and parses that JSON (`app/.../data/network/TerminalXParser.kt`),
   reading each variant's `stock_status2` (`IN_STOCK` / `OUT_OF_STOCK`).
3. Compares against the last known status of the sizes you track and fires a
   high-priority notification when a size flips from out-of-stock to in-stock.
   Tapping the notification opens the product page so you can buy it.

Background checks run through WorkManager on a user-configurable interval
(15 min – 6 h, default 30 min), only when the network is up. A manual
"check now" button in the top bar re-checks everything immediately.

## Using the app

1. Open a product on terminalx.com (or in the Terminal X app) and copy its link —
   or use the system **Share** sheet and pick **Item Notifier**.
2. In the app, tap **+**, paste the link, and tap **Load product**.
3. Pick the color (when the product has several) and tap the size(s) you want to
   watch — struck-through sizes are the ones currently out of stock.
4. Tap **Track this item**. You'll get a notification when the size comes back.

Any number of items can be tracked; each tracked size shows its live status,
and items sharing a product page are checked with a single request.

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
│   ├── db/                     # Room: TrackedItem entity + DAO
│   ├── model/Product.kt        # parsed product/variant models
│   └── network/                # OkHttp client + __INITIAL_STATE__ parser
├── notifications/Notifier.kt   # "back in stock" notifications
├── worker/StockCheckWorker.kt  # WorkManager periodic + one-shot jobs
└── ui/                         # Compose screens (item list, add item)
```

## Notes & limitations

- **Terminal X only.** The parser is specific to Terminal X's page structure.
  If the site changes its rendering, tracked items will show "Check failed"
  until the parser is updated.
- WorkManager's minimum periodic interval is 15 minutes, and Android may defer
  checks in Doze mode. For the most reliable timing, exclude the app from
  battery optimization (Settings → Apps → Item Notifier → Battery → Unrestricted).
- Checks are polite: one page request per tracked product per cycle — roughly
  what a person refreshing the page would generate.
