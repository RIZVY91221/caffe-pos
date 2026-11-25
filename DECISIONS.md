# Design Decisions & Checkpoints

## Checkpoint C1 — Floating-point risk and VAT rounding

Storing money in `Double` is risky because binary floating-point cannot represent base-10 fractions like `0.01` or `1094.31` exactly. Over repeated additions or percentage multiplications, small representation errors (e.g. `1094.3099999999999`) accumulate and can flip a `.5` boundary the wrong way during rounding. Keeping everything in `Long` poisha (1 BDT = 100 poisha) keeps all arithmetic exact in integer math until we explicitly round.

For a VAT of `47.025` BDT:
- Discounted subtotal in O3 is `940.50` BDT (`94,050` poisha).
- `5%` of `94,050` poisha = `4,702.5` poisha (`47.025` BDT).
- Half-up to the nearest poisha: `(94050 * 5 + 50) / 100 = 470300 / 100 = 4,703` poisha = `47.03` BDT.

---

## Decision D1 — Server time while offline

Whenever `CatalogRepository` or `ServerTimeRepository` gets a response from `FakeApi`, it computes `offsetMs = serverTime - System.currentTimeMillis()` and persists it in Room (`server_time_offset`). Offline or after an app restart, `getServerTime()` reads `System.currentTimeMillis() + offsetMs` and converts to `Asia/Dhaka` (with a 150-second fallback if no sync has happened yet). This keeps pricing, happy hour, coupon expiry, and business-day grouping independent of the phone's clock.

---

## Decision D2 — Order states and transitions

- `PAID`: Written atomically to Room (`orders`, `order_lines`, `payments`) before the cashier sees the "Order Complete" screen.
- `PENDING`: Queued in WorkManager for upload. Cashier sees an orange `PENDING` badge in the Orders list.
- `SYNCING`: `OrderSyncWorker` is actively posting to `FakeApi`. Cashier sees a blue `SYNCING` badge.
- `SYNCED`: Server confirmed and returned `serverOrderNo`. Cashier sees a green `SYNCED` badge with the server order number.
- `FAILED`: Reached after 5 failed upload attempts. Stays visible in red with a "Retry Sync" button; nothing is ever deleted.

---

## Decision D3 — When `clientOrderId` and card `ref` are created

Both `clientOrderId` and `cardRef` are generated as UUIDs and saved into `SavedStateHandle` before calling `LegacyCardTerminal.charge()` and before writing to Room. If the process is killed while waiting on the card terminal, the same IDs survive restoration so we never issue a second `charge()` for an already-started card attempt, and any retried `postOrder` uses the same `clientOrderId` for server idempotency.

---

## Decision D4 — Split payment order

Card is processed first, then cash. Taking cash first and then hitting a card decline forces the cashier to hand cash back and either cancel or re-key the tender. Charging the card first means no physical cash changes hands until the card portion is approved; if the card declines, the order stays unpaid and the cashier can immediately switch to full cash or another card.

---

## Decision D5 — Catalog changes while orders are unsynced

Each `OrderLineEntity` stores a snapshot of `unitPricePoisha` and `lineTotalPoisha` calculated at checkout time. `OrderSyncWorker` uploads those stored line totals rather than recomputing from the current catalog. If the catalog bumps to v2 while v1 orders are still queued offline, the synced payload matches what the customer actually paid and what was printed on their receipt.

---

## Decision D6 — Room schema and JSON shapes

Room tables (`AppDatabase`):
- `orders`: `clientOrderId` (PK), `receiptNo`, `serverOrderNo`, `status`, `payablePoisha`, `serverTimeMs`, `createdAtMs`, `syncAttempts`, `isDuplicate`
- `order_lines`: `id` (PK), `orderId` (FK -> `orders`), `productId`, `productName`, `quantity`, `unitPricePoisha`, `lineTotalPoisha`, `modifiersJson`
- `payments`: `id` (PK), `orderId` (FK -> `orders`), `type` (`CASH`/`CARD`), `amountPoisha`, `cardRef`, `authCode`
- `catalog_cache`: singleton row holding `version`, `catalogJson`, `fetchedAtMs`
- `server_time_offset`: singleton row holding `offsetMs`, `fetchedAtMs`
- `receipt_sequences`: `dateKey` (`yyyyMMdd` in server date), `lastSequence`

`modifiersJson` stores an array of `{ "optionId", "optionName", "quantity", "pricePoisha" }`. `OrderRequest` sent to `FakeApi.postOrder()` mirrors `clientOrderId`, `receiptNo`, `lines`, `payments`, `payablePoisha`, and `serverTime`.

---

## Checkpoint C2 — Card wrapper callback cases

`CardTerminalWrapper.charge()` uses `withTimeoutOrNull(15_000L)` around `suspendCancellableCoroutine` guarded by an `AtomicBoolean`:
- **(a) Arrives once:** `compareAndSet(false, true)` succeeds and resumes the coroutine with `Approved` or `Declined`.
- **(b) Arrives twice (tail `55`):** The first callback flips `AtomicBoolean` to `true` and resumes; the second callback sees `compareAndSet(false, true) == false` and is ignored, so only one payment is recorded.
- **(c) Never arrives (tail `77`):** `withTimeoutOrNull(15_000L)` returns `null` after 15 seconds. We call `terminal.queryStatus(ref)` (which never charges) and return `Approved` (or `Declined` / `Unknown`).
- **(d) Coroutine cancelled:** If the coroutine is cancelled (e.g., process death or navigation), `charge()` is never called again with a new attempt; `cardRef` is persisted in `SavedStateHandle` so recovery checks terminal status rather than double-charging.

---

## Checkpoint C3 — POST reaches server, app killed before reading response

1. On next launch, WorkManager reschedules the pending `OrderSyncWorker` with the same `clientOrderId`.
2. `OrderSyncWorker` reads the `PENDING` order and its lines/payments from Room and calls `fakeApi.postOrder(request)` with the same `clientOrderId`.
3. `FakeApiImpl` finds `clientOrderId` in `savedOrders` and returns the original `serverOrderNo` with `duplicate = true` without creating a second order.
4. `OrderSyncWorker` updates the local Room row to `SYNCED`, saving `serverOrderNo` and `isDuplicate = true`.

---

## Checkpoint C4 — Offline sale when phone clock says 19 November

The sale belongs to **20 November**. `ServerTimeRepository` adds the persisted `offsetMs` (computed when online from the server's clock) to the current millis, rather than trusting the device's wall clock date. Both `serverTimeMs` on the order row and the `T1-yyyyMMdd-0001` receipt number use that server-derived `Asia/Dhaka` date, so the Sales Summary places the order on 20 November.
