# Offline-First Café POS

Offline-first point-of-sale Android application built with Kotlin, Jetpack Compose, Hilt, Room, and WorkManager.

## Demo

- [Screen Recording](demo/screen_record.mp4)

## Setup

1. Open the project in Android Studio (JDK 17, Android SDK 35).
2. Run unit tests:
   ```bash
   ./gradlew test
   ```
3. Build debug APK:
   ```bash
   ./gradlew assembleDebug
   ```
   Output APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Architecture

- **`core/`** — Pure Kotlin domain models, `CatalogValidator.validate()`, and `PricingEngine.priceOrder()`. No Android dependencies; all money values are `Long` poisha.
- **`data/api/`** — `FakeApi` interface and `FakeApiImpl` in-memory implementation with simulated latency, network/HTTP 500 failures, idempotent `postOrder`, and a +150s server clock.
- **`data/terminal/`** — Unchanged `LegacyCardTerminal.java` wrapped by `CardTerminalWrapper` using `suspendCancellableCoroutine`, `AtomicBoolean` duplicate-callback guard, and a 15-second `queryStatus` fallback.
- **`data/db/` & `data/repository/`** — Room entities/DAOs (`orders`, `order_lines`, `payments`, `catalog_cache`, `server_time_offset`, `receipt_sequences`), `CartStore`, `CatalogRepository`, `ServerTimeRepository`, `OrderRepository`, and `SeedManager` (loads Appendix B seed orders on first launch).
- **`data/sync/`** — `OrderSyncWorker` (WorkManager with `NetworkType.CONNECTED` and exponential backoff, marking `FAILED` after 5 attempts).
- **`ui/`** — Jetpack Compose screens (`CatalogScreen`, `CartScreen`, `OrdersScreen`, `SummaryScreen`) backed by Hilt ViewModels and `StateFlow`.

## Trade-offs

Detailed rationale for D1–D6 and C1–C4 is in [DECISIONS.md](DECISIONS.md), and calculated Appendix B answers are in [ANSWERS.md](ANSWERS.md).

- Kept `core/` inside `:app` rather than splitting a separate Gradle module to keep setup simple while still keeping zero Android imports in `core/`.
- Used `SavedStateHandle` for `clientOrderId` and `cardRef` instead of a separate `in_flight_payments` table; simple and survives process death, though a multi-terminal setup would benefit from persisting in-flight intents in Room as well.

## Unfinished / Bonus (Part F)

- Live WebSocket order board (Part F) and GitHub Actions workflow were not implemented; all mandatory Parts A–E and TC-01–TC-18 are complete.

## Actual Hours Spent

~14 hours across architecture/decisions (2h), core validator & pricing engine (3h), Room/FakeApi/WorkManager sync (4h), card terminal wrapper & tests (2h), and Compose UI + documentation (3h).
